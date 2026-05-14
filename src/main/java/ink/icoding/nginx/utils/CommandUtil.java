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

    public static void setSshSessionManager(SshSessionManager manager) {
        sshSessionManager = manager;
    }

    public static SshSessionManager getSshSessionManager() {
        return sshSessionManager;
    }

    public static boolean isSshEnabled() {
        return sshSessionManager != null;
    }

    public static boolean isLocalNginx(){
        return sshSessionManager.isLocalNginx();
    }

    private CommandUtil() {
    }

    // ==================== 一次性执行 ====================

    public static CommandResult execute(String... command) {
        return execute(false, null, null, -1, command);
    }

    public static CommandResult execute(boolean nginxCMD, String... command) {
        return execute(nginxCMD, null, null, -1, command);
    }

    public static CommandResult execute(File workDir, String... command) {
        return execute(false, workDir, null, -1, command);
    }

    public static CommandResult execute(long timeoutMs, String... command) {
        return execute(false, null, null, timeoutMs, command);
    }

    public static CommandResult execute(boolean isLocalNginx, long timeoutMs, String... command) {
        return execute(isLocalNginx, null, null, timeoutMs, command);
    }

    public static CommandResult execute(File workDir, long timeoutMs, String... command) {
        return execute(false, workDir, null, timeoutMs, command);
    }

    public static CommandResult execute(Charset charset, String... command) {
        return execute(false, null, charset, -1, command);
    }

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

    private static String toSshCommand(String... command) {
        if (command.length == 1) {
            return command[0];
        }
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

    public static CommandStream stream(String command, Consumer<String> listener) {
        return stream(null, null, command, listener);
    }

    public static CommandStream stream(File workDir, String command, Consumer<String> listener) {
        return stream(workDir, null, command, listener);
    }

    public static CommandStream stream(Charset charset, String command, Consumer<String> listener) {
        return stream(null, charset, command, listener);
    }

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

    public static CompletableFuture<CommandResult> executeAsync(String... command) {
        return CompletableFuture.supplyAsync(() -> execute(command), EXECUTOR);
    }

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

    // ==================== 流式命令实现 ====================

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
}
