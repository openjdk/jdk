/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.stream.Stream;


import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @summary verify logging of call to System.exit or Runtime.exit.
 * @requires vm.flagless
 * @run junit/othervm RuntimeExitLogTest
 */

public class RuntimeExitLogTest {

    private static final String TEST_JDK = System.getProperty("test.jdk");
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final String NEW_LINE = System.lineSeparator();
    private static Object HOLD_LOGGER;

    /**
     * Call System.exit() with the parameter (or zero if not supplied).
     * @param args zero or 1 argument, an exit status
     */
    public static void main(String[] args) throws InterruptedException {
        int status = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        if (System.getProperty("ThrowingHandler") != null) {
            HOLD_LOGGER = ThrowingHandler.installHandler();
        }
        System.exit(status);
    }

    /**
     * Generate a regular expression pattern that match the expected log output for a Runtime.exit() call.
     * The pattern includes the method call stack trace and the exit status.
     * @param status the exit status passed to the Runtime.exit() call
     * @return the regex pattern as a string
     */
    private static String generateStackTraceLogPattern(int status) {
        return "(?s)^.+ java\\.lang\\.Shutdown logRuntimeExit\\n" +
                ".*: Runtime\\.exit\\(\\) called with status: " + status + "\\n" +
                "java\\.lang\\.Throwable: Runtime\\.exit\\(" + status + "\\)\\n" +
                "\\s+at java\\.base/java\\.lang\\.Shutdown\\.logRuntimeExit\\(Shutdown\\.java:\\d+\\)\\n" +
                "\\s+at(?: .+)";
    }

    /**
     * Test various log level settings, and none.
     * @return a stream of arguments for parameterized test
     */
    private static Stream<Arguments> logParamProvider() {
        return Stream.of(
                // Logging configuration using the java.util.logging.config.file property
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-ALL.properties").toString()), 1,
                        generateStackTraceLogPattern(1)),
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-FINER.properties").toString()), 2,
                        generateStackTraceLogPattern(2)),
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-FINE.properties").toString()), 3,
                        generateStackTraceLogPattern(3)),
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-INFO.properties").toString()), 4,
                        ""),
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-WARNING.properties").toString()), 5,
                        ""),
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-SEVERE.properties").toString()), 6,
                        ""),
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-OFF.properties").toString()), 7,
                        ""),

                // Logging configuration using the jdk.system.logger.level property
                Arguments.of(List.of("--limit-modules", "java.base",
                        "-Djdk.system.logger.level=ALL"), 8,
                        generateStackTraceLogPattern(8)),
                Arguments.of(List.of("--limit-modules", "java.base",
                        "-Djdk.system.logger.level=TRACE"), 9,
                        generateStackTraceLogPattern(9)),
                Arguments.of(List.of("--limit-modules", "java.base",
                        "-Djdk.system.logger.level=DEBUG"), 10,
                        generateStackTraceLogPattern(10)),
                Arguments.of(List.of("--limit-modules", "java.base",
                        "-Djdk.system.logger.level=INFO"), 11,
                        ""),
                Arguments.of(List.of("--limit-modules", "java.base",
                        "-Djdk.system.logger.level=WARNING"), 12,
                        ""),
                Arguments.of(List.of("--limit-modules", "java.base",
                        "-Djdk.system.logger.level=ERROR"), 13,
                        ""),
                Arguments.of(List.of("--limit-modules", "java.base",
                        "-Djdk.system.logger.level=OFF"), 14,
                        ""),

                // Throwing Handler
                Arguments.of(List.of("-DThrowingHandler",
                        "-Djava.util.logging.config.file=" +
                        Path.of(TEST_SRC, "ExitLogging-FINE.properties").toString()), 15,
                        "Runtime\\.exit\\(15\\) logging failed: Exception in publish"),

                // Default console logging configuration with no additional parameters
                Arguments.of(List.of(), 16, "")
                );
    }

    /**
     * Check that the logger output of a launched process contains the expected message.
     * @param logProps The name of the log.properties file to set on the command line
     * @param status the expected exit status of the process
     * @param expectMessage log should contain the message
     */
    @ParameterizedTest
    @MethodSource("logParamProvider")
    public void checkLogger(List<String> logProps, int status, String expectMessage) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(true);

        List<String> cmd = pb.command();
        cmd.add(Path.of(TEST_JDK,"bin", "java").toString());
        cmd.addAll(logProps);
        cmd.add(this.getClass().getName());
        cmd.add(Integer.toString(status));

        try {
            Process process = pb.start();
            try (BufferedReader reader = process.inputReader()) {
                List<String> lines = reader.lines().toList();
                boolean match = (expectMessage.isEmpty())
                        ? lines.isEmpty()
                        : String.join("\n", lines).matches(expectMessage);
                if (!match) {
                    // Output lines for debug
                    System.err.println("Expected pattern (line-break):");
                    System.err.println(expectMessage.replaceAll("\\n", NEW_LINE));
                    System.err.println("---- Actual output begin");
                    lines.forEach(System.err::println);
                    System.err.println("---- Actual output end");
                    fail("Unexpected log contents");
                }
            }
            int result = process.waitFor();
            assertEquals(status, result, "Exit status");
        } catch (IOException | InterruptedException ex) {
            fail(ex);
        }
    }

    /**
     * A LoggingHandler that throws an Exception.
     */
    public static class ThrowingHandler extends StreamHandler {

        // Install this handler for java.lang.Runtime
        public static Logger installHandler() {
            Logger logger = Logger.getLogger("java.lang.Runtime");
            logger.addHandler(new ThrowingHandler());
            return logger;
        }

        @Override
        public synchronized void publish(LogRecord record) {
            super.publish(record);
            throw new RuntimeException("Exception in publish");
        }
    }
}
