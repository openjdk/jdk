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
 * @bug 8345145
 * @summary javap should not print LineNumberTable/LocalVariableTable (-l) without disassembled code (-c).
 * @compile -g ClassWriterNoLineVariableTableTest.java
 * @run junit ClassWriterNoLineVariableTableTest
 * @modules jdk.jdeps/com.sun.tools.javap
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class ClassWriterNoLineVariableTableTest {
    String expectedErrorOutput = "Warning: bad combination of options: -l without -c, line number and local variable tables will not be printed";

    @Test
    public void testJavapWithoutCodeAttribute() {
        String output = javap("-l");
        assertContains(output, expectedErrorOutput,
            "javap should throw warning, when -l used without -c or -v");
        assertNotContains(output, "LineNumberTable",
            "There should be no LineNumberTable output when javap is provided l without -c or -v");
        assertNotContains(output, "LocalVariableTable",
            "There should be no LineNumberTable output when javap is provided l without -c or -v");
    }

    @ParameterizedTest(name = "Test javap with fixed option -l and varying option: {0}")
    @ValueSource(strings = {"-v", "-c"})
    public void testJavapWithCodeAttribute(String addedOption) {
        String output = javap("-l", addedOption);
        assertNotContains(output, expectedErrorOutput,
            "There should be no warning when javap is provided -l and " + addedOption);
        assertContains(output, "LineNumberTable",
            "There should be LineNumberTable output when javap is provided -l and " + addedOption);
        assertContains(output, "LocalVariableTable",
            "There should be LocalVariableTable output when javap is provided -l and " + addedOption);
    }

    private static void assertContains(String actual, String expectedSubstring, String message) {
        assertTrue(actual.contains(expectedSubstring),
            message + " - Expected '" + actual + "' to contain '" + expectedSubstring + "'");
    }

    private static void assertNotContains(String actual, String expectedSubstring, String message) {
        assertFalse(actual.contains(expectedSubstring),
            message + " - Expected '" + actual + "' not to contain '" + expectedSubstring + "'");
    }

    private String javap(String... args) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        String[] fullArgs = new String[args.length + 1];
        System.arraycopy(args, 0, fullArgs, 0, args.length);
        fullArgs[args.length] = System.getProperty("test.classes") + "/RandomLoop8345145.class";

        int rc = com.sun.tools.javap.Main.run(fullArgs, out);
        if (rc != 0)
            throw new Error("javap failed. rc=" + rc);
        out.close();
        System.out.println(sw);
        return sw.toString();
    }
}

class RandomLoop8345145 {
    public void randomLoop() {
        int x = 5;
        for (int i = 0; i < 10; i++) {
            x*=2;
        }
    }
}