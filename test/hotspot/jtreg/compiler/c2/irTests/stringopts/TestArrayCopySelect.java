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
 * @summary C2: alignment check should consider base offset when emitting
            arraycopy runtime call.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.stringopts.TestArrayCopySelect
 */

public class TestArrayCopySelect {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addScenarios(new Scenario(0, "-XX:-UseCompactObjectHeaders"),
                               new Scenario(1, "-XX:+UseCompactObjectHeaders"));
        framework.start();
    }

    @Test
    @IR(applyIf = {"UseCompactObjectHeaders", "false"},
        counts = {IRNode.CALL_OF, "arrayof_jbyte_disjoint_arraycopy", ">0"})
    static String testStrLConcatAligned() {
        // Exercise the StringBuilder.toString API
        StringBuilder sb = new StringBuilder("abcdefghijklmnop");
        return sb.append("ABCDEFGHIJKLMNOP").toString();
    }

    @Test
    @IR(applyIf = {"UseCompactObjectHeaders", "true"},
        counts = {IRNode.CALL_OF, "arrayof_jbyte_disjoint_arraycopy", "0"})
    static String testStrLConcatUnAligned() {
        // Exercise the StringBuilder.toString API
        StringBuilder sb = new StringBuilder("abcdefghijklmnop");
        return sb.append("ABCDEFGHIJKLMNOP").toString();
    }

    @Test
    @IR(applyIf = {"UseCompactObjectHeaders", "false"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", ">0"})
    static char[] testStrUGetCharsAligned(String strU) {
        // Exercise the StringUTF16.getChars API
        return strU.toCharArray();
    }

    @Test
    @IR(applyIf = {"UseCompactObjectHeaders", "true"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", "0"})
    static char[] testStrUGetCharsUnAligned(String strU) {
        // Exercise the StringUTF16.getChars API
        return strU.toCharArray();
    }

    @Test
    @IR(applyIf = {"UseCompactObjectHeaders", "false"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", ">0"})
    static String testStrUtoBytesAligned(char[] arrU) {
        // Exercise the StringUTF16.toBytes API
        return String.valueOf(arrU);
    }

    @Test
    @IR(applyIf = {"UseCompactObjectHeaders", "true"},
        counts = {IRNode.CALL_OF, "arrayof_jshort_disjoint_arraycopy", "0"})
    static String testStrUtoBytesUnAligned(char[] arrU) {
        // Exercise the StringUTF16.toBytes API
        return String.valueOf(arrU);
    }

    @Run(test = {"testStrLConcatAligned",
                 "testStrLConcatUnAligned",
                 "testStrUGetCharsAligned",
                 "testStrUGetCharsUnAligned",
                 "testStrUtoBytesAligned",
                 "testStrUtoBytesUnAligned"})
    public void runTests() {
        {
            String strL = testStrLConcatAligned();
        }
        {
            String strL = testStrLConcatUnAligned();
        }
        {
            String strU = "\u0f21\u0f22\u0f23\u0f24\u0f25\u0f26\u0f27\u0f28";
            char[] arrU = testStrUGetCharsAligned(strU);
        }
        {
            String strU = "\u0f21\u0f22\u0f23\u0f24\u0f25\u0f26\u0f27\u0f28";
            char[] arrU = testStrUGetCharsUnAligned(strU);
        }
        {
            char[] arrU = new char[] {'\u0f21', '\u0f22', '\u0f23', '\u0f24',
                                      '\u0f25', '\u0f26', '\u0f27', '\u0f28'};
            String strU = testStrUtoBytesAligned(arrU);
        }
        {
            char[] arrU = new char[] {'\u0f21', '\u0f22', '\u0f23', '\u0f24',
                                      '\u0f25', '\u0f26', '\u0f27', '\u0f28'};
            String strU = testStrUtoBytesUnAligned(arrU);
        }
    }

}
