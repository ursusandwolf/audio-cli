package com.stt;

import java.nio.file.Path;

/**
 * Interface for executing system commands.
 * Allows mocking for tests.
 */
public interface CommandExecutor {
    int execute(String command, String... args) throws Exception;
    String executeAndCapture(String command, String... args) throws Exception;
}
