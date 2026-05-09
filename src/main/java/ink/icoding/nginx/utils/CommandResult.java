package ink.icoding.nginx.utils;

public class CommandResult {
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

    public int getExitCode() { return exitCode; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public String getOutput() {
        if (stderr.isEmpty()) return stdout;
        if (stdout.isEmpty()) return stderr;
        return stdout + System.lineSeparator() + stderr;
    }
    public boolean isSuccess() { return success; }
    public boolean isTimeout() { return timeout; }
    public String getErrorMessage() { return errorMessage; }

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
