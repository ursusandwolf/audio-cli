package com.stt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Standard implementation of CommandExecutor using ProcessBuilder.
 */
public class DefaultCommandExecutor implements CommandExecutor {

    @Override
    public int execute(String command, String... args) throws Exception {
        return execute(command, 0, args);
    }

    @Override
    public String executeAndCapture(String command, String... args) throws Exception {
        return executeAndCapture(command, 0, args);
    }

    @Override
    public int execute(String command, int timeoutSeconds, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(buildCommandList(command, args));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        boolean finished = true;
        if (timeoutSeconds > 0) {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            process.waitFor();
        }

        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Command timed out after " + timeoutSeconds + "s: " + command);
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + command);
        }
        return exitCode;
    }

    @Override
    public String executeAndCapture(String command, int timeoutSeconds, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(buildCommandList(command, args));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        boolean finished = true;
        if (timeoutSeconds > 0) {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            process.waitFor();
        }

        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Command timed out after " + timeoutSeconds + "s: " + command);
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + command);
        }
        return output.toString();
    }

    private List<String> buildCommandList(String command, String[] args) {
        return Stream.concat(Stream.of(command), Arrays.stream(args)).toList();
    }
}
