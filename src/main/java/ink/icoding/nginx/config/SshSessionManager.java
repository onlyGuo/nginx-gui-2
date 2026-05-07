package ink.icoding.nginx.config;

import com.jcraft.jsch.*;
import ink.icoding.nginx.utils.CommandUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Component
public class SshSessionManager {

    private final SshConfig config;
    private Session session;
    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ssh-command-worker");
        t.setDaemon(true);
        return t;
    });

    public SshSessionManager(SshConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("SSH 未配置，使用本地命令执行");
            return;
        }
        log.info("SSH 已启用: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
        CommandUtil.setSshSessionManager(this);
    }

    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
        disconnect();
    }

    private Session createSession() throws JSchException {
        JSch jsch = new JSch();

        if (config.getPrivateKey() != null && !config.getPrivateKey().isBlank()) {
            if (config.getPassphrase() != null && !config.getPassphrase().isBlank()) {
                jsch.addIdentity(config.getPrivateKey(), config.getPassphrase());
            } else {
                jsch.addIdentity(config.getPrivateKey());
            }
        }

        Session sess = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        sess.setConfig("StrictHostKeyChecking", "no");

        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            sess.setPassword(config.getPassword());
        }

        sess.connect(10000);
        return sess;
    }

    public Session getSession() throws JSchException {
        synchronized (lock) {
            if (session != null && session.isConnected()) {
                return session;
            }
            disconnect();
            session = createSession();
            log.info("SSH 连接已建立: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
            return session;
        }
    }

    private void disconnect() {
        synchronized (lock) {
            if (session != null) {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
                session = null;
            }
        }
    }

    public CommandUtil.CommandResult executeCommand(String command) {
        try {
            Session sess = getSession();
            ChannelExec channel = (ChannelExec) sess.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();

            channel.connect(30000);

            String out = readStream(stdout);
            String err = readStream(stderr);

            // 等待命令完成
            while (!channel.isClosed()) {
                Thread.sleep(50);
            }

            int exitCode = channel.getExitStatus();
            channel.disconnect();

            return new CommandUtil.CommandResult(exitCode, out, err);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandUtil.CommandResult.error("SSH 命令执行被中断: " + e.getMessage());
        } catch (JSchException e) {
            // 连接断开，清除缓存
            disconnect();
            return CommandUtil.CommandResult.error("SSH 连接异常: " + e.getMessage());
        } catch (Exception e) {
            return CommandUtil.CommandResult.error("SSH 命令执行异常: " + e.getMessage());
        }
    }

    public CommandUtil.CommandStream streamCommand(String command, Consumer<String> listener) {
        try {
            Session sess = getSession();
            ChannelExec channel = (ChannelExec) sess.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            InputStream stdout;
            InputStream stderr;
            try {
                stdout = channel.getInputStream();
                stderr = channel.getErrStream();
            } catch (IOException e) {
                channel.disconnect();
                throw new CommandUtil.CommandException("SSH 获取流失败: " + e.getMessage(), e);
            }

            channel.connect();

            Future<?> stdoutFuture = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            listener.accept(line);
                        } catch (Exception ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            });

            Future<?> stderrFuture = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
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

            return new SshCommandStream(channel, stdoutFuture, stderrFuture);

        } catch (JSchException e) {
            disconnect();
            throw new CommandUtil.CommandException("SSH 流式命令启动失败: " + e.getMessage(), e);
        }
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) sb.append(System.lineSeparator());
                sb.append(line);
                first = false;
            }
        }
        return sb.toString();
    }

    private static class SshCommandStream implements CommandUtil.CommandStream {
        private final ChannelExec channel;
        private final Future<?> stdoutFuture;
        private final Future<?> stderrFuture;

        SshCommandStream(ChannelExec channel, Future<?> stdoutFuture, Future<?> stderrFuture) {
            this.channel = channel;
            this.stdoutFuture = stdoutFuture;
            this.stderrFuture = stderrFuture;
        }

        @Override
        public void stop() {
            channel.disconnect();
            stdoutFuture.cancel(true);
            stderrFuture.cancel(true);
        }

        @Override
        public boolean isRunning() {
            return !channel.isClosed();
        }

        @Override
        public int await() throws InterruptedException {
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }
            try {
                stdoutFuture.get();
                stderrFuture.get();
            } catch (ExecutionException ignored) {
            }
            return channel.getExitStatus();
        }

        @Override
        public boolean await(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!channel.isClosed() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            if (channel.isClosed()) {
                try {
                    stdoutFuture.get(3, TimeUnit.SECONDS);
                    stderrFuture.get(3, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        }

        @Override
        public Process getProcess() {
            return null;
        }
    }
}
