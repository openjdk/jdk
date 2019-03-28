/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.test.MatchRuleTest;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.amd64.AMD64Binary;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer.MemoryConstOp;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer.ConstOp;
import org.graalvm.compiler.lir.amd64.AMD64Unary;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;

public class AMD64MatchRuleTest extends MatchRuleTest {
    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    public static int test1Snippet(TestClass o, TestClass b, TestClass c) {
        if (o.x == 42) {
            return b.z;
        } else {
            return c.y;
        }
    }

    /**
     * Verifies, if the match rules in AMD64NodeMatchRules do work on the graphs by compiling and
     * checking if the expected LIR instruction show up.
     */
    @Test
    public void test1() {
        compile(getResolvedJavaMethod("test1Snippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof MemoryConstOp && ((MemoryConstOp) ins).getOpcode().toString().equals("CMP")) {
                assertFalse("MemoryConstOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("Memory compare must be in the LIR", found);
    }

    public static class TestClass {
        public int x;
        public int y;
        public int z;

        public TestClass(int x) {
            super();
            this.x = x;
        }
    }

    static volatile short shortValue;

    public static long testVolatileExtensionSnippet() {
        return shortValue;
    }

    @Test
    public void testVolatileExtension() {
        compile(getResolvedJavaMethod("testVolatileExtensionSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AMD64Unary.MemoryOp) {
                ins.visitEachOutput((value, mode, flags) -> assertTrue(value.getPlatformKind().toString(), value.getPlatformKind().equals(AMD64Kind.QWORD)));
                assertFalse("MemoryOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("sign extending load must be in the LIR", found);
    }

    static int intValue;
    static volatile int volatileIntValue;

    /**
     * Can't match test and load of input because of volatile store in between.
     */
    public static short testLoadTestNoMatchSnippet() {
        int v = intValue;
        volatileIntValue = 42;
        if (v == 42) {
            return shortValue;
        }
        return 0;
    }

    @Test
    public void testLoadTestNoMatch() {
        compile(getResolvedJavaMethod("testLoadTestNoMatchSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof ConstOp && ((ConstOp) ins).getOpcode().toString().equals("CMP")) {
                assertFalse("CMP expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("CMP must be in the LIR", found);
    }

    /**
     * Should match as an add with a memory operand.
     */
    public static int testAddLoadSnippet() {
        int v1 = volatileIntValue;
        int v2 = intValue;
        return v2 + (2 * v1);
    }

    @Test
    public void testAddLoad() {
        compile(getResolvedJavaMethod("testAddLoadSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AMD64Binary.MemoryTwoOp && ((AMD64Binary.MemoryTwoOp) ins).getOpcode().toString().equals("ADD")) {
                assertFalse("MemoryTwoOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("ADD with memory argument must be in the LIR", found);
    }

    /**
     * Can't match as an add with a memory operand because the other add input is too late.
     */
    public static int testAddLoadNoMatchSnippet() {
        int v1 = volatileIntValue;
        int v2 = intValue;
        return v1 + (2 * v2);
    }

    @Test
    public void testAddLoadNoMatch() {
        compile(getResolvedJavaMethod("testAddLoadNoMatchSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AMD64Binary.CommutativeTwoOp && ((AMD64Binary.CommutativeTwoOp) ins).getOpcode().toString().equals("ADD")) {
                assertFalse("CommutativeTwoOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("ADD with memory argument must not be in the LIR", found);
    }

    /**
     * sign extension and load are in different blocks but can still be matched as a single
     * instruction.
     */
    public static long testVolatileExtensionDifferentBlocksSnippet(boolean flag) {
        short v = shortValue;
        if (flag) {
            return v;
        }
        return 0;
    }

    @Test
    public void testVolatileExtensionDifferentBlocks() {
        compile(getResolvedJavaMethod("testVolatileExtensionDifferentBlocksSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AMD64Unary.MemoryOp) {
                ins.visitEachOutput((value, mode, flags) -> assertTrue(value.getPlatformKind().toString(), value.getPlatformKind().equals(AMD64Kind.QWORD)));
                assertFalse("MemoryOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("sign extending load must be in the LIR", found);
    }

    /**
     * Add and load are not in the same block and one input is too late: can't match.
     */
    public static int testAddLoadDifferentBlocksNoMatchSnippet(boolean flag) {
        int v1 = volatileIntValue;
        if (flag) {
            int v2 = intValue;
            return v1 + (2 * v2);
        }
        return 0;
    }

    @Test
    public void testAddLoadDifferentBlocksNoMatch() {
        compile(getResolvedJavaMethod("testAddLoadDifferentBlocksNoMatchSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (AbstractBlockBase<?> b : lir.codeEmittingOrder()) {
            for (LIRInstruction ins : lir.getLIRforBlock(b)) {
                if (ins instanceof AMD64Binary.CommutativeTwoOp && ((AMD64Binary.CommutativeTwoOp) ins).getOpcode().toString().equals("ADD")) {
                    assertFalse("CommutativeTwoOp expected only once in first block", found);
                    found = true;
                }
            }
        }
        assertTrue("ADD with memory argument must not be in the LIR", found);
    }

    /**
     * Add and load are in different blocks but can still match.
     */
    public static int testAddLoadDifferentBlocksSnippet(boolean flag) {
        int v2 = intValue;
        int v1 = volatileIntValue;
        if (flag) {
            return v1 + v2;
        }
        return 0;
    }

    @Test
    public void testAddLoadDifferentBlocks() {
        compile(getResolvedJavaMethod("testAddLoadDifferentBlocksSnippet"), null);
        LIR lir = getLIR();
        boolean found = false;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AMD64Binary.MemoryTwoOp && ((AMD64Binary.MemoryTwoOp) ins).getOpcode().toString().equals("ADD")) {
                assertFalse("MemoryTwoOp expected only once in first block", found);
                found = true;
            }
        }
        assertTrue("ADD with memory argument must be in the LIR", found);
    }

}
