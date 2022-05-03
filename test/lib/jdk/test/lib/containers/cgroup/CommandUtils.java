package jdk.test.lib.containers.cgroup;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

public class CommandUtils {

    public static OutputAnalyzer execute(String filename,int lineNumber,String... command) throws Exception {

        ProcessBuilder pb = new ProcessBuilder(command);
        System.out.println("[COMMAND]\n" + Utils.getCommandLine(pb));

        long started = System.currentTimeMillis();
        Process p = pb.start();
        long pid = p.pid();
        OutputAnalyzer output = new OutputAnalyzer(p);

        String stdoutLogFile = String.format(filename, pid);
        System.out.println("[ELAPSED: " + (System.currentTimeMillis() - started) + " ms]");
        System.out.println("[STDERR]\n" + output.getStderr());
        System.out.println("[STDOUT]\n" +
                trimLines(output.getStdout(),lineNumber));
        System.out.printf("Child process STDOUT is trimmed to %d lines \n",
                lineNumber);
        writeOutputToFile(output.getStdout(), stdoutLogFile);
        System.out.println("Full child process STDOUT was saved to " + stdoutLogFile);

        return output;
    }

    private static void writeOutputToFile(String output, String fileName) throws Exception {
        try (FileWriter fw = new FileWriter(fileName)) {
            fw.write(output, 0, output.length());
        }
    }


    private static String trimLines(String buffer, int nrOfLines) {
        List<String> l = Arrays.asList(buffer.split("\\R"));
        if (l.size() < nrOfLines) {
            return buffer;
        }

        return String.join("\n", l.subList(0, nrOfLines));
    }

}
