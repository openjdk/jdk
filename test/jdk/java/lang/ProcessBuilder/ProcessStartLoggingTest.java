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
import java.io.File;
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
 * @summary verify logging of ProcessBuilder.start()
 * @requires vm.flagless
 * @run junit/othervm ProcessStartLoggingTest
 */
public class ProcessStartLoggingTest {

    private final static int NORMAL_STATUS = 0;
    private final static int ERROR_STATUS = 1;

    private static final String TEST_JDK = System.getProperty("test.jdk");
    private static final String TEST_SRC = System.getProperty("test.src");

    private static Object HOLD_LOGGER;

    /**
     * Launch a process with the arguments.
     * @param args 1 or strings passed directly to ProcessBuilder as command and arguments.
     */
    public static void main(String[] args) throws InterruptedException {
        if (System.getProperty("ThrowingHandler") != null) {
            HOLD_LOGGER = ProcessStartLoggingTest.ThrowingHandler.installHandler();
        }
        String directory = System.getProperty("directory");
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory((directory == null) ? null : new File(directory));
            Process p = pb.start();
            int status = p.waitFor();
            if (status != 0) {
                System.out.println("exitValue: " + status);
            }
        } catch (IOException ioe) {
            System.out.println("ProcessBuilder.start() threw IOException: " + ioe);
        }
    }

    /**
     * Test various log level settings, and none.
     * @return a stream of arguments for parameterized test
     */
    private static Stream<Arguments> logParamProvider() {

        return Stream.of(
                // Logging enabled with level TRACE
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                                        Path.of(TEST_SRC, "ProcessLogging-FINER.properties").toString(),
                                "-Ddirectory=."),
                        List.of("echo", "echo0"),
                        NORMAL_STATUS,
                        "dir: ., cmd: \"echo\" \"echo0\""),
                // Logging enabled with level DEBUG
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                                        Path.of(TEST_SRC, "ProcessLogging-FINE.properties").toString(),
                                "-Ddirectory=."),
                        List.of("echo", "echo1"),
                        NORMAL_STATUS,
                        "dir: ., cmd: \"echo\""),
                // Logging disabled due to level INFO
                Arguments.of(List.of("-Djava.util.logging.config.file=" +
                                Path.of(TEST_SRC, "ProcessLogging-INFO.properties").toString()),
                        List.of("echo", "echo2"),
                        NORMAL_STATUS,
                        ""),
                // Console logger DEBUG
                Arguments.of(List.of("--limit-modules", "java.base",
                                "-Djdk.system.logger.level=DEBUG"),
                        List.of("echo", "echo3"),
                        NORMAL_STATUS,
                        "dir: null, cmd: \"echo\""),
                // Console logger TRACE
                Arguments.of(List.of("--limit-modules", "java.base",
                                "-Djdk.system.logger.level=TRACE",
                                "-Ddirectory=."),
                        List.of("echo", "echo4"),
                        NORMAL_STATUS,
                        "dir: ., cmd: \"echo\" \"echo4\""),
                // No Logging configured
                Arguments.of(List.of(),
                        List.of("echo", "echo5"),
                        NORMAL_STATUS,
                        ""),
                // Throwing Handler
                Arguments.of(List.of("-DThrowingHandler",
                                "-Djava.util.logging.config.file=" +
                                        Path.of(TEST_SRC, "ProcessLogging-FINE.properties").toString()),
                        List.of("echo", "echo6"),
                        ERROR_STATUS,
                        "Exception in thread \"main\" java.lang.RuntimeException: Exception in publish")
        );
    }

    /**
     * Check that the logger output of a launched process contains the expected message.
     *
     * @param logArgs       Arguments to configure logging in the java test process
     * @param childArgs     the args passed to the child to be invoked as a Process
     * @param expectMessage log should contain the message
     */
    @ParameterizedTest
    @MethodSource("logParamProvider")
    public void checkLogger(List<String> logArgs, List<String> childArgs,
                            int expectedStatus, String expectMessage) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(true);

        List<String> cmd = pb.command();
        cmd.add(Path.of(TEST_JDK,"bin", "java").toString());
        cmd.addAll(logArgs);
        cmd.add(this.getClass().getName());
        cmd.addAll(childArgs);
        try {
            Process process = pb.start();
            try (BufferedReader reader = process.inputReader()) {
                List<String> lines = reader.lines().toList();
                boolean match = (expectMessage.isEmpty())
                        ? lines.size() == 0
                        : lines.stream().filter(s -> s.contains(expectMessage)).findFirst().isPresent();
                if (!match) {
                    // Output lines for debug
                    System.err.println("Expected> \"" + expectMessage + "\"");
                    lines.forEach(l -> System.err.println("Actual>   \"" + l+ "\""));
                    fail("Unexpected log contents");
                }
            }
            int result = process.waitFor();
            assertEquals(expectedStatus, result, "Exit status");
        } catch (IOException | InterruptedException ex) {
            fail(ex);
        }
    }

    /**
     * A LoggingHandler that throws an Exception.
     */
    public static class ThrowingHandler extends StreamHandler {

        // Install this handler for java.lang.ProcessBuilder
        public static Logger installHandler() {
            Logger logger = Logger.getLogger("java.lang.ProcessBuilder");
            logger.addHandler(new ProcessStartLoggingTest.ThrowingHandler());
            return logger;
        }

        @Override
        public synchronized void publish(LogRecord record) {
            throw new RuntimeException("Exception in publish");
        }
    }
}
