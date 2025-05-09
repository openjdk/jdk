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
import compiler.lib.verify.*;
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
            ia[i] = RD.nextInt(Integer.MAX_VALUE);
            ib[i] = RD.nextInt(Integer.MAX_VALUE);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 2 ",
                  IRNode.MUL_VI, IRNode.VECTOR_SIZE_ANY, " 2 ",
                  IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 2 ",
                  IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 2 "},
        applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing1();
    }

    public void checkVectorIRSharing1() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ib[i]) + (ib[i] + ia[i]));
            Verify.checkEQ(ir2[i], (ia[i] * ib[i]) * (ib[i] * ia[i]));
            Verify.checkEQ(ir3[i], Integer.max(Integer.max(ia[i], ib[i]), Integer.max(ib[i], ia[i])));
            Verify.checkEQ(ir4[i], Integer.min(Integer.max(ia[i], ib[i]), Integer.min(ib[i], ia[i])));
        }
    }

    @Test
    @IR(counts = {IRNode.XOR_VI, IRNode.VECTOR_SIZE_ANY, " 0 ",
                  IRNode.OR_VI, IRNode.VECTOR_SIZE_ANY, " 1 ",
                  IRNode.AND_VI, IRNode.VECTOR_SIZE_ANY, " 1 "},
        applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing2();
    }

    public void checkVectorIRSharing2() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] ^ ib[i]) ^ (ib[i] ^ ia[i]));
            Verify.checkEQ(ir2[i], (ia[i] & ib[i]) & (ib[i] & ia[i]));
            Verify.checkEQ(ir3[i], (ia[i] | ib[i]) | (ib[i] | ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.SATURATING_ADD_VI, IRNode.VECTOR_SIZE_ANY, " 2 "},
        applyIfCPUFeatureOr = {"avx2", "true", "rvv", "true"})
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
        checkVectorIRSharing3();
    }

    public void checkVectorIRSharing3() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir4[i], VectorMath.addSaturating(VectorMath.addSaturating(ia[i], ib[i]),
                                                            VectorMath.addSaturating(ib[i], ia[i])));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 2 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing4();
    }

    public void checkVectorIRSharing4() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ia[i]) + (ia[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing5();
    }

    public void checkVectorIRSharing5() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ia[i]) + (ia[i] + ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing6();
    }

    public void checkVectorIRSharing6() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ia[i]) + (ib[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing7();
    }

    public void checkVectorIRSharing7() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ia[i]) + (ib[i] + ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing8();
    }

    public void checkVectorIRSharing8() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ib[i]) + (ia[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 2 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing9();
    }

    public void checkVectorIRSharing9() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ib[i]) + (ia[i] + ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 2 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing10();
    }

    public void checkVectorIRSharing10() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ib[i]) + (ib[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing11();
    }

    public void checkVectorIRSharing11() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ia[i] + ib[i]) + (ib[i] + ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing12();
    }

    public void checkVectorIRSharing12() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ib[i] + ia[i]) + (ia[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 2 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing13();
    }

    public void checkVectorIRSharing13() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ib[i] + ia[i]) + (ia[i] + ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 2 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing14();
    }

    public void checkVectorIRSharing14() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ib[i] + ia[i]) + (ib[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing15();
    }

    public void checkVectorIRSharing15() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ib[i] + ia[i]) + (ib[i] + ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing16();
    }

    public void checkVectorIRSharing16() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ib[i] + ib[i]) + (ia[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing17();
    }

    public void checkVectorIRSharing17() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ib[i] + ib[i]) + (ia[i] + ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
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
        checkVectorIRSharing18();
    }

    public void checkVectorIRSharing18() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], (ib[i] + ib[i]) + (ib[i] + ia[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " 3 "}, applyIfCPUFeatureOr = {"avx512vl", "true", "sve", "true", "rvv", "true"})
    public void testVectorIRSharing19(int index) {
        VectorMask<Integer> VMASK = VectorMask.fromLong(I_SPECIES, ((1 << 4) - 1));
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // PRED:(vec1 + vec2) + PRED:(vec2 + vec1)
        vec1.lanewise(VectorOperators.ADD, vec2, VMASK)
            .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1, VMASK))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing19")
    public void testVectorIRSharingDriver19() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing19(i);
        }
        checkVectorIRSharing19();
    }

    public void checkVectorIRSharing19() {
        VectorMask<Integer> VMASK = VectorMask.fromLong(I_SPECIES, ((1 << 4) - 1));
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            boolean mask = VMASK.laneIsSet(i & (I_SPECIES.length() - 1));
            Verify.checkEQ(ir1[i], (mask ? ia[i] + ib[i] : ia[i]) + (mask ? ib[i] + ia[i] : ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.UMAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    public void testVectorIRSharing20(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // UMax ((UMax vec1, vec2), (UMax vec2, vec1))
        vec1.lanewise(VectorOperators.UMAX, vec2)
            .lanewise(VectorOperators.UMAX, vec2.lanewise(VectorOperators.UMAX, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing20")
    public void testVectorIRSharingDriver20() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing20(i);
        }
        checkVectorIRSharing20();
    }

    public void checkVectorIRSharing20() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], VectorMath.maxUnsigned(ia[i], ib[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.UMIN_VI, IRNode.VECTOR_SIZE_ANY, " 1 "}, applyIfCPUFeatureOr = {"avx", "true", "rvv", "true"})
    public void testVectorIRSharing21(int index) {
        IntVector vec1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector vec2 = IntVector.fromArray(I_SPECIES, ib, index);
        // UMin ((UMin vec1, vec2), (UMin vec2, vec1))
        vec1.lanewise(VectorOperators.UMIN, vec2)
            .lanewise(VectorOperators.UMIN, vec2.lanewise(VectorOperators.UMIN, vec1))
            .intoArray(ir1, index);
    }

    @Run(test = "testVectorIRSharing21")
    public void testVectorIRSharingDriver21() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testVectorIRSharing21(i);
        }
        checkVectorIRSharing21();
    }

    public void checkVectorIRSharing21() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(ir1[i], VectorMath.minUnsigned(ia[i], ib[i]));
        }
    }
}
