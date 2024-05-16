/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331670
 * @summary Basic test for --sun-misc-unsafe-memory-access=<value>
 * @library /test/lib
 * @compile TryUnsafeMemoryAccess.java
 * @run junit UnsafeMemoryAccessWarnings
 */

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

class UnsafeMemoryAccessWarnings {

    /**
     * Test default is "allow"
     */
    @Test
    void testDefault() throws Exception {
        test("allocateMemory+freeMemory+objectFieldOffset+putLong+getLong+invokeCleaner")
            .shouldHaveExitValue(0)
            .shouldNotContain("WARNING: A terminally deprecated method in sun.misc.Unsafe has been called")
            .shouldNotContain("WARNING: sun.misc.Unsafe::allocateMemory")
            .shouldNotContain("WARNING: sun.misc.Unsafe::freeMemory")
            .shouldNotContain("WARNING: sun.misc.Unsafe::objectFieldOffset")
            .shouldNotContain("WARNING: sun.misc.Unsafe::putLong")
            .shouldNotContain("WARNING: sun.misc.Unsafe::getLong")
            .shouldNotContain("WARNING: sun.misc.Unsafe::invokeCleaner");
    }

    /**
     * Test --sun-misc-unsafe-memory-access=allow
     */
    @Test
    void testAllow() throws Exception {
        test("allocateMemory+freeMemory+objectFieldOffset+putLong+getLong+invokeCleaner",
                "--sun-misc-unsafe-memory-access=allow")
            .shouldHaveExitValue(0)
            .shouldNotContain("WARNING: A terminally deprecated method in sun.misc.Unsafe has been called")
            .shouldNotContain("WARNING: sun.misc.Unsafe::allocateMemory")
            .shouldNotContain("WARNING: sun.misc.Unsafe::freeMemory")
            .shouldNotContain("WARNING: sun.misc.Unsafe::objectFieldOffset")
            .shouldNotContain("WARNING: sun.misc.Unsafe::putLong")
            .shouldNotContain("WARNING: sun.misc.Unsafe::getLong")
            .shouldNotContain("WARNING: sun.misc.Unsafe::invokeCleaner");
    }

    /**
     * Test --sun-misc-unsafe-memory-access=warn
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "allocateMemory+freeMemory",
            "objectFieldOffset+putLong+getLong",
            "invokeCleaner"
    })
    void testWarn(String input) throws Exception {
        var output = test(input, "--sun-misc-unsafe-memory-access=warn").shouldHaveExitValue(0);

        // should be warning printed for the first memory access method
        String[] methodNames = input.split("\\+");
        String firstMethodName = methodNames[0];
        output.shouldContain("WARNING: A terminally deprecated method in sun.misc.Unsafe has been called")
            .shouldContain("WARNING: sun.misc.Unsafe::" + firstMethodName + " has been called by")
            .shouldContain("WARNING: Please consider reporting this to the maintainers of")
            .shouldContain("WARNING: sun.misc.Unsafe::" + firstMethodName + " will be removed in a future release");

        // should be no warning for the second/subsequent memory access methods
        int index = 1;
        while (index < methodNames.length) {
            String methodName = methodNames[index++];
            output.shouldNotContain("WARNING: sun.misc.Unsafe::" + methodName);
        }
    }

    /**
     * Test --sun-misc-unsafe-memory-access=debug
     */
    @Test
    void testDebug() throws Exception {
        test("allocateMemory+freeMemory+objectFieldOffset+putLong+getLong+invokeCleaner",
                "--sun-misc-unsafe-memory-access=debug")
            .shouldHaveExitValue(0)
            .shouldContain("WARNING: sun.misc.Unsafe::allocateMemory called")
            .shouldContain("WARNING: sun.misc.Unsafe::freeMemory called")
            .shouldContain("WARNING: sun.misc.Unsafe::objectFieldOffset called")
            .shouldContain("WARNING: sun.misc.Unsafe::putLong called")
            .shouldContain("WARNING: sun.misc.Unsafe::getLong called")
            .shouldContain("WARNING: sun.misc.Unsafe::invokeCleaner called");
    }

