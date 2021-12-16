import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.util.JarBuilder;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.ArrayList;
import java.util.List;

class TestUtils {
    public static final int EXIT_OK = 0;

    static void buildJar(String name) throws Exception {
        String baseDir = System.getProperty("test.classes", ".");

        JarBuilder jarBuilder = new JarBuilder(name + ".jar");
        jarBuilder.addAttribute("Main-Class", name);

        Files.walk(Paths.get(baseDir), 10)
            .filter(p -> p.getFileName().toString().contains(name))
            .forEach(p -> {
                    try {
                        System.out.println("Adding: " + p.getFileName() + " to jar");
                        jarBuilder.addEntry(p.getFileName().toString(), Files.readAllBytes(p));
                    } catch (Exception e) {
                        System.out.println("Warning: failed to add " + p.getFileName() + " to jar");
                        System.out.println(e);
                    }
                });

        jarBuilder.build();

        File p_jarFile = new File(name + ".jar");
        Asserts.assertTrue(p_jarFile.isFile(),
                           "Error: Jar file " + name + ".jar" + " is not a regular file");
    }

    /**
     * Create & Run a JVM instance, passing the jarPath as the location of a jar to be run.
     *
     * See more info in ProcessTools about process creation, logging, etc.
     *
     * java -XX:+FlightRecorder \
     *      -XX:StartFlightRecording=duration=60s,filename=recording.jfr,dumponexit=true \
     *      <extra-args> \
     *      -jar <jarPath>
     */
    static OutputAnalyzer runJar(String jarPath, List<String> extra_cmdline_args) throws Exception {
        List<String> cmdline_args = new ArrayList<String>();
        cmdline_args.add("-XX:StartFlightRecording=duration=5s,filename=recording.jfr,dumponexit=true");

        cmdline_args.addAll(extra_cmdline_args);

        cmdline_args.add("-jar");
        cmdline_args.add(jarPath);
        OutputAnalyzer output = ProcessTools.executeTestJvm(cmdline_args);

        File p_recfile = new File("recording.jfr");
        Asserts.assertTrue(p_recfile.isFile(),
                           "Error: Recording file is not a regular file");
        return output;
    }
}
/**
 * @test
 * @summary A test runner for Java Flight Recorder Tests.
 * - BasicJFRTestHelper Creates an event and errors if 'shouldCommit' returns false. Otherwise the event is commited.
 * - NetworkJFRTestHelper: Performs computation then sends results to localhost. Logs progress with Events.
 * @requires vm.hasJFR
 * @library /test/lib /test/hotspot/jtreg/runtime/jfr
 * @compile test-classes/BasicJFRTestHelper.java test-classes/NetworkJFRTestHelper.java
 * @run main JFRTest BasicJFRTestHelper
 * @run main JFRTest NetworkJFRTestHelper
 */
public class JFRTest {
    public static void main(String[] args) throws Exception {
        if (args[0] == null) {
            System.out.println("Error: expected test-file as args[0]");
            return;
        }

        String baseDir = System.getProperty("test.classes", ".");
        String name = args[0];
        String jarName = name + ".jar";

        System.out.println("Proceeding with test: " + name);
        TestUtils.buildJar(name);
        OutputAnalyzer output = TestUtils.runJar(jarName, new ArrayList<String>());
        output.shouldHaveExitValue(TestUtils.EXIT_OK);
    }
}
