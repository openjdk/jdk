/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi.reshape.tests;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;

import static compiler.vectorapi.reshape.utils.VectorReshapeHelper.*;

/**
 * This class contains methods to test for reinterpretation operations that reinterpret
 * a vector as a similar vector with another element type.
 *
 * It is complicated to verify the IR in this case since a load/store with respect to
 * byte array will result in additional ReinterpretNodes if the vector element type is
 * not byte. As a result, arguments need to be arrays of the correct type.
 *
 * In each test, the ReinterpretNode is expected to appear exactly once.
 */
public class TestVectorRebracket {
    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB64toS64(byte[] input, short[] output) {
        vectorRebracket(BSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testB64toS64")
    public static void runB64toS64() throws Throwable {
        runRebracketHelper(BSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB64toI64(byte[] input, int[] output) {
        vectorRebracket(BSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testB64toI64")
    public static void runB64toI64() throws Throwable {
        runRebracketHelper(BSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB64toL64(byte[] input, long[] output) {
        vectorRebracket(BSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testB64toL64")
    public static void runB64toL64() throws Throwable {
        runRebracketHelper(BSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB64toF64(byte[] input, float[] output) {
        vectorRebracket(BSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testB64toF64")
    public static void runB64toF64() throws Throwable {
        runRebracketHelper(BSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB64toD64(byte[] input, double[] output) {
        vectorRebracket(BSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testB64toD64")
    public static void runB64toD64() throws Throwable {
        runRebracketHelper(BSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS64toB64(short[] input, byte[] output) {
        vectorRebracket(SSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testS64toB64")
    public static void runS64toB64() throws Throwable {
        runRebracketHelper(SSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS64toI64(short[] input, int[] output) {
        vectorRebracket(SSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testS64toI64")
    public static void runS64toI64() throws Throwable {
        runRebracketHelper(SSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS64toL64(short[] input, long[] output) {
        vectorRebracket(SSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testS64toL64")
    public static void runS64toL64() throws Throwable {
        runRebracketHelper(SSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS64toF64(short[] input, float[] output) {
        vectorRebracket(SSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testS64toF64")
    public static void runS64toF64() throws Throwable {
        runRebracketHelper(SSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS64toD64(short[] input, double[] output) {
        vectorRebracket(SSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testS64toD64")
    public static void runS64toD64() throws Throwable {
        runRebracketHelper(SSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI64toB64(int[] input, byte[] output) {
        vectorRebracket(ISPEC64, BSPEC64, input, output);
    }

    @Run(test = "testI64toB64")
    public static void runI64toB64() throws Throwable {
        runRebracketHelper(ISPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI64toS64(int[] input, short[] output) {
        vectorRebracket(ISPEC64, SSPEC64, input, output);
    }

    @Run(test = "testI64toS64")
    public static void runI64toS64() throws Throwable {
        runRebracketHelper(ISPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI64toL64(int[] input, long[] output) {
        vectorRebracket(ISPEC64, LSPEC64, input, output);
    }

    @Run(test = "testI64toL64")
    public static void runI64toL64() throws Throwable {
        runRebracketHelper(ISPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI64toF64(int[] input, float[] output) {
        vectorRebracket(ISPEC64, FSPEC64, input, output);
    }

    @Run(test = "testI64toF64")
    public static void runI64toF64() throws Throwable {
        runRebracketHelper(ISPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI64toD64(int[] input, double[] output) {
        vectorRebracket(ISPEC64, DSPEC64, input, output);
    }

    @Run(test = "testI64toD64")
    public static void runI64toD64() throws Throwable {
        runRebracketHelper(ISPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL64toB64(long[] input, byte[] output) {
        vectorRebracket(LSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testL64toB64")
    public static void runL64toB64() throws Throwable {
        runRebracketHelper(LSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL64toS64(long[] input, short[] output) {
        vectorRebracket(LSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testL64toS64")
    public static void runL64toS64() throws Throwable {
        runRebracketHelper(LSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL64toI64(long[] input, int[] output) {
        vectorRebracket(LSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testL64toI64")
    public static void runL64toI64() throws Throwable {
        runRebracketHelper(LSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL64toF64(long[] input, float[] output) {
        vectorRebracket(LSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testL64toF64")
    public static void runL64toF64() throws Throwable {
        runRebracketHelper(LSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL64toD64(long[] input, double[] output) {
        vectorRebracket(LSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testL64toD64")
    public static void runL64toD64() throws Throwable {
        runRebracketHelper(LSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF64toB64(float[] input, byte[] output) {
        vectorRebracket(FSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testF64toB64")
    public static void runF64toB64() throws Throwable {
        runRebracketHelper(FSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF64toS64(float[] input, short[] output) {
        vectorRebracket(FSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testF64toS64")
    public static void runF64toS64() throws Throwable {
        runRebracketHelper(FSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF64toI64(float[] input, int[] output) {
        vectorRebracket(FSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testF64toI64")
    public static void runF64toI64() throws Throwable {
        runRebracketHelper(FSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF64toL64(float[] input, long[] output) {
        vectorRebracket(FSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testF64toL64")
    public static void runF64toL64() throws Throwable {
        runRebracketHelper(FSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF64toD64(float[] input, double[] output) {
        vectorRebracket(FSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testF64toD64")
    public static void runF64toD64() throws Throwable {
        runRebracketHelper(FSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD64toB64(double[] input, byte[] output) {
        vectorRebracket(DSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testD64toB64")
    public static void runD64toB64() throws Throwable {
        runRebracketHelper(DSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD64toS64(double[] input, short[] output) {
        vectorRebracket(DSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testD64toS64")
    public static void runD64toS64() throws Throwable {
        runRebracketHelper(DSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD64toI64(double[] input, int[] output) {
        vectorRebracket(DSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testD64toI64")
    public static void runD64toI64() throws Throwable {
        runRebracketHelper(DSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD64toL64(double[] input, long[] output) {
        vectorRebracket(DSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testD64toL64")
    public static void runD64toL64() throws Throwable {
        runRebracketHelper(DSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD64toF64(double[] input, float[] output) {
        vectorRebracket(DSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testD64toF64")
    public static void runD64toF64() throws Throwable {
        runRebracketHelper(DSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB128toS128(byte[] input, short[] output) {
        vectorRebracket(BSPEC128, SSPEC128, input, output);
    }

    @Run(test = "testB128toS128")
    public static void runB128toS128() throws Throwable {
        runRebracketHelper(BSPEC128, SSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB128toI128(byte[] input, int[] output) {
        vectorRebracket(BSPEC128, ISPEC128, input, output);
    }

    @Run(test = "testB128toI128")
    public static void runB128toI128() throws Throwable {
        runRebracketHelper(BSPEC128, ISPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB128toL128(byte[] input, long[] output) {
        vectorRebracket(BSPEC128, LSPEC128, input, output);
    }

    @Run(test = "testB128toL128")
    public static void runB128toL128() throws Throwable {
        runRebracketHelper(BSPEC128, LSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB128toF128(byte[] input, float[] output) {
        vectorRebracket(BSPEC128, FSPEC128, input, output);
    }

    @Run(test = "testB128toF128")
    public static void runB128toF128() throws Throwable {
        runRebracketHelper(BSPEC128, FSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB128toD128(byte[] input, double[] output) {
        vectorRebracket(BSPEC128, DSPEC128, input, output);
    }

    @Run(test = "testB128toD128")
    public static void runB128toD128() throws Throwable {
        runRebracketHelper(BSPEC128, DSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS128toB128(short[] input, byte[] output) {
        vectorRebracket(SSPEC128, BSPEC128, input, output);
    }

    @Run(test = "testS128toB128")
    public static void runS128toB128() throws Throwable {
        runRebracketHelper(SSPEC128, BSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS128toI128(short[] input, int[] output) {
        vectorRebracket(SSPEC128, ISPEC128, input, output);
    }

    @Run(test = "testS128toI128")
    public static void runS128toI128() throws Throwable {
        runRebracketHelper(SSPEC128, ISPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS128toL128(short[] input, long[] output) {
        vectorRebracket(SSPEC128, LSPEC128, input, output);
    }

    @Run(test = "testS128toL128")
    public static void runS128toL128() throws Throwable {
        runRebracketHelper(SSPEC128, LSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS128toF128(short[] input, float[] output) {
        vectorRebracket(SSPEC128, FSPEC128, input, output);
    }

    @Run(test = "testS128toF128")
    public static void runS128toF128() throws Throwable {
        runRebracketHelper(SSPEC128, FSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS128toD128(short[] input, double[] output) {
        vectorRebracket(SSPEC128, DSPEC128, input, output);
    }

    @Run(test = "testS128toD128")
    public static void runS128toD128() throws Throwable {
        runRebracketHelper(SSPEC128, DSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI128toB128(int[] input, byte[] output) {
        vectorRebracket(ISPEC128, BSPEC128, input, output);
    }

    @Run(test = "testI128toB128")
    public static void runI128toB128() throws Throwable {
        runRebracketHelper(ISPEC128, BSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI128toS128(int[] input, short[] output) {
        vectorRebracket(ISPEC128, SSPEC128, input, output);
    }

    @Run(test = "testI128toS128")
    public static void runI128toS128() throws Throwable {
        runRebracketHelper(ISPEC128, SSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI128toL128(int[] input, long[] output) {
        vectorRebracket(ISPEC128, LSPEC128, input, output);
    }

    @Run(test = "testI128toL128")
    public static void runI128toL128() throws Throwable {
        runRebracketHelper(ISPEC128, LSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI128toF128(int[] input, float[] output) {
        vectorRebracket(ISPEC128, FSPEC128, input, output);
    }

    @Run(test = "testI128toF128")
    public static void runI128toF128() throws Throwable {
        runRebracketHelper(ISPEC128, FSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI128toD128(int[] input, double[] output) {
        vectorRebracket(ISPEC128, DSPEC128, input, output);
    }

    @Run(test = "testI128toD128")
    public static void runI128toD128() throws Throwable {
        runRebracketHelper(ISPEC128, DSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL128toB128(long[] input, byte[] output) {
        vectorRebracket(LSPEC128, BSPEC128, input, output);
    }

    @Run(test = "testL128toB128")
    public static void runL128toB128() throws Throwable {
        runRebracketHelper(LSPEC128, BSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL128toS128(long[] input, short[] output) {
        vectorRebracket(LSPEC128, SSPEC128, input, output);
    }

    @Run(test = "testL128toS128")
    public static void runL128toS128() throws Throwable {
        runRebracketHelper(LSPEC128, SSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL128toI128(long[] input, int[] output) {
        vectorRebracket(LSPEC128, ISPEC128, input, output);
    }

    @Run(test = "testL128toI128")
    public static void runL128toI128() throws Throwable {
        runRebracketHelper(LSPEC128, ISPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL128toF128(long[] input, float[] output) {
        vectorRebracket(LSPEC128, FSPEC128, input, output);
    }

    @Run(test = "testL128toF128")
    public static void runL128toF128() throws Throwable {
        runRebracketHelper(LSPEC128, FSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL128toD128(long[] input, double[] output) {
        vectorRebracket(LSPEC128, DSPEC128, input, output);
    }

    @Run(test = "testL128toD128")
    public static void runL128toD128() throws Throwable {
        runRebracketHelper(LSPEC128, DSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF128toB128(float[] input, byte[] output) {
        vectorRebracket(FSPEC128, BSPEC128, input, output);
    }

    @Run(test = "testF128toB128")
    public static void runF128toB128() throws Throwable {
        runRebracketHelper(FSPEC128, BSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF128toS128(float[] input, short[] output) {
        vectorRebracket(FSPEC128, SSPEC128, input, output);
    }

    @Run(test = "testF128toS128")
    public static void runF128toS128() throws Throwable {
        runRebracketHelper(FSPEC128, SSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF128toI128(float[] input, int[] output) {
        vectorRebracket(FSPEC128, ISPEC128, input, output);
    }

    @Run(test = "testF128toI128")
    public static void runF128toI128() throws Throwable {
        runRebracketHelper(FSPEC128, ISPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF128toL128(float[] input, long[] output) {
        vectorRebracket(FSPEC128, LSPEC128, input, output);
    }

    @Run(test = "testF128toL128")
    public static void runF128toL128() throws Throwable {
        runRebracketHelper(FSPEC128, LSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF128toD128(float[] input, double[] output) {
        vectorRebracket(FSPEC128, DSPEC128, input, output);
    }

    @Run(test = "testF128toD128")
    public static void runF128toD128() throws Throwable {
        runRebracketHelper(FSPEC128, DSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD128toB128(double[] input, byte[] output) {
        vectorRebracket(DSPEC128, BSPEC128, input, output);
    }

    @Run(test = "testD128toB128")
    public static void runD128toB128() throws Throwable {
        runRebracketHelper(DSPEC128, BSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD128toS128(double[] input, short[] output) {
        vectorRebracket(DSPEC128, SSPEC128, input, output);
    }

    @Run(test = "testD128toS128")
    public static void runD128toS128() throws Throwable {
        runRebracketHelper(DSPEC128, SSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD128toI128(double[] input, int[] output) {
        vectorRebracket(DSPEC128, ISPEC128, input, output);
    }

    @Run(test = "testD128toI128")
    public static void runD128toI128() throws Throwable {
        runRebracketHelper(DSPEC128, ISPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD128toL128(double[] input, long[] output) {
        vectorRebracket(DSPEC128, LSPEC128, input, output);
    }

    @Run(test = "testD128toL128")
    public static void runD128toL128() throws Throwable {
        runRebracketHelper(DSPEC128, LSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD128toF128(double[] input, float[] output) {
        vectorRebracket(DSPEC128, FSPEC128, input, output);
    }

    @Run(test = "testD128toF128")
    public static void runD128toF128() throws Throwable {
        runRebracketHelper(DSPEC128, FSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB256toS256(byte[] input, short[] output) {
        vectorRebracket(BSPEC256, SSPEC256, input, output);
    }

    @Run(test = "testB256toS256")
    public static void runB256toS256() throws Throwable {
        runRebracketHelper(BSPEC256, SSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB256toI256(byte[] input, int[] output) {
        vectorRebracket(BSPEC256, ISPEC256, input, output);
    }

    @Run(test = "testB256toI256")
    public static void runB256toI256() throws Throwable {
        runRebracketHelper(BSPEC256, ISPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB256toL256(byte[] input, long[] output) {
        vectorRebracket(BSPEC256, LSPEC256, input, output);
    }

    @Run(test = "testB256toL256")
    public static void runB256toL256() throws Throwable {
        runRebracketHelper(BSPEC256, LSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB256toF256(byte[] input, float[] output) {
        vectorRebracket(BSPEC256, FSPEC256, input, output);
    }

    @Run(test = "testB256toF256")
    public static void runB256toF256() throws Throwable {
        runRebracketHelper(BSPEC256, FSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB256toD256(byte[] input, double[] output) {
        vectorRebracket(BSPEC256, DSPEC256, input, output);
    }

    @Run(test = "testB256toD256")
    public static void runB256toD256() throws Throwable {
        runRebracketHelper(BSPEC256, DSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS256toB256(short[] input, byte[] output) {
        vectorRebracket(SSPEC256, BSPEC256, input, output);
    }

    @Run(test = "testS256toB256")
    public static void runS256toB256() throws Throwable {
        runRebracketHelper(SSPEC256, BSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS256toI256(short[] input, int[] output) {
        vectorRebracket(SSPEC256, ISPEC256, input, output);
    }

    @Run(test = "testS256toI256")
    public static void runS256toI256() throws Throwable {
        runRebracketHelper(SSPEC256, ISPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS256toL256(short[] input, long[] output) {
        vectorRebracket(SSPEC256, LSPEC256, input, output);
    }

    @Run(test = "testS256toL256")
    public static void runS256toL256() throws Throwable {
        runRebracketHelper(SSPEC256, LSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS256toF256(short[] input, float[] output) {
        vectorRebracket(SSPEC256, FSPEC256, input, output);
    }

    @Run(test = "testS256toF256")
    public static void runS256toF256() throws Throwable {
        runRebracketHelper(SSPEC256, FSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS256toD256(short[] input, double[] output) {
        vectorRebracket(SSPEC256, DSPEC256, input, output);
    }

    @Run(test = "testS256toD256")
    public static void runS256toD256() throws Throwable {
        runRebracketHelper(SSPEC256, DSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI256toB256(int[] input, byte[] output) {
        vectorRebracket(ISPEC256, BSPEC256, input, output);
    }

    @Run(test = "testI256toB256")
    public static void runI256toB256() throws Throwable {
        runRebracketHelper(ISPEC256, BSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI256toS256(int[] input, short[] output) {
        vectorRebracket(ISPEC256, SSPEC256, input, output);
    }

    @Run(test = "testI256toS256")
    public static void runI256toS256() throws Throwable {
        runRebracketHelper(ISPEC256, SSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI256toL256(int[] input, long[] output) {
        vectorRebracket(ISPEC256, LSPEC256, input, output);
    }

    @Run(test = "testI256toL256")
    public static void runI256toL256() throws Throwable {
        runRebracketHelper(ISPEC256, LSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI256toF256(int[] input, float[] output) {
        vectorRebracket(ISPEC256, FSPEC256, input, output);
    }

    @Run(test = "testI256toF256")
    public static void runI256toF256() throws Throwable {
        runRebracketHelper(ISPEC256, FSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI256toD256(int[] input, double[] output) {
        vectorRebracket(ISPEC256, DSPEC256, input, output);
    }

    @Run(test = "testI256toD256")
    public static void runI256toD256() throws Throwable {
        runRebracketHelper(ISPEC256, DSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL256toB256(long[] input, byte[] output) {
        vectorRebracket(LSPEC256, BSPEC256, input, output);
    }

    @Run(test = "testL256toB256")
    public static void runL256toB256() throws Throwable {
        runRebracketHelper(LSPEC256, BSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL256toS256(long[] input, short[] output) {
        vectorRebracket(LSPEC256, SSPEC256, input, output);
    }

    @Run(test = "testL256toS256")
    public static void runL256toS256() throws Throwable {
        runRebracketHelper(LSPEC256, SSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL256toI256(long[] input, int[] output) {
        vectorRebracket(LSPEC256, ISPEC256, input, output);
    }

    @Run(test = "testL256toI256")
    public static void runL256toI256() throws Throwable {
        runRebracketHelper(LSPEC256, ISPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL256toF256(long[] input, float[] output) {
        vectorRebracket(LSPEC256, FSPEC256, input, output);
    }

    @Run(test = "testL256toF256")
    public static void runL256toF256() throws Throwable {
        runRebracketHelper(LSPEC256, FSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL256toD256(long[] input, double[] output) {
        vectorRebracket(LSPEC256, DSPEC256, input, output);
    }

    @Run(test = "testL256toD256")
    public static void runL256toD256() throws Throwable {
        runRebracketHelper(LSPEC256, DSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF256toB256(float[] input, byte[] output) {
        vectorRebracket(FSPEC256, BSPEC256, input, output);
    }

    @Run(test = "testF256toB256")
    public static void runF256toB256() throws Throwable {
        runRebracketHelper(FSPEC256, BSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF256toS256(float[] input, short[] output) {
        vectorRebracket(FSPEC256, SSPEC256, input, output);
    }

    @Run(test = "testF256toS256")
    public static void runF256toS256() throws Throwable {
        runRebracketHelper(FSPEC256, SSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF256toI256(float[] input, int[] output) {
        vectorRebracket(FSPEC256, ISPEC256, input, output);
    }

    @Run(test = "testF256toI256")
    public static void runF256toI256() throws Throwable {
        runRebracketHelper(FSPEC256, ISPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF256toL256(float[] input, long[] output) {
        vectorRebracket(FSPEC256, LSPEC256, input, output);
    }

    @Run(test = "testF256toL256")
    public static void runF256toL256() throws Throwable {
        runRebracketHelper(FSPEC256, LSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF256toD256(float[] input, double[] output) {
        vectorRebracket(FSPEC256, DSPEC256, input, output);
    }

    @Run(test = "testF256toD256")
    public static void runF256toD256() throws Throwable {
        runRebracketHelper(FSPEC256, DSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD256toB256(double[] input, byte[] output) {
        vectorRebracket(DSPEC256, BSPEC256, input, output);
    }

    @Run(test = "testD256toB256")
    public static void runD256toB256() throws Throwable {
        runRebracketHelper(DSPEC256, BSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD256toS256(double[] input, short[] output) {
        vectorRebracket(DSPEC256, SSPEC256, input, output);
    }

    @Run(test = "testD256toS256")
    public static void runD256toS256() throws Throwable {
        runRebracketHelper(DSPEC256, SSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD256toI256(double[] input, int[] output) {
        vectorRebracket(DSPEC256, ISPEC256, input, output);
    }

    @Run(test = "testD256toI256")
    public static void runD256toI256() throws Throwable {
        runRebracketHelper(DSPEC256, ISPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD256toL256(double[] input, long[] output) {
        vectorRebracket(DSPEC256, LSPEC256, input, output);
    }

    @Run(test = "testD256toL256")
    public static void runD256toL256() throws Throwable {
        runRebracketHelper(DSPEC256, LSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD256toF256(double[] input, float[] output) {
        vectorRebracket(DSPEC256, FSPEC256, input, output);
    }

    @Run(test = "testD256toF256")
    public static void runD256toF256() throws Throwable {
        runRebracketHelper(DSPEC256, FSPEC256);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB512toS512(byte[] input, short[] output) {
        vectorRebracket(BSPEC512, SSPEC512, input, output);
    }

    @Run(test = "testB512toS512")
    public static void runB512toS512() throws Throwable {
        runRebracketHelper(BSPEC512, SSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB512toI512(byte[] input, int[] output) {
        vectorRebracket(BSPEC512, ISPEC512, input, output);
    }

    @Run(test = "testB512toI512")
    public static void runB512toI512() throws Throwable {
        runRebracketHelper(BSPEC512, ISPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB512toL512(byte[] input, long[] output) {
        vectorRebracket(BSPEC512, LSPEC512, input, output);
    }

    @Run(test = "testB512toL512")
    public static void runB512toL512() throws Throwable {
        runRebracketHelper(BSPEC512, LSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB512toF512(byte[] input, float[] output) {
        vectorRebracket(BSPEC512, FSPEC512, input, output);
    }

    @Run(test = "testB512toF512")
    public static void runB512toF512() throws Throwable {
        runRebracketHelper(BSPEC512, FSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testB512toD512(byte[] input, double[] output) {
        vectorRebracket(BSPEC512, DSPEC512, input, output);
    }

    @Run(test = "testB512toD512")
    public static void runB512toD512() throws Throwable {
        runRebracketHelper(BSPEC512, DSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS512toB512(short[] input, byte[] output) {
        vectorRebracket(SSPEC512, BSPEC512, input, output);
    }

    @Run(test = "testS512toB512")
    public static void runS512toB512() throws Throwable {
        runRebracketHelper(SSPEC512, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS512toI512(short[] input, int[] output) {
        vectorRebracket(SSPEC512, ISPEC512, input, output);
    }

    @Run(test = "testS512toI512")
    public static void runS512toI512() throws Throwable {
        runRebracketHelper(SSPEC512, ISPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS512toL512(short[] input, long[] output) {
        vectorRebracket(SSPEC512, LSPEC512, input, output);
    }

    @Run(test = "testS512toL512")
    public static void runS512toL512() throws Throwable {
        runRebracketHelper(SSPEC512, LSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS512toF512(short[] input, float[] output) {
        vectorRebracket(SSPEC512, FSPEC512, input, output);
    }

    @Run(test = "testS512toF512")
    public static void runS512toF512() throws Throwable {
        runRebracketHelper(SSPEC512, FSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testS512toD512(short[] input, double[] output) {
        vectorRebracket(SSPEC512, DSPEC512, input, output);
    }

    @Run(test = "testS512toD512")
    public static void runS512toD512() throws Throwable {
        runRebracketHelper(SSPEC512, DSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI512toB512(int[] input, byte[] output) {
        vectorRebracket(ISPEC512, BSPEC512, input, output);
    }

    @Run(test = "testI512toB512")
    public static void runI512toB512() throws Throwable {
        runRebracketHelper(ISPEC512, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI512toS512(int[] input, short[] output) {
        vectorRebracket(ISPEC512, SSPEC512, input, output);
    }

    @Run(test = "testI512toS512")
    public static void runI512toS512() throws Throwable {
        runRebracketHelper(ISPEC512, SSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI512toL512(int[] input, long[] output) {
        vectorRebracket(ISPEC512, LSPEC512, input, output);
    }

    @Run(test = "testI512toL512")
    public static void runI512toL512() throws Throwable {
        runRebracketHelper(ISPEC512, LSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI512toF512(int[] input, float[] output) {
        vectorRebracket(ISPEC512, FSPEC512, input, output);
    }

    @Run(test = "testI512toF512")
    public static void runI512toF512() throws Throwable {
        runRebracketHelper(ISPEC512, FSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testI512toD512(int[] input, double[] output) {
        vectorRebracket(ISPEC512, DSPEC512, input, output);
    }

    @Run(test = "testI512toD512")
    public static void runI512toD512() throws Throwable {
        runRebracketHelper(ISPEC512, DSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL512toB512(long[] input, byte[] output) {
        vectorRebracket(LSPEC512, BSPEC512, input, output);
    }

    @Run(test = "testL512toB512")
    public static void runL512toB512() throws Throwable {
        runRebracketHelper(LSPEC512, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL512toS512(long[] input, short[] output) {
        vectorRebracket(LSPEC512, SSPEC512, input, output);
    }

    @Run(test = "testL512toS512")
    public static void runL512toS512() throws Throwable {
        runRebracketHelper(LSPEC512, SSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL512toI512(long[] input, int[] output) {
        vectorRebracket(LSPEC512, ISPEC512, input, output);
    }

    @Run(test = "testL512toI512")
    public static void runL512toI512() throws Throwable {
        runRebracketHelper(LSPEC512, ISPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL512toF512(long[] input, float[] output) {
        vectorRebracket(LSPEC512, FSPEC512, input, output);
    }

    @Run(test = "testL512toF512")
    public static void runL512toF512() throws Throwable {
        runRebracketHelper(LSPEC512, FSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testL512toD512(long[] input, double[] output) {
        vectorRebracket(LSPEC512, DSPEC512, input, output);
    }

    @Run(test = "testL512toD512")
    public static void runL512toD512() throws Throwable {
        runRebracketHelper(LSPEC512, DSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF512toB512(float[] input, byte[] output) {
        vectorRebracket(FSPEC512, BSPEC512, input, output);
    }

    @Run(test = "testF512toB512")
    public static void runF512toB512() throws Throwable {
        runRebracketHelper(FSPEC512, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF512toS512(float[] input, short[] output) {
        vectorRebracket(FSPEC512, SSPEC512, input, output);
    }

    @Run(test = "testF512toS512")
    public static void runF512toS512() throws Throwable {
        runRebracketHelper(FSPEC512, SSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF512toI512(float[] input, int[] output) {
        vectorRebracket(FSPEC512, ISPEC512, input, output);
    }

    @Run(test = "testF512toI512")
    public static void runF512toI512() throws Throwable {
        runRebracketHelper(FSPEC512, ISPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF512toL512(float[] input, long[] output) {
        vectorRebracket(FSPEC512, LSPEC512, input, output);
    }

    @Run(test = "testF512toL512")
    public static void runF512toL512() throws Throwable {
        runRebracketHelper(FSPEC512, LSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testF512toD512(float[] input, double[] output) {
        vectorRebracket(FSPEC512, DSPEC512, input, output);
    }

    @Run(test = "testF512toD512")
    public static void runF512toD512() throws Throwable {
        runRebracketHelper(FSPEC512, DSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD512toB512(double[] input, byte[] output) {
        vectorRebracket(DSPEC512, BSPEC512, input, output);
    }

    @Run(test = "testD512toB512")
    public static void runD512toB512() throws Throwable {
        runRebracketHelper(DSPEC512, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD512toS512(double[] input, short[] output) {
        vectorRebracket(DSPEC512, SSPEC512, input, output);
    }

    @Run(test = "testD512toS512")
    public static void runD512toS512() throws Throwable {
        runRebracketHelper(DSPEC512, SSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD512toI512(double[] input, int[] output) {
        vectorRebracket(DSPEC512, ISPEC512, input, output);
    }

    @Run(test = "testD512toI512")
    public static void runD512toI512() throws Throwable {
        runRebracketHelper(DSPEC512, ISPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD512toL512(double[] input, long[] output) {
        vectorRebracket(DSPEC512, LSPEC512, input, output);
    }

    @Run(test = "testD512toL512")
    public static void runD512toL512() throws Throwable {
        runRebracketHelper(DSPEC512, LSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "1"})
    public static void testD512toF512(double[] input, float[] output) {
        vectorRebracket(DSPEC512, FSPEC512, input, output);
    }

    @Run(test = "testD512toF512")
    public static void runD512toF512() throws Throwable {
        runRebracketHelper(DSPEC512, FSPEC512);
    }
}