    /**
     * Test --sun-misc-unsafe-memory-access=deny
     */
    @Test
    void testDeny() throws Exception {
        test("allocateMemory+objectFieldOffset+invokeCleaner", "--sun-misc-unsafe-memory-access=deny")
            .shouldHaveExitValue(0)
            .shouldContain("java.lang.UnsupportedOperationException: allocateMemory")
            .shouldContain("java.lang.UnsupportedOperationException: objectFieldOffset")
            .shouldContain("java.lang.UnsupportedOperationException: invokeCleaner");
    }

    /**
     * Test invoking Unsafe methods with core reflection.
     */
    @Test
    void testInvokeReflectively() throws Exception {
        test("reflectivelyAllocateMemory+reflectivelyFreeMemory", "--sun-misc-unsafe-memory-access=allow")
            .shouldHaveExitValue(0)
            .shouldNotContain("WARNING: A terminally deprecated method in sun.misc.Unsafe has been called")
            .shouldNotContain("WARNING: sun.misc.Unsafe::allocateMemory")
            .shouldNotContain("WARNING: sun.misc.Unsafe::freeMemory");

        test("reflectivelyAllocateMemory+reflectivelyFreeMemory", "--sun-misc-unsafe-memory-access=warn")
            .shouldHaveExitValue(0)
            .shouldContain("WARNING: A terminally deprecated method in sun.misc.Unsafe has been called")
            .shouldContain("WARNING: sun.misc.Unsafe::allocateMemory has been called by")
            .shouldContain("WARNING: Please consider reporting this to the maintainers of")
            .shouldContain("WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release")
            .shouldNotContain("WARNING: sun.misc.Unsafe::freeMemory");

        test("reflectivelyAllocateMemory+reflectivelyFreeMemory", "--sun-misc-unsafe-memory-access=debug")
            .shouldHaveExitValue(0)
            .shouldContain("WARNING: sun.misc.Unsafe::allocateMemory called")
            .shouldContain("WARNING: sun.misc.Unsafe::freeMemory called");

        test("reflectivelyAllocateMemory", "--sun-misc-unsafe-memory-access=deny")
            .shouldHaveExitValue(0)
            .shouldContain("java.lang.UnsupportedOperationException: allocateMemory");
    }

    /**
     * If --sun-misc-unsafe-memory-access specified more than once then last one wins.
     */
    @Test
    void testLastOneWins() throws Exception {
        test("allocateMemory+objectFieldOffset+invokeCleaner",
                "--sun-misc-unsafe-memory-access=allow",
                "--sun-misc-unsafe-memory-access=deny")
            .shouldHaveExitValue(0)
            .shouldContain("java.lang.UnsupportedOperationException: allocateMemory")
            .shouldContain("java.lang.UnsupportedOperationException: objectFieldOffset")
            .shouldContain("java.lang.UnsupportedOperationException: invokeCleaner");
    }

    /**
     * Test --sun-misc-unsafe-memory-access with invalid values.
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "bad" })
    void testInvalidValues(String value) throws Exception {
        test("allocateMemory", "--sun-misc-unsafe-memory-access=" + value)
            .shouldNotHaveExitValue(0)
            .shouldContain("Value specified to --sun-misc-unsafe-memory-access not recognized: '" + value);
    }

    /**
     * Test System.setProperty("sun.misc.unsafe.memory.access", "allow")
     * The saved value from startup should be used, not the system property set at run-time.
     */
    @Test
    void testSetPropertyToAllow() throws Exception {
        test("setSystemPropertyToAllow+objectFieldOffset", "--sun-misc-unsafe-memory-access=deny")
            .shouldHaveExitValue(0)
            .shouldContain("java.lang.UnsupportedOperationException: objectFieldOffset");
    }

    /**
     * Launch TryUnsafeMemoryAccess with the given arguments and VM options.
     */
    private OutputAnalyzer test(String action, String... vmopts) throws Exception {
        Stream<String> s1 = Stream.of(vmopts);
        Stream<String> s2 = Stream.of("TryUnsafeMemoryAccess", action);
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        var outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.err)
                .errorTo(System.err);
        return outputAnalyzer;
    }
}
