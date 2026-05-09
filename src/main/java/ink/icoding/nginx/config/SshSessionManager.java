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
    private ChannelSftp sftpChannel;
    private volatile long sessionConnectedAt;
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

    public boolean isLocalNginx(){
        return config.isLocalNginx();
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
                log.info("复用 SSH 连接: {}@{}:{}, connectedAt={}, ageMs={}",
                        config.getUsername(), config.getHost(), config.getPort(),
                        sessionConnectedAt,
                        sessionConnectedAt > 0 ? (System.currentTimeMillis() - sessionConnectedAt) : -1);
                return session;
            }
            disconnect();
            session = createSession();
            sessionConnectedAt = System.currentTimeMillis();
            log.info("SSH 连接已建立: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
            return session;
        }
    }

    private void disconnect() {
        synchronized (lock) {
            disconnectSftpChannelLocked();
            if (session != null) {
                try {
                    session.disconnect();
                } catch (Exception ignored) {
                }
                session = null;
                sessionConnectedAt = 0L;
            }
        }
    }

    private ChannelSftp getSftpChannelLocked() throws JSchException {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            return sftpChannel;
        }
        Session sess = getSession();
        ChannelSftp channel = (ChannelSftp) sess.openChannel("sftp");
        channel.connect(30000);
        sftpChannel = channel;
        log.info("SFTP 通道已建立: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
        return sftpChannel;
    }

    private void disconnectSftpChannelLocked() {
        if (sftpChannel != null) {
            try {
                sftpChannel.disconnect();
            } catch (Exception ignored) {
            }
            sftpChannel = null;
        }
    }

    public <T> T executeSftp(String action, SftpCallback<T> callback) {
        synchronized (lock) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    return callback.doWithChannel(getSftpChannelLocked());
                } catch (JSchException | SftpException e) {
                    log.warn("SFTP 操作失败: action={}, attempt={}, message={}", action, attempt, e.getMessage());
                    disconnect();
                    if (attempt == 2) {
                        throw new RuntimeException("SFTP 操作失败: " + action, e);
                    }
                }
            }
        }
        throw new RuntimeException("SFTP 操作失败: " + action);
    }

    public CommandUtil.CommandResult executeCommand(String command) {
        long start = System.currentTimeMillis();
        log.debug("SSH exec start: command='{}', startMs={}", command, start);
        try {
            Session sess = getSession();
            long afterGetSession = System.currentTimeMillis();
            log.debug("SSH exec stage[getSession]: command='{}', costMs={}, sessionHash={}",
                    command, afterGetSession - start, System.identityHashCode(sess));

            ChannelExec channel = (ChannelExec) sess.openChannel("exec");
            long afterOpenChannel = System.currentTimeMillis();
            log.debug("SSH exec stage[openChannel]: command='{}', costMs={}",
                    command, afterOpenChannel - afterGetSession);

            channel.setCommand(command);
            channel.setInputStream(null);
            long afterSetCommand = System.currentTimeMillis();
            log.debug("SSH exec stage[setCommand]: command='{}', costMs={}",
                    command, afterSetCommand - afterOpenChannel);

            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            long afterSetupStreams = System.currentTimeMillis();
            log.debug("SSH exec stage[setupStreams]: command='{}', costMs={}",
                    command, afterSetupStreams - afterSetCommand);

            channel.connect(30000);
            long afterConnect = System.currentTimeMillis();
            log.debug("SSH exec stage[channel.connect]: command='{}', costMs={}, channelConnected={}",
                    command, afterConnect - afterSetupStreams, channel.isConnected());

            String out = readStream(stdout);
            long afterReadStdout = System.currentTimeMillis();
            log.debug("SSH exec stage[readStdout]: command='{}', costMs={}, stdoutChars={}, channelClosed={}",
                    command, afterReadStdout - afterConnect, out.length(), channel.isClosed());

            String err = readStream(stderr);
            long afterReadStderr = System.currentTimeMillis();
            log.debug("SSH exec stage[readStderr]: command='{}', costMs={}, stderrChars={}, channelClosed={}",
                    command, afterReadStderr - afterReadStdout, err.length(), channel.isClosed());

            // 等待命令完成
            int waitLoops = 0;
            while (!channel.isClosed()) {
                waitLoops++;
                Thread.sleep(50);
            }
            long afterWaitClose = System.currentTimeMillis();
            log.debug("SSH exec stage[waitChannelClose]: command='{}', costMs={}, loops={}, exitStatus={}",
                    command, afterWaitClose - afterReadStderr, waitLoops, channel.getExitStatus());

            int exitCode = channel.getExitStatus();
            channel.disconnect();
            long afterDisconnect = System.currentTimeMillis();
            log.debug("SSH exec done: command='{}', exitCode={}, totalMs={}, disconnectCostMs={}, stdoutChars={}, stderrChars={}",
                    command, exitCode, afterDisconnect - start, afterDisconnect - afterWaitClose, out.length(), err.length());

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

    @FunctionalInterface
    public interface SftpCallback<T> {
        T doWithChannel(ChannelSftp channel) throws SftpException, JSchException;
    }
}
