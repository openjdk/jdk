/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @summary verify logging of call to System.exit or Runtime.exit.
 * @run junit/othervm RuntimeExitLogTest
 */

public class RuntimeExitLogTest {

    private static final String TEST_JDK = System.getProperty("test.jdk");
    private static final String TEST_SRC = System.getProperty("test.src");

    /**
     * Call System.exit() with the parameter (or zero if not supplied).
     * @param args zero or 1 argument, an exit status
     */
    public static void main(String[] args) throws InterruptedException {
        int status = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        System.exit(status);
    }

    /**
     * Test various log level settings, and none.
     * @return a stream of arguments for parameterized test
     */
    private static Stream<Arguments> logParamProvider() {
        return Stream.of(
                Arguments.of("ExitLogging-FINE.properties", 1, true),
                Arguments.of("ExitLogging-INFO.properties", 2, false),
                Arguments.of(null, 3, false)
        );
    }

    /**
     * Check that the logger output of a launched process contains the expected message.
     * @param logProps The name of the log.properties file to set on the command line
     * @param status the expected exit status of the process
     * @param shouldLog true if the log should contain the message expected from Runtime.exit(status)
     */
    @ParameterizedTest
    @MethodSource("logParamProvider")
    public void checkLogger(String logProps, int status, boolean shouldLog) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(true);

        List<String> cmd = pb.command();
        cmd.add(Path.of(TEST_JDK,"bin", "java").toString());
        if (logProps != null) {
            cmd.add("-Djava.util.logging.config.file=" + Path.of(TEST_SRC, logProps).toString());
        }
        cmd.add(this.getClass().getName());
        cmd.add(Integer.toString(status));

        try {
            Process process = pb.start();
            try (BufferedReader reader = process.inputReader()) {
                List<String> lines = reader.lines().toList();
                final String expected = "Runtime.exit() called with status: " + status;
                Optional<String> found = lines.stream().filter(s -> s.contains(expected)).findFirst();
                if (found.isPresent() != shouldLog) {
                    System.err.println("---- Process output begin");
                    lines.forEach(l -> System.err.println(l));
                    System.err.println("---- Process output end");
                    fail("Unexpected log contents");
                }
            }
            int result = process.waitFor();
            assertEquals(status, result, "Exit status");
        } catch (IOException | InterruptedException ex) {
            fail(ex);
        }
    }
}
