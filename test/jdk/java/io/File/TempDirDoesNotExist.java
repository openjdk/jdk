/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8290313
 * @library /test/lib
 * @summary Produce warning when user specified java.io.tmpdir directory doesn't exist
 * @run junit TempDirDoesNotExist
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TempDirDoesNotExist {
    final static String WARNING = "WARNING: java.io.tmpdir directory does not exist";

    private static final String USER_DIR = System.getProperty("user.home");

    //
    // This class is spawned to test combinations of parameters.
    //
    public static void main(String... args) throws IOException {
        for (String arg : args) {
            switch (arg) {
                case "io" -> {
                    File file = null;
                    try {
                        file = File.createTempFile("prefix", ".suffix");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (file != null && file.exists())
                            if (!file.delete())
                                throw new RuntimeException(file + " not deleted");
                    }
                }
                case "nio" -> {
                Path path = null;
                    try {
                        path = Files.createTempFile("prefix", ".suffix");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (path != null)
                            if (!Files.deleteIfExists(path))
                                throw new RuntimeException(path + " not deleted");
                    }
                }
                default -> {
                    throw new RuntimeException("unknown case: " + arg);
                }
            }
        }
    }

    private static String tempDir() {
        String timeStamp = String.valueOf(System.currentTimeMillis());
        return Path.of(USER_DIR, "non-existing-", timeStamp).toString();
    }

    public static Stream<Arguments> tempDirSource() {
        return Stream.of(Arguments.of(List.of("-Djava.io.tmpdir=" + tempDir(),
                                              "TempDirDoesNotExist", "io")),
                         Arguments.of(List.of("-Djava.io.tmpdir=" + tempDir(),
                                              "TempDirDoesNotExist", "nio")),
                         Arguments.of(List.of("-Djava.io.tmpdir=" + tempDir() +
                                              " -Djava.security.manager",
                                              "TempDirDoesNotExist", "io")),
                         Arguments.of(List.of("-Djava.io.tmpdir=" + tempDir() +
                                              " -Djava.security.manager",
                                              "TempDirDoesNotExist", "nio")));
    }

    public static Stream<Arguments> noTempDirSource() {
        return Stream.of(Arguments.of(List.of("TempDirDoesNotExist", "io")),
                         Arguments.of(List.of("TempDirDoesNotExist", "nio")),
                         Arguments.of(List.of("-Djava.io.tmpdir=" + USER_DIR,
                                              "TempDirDoesNotExist", "io")),
                         Arguments.of(List.of("-Djava.io.tmpdir=" + USER_DIR,
                                              "TempDirDoesNotExist", "nio")));
    }

    public static Stream<Arguments> counterSource() {
        // standard test with default setting for java.io.tmpdir
        return Stream.of(Arguments.of(List.of("-Djava.io.tmpdir=" + tempDir(),
                                             "TempDirDoesNotExist",
                                             "io", "nio")));
    }

    @ParameterizedTest
    @MethodSource("tempDirSource")
    public void existingMessage(List<String> options) throws Exception {
       ProcessTools.executeTestJvm(options).shouldContain(WARNING)
           .shouldHaveExitValue(0);
    }

    @ParameterizedTest
    @MethodSource("noTempDirSource")
    public void nonexistentMessage(List<String> options) throws Exception {
        ProcessTools.executeTestJvm(options).shouldNotContain(WARNING)
            .shouldHaveExitValue(0);
    }

    @ParameterizedTest
    @MethodSource("counterSource")
    public void messageCounter(List<String> options) throws Exception {
        OutputAnalyzer originalOutput = ProcessTools.executeTestJvm(options);
        long count = originalOutput.asLines().stream().filter(
                line -> line.equalsIgnoreCase(WARNING)).count();
        assertEquals(1, count,
                     "counter of messages is not one, but " + count +
                     "\n" + originalOutput.asLines().toString());
        int exitValue = originalOutput.getExitValue();
        assertEquals(0, exitValue);
    }
}
