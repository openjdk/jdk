package jdk.jfr.startupargs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
/**
 * @test
 * @summary Tests that -XX:FlightRecorderOptions:preserve-repository works
 * @key jfr
 * @requires vm.hasJFR
 * @modules jdk.jfr
 * @library /test/lib
 * @run main/othervm jdk.jfr.startupargs.TestPreserveRepository
 */
public class TestPreserveRepository {

    public static void main(String... args) throws Exception {
        Path path  = Path.of("./preserved");
        String[] arguments = {
            "-XX:StartFlightRecording",
            "-XX:FlightRecorderOptions:repository=" + path + ",preserve-repository=true",
            "-version"
        };
        ProcessBuilder pb = ProcessTools.createTestJvm(arguments);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);
        Optional<Path> p = Files.find(path, 99, (a,b) -> a.getFileName().toString().endsWith(".jfr")).findAny();
        if (p.isEmpty()) {
            throw new Exception("Could not find preserved files in repository");
        }
    }
}
