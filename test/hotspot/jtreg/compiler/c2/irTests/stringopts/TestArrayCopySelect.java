/*
 * Copyright (c) 2025, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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

package compiler.c2.irTests.stringopts;

import compiler.lib.ir_framework.*;

/**
 * @test
 * @bug 8359270
 * @requires vm.debug == true & vm.compiler2.enabled
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="riscv64" | os.arch=="aarch64"
 * @summary C2: alignment check should consider base offset when emitting arraycopy runtime call.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.stringopts.TestArrayCopySelect
 */

public class TestArrayCopySelect {

    public static final String input_strU = "\u0f21\u0f22\u0f23\u0f24\u0f25\u0f26\u0f27\u0f28";
    public static final char[] input_arrU = new char[] {'\u0f21', '\u0f22', '\u0f23', '\u0f24',
                                                        '\u0f25', '\u0f26', '\u0f27', '\u0f28'};

    public static String output_strU;
    public static char[] output_arrU;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseCompactObjectHeaders",
                                   "-XX:-CompactStrings",
                                   "-XX:CompileCommand=inline,java.lang.StringBuilder::toString",
                                   "-XX:CompileCommand=inline,java.lang.StringUTF16::getChars",
                                   "-XX:CompileCommand=inline,java.lang.StringUTF16::toBytes");

        TestFramework.runWithFlags("-XX:+UseCompactObjectHeaders",
                                   "-XX:-CompactStrings",
                                   "-XX:CompileCommand=inline,java.lang.StringBuilder::toString",
                                   "-XX:CompileCommand=inline,java.lang.StringUTF16::getChars",
                                   "-XX:CompileCommand=inline,java.lang.StringUTF16::toBytes");
    }

    @Test
    @Warmup(10000)
    @IR(applyIf = {"UseCompactObjectHeaders", "false"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", ">0"})
    static void testSBToStringAligned() {
        // Exercise the StringBuilder.toString API
        StringBuilder sb = new StringBuilder(input_strU);
        output_strU = sb.append(input_strU).toString();
    }

    @Test
    @Warmup(10000)
    @IR(applyIf = {"UseCompactObjectHeaders", "true"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", "0"})
    static void testSBToStringUnAligned() {
        // Exercise the StringBuilder.toString API
        StringBuilder sb = new StringBuilder(input_strU);
        output_strU = sb.append(input_strU).toString();
    }

    @Test
    @Warmup(10000)
    @IR(applyIf = {"UseCompactObjectHeaders", "false"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", ">0"})
    static void testStrUGetCharsAligned() {
        // Exercise the StringUTF16.getChars API
        output_arrU = input_strU.toCharArray();
    }

    @Test
    @Warmup(10000)
    @IR(applyIf = {"UseCompactObjectHeaders", "true"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", "0"})
    static void testStrUGetCharsUnAligned() {
        // Exercise the StringUTF16.getChars API
        output_arrU = input_strU.toCharArray();
    }

    @Test
    @Warmup(10000)
    @IR(applyIf = {"UseCompactObjectHeaders", "false"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", ">0"})
    static void testStrUtoBytesAligned() {
        // Exercise the StringUTF16.toBytes API
        output_strU = String.valueOf(input_arrU);
    }

    @Test
    @Warmup(10000)
    @IR(applyIf = {"UseCompactObjectHeaders", "true"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", "0"})
    static void testStrUtoBytesUnAligned() {
        // Exercise the StringUTF16.toBytes API
        output_strU = String.valueOf(input_arrU);
    }

}
