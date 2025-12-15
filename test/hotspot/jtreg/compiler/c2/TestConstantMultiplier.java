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

package compiler.c2;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import compiler.lib.ir_framework.Test;

import java.util.Random;

/*
 * @test
 * @bug 8373480
 * @summary Optimize multiplication by constant multiplier using LEA instructions
 * @library /test/lib /
 * @run driver compiler.c2.TestConstantMultiplier
 */
public class TestConstantMultiplier {
    private static final Random RANDOM = AbstractInfo.getRandom();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy81I(int num) {
        return num * 81;
    }

    @Run(test = "testMultBy81I")
    private static void runMultBy81II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(81 * multiplicand, testMultBy81I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy73I(int num) {
        return num * 73;
    }

    @Run(test = "testMultBy73I")
    private static void runMultBy73II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(73 * multiplicand, testMultBy73I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy45I(int num) {
        return num * 45;
    }

    @Run(test = "testMultBy45I")
    private static void runMultBy45II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(45 * multiplicand, testMultBy45I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy41I(int num) {
        return num * 41;
    }

    @Run(test = "testMultBy41I")
    private static void runMultBy41II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(41 * multiplicand, testMultBy41I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy37I(int num) {
        return num * 37;
    }

    @Run(test = "testMultBy37I")
    private static void runMultBy37II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(37 * multiplicand, testMultBy37I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy27I(int num) {
        return num * 27;
    }

    @Run(test = "testMultBy27I")
    private static void runMultBy27II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(27 * multiplicand, testMultBy27I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy25I(int num) {
        return num * 25;
    }

    @Run(test = "testMultBy25I")
    private static void runMultBy25II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(25 * multiplicand, testMultBy25I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy21I(int num) {
        return num * 21;
    }

    @Run(test = "testMultBy21I")
    private static void runMultBy21II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(21 * multiplicand, testMultBy21I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy19I(int num) {
        return num * 19;
    }

    @Run(test = "testMultBy19I")
    private static void runMultBy19II() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(19 * multiplicand, testMultBy19I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy13I(int num) {
        return num * 13;
    }

    @Run(test = "testMultBy13I")
    private static void runMultBy13I() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(13 * multiplicand, testMultBy13I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_I, "1"})
    private static int testMultBy11I(int num) {
        return num * 11;
    }

    @Run(test = "testMultBy11I")
    private static void runMultBy11I() {
        int multiplicand = RANDOM.nextInt();
        Verify.checkEQ(11 * multiplicand, testMultBy11I(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy81L(long num) {
        return num * 81;
    }

    @Run(test = "testMultBy81L")
    private static void runMultBy81L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(81 * multiplicand, testMultBy81L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy73L(long num) {
        return num * 73;
    }

    @Run(test = "testMultBy73L")
    private static void runMultBy73L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(73 * multiplicand, testMultBy73L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy45L(long num) {
        return num * 45;
    }

    @Run(test = "testMultBy45L")
    private static void runMultBy45L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(45 * multiplicand, testMultBy45L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy41L(long num) {
        return num * 41;
    }

    @Run(test = "testMultBy41L")
    private static void runMultBy41L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(41 * multiplicand, testMultBy41L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy37L(long num) {
        return num * 37;
    }

    @Run(test = "testMultBy37L")
    private static void runMultBy37L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(37 * multiplicand, testMultBy37L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy27L(long num) {
        return num * 27;
    }

    @Run(test = "testMultBy27L")
    private static void runMultBy27L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(27 * multiplicand, testMultBy27L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy25L(long num) {
        return num * 25;
    }

    @Run(test = "testMultBy25L")
    private static void runMultBy25L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(25 * multiplicand, testMultBy25L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy21L(long num) {
        return num * 21;
    }

    @Run(test = "testMultBy21L")
    private static void runMultBy21L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(21 * multiplicand, testMultBy21L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy19L(long num) {
        return num * 19;
    }

    @Run(test = "testMultBy19L")
    private static void runMultBy19L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(19 * multiplicand, testMultBy19L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy13L(long num) {
        return num * 13;
    }

    @Run(test = "testMultBy13L")
    private static void runMultBy13L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(13 * multiplicand, testMultBy13L(multiplicand));
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, counts = {IRNode.X86_MULT_IMM_L, "1"})
    private static long testMultBy11L(long num) {
        return num * 11;
    }

    @Run(test = "testMultBy11L")
    private static void runMultBy11L() {
        long multiplicand = RANDOM.nextInt();
        Verify.checkEQ(11 * multiplicand, testMultBy11L(multiplicand));
    }
}
