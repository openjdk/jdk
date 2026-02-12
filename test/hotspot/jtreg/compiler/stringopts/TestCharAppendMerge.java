/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * or visit www.oracle.com if you need more information or have any questions.
 */

package compiler.stringopts;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8350123
 * @summary Test merging of consecutive char appends and constant string appends in StringBuilder
 * @library /test/lib /
 * @run main compiler.stringopts.TestCharAppendMerge
 */

/**
 * This test verifies that consecutive StringBuilder.append(char) calls are merged
 * into append(char, char) or append(char, char, char, char) calls, and that constant
 * strings of length 2 or 4 are converted to char appends.
 *
 * The optimization enables MergeStore to combine multiple byte stores into a single
 * short or int store, improving performance.
 */
public class TestCharAppendMerge {

    public static void main(String[] args) {
        TestFramework.run();
    }

    // Test: Two consecutive append(char) calls should be merged
    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\[int:>=0\\].*java/lang/String", "<=2"})  // Should have fewer stores after merge
    public String testAppendCharPair() {
        StringBuilder sb = new StringBuilder();
        sb.append('a');
        sb.append('b');
        return sb.toString();
    }

    // Test: Four consecutive append(char) calls should be merged
    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\[int:>=0\\].*java/lang/String", "<=4"})  // Should have fewer stores after merge
    public String testAppendCharQuad() {
        StringBuilder sb = new StringBuilder();
        sb.append('a');
        sb.append('b');
        sb.append('c');
        sb.append('d');
        return sb.toString();
    }

    // Test: Chained append(char) calls should be merged
    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\[int:>=0\\].*java/lang/String", "<=2"})
    public String testChainedAppendCharPair() {
        StringBuilder sb = new StringBuilder();
        sb.append('a').append('b');
        return sb.toString();
    }

    // Test: Chained append(char) calls with 4 chars should be merged
    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\[int:>=0\\].*java/lang/String", "<=4"})
    public String testChainedAppendCharQuad() {
        StringBuilder sb = new StringBuilder();
        sb.append('a').append('b').append('c').append('d');
        return sb.toString();
    }

    // Test: Constant string of length 2 should be converted to char pair
    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\[int:>=0\\].*java/lang/String", "<=2"})
    public String testConstantStringLength2() {
        StringBuilder sb = new StringBuilder();
        sb.append("ab");
        return sb.toString();
    }

    // Test: Constant string of length 4 should be converted to char quad
    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\[int:>=0\\].*java/lang/String", "<=4"})
    public String testConstantStringLength4() {
        StringBuilder sb = new StringBuilder();
        sb.append("abcd");
        return sb.toString();
    }

    // Test: Mix of merged and non-merged appends
    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\[int:>=0\\].*java/lang/String", "<=7"})
    public String testMixedAppends() {
        StringBuilder sb = new StringBuilder();
        sb.append('a').append('b');     // merged to pair
        sb.append('x');                  // single
        sb.append('c').append('d').append('e').append('f');  // merged to quad
        sb.append('y');                  // single
        return sb.toString();
    }

    // Test: Ensure correctness with digits - using pairs
    @Test
    public String testDigitsPair() {
        StringBuilder sb = new StringBuilder();
        sb.append('0').append('1');  // first pair -> "01"
        sb.append('2').append('3');  // second pair -> "0123"
        return sb.toString();
    }

    // Test: Ensure correctness with digits in quad
    @Test
    public String testDigitsQuad() {
        StringBuilder sb = new StringBuilder();
        sb.append('0').append('1').append('2').append('3');  // quad -> "0123"
        return sb.toString();
    }

    // Test: Verify result correctness for pair
    @Run(test = "testAppendCharPair")
    public void runTestAppendCharPair(RunInfo info) {
        String result = testAppendCharPair();
        if (!result.equals("ab")) {
            throw new RuntimeException("Expected 'ab' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for quad
    @Run(test = "testAppendCharQuad")
    public void runTestAppendCharQuad(RunInfo info) {
        String result = testAppendCharQuad();
        if (!result.equals("abcd")) {
            throw new RuntimeException("Expected 'abcd' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for chained pair
    @Run(test = "testChainedAppendCharPair")
    public void runTestChainedAppendCharPair(RunInfo info) {
        String result = testChainedAppendCharPair();
        if (!result.equals("ab")) {
            throw new RuntimeException("Expected 'ab' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for chained quad
    @Run(test = "testChainedAppendCharQuad")
    public void runTestChainedAppendCharQuad(RunInfo info) {
        String result = testChainedAppendCharQuad();
        if (!result.equals("abcd")) {
            throw new RuntimeException("Expected 'abcd' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for constant string length 2
    @Run(test = "testConstantStringLength2")
    public void runTestConstantStringLength2(RunInfo info) {
        String result = testConstantStringLength2();
        if (!result.equals("ab")) {
            throw new RuntimeException("Expected 'ab' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for constant string length 4
    @Run(test = "testConstantStringLength4")
    public void runTestConstantStringLength4(RunInfo info) {
        String result = testConstantStringLength4();
        if (!result.equals("abcd")) {
            throw new RuntimeException("Expected 'abcd' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for mixed appends
    @Run(test = "testMixedAppends")
    public void runTestMixedAppends(RunInfo info) {
        String result = testMixedAppends();
        if (!result.equals("abxcdxefy")) {
            throw new RuntimeException("Expected 'abxcdxefy' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for digits pair
    @Run(test = "testDigitsPair")
    public void runTestDigitsPair(RunInfo info) {
        String result = testDigitsPair();
        if (!result.equals("0123")) {
            throw new RuntimeException("Expected '0123' but got '" + result + "'");
        }
    }

    // Test: Verify result correctness for digits quad
    @Run(test = "testDigitsQuad")
    public void runTestDigitsQuad(RunInfo info) {
        String result = testDigitsQuad();
        if (!result.equals("0123")) {
            throw new RuntimeException("Expected '0123' but got '" + result + "'");
        }
    }
}
