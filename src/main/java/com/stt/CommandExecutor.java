package com.stt;

/**
 * Interface for executing system commands.
 * Allows mocking for tests.
 */
public interface CommandExecutor {
    int execute(String command, String... args) throws Exception;
    String executeAndCapture(String command, String... args) throws Exception;
    
    int execute(String command, int timeoutSeconds, int retryCount, String... args) throws Exception;
    String executeAndCapture(String command, int timeoutSeconds, int retryCount, String... args) throws Exception;
}
