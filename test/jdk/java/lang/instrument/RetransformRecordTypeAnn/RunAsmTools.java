import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class RunAsmTools {
    static final String SRC = System.getProperty("test.src");
    static final String DEST = System.getProperty("test.classes");

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("missing arguments");
            return;
        }
        String inputJcodFile = args[0];
        Path outputDirPath = null;
        if (args.length >= 2) {
            String outputDir = args[1];
            outputDirPath = Paths.get(DEST + "/" + outputDir);
            try {
                Files.createDirectories(outputDirPath);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        List<String> commandArgs = new ArrayList<>();
        commandArgs.add("java");
        commandArgs.add("-jar");
        commandArgs.add(getAsmToolsPath());
        commandArgs.add("jcoder");
        if (outputDirPath != null) {
            commandArgs.add("-d");
            commandArgs.add(outputDirPath.toString());
        }
        commandArgs.add(SRC + "/" + inputJcodFile);
        execute(commandArgs);
    }

    private static void execute(List<String> commandArgs) {
        ProcessBuilder builder = new ProcessBuilder(commandArgs);
        builder.redirectErrorStream(true);
        String output;
        int exitCode;
        try {
            Process process = builder.start();
            boolean exited = process.waitFor(30, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                System.out.println("Timeout: asmtools compile command: " + String.join(" ", commandArgs));
                throw new RuntimeException("Process timeout: asmtools compilation took too long.");
            }
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.exitValue();
        } catch (IOException e) {
            throw new RuntimeException("IOException during asmtools compilation", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("InterruptedException during asmtools compilation", e);
        }

        if (exitCode != 0) {
            System.err.println("asmtools compilation failed.");
            System.err.println("Command: " + commandArgs);
            System.err.println("Exit code: " + exitCode);
            System.err.println("Output: '" + output + "'");
            throw new RuntimeException("asmtools compilation failed.");
        }
    }

    private static String getAsmToolsPath() {
        for (String path : getClassPaths()) {
            if (path.endsWith("jtreg.jar")) {
                File jtreg = new File(path);
                File dir = jtreg.getAbsoluteFile().getParentFile();
                File asmtools = new File(dir, "asmtools.jar");
                if (!asmtools.exists()) {
                    throw new RuntimeException("Found jtreg.jar in classpath, but could not find asmtools.jar");
                }
                return asmtools.getAbsolutePath();
            }
        }
        throw new RuntimeException("Could not find asmtools because could not find jtreg.jar in classpath");
    }

    private static String[] getClassPaths() {
        return System.getProperty("java.class.path").split(File.pathSeparator);
    }
}
