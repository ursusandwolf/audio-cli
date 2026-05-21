package com.stt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard implementation of CommandExecutor using ProcessBuilder.
 */
public class DefaultCommandExecutor implements CommandExecutor {

    @Override
    public int execute(String command, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(buildCommandList(command, args));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        return process.waitFor();
    }

    @Override
    public String executeAndCapture(String command, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(buildCommandList(command, args));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
            process.waitFor();
            return output.toString();
        }
    }

    private List<String> buildCommandList(String command, String[] args) {
        List<String> list = new ArrayList<>();
        list.add(command);
        for (String arg : args) {
            list.add(arg);
        }
        return list;
    }
}
