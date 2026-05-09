package ink.icoding.nginx.core;

public class NginxException extends RuntimeException {
    public NginxException(String message) {
        super(message);
    }

    public NginxException(String message, Throwable cause) {
        super(message, cause);
    }
}
