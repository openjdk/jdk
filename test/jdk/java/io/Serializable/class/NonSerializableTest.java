/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4075221
 * @library /test/lib
 * @build jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 * @run junit/timeout=300 NonSerializableTest
 * @summary Enable serialize of nonSerializable Class descriptor.
 */

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


public class NonSerializableTest {

    @BeforeAll
    public static void setup() throws Exception {
        boolean b = CompilerUtils.compile(
                Paths.get(System.getProperty("test.src"), "TestEntry.java"),
                Paths.get(System.getProperty("user.dir")));
        assertTrue(b, "Compilation failed");
    }

    // Test cases to compile and run
    public static Stream<List<String>> provider() {
        return Stream.of(
            // Write NonSerial1, Read NonSerial1
            List.of("NonSerialA_1", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("NonSerialA_1", "-cp", ".", "TestEntry", "-d"),

            // Write NonSerial1, Read NonSerial2
            List.of("NonSerialA_1", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("NonSerialA_2", "-cp", ".", "TestEntry", "-d"),

            // Write NonSerial1, Read Serial1
            List.of("NonSerialA_1", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("SerialA_1", "-cp", ".", "TestEntry", "-d"),

            // Write Serial1, Read NonSerial1
            List.of("SerialA_1", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("NonSerialA_1", "-cp", ".", "TestEntry", "-doe"),

            // Write Serial1, Read Serial2
            List.of("SerialA_1", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("SerialA_2", "-cp", ".", "TestEntry", "-d"),

            // Write Serial2, Read Serial1
            List.of("SerialA_2", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("SerialA_1", "-cp", ".", "TestEntry", "-d"),

            // Write Serial1, Read Serial3
            List.of("SerialA_1", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("SerialA_3", "-cp", ".", "TestEntry", "-de"),

            // Write Serial3, Read Serial1
            List.of("SerialA_3", "-cp", ".", "TestEntry", "-s", "A"),
            List.of("SerialA_1", "-cp", ".", "TestEntry", "-de"));
    }

    @ParameterizedTest
    @MethodSource("provider")
    public void test(List<String> argList) throws Exception {
        String[] args = argList.toArray(new String[0]);
        boolean b = CompilerUtils.compile(Paths.get(System.getProperty("test.src"), args[0]),
                                          Paths.get(System.getProperty("user.dir")));
        assertTrue(b, "Compilation failed");
        String[] params = Arrays.copyOfRange(args, 1, args.length);
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(params);
        try (Process p = ProcessTools.startProcess("Serializable Test", pb)) {
            int exitValue = p.waitFor();
            assertEquals(0, exitValue, "Test failed");
        }
    }
}
