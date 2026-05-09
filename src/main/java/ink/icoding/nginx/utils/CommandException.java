package ink.icoding.nginx.utils;

public class CommandException extends RuntimeException {
    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
