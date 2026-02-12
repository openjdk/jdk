/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * 2 along with this work; if not write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have
 * any questions.
 */

/**
 * @test
 * @summary Test that DecimalDigits.appendPair/appendQuad trigger char coalescing and MergeStore optimizations
 * @library /test/lib /
 * @modules java.base/jdk.internal.util
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   --add-exports java.base/jdk.internal.util=ALL-UNNAMED
 *                   compiler.stringopts.TestDecimalDigitsCharCoalescing
 */

package compiler.stringopts;

import compiler.lib.ir_framework.*;
import jdk.internal.util.DecimalDigits;

public class TestDecimalDigitsCharCoalescing {

    private static final StringBuilder SB = new StringBuilder();

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework(TestDecimalDigitsCharCoalescing.class);
        testFramework.addFlags("--add-exports", "java.base/jdk.internal.util=ALL-UNNAMED");
        testFramework.start();
    }

    // Setup methods
    @Setup
    static Object[] setupStringBuilderAndInt() {
        return new Object[] { SB, 42 };
    }

    @Setup
    static Object[] setupStringBuilderAndTwoInts() {
        return new Object[] { SB, 12, 3456 };
    }

    // Test appendPair: two consecutive append(char) calls should be coalesced into CharPairMode,
    // then MergeStore should convert two StoreB into one StoreC
    @Test
    @Arguments(setup = "setupStringBuilderAndInt")
    @IR(counts = {IRNode.STORE_C_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testAppendPair(StringBuilder sb, int v) {
        sb.setLength(0);
        DecimalDigits.appendPair(sb, v);
        return sb.toString();
    }

    // Test appendQuad: four consecutive append(char) calls should be coalesced into CharQuadMode,
    // then MergeStore should convert four StoreB into one StoreI
    @Test
    @Arguments(setup = "setupStringBuilderAndInt")
    @IR(counts = {IRNode.STORE_I_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testAppendQuad(StringBuilder sb, int v) {
        sb.setLength(0);
        DecimalDigits.appendQuad(sb, v);
        return sb.toString();
    }

    // Test combined appendPair + appendQuad: should result in both StoreC and StoreI
    @Test
    @Arguments(setup = "setupStringBuilderAndTwoInts")
    @IR(counts = {IRNode.STORE_C_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1",
                  IRNode.STORE_I_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testAppendPairAndQuad(StringBuilder sb, int pair, int quad) {
        sb.setLength(0);
        DecimalDigits.appendPair(sb, pair);
        DecimalDigits.appendQuad(sb, quad);
        return sb.toString();
    }

    // Test multiple appendQuad calls: should result in multiple StoreI
    @Test
    @Arguments(setup = "setupStringBuilderAndTwoInts")
    @IR(counts = {IRNode.STORE_I_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=2"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testMultipleAppendQuad(StringBuilder sb, int v1, int v2) {
        sb.setLength(0);
        DecimalDigits.appendQuad(sb, v1);
        DecimalDigits.appendQuad(sb, v2);
        return sb.toString();
    }
}
