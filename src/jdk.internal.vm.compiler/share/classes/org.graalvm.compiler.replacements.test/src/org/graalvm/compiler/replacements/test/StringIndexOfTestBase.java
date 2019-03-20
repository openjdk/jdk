/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import static org.junit.Assume.assumeFalse;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import jdk.vm.ci.aarch64.AArch64;

@RunWith(value = Parameterized.class)
public abstract class StringIndexOfTestBase extends GraalCompilerTest {
    @Parameterized.Parameter(value = 0) public String sourceString;
    @Parameterized.Parameter(value = 1) public String constantString;

    @Parameterized.Parameters(name = "{0},{1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        String[] targets = new String[]{"foobar", "foo", "bar"};
        String[] utf16targets = new String[]{"grga " + ((char) 0x10D) + "varak", "grga", ((char) 0x10D) + "varak"};
        addTargets(tests, targets);
        addTargets(tests, utf16targets);

        // Check long targets
        // Checkstyle: stop
        String lipsum = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata ";
        // Checkstyle: resume
        String lipsumUTF16 = lipsum + ((char) 0x10D);
        int[] subStringLengths = {7, 8, 15, 16, 31, 32, 63, 64};
        for (int len : subStringLengths) {
            String target = lipsum.substring(50, 50 + len);
            tests.add(new Object[]{lipsum, target});
            tests.add(new Object[]{lipsum, target + "X"});
            tests.add(new Object[]{lipsumUTF16, target});
            tests.add(new Object[]{lipsumUTF16, target + "X"});
            tests.add(new Object[]{lipsumUTF16, target + ((char) 0x10D)});
        }
        tests.add(new Object[]{
                        "\u0100\u0101\u0102\u0103\u0104\u0105\u0106\u0107\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff\u0108\u0109\u010a\u010b\u010c",
                        "\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff"});

        return tests;
    }

    private static void addTargets(ArrayList<Object[]> tests, String[] targets) {
        for (String source : targets) {
            for (String target : targets) {
                tests.add(new Object[]{source, target});
            }
            tests.add(new Object[]{source, ""});
            tests.add(new Object[]{"", source});
            tests.add(new Object[]{"", ""});
        }
        for (String source : targets) {
            String s = "";
            for (int i = 0; i < 10; i++) {
                s = s + source.substring(0, source.length() - 1);
            }
            for (String target : targets) {
                tests.add(new Object[]{s, target});
                tests.add(new Object[]{s + target, target});
                tests.add(new Object[]{s.substring(0, s.length() - 1) + s, s});
            }
        }
    }

    public int testStringIndexOf(String a, String b) {
        return a.indexOf(b);
    }

    public int testStringIndexOfOffset(String a, String b, int fromIndex) {
        return a.indexOf(b, fromIndex);
    }

    public int testStringBuilderIndexOf(StringBuilder a, String b) {
        return a.indexOf(b);
    }

    public int testStringBuilderIndexOfOffset(StringBuilder a, String b, int fromIndex) {
        return a.indexOf(b, fromIndex);
    }

    @Test
    public void testStringIndexOfConstant() {
        test("testStringIndexOf", new Object[]{this.sourceString, this.constantString});
    }

    @Test
    public void testStringIndexOfConstantOffset() {
        test("testStringIndexOfOffset", new Object[]{this.sourceString, this.constantString, -1});
        test("testStringIndexOfOffset", new Object[]{this.sourceString, this.constantString, 0});
        test("testStringIndexOfOffset", new Object[]{this.sourceString, this.constantString, Math.max(0, sourceString.length() - constantString.length())});
    }

    @Test
    public void testStringBuilderIndexOfConstant() {
        assumeFalse("Disabled on AArch64 due to issues on AArch64; see GR-13100 or JDK-8215792", getTarget().arch instanceof AArch64);
        /*
         * Put a copy of the target string in the space after the current string to detect cases
         * where we search too far.
         */
        StringBuilder sb = new StringBuilder(this.sourceString);
        sb.append(constantString);
        sb.setLength(sourceString.length());
        test("testStringBuilderIndexOf", new Object[]{sb, this.constantString});
    }

    @Test
    public void testStringBuilderIndexOfConstantOffset() {
        assumeFalse("Disabled on AArch64 due to issues on AArch64; see GR-13100 or JDK-8215792", getTarget().arch instanceof AArch64);
        /*
         * Put a copy of the target string in the space after the current string to detect cases
         * where we search too far.
         */
        StringBuilder sb = new StringBuilder(this.sourceString);
        sb.append(constantString);
        sb.setLength(sourceString.length());
        test("testStringBuilderIndexOfOffset", new Object[]{sb, this.constantString, -1});
        test("testStringBuilderIndexOfOffset", new Object[]{sb, this.constantString, 0});
        test("testStringBuilderIndexOfOffset", new Object[]{sb, this.constantString, Math.max(0, sourceString.length() - constantString.length())});
    }
}
