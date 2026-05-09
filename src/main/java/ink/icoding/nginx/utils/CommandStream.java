package ink.icoding.nginx.utils;

public interface CommandStream {
    void stop();
    boolean isRunning();
    int await() throws InterruptedException;
    boolean await(long timeoutMs) throws InterruptedException;
    Process getProcess();
}
