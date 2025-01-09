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
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.incubator.vector.*;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8342393
 * @key randomness
 * @library /test/lib /
 * @summary Promote vector IR node sharing
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorCommutativeOperSharingTest
 */

public class VectorCommutativeOperSharingTest {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;

    private static int LENGTH = 128;
    private static final Random RD = Utils.getRandomInstance();

    private static int[] ia;
    private static int[] ib;
    private static int[] ir1;
    private static int[] ir2;
    private static int[] ir3;
    private static int[] ir4;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ir1 = new int[LENGTH];
        ir2 = new int[LENGTH];
        ir3 = new int[LENGTH];
        ir4 = new int[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = RD.nextInt(25);
            ib[i] = RD.nextInt(25);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 2 ", IRNode.MUL_VI, " 2 ", IRNode.MAX_VI, " 2 ",
                  IRNode.MIN_VI, " 2 "})
    public void testVectorIRSharing1(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        vec1.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
        vec1.lanewise(VectorOperators.MUL, vec2)
            .lanewise(VectorOperators.MUL, vec2.lanewise(VectorOperators.MUL, vec1))
            .intoArray(ir2, index);
        vec1.lanewise(VectorOperators.MAX, vec2)
            .lanewise(VectorOperators.MAX, vec2.lanewise(VectorOperators.MAX, vec1))
            .intoArray(ir3, index);
        vec1.lanewise(VectorOperators.MIN, vec2)
            .lanewise(VectorOperators.MIN, vec2.lanewise(VectorOperators.MIN, vec1))
            .intoArray(ir4, index);
    }

    @Run(test = "testVectorIRSharing1")
    public void testVectorIRSharingDriver1() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing1(i);
        }
    }

    @Test
    @IR(counts = {IRNode.XOR_VI, " 0 ", IRNode.OR_VI, " 1 ", IRNode.AND_VI, " 1 "})
    public void testVectorIRSharing2(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        vec1.lanewise(VectorOperators.XOR, vec2)
            .lanewise(VectorOperators.XOR, vec2.lanewise(VectorOperators.XOR, vec1))
            .intoArray(ir1, index);
        vec1.lanewise(VectorOperators.AND, vec2)
            .lanewise(VectorOperators.AND, vec2.lanewise(VectorOperators.AND, vec1))
            .intoArray(ir2, index);
        vec1.lanewise(VectorOperators.OR, vec2)
            .lanewise(VectorOperators.OR, vec2.lanewise(VectorOperators.OR, vec1))
            .intoArray(ir3, index);
    }

    @Run(test = "testVectorIRSharing2")
    public void testVectorIRSharingDriver2() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing2(i);
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, " 2 "}, applyIfCPUFeature = {"avx2", "true"})
    public void testVectorIRSharing3(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        vec1.lanewise(VectorOperators.SADD, vec2)
            .lanewise(VectorOperators.SADD, vec2.lanewise(VectorOperators.SADD, vec1))
            .intoArray(ir4, index);
    }

    @Run(test = "testVectorIRSharing3")
    public void testVectorIRSharingDriver3() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing3(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 2 "})
    public void testVectorIRSharing4(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec1) + (vec1 + vec1)
        vec1.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing4")
    public void testVectorIRSharingDriver4() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing4(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing5(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec1) + (vec1 + vec2)
        vec1.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec2))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing5")
    public void testVectorIRSharingDriver5() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing5(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing6(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec1) + (vec2 + vec1)
        vec1.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing6")
    public void testVectorIRSharingDriver6() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing6(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing7(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec1) + (vec2 + vec2)
        vec1.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec2))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing7")
    public void testVectorIRSharingDriver7() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing7(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing8(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec2) + (vec1 + vec1)
        vec1.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing8")
    public void testVectorIRSharingDriver8() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing8(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 2 "})
    public void testVectorIRSharing9(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec2) + (vec1 + vec2)
        vec1.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec2))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing9")
    public void testVectorIRSharingDriver9() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing9(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 2 "})
    public void testVectorIRSharing10(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec2) + (vec2 + vec1)
        vec1.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing10")
    public void testVectorIRSharingDriver10() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing10(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing11(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec1 + vec2) + (vec2 + vec2)
        vec1.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec2))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing11")
    public void testVectorIRSharingDriver11() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing11(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing12(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec2 + vec1) + (vec1 + vec1)
        vec2.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing12")
    public void testVectorIRSharingDriver12() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing12(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 2 "})
    public void testVectorIRSharing13(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec2 + vec1) + (vec1 + vec2)
        vec2.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec2))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing13")
    public void testVectorIRSharingDriver13() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing13(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 2 "})
    public void testVectorIRSharing14(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec2 + vec1) + (vec2 + vec1)
        vec2.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing14")
    public void testVectorIRSharingDriver14() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing14(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing15(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec2 + vec1) + (vec2 + vec2)
        vec2.lanewise(VectorOperators.ADD, vec1)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec2))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing15")
    public void testVectorIRSharingDriver15() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing15(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing16(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec2 + vec2) + (vec1 + vec1)
        vec2.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing16")
    public void testVectorIRSharingDriver16() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing16(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing17(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec2 + vec2) + (vec1 + vec2)
        vec2.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.ADD, vec2))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing17")
    public void testVectorIRSharingDriver17() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing17(i);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " 3 "})
    public void testVectorIRSharing18(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // (vec2 + vec2) + (vec2 + vec1)
        vec2.lanewise(VectorOperators.ADD, vec2)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing18")
    public void testVectorIRSharingDriver18() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing18(i);
        }
    }
}
