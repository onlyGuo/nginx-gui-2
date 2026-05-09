package ink.icoding.nginx.utils;

import ink.icoding.nginx.config.SshSessionManager;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 跨平台命令执行工具类
 * <p>
 * 支持一次性执行和流式执行，兼容 Windows / Linux / macOS。
 *
 * <pre>
 * // 一次性执行
 * CommandResult result = CommandUtil.execute("nginx -t");
 * if (result.isSuccess()) { ... }
 *
 * // 流式执行 (如 tail -f)
 * CommandStream stream = CommandUtil.stream("tail -f /var/log/nginx/access.log", line -> {
 *     System.out.println(line);
 * });
 * // 稍后停止
 * stream.stop();
 * </pre>
 */
public final class CommandUtil {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final Charset DEFAULT_CHARSET = IS_WINDOWS ? Charset.forName("GBK") : StandardCharsets.UTF_8;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "command-util-worker");
        t.setDaemon(true);
        return t;
    });

    private static volatile SshSessionManager sshSessionManager;

    /**
     * 设置 SSH 会话管理器（由 Spring 配置在启动时调用）
     */
    public static void setSshSessionManager(SshSessionManager manager) {
        sshSessionManager = manager;
    }

    public static SshSessionManager getSshSessionManager() {
        return sshSessionManager;
    }

    /**
     * 是否启用了 SSH 远程执行
     */
    public static boolean isSshEnabled() {
        return sshSessionManager != null;
    }

    public static boolean isLocalNginx(){
        return sshSessionManager.isLocalNginx();
    }

    private CommandUtil() {
    }

    // ==================== 一次性执行 ====================

    /**
     * 执行命令，等待完成并返回结果
     *
     * @param command 命令字符串（会按空格拆分）或可变参数命令
     * @return 命令执行结果
     */
    public static CommandResult execute(String... command) {
        return execute(false, null, null, -1, command);
    }

    /**
     * 执行命令，等待完成并返回结果
     *
     * @param command 命令字符串（会按空格拆分）或可变参数命令
     * @return 命令执行结果
     */
    public static CommandResult execute(boolean nginxCMD, String... command) {
        return execute(nginxCMD, null, null, -1, command);
    }

    /**
     * 执行命令，指定工作目录
     */
    public static CommandResult execute(File workDir, String... command) {
        return execute(false, workDir, null, -1, command);
    }

    /**
     * 执行命令，指定超时时间（毫秒）
     */
    public static CommandResult execute(long timeoutMs, String... command) {
        return execute(false, null, null, timeoutMs, command);
    }

    /**
     * 执行命令，指定工作目录和超时时间
     */
    public static CommandResult execute(File workDir, long timeoutMs, String... command) {
        return execute(false, workDir, null, timeoutMs, command);
    }

    /**
     * 执行命令，指定字符集
     */
    public static CommandResult execute(Charset charset, String... command) {
        return execute(false, null, charset, -1, command);
    }

    /**
     * 执行命令，全参数版本
     *
     * @param workDir  工作目录，null 表示继承当前进程
     * @param charset  字符集，null 则自动检测
     * @param timeoutMs 超时时间（毫秒），-1 表示不超时
     * @param command  命令
     * @return 命令执行结果
     */
    public static CommandResult execute(boolean nginxCMD, File workDir, Charset charset, long timeoutMs, String... command) {
        SshSessionManager ssh = sshSessionManager;
        if (nginxCMD) {
            if (ssh != null && !isLocalNginx()) {
                return ssh.executeCommand(toSshCommand(command));
            }
        }else{
            if (ssh != null) {
                return ssh.executeCommand(toSshCommand(command));
            }
        }
        return executeLocal(workDir, charset, timeoutMs, command);
    }

    /**
     * 将命令参数转为 SSH 可执行的命令字符串
     * <p>
     * 本地执行时 ProcessBuilder 会用 /bin/sh -c 包装，但 SSH 的 ChannelExec
     * 本身就在远程 shell 中执行，需要去掉这个包装。
     */
    private static String toSshCommand(String... command) {
        if (command.length == 1) {
            return command[0];
        }
        // 去掉 "/bin/sh", "-c", cmd 或 "cmd", "/c", cmd 这种 shell 包装
        if (command.length == 3 && "-c".equals(command[1])
                && ("/bin/sh".equals(command[0]) || "cmd".equals(command[0]))) {
            return command[2];
        }
        return String.join(" ", command);
    }

    private static CommandResult executeLocal(File workDir, Charset charset, long timeoutMs, String... command) {
        Process process = null;
        try {
            process = startProcess(workDir, command);
            Charset cs = charset != null ? charset : DEFAULT_CHARSET;

            String stdout = readStream(process.getInputStream(), cs);
            String stderr = readStream(process.getErrorStream(), cs);

            boolean finished;
            if (timeoutMs > 0) {
                finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return CommandResult.timeout(stderr);
                }
            } else {
                process.waitFor();
            }

            int exitCode = process.exitValue();
            return new CommandResult(exitCode, stdout, stderr);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandResult.error("命令执行被中断: " + e.getMessage());
        } catch (Exception e) {
            return CommandResult.error("命令执行异常: " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    // ==================== 流式执行 ====================

    /**
     * 启动一个流式命令，通过监听器实时接收输出
     * <p>
     * 适用于 tail -f、持续日志监控等场景。
     *
     * @param command  命令
     * @param listener 每行输出的监听回调
     * @return CommandStream，可用于停止命令
     */
    public static CommandStream stream(String command, Consumer<String> listener) {
        return stream(null, null, command, listener);
    }

    /**
     * 启动流式命令，指定工作目录
     */
    public static CommandStream stream(File workDir, String command, Consumer<String> listener) {
        return stream(workDir, null, command, listener);
    }

    /**
     * 启动流式命令，指定字符集
     */
    public static CommandStream stream(Charset charset, String command, Consumer<String> listener) {
        return stream(null, charset, command, listener);
    }

    /**
     * 启动流式命令，全参数版本
     *
     * @param workDir  工作目录
     * @param charset  字符集
     * @param command  命令
     * @param listener 每行输出的监听回调
     * @return CommandStream，可用于停止命令
     */
    public static CommandStream stream(File workDir, Charset charset, String command, Consumer<String> listener) {
        SshSessionManager ssh = sshSessionManager;
        if (ssh != null) {
            return ssh.streamCommand(command, listener);
        }
        return streamLocal(workDir, charset, command, listener);
    }

    private static CommandStream streamLocal(File workDir, Charset charset, String command, Consumer<String> listener) {
        Charset cs = charset != null ? charset : DEFAULT_CHARSET;
        Process process;
        try {
            process = startProcess(workDir, splitCommand(command));
        } catch (IOException e) {
            throw new CommandException("启动流式命令失败: " + e.getMessage(), e);
        }

        Future<?> stdoutFuture = EXECUTOR.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), cs))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        listener.accept(line);
                    } catch (Exception e) {
                        // 监听器异常不影响流的继续读取
                    }
                }
            } catch (IOException e) {
                // 进程被终止时会抛出，正常退出
            }
        });

        Future<?> stderrFuture = EXECUTOR.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), cs))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        listener.accept("[ERROR] " + line);
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        });

        return new CommandStreamImpl(process, stdoutFuture, stderrFuture);
    }

    // ==================== 异步执行 ====================

    /**
     * 异步执行命令，返回 Future
     */
    public static CompletableFuture<CommandResult> executeAsync(String... command) {
        return CompletableFuture.supplyAsync(() -> execute(command), EXECUTOR);
    }

    /**
     * 异步执行命令，指定工作目录
     */
    public static CompletableFuture<CommandResult> executeAsync(File workDir, String... command) {
        return CompletableFuture.supplyAsync(() -> execute(workDir, command), EXECUTOR);
    }

    // ==================== 内部方法 ====================

    private static Process startProcess(File workDir, String... command) throws IOException {
        String[] cmd = buildPlatformCommand(command);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workDir != null) {
            pb.directory(workDir);
        }
        pb.redirectErrorStream(false);
        return pb.start();
    }

    private static String[] buildPlatformCommand(String... command) {
        if (command.length == 1) {
            return splitCommand(command[0]);
        }
        return command;
    }

    /**
     * 按平台拆分命令字符串
     * <p>
     * Windows 下使用 cmd /c 包装，Linux/macOS 使用 sh -c 包装，
     * 以确保管道、重定向等 shell 特性能正常工作。
     */
    private static String[] splitCommand(String command) {
        if (IS_WINDOWS) {
            return new String[]{"cmd", "/c", command};
        }
        return new String[]{"/bin/sh", "-c", command};
    }

    private static String readStream(InputStream is, Charset charset) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    sb.append(System.lineSeparator());
                }
                sb.append(line);
                first = false;
            }
        }
        return sb.toString();
    }

    // ==================== 结果类 ====================

    /**
     * 命令执行结果
     */
    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final boolean success;
        private final boolean timeout;
        private final String errorMessage;

        private CommandResult(int exitCode, String stdout, String stderr, boolean timeout, String errorMessage) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.success = exitCode == 0 && !timeout && errorMessage == null;
            this.timeout = timeout;
            this.errorMessage = errorMessage;
        }

        public CommandResult(int exitCode, String stdout, String stderr) {
            this(exitCode, stdout, stderr, false, null);
        }

        public static CommandResult timeout(String stderr) {
            return new CommandResult(-1, "", stderr, true, "命令执行超时");
        }

        public static CommandResult error(String message) {
            return new CommandResult(-1, "", "", false, message);
        }

        /** 退出码，0 表示成功 */
        public int getExitCode() {
            return exitCode;
        }

        /** 标准输出内容 */
        public String getStdout() {
            return stdout;
        }

        /** 标准错误内容 */
        public String getStderr() {
            return stderr;
        }

        /** 合并输出（stdout + stderr） */
        public String getOutput() {
            if (stderr.isEmpty()) return stdout;
            if (stdout.isEmpty()) return stderr;
            return stdout + System.lineSeparator() + stderr;
        }

        /** 是否执行成功（退出码为 0 且未超时） */
        public boolean isSuccess() {
            return success;
        }

        /** 是否超时 */
        public boolean isTimeout() {
            return timeout;
        }

        /** 错误信息（执行异常时才有值） */
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (errorMessage != null) {
                return "CommandResult{error=" + errorMessage + "}";
            }
            return "CommandResult{exitCode=" + exitCode
                    + ", timeout=" + timeout
                    + ", stdout='" + truncate(stdout, 200) + "'"
                    + ", stderr='" + truncate(stderr, 200) + "'"
                    + "}";
        }

        private static String truncate(String s, int maxLen) {
            if (s == null) return "";
            return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
        }
    }

    // ==================== 流式命令控制 ====================

    /**
     * 流式命令控制句柄
     */
    public interface CommandStream {
        /** 停止命令执行 */
        void stop();

        /** 命令是否仍在运行 */
        boolean isRunning();

        /** 等待命令结束，返回退出码 */
        int await() throws InterruptedException;

        /** 等待命令结束，带超时，返回是否正常结束 */
        boolean await(long timeoutMs) throws InterruptedException;

        /** 获取底层 Process（高级用法） */
        Process getProcess();
    }

    private static class CommandStreamImpl implements CommandStream {
        private final Process process;
        private final Future<?> stdoutFuture;
        private final Future<?> stderrFuture;

        CommandStreamImpl(Process process, Future<?> stdoutFuture, Future<?> stderrFuture) {
            this.process = process;
            this.stdoutFuture = stdoutFuture;
            this.stderrFuture = stderrFuture;
        }

        @Override
        public void stop() {
            process.destroy();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
        }

        @Override
        public boolean isRunning() {
            return process.isAlive();
        }

        @Override
        public int await() throws InterruptedException {
            int code = process.waitFor();
            try {
                stdoutFuture.get();
                stderrFuture.get();
            } catch (ExecutionException e) {
                // 读取流时异常，不影响退出码
            }
            return code;
        }

        @Override
        public boolean await(long timeoutMs) throws InterruptedException {
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (finished) {
                try {
                    stdoutFuture.get(3, TimeUnit.SECONDS);
                    stderrFuture.get(3, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }
            return finished;
        }

        @Override
        public Process getProcess() {
            return process;
        }
    }

    // ==================== 异常类 ====================

    /**
     * 命令执行异常
     */
    public static class CommandException extends RuntimeException {
        public CommandException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
