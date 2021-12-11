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
import static jdk.incubator.vector.VectorOperators.*;

/**
 * This class contains all possible cast operations between different vector species.
 * The methods only take into consideration the actual cast in C2, as the vectors are
 * ofter shrunk or expanded before/after casting if the element numbers mismatch.
 * In each cast, the VectorCastNode is expected to appear exactly once.
 */
public class TestVectorCast {
    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toS64(byte[] input, byte[] output) {
        vectorCast(B2S, BSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testB64toS64")
    public static void runB64toS64() throws Throwable {
        runCastHelper(B2S, BSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toS128(byte[] input, byte[] output) {
        vectorCast(B2S, BSPEC64, SSPEC128, input, output);
    }

    @Run(test = "testB64toS128")
    public static void runB64toS128() throws Throwable {
        runCastHelper(B2S, BSPEC64, SSPEC128);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB128toS256(byte[] input, byte[] output) {
        vectorCast(B2S, BSPEC128, SSPEC256, input, output);
    }

    @Run(test = "testB128toS256")
    public static void runB128toS256() throws Throwable {
        runCastHelper(B2S, BSPEC128, SSPEC256);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB256toS512(byte[] input, byte[] output) {
        vectorCast(B2S, BSPEC256, SSPEC512, input, output);
    }

    @Run(test = "testB256toS512")
    public static void runB256toS512() throws Throwable {
        runCastHelper(B2S, BSPEC256, SSPEC512);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toI64(byte[] input, byte[] output) {
        vectorCast(B2I, BSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testB64toI64")
    public static void runB64toI64() throws Throwable {
        runCastHelper(B2I, BSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toI128(byte[] input, byte[] output) {
        vectorCast(B2I, BSPEC64, ISPEC128, input, output);
    }

    @Run(test = "testB64toI128")
    public static void runB64toI128() throws Throwable {
        runCastHelper(B2I, BSPEC64, ISPEC128);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toI256(byte[] input, byte[] output) {
        vectorCast(B2I, BSPEC64, ISPEC256, input, output);
    }

    @Run(test = "testB64toI256")
    public static void runB64toI256() throws Throwable {
        runCastHelper(B2I, BSPEC64, ISPEC256);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB128toI512(byte[] input, byte[] output) {
        vectorCast(B2I, BSPEC128, ISPEC512, input, output);
    }

    @Run(test = "testB128toI512")
    public static void runB128toI512() throws Throwable {
        runCastHelper(B2I, BSPEC128, ISPEC512);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toL64(byte[] input, byte[] output) {
        vectorCast(B2L, BSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testB64toL64")
    public static void runB64toL64() throws Throwable {
        runCastHelper(B2L, BSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toL128(byte[] input, byte[] output) {
        vectorCast(B2L, BSPEC64, LSPEC128, input, output);
    }

    @Run(test = "testB64toL128")
    public static void runB64toL128() throws Throwable {
        runCastHelper(B2L, BSPEC64, LSPEC128);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toL256(byte[] input, byte[] output) {
        vectorCast(B2L, BSPEC64, LSPEC256, input, output);
    }

    @Run(test = "testB64toL256")
    public static void runB64toL256() throws Throwable {
        runCastHelper(B2L, BSPEC64, LSPEC256);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toL512(byte[] input, byte[] output) {
        vectorCast(B2L, BSPEC64, LSPEC512, input, output);
    }

    @Run(test = "testB64toL512")
    public static void runB64toL512() throws Throwable {
        runCastHelper(B2L, BSPEC64, LSPEC512);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toF64(byte[] input, byte[] output) {
        vectorCast(B2F, BSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testB64toF64")
    public static void runB64toF64() throws Throwable {
        runCastHelper(B2F, BSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toF128(byte[] input, byte[] output) {
        vectorCast(B2F, BSPEC64, FSPEC128, input, output);
    }

    @Run(test = "testB64toF128")
    public static void runB64toF128() throws Throwable {
        runCastHelper(B2F, BSPEC64, FSPEC128);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toF256(byte[] input, byte[] output) {
        vectorCast(B2F, BSPEC64, FSPEC256, input, output);
    }

    @Run(test = "testB64toF256")
    public static void runB64toF256() throws Throwable {
        runCastHelper(B2F, BSPEC64, FSPEC256);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB128toF512(byte[] input, byte[] output) {
        vectorCast(B2F, BSPEC128, FSPEC512, input, output);
    }

    @Run(test = "testB128toF512")
    public static void runB128toF512() throws Throwable {
        runCastHelper(B2F, BSPEC128, FSPEC512);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toD64(byte[] input, byte[] output) {
        vectorCast(B2D, BSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testB64toD64")
    public static void runB64toD64() throws Throwable {
        runCastHelper(B2D, BSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toD128(byte[] input, byte[] output) {
        vectorCast(B2D, BSPEC64, DSPEC128, input, output);
    }

    @Run(test = "testB64toD128")
    public static void runB64toD128() throws Throwable {
        runCastHelper(B2D, BSPEC64, DSPEC128);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toD256(byte[] input, byte[] output) {
        vectorCast(B2D, BSPEC64, DSPEC256, input, output);
    }

    @Run(test = "testB64toD256")
    public static void runB64toD256() throws Throwable {
        runCastHelper(B2D, BSPEC64, DSPEC256);
    }

    @Test
    @IR(counts = {B2X_NODE, "1"})
    public static void testB64toD512(byte[] input, byte[] output) {
        vectorCast(B2D, BSPEC64, DSPEC512, input, output);
    }

    @Run(test = "testB64toD512")
    public static void runB64toD512() throws Throwable {
        runCastHelper(B2D, BSPEC64, DSPEC512);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toB64(byte[] input, byte[] output) {
        vectorCast(S2B, SSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testS64toB64")
    public static void runS64toB64() throws Throwable {
        runCastHelper(S2B, SSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS128toB64(byte[] input, byte[] output) {
        vectorCast(S2B, SSPEC128, BSPEC64, input, output);
    }

    @Run(test = "testS128toB64")
    public static void runS128toB64() throws Throwable {
        runCastHelper(S2B, SSPEC128, BSPEC64);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS256toB128(byte[] input, byte[] output) {
        vectorCast(S2B, SSPEC256, BSPEC128, input, output);
    }

    @Run(test = "testS256toB128")
    public static void runS256toB128() throws Throwable {
        runCastHelper(S2B, SSPEC256, BSPEC128);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS512toB256(byte[] input, byte[] output) {
        vectorCast(S2B, SSPEC512, BSPEC256, input, output);
    }

    @Run(test = "testS512toB256")
    public static void runS512toB256() throws Throwable {
        runCastHelper(S2B, SSPEC512, BSPEC256);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toI64(byte[] input, byte[] output) {
        vectorCast(S2I, SSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testS64toI64")
    public static void runS64toI64() throws Throwable {
        runCastHelper(S2I, SSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toI128(byte[] input, byte[] output) {
        vectorCast(S2I, SSPEC64, ISPEC128, input, output);
    }

    @Run(test = "testS64toI128")
    public static void runS64toI128() throws Throwable {
        runCastHelper(S2I, SSPEC64, ISPEC128);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS128toI256(byte[] input, byte[] output) {
        vectorCast(S2I, SSPEC128, ISPEC256, input, output);
    }

    @Run(test = "testS128toI256")
    public static void runS128toI256() throws Throwable {
        runCastHelper(S2I, SSPEC128, ISPEC256);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS256toI512(byte[] input, byte[] output) {
        vectorCast(S2I, SSPEC256, ISPEC512, input, output);
    }

    @Run(test = "testS256toI512")
    public static void runS256toI512() throws Throwable {
        runCastHelper(S2I, SSPEC256, ISPEC512);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toL64(byte[] input, byte[] output) {
        vectorCast(S2L, SSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testS64toL64")
    public static void runS64toL64() throws Throwable {
        runCastHelper(S2L, SSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toL128(byte[] input, byte[] output) {
        vectorCast(S2L, SSPEC64, LSPEC128, input, output);
    }

    @Run(test = "testS64toL128")
    public static void runS64toL128() throws Throwable {
        runCastHelper(S2L, SSPEC64, LSPEC128);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toL256(byte[] input, byte[] output) {
        vectorCast(S2L, SSPEC64, LSPEC256, input, output);
    }

    @Run(test = "testS64toL256")
    public static void runS64toL256() throws Throwable {
        runCastHelper(S2L, SSPEC64, LSPEC256);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS128toL512(byte[] input, byte[] output) {
        vectorCast(S2L, SSPEC128, LSPEC512, input, output);
    }

    @Run(test = "testS128toL512")
    public static void runS128toL512() throws Throwable {
        runCastHelper(S2L, SSPEC128, LSPEC512);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toF64(byte[] input, byte[] output) {
        vectorCast(S2F, SSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testS64toF64")
    public static void runS64toF64() throws Throwable {
        runCastHelper(S2F, SSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toF128(byte[] input, byte[] output) {
        vectorCast(S2F, SSPEC64, FSPEC128, input, output);
    }

    @Run(test = "testS64toF128")
    public static void runS64toF128() throws Throwable {
        runCastHelper(S2F, SSPEC64, FSPEC128);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS128toF256(byte[] input, byte[] output) {
        vectorCast(S2F, SSPEC128, FSPEC256, input, output);
    }

    @Run(test = "testS128toF256")
    public static void runS128toF256() throws Throwable {
        runCastHelper(S2F, SSPEC128, FSPEC256);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS256toF512(byte[] input, byte[] output) {
        vectorCast(S2F, SSPEC256, FSPEC512, input, output);
    }

    @Run(test = "testS256toF512")
    public static void runS256toF512() throws Throwable {
        runCastHelper(S2F, SSPEC256, FSPEC512);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toD64(byte[] input, byte[] output) {
        vectorCast(S2D, SSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testS64toD64")
    public static void runS64toD64() throws Throwable {
        runCastHelper(S2D, SSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toD128(byte[] input, byte[] output) {
        vectorCast(S2D, SSPEC64, DSPEC128, input, output);
    }

    @Run(test = "testS64toD128")
    public static void runS64toD128() throws Throwable {
        runCastHelper(S2D, SSPEC64, DSPEC128);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS64toD256(byte[] input, byte[] output) {
        vectorCast(S2D, SSPEC64, DSPEC256, input, output);
    }

    @Run(test = "testS64toD256")
    public static void runS64toD256() throws Throwable {
        runCastHelper(S2D, SSPEC64, DSPEC256);
    }

    @Test
    @IR(counts = {S2X_NODE, "1"})
    public static void testS128toD512(byte[] input, byte[] output) {
        vectorCast(S2D, SSPEC128, DSPEC512, input, output);
    }

    @Run(test = "testS128toD512")
    public static void runS128toD512() throws Throwable {
        runCastHelper(S2D, SSPEC128, DSPEC512);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI64toB64(byte[] input, byte[] output) {
        vectorCast(I2B, ISPEC64, BSPEC64, input, output);
    }

    @Run(test = "testI64toB64")
    public static void runI64toB64() throws Throwable {
        runCastHelper(I2B, ISPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI128toB64(byte[] input, byte[] output) {
        vectorCast(I2B, ISPEC128, BSPEC64, input, output);
    }

    @Run(test = "testI128toB64")
    public static void runI128toB64() throws Throwable {
        runCastHelper(I2B, ISPEC128, BSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI256toB64(byte[] input, byte[] output) {
        vectorCast(I2B, ISPEC256, BSPEC64, input, output);
    }

    @Run(test = "testI256toB64")
    public static void runI256toB64() throws Throwable {
        runCastHelper(I2B, ISPEC256, BSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI512toB128(byte[] input, byte[] output) {
        vectorCast(I2B, ISPEC512, BSPEC128, input, output);
    }

    @Run(test = "testI512toB128")
    public static void runI512toB128() throws Throwable {
        runCastHelper(I2B, ISPEC512, BSPEC128);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI64toS64(byte[] input, byte[] output) {
        vectorCast(I2S, ISPEC64, SSPEC64, input, output);
    }

    @Run(test = "testI64toS64")
    public static void runI64toS64() throws Throwable {
        runCastHelper(I2S, ISPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI128toS64(byte[] input, byte[] output) {
        vectorCast(I2S, ISPEC128, SSPEC64, input, output);
    }

    @Run(test = "testI128toS64")
    public static void runI128toS64() throws Throwable {
        runCastHelper(I2S, ISPEC128, SSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI256toS128(byte[] input, byte[] output) {
        vectorCast(I2S, ISPEC256, SSPEC128, input, output);
    }

    @Run(test = "testI256toS128")
    public static void runI256toS128() throws Throwable {
        runCastHelper(I2S, ISPEC256, SSPEC128);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI512toS256(byte[] input, byte[] output) {
        vectorCast(I2S, ISPEC512, SSPEC256, input, output);
    }

    @Run(test = "testI512toS256")
    public static void runI512toS256() throws Throwable {
        runCastHelper(I2S, ISPEC512, SSPEC256);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI64toL64(byte[] input, byte[] output) {
        vectorCast(I2L, ISPEC64, LSPEC64, input, output);
    }

    @Run(test = "testI64toL64")
    public static void runI64toL64() throws Throwable {
        runCastHelper(I2L, ISPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI64toL128(byte[] input, byte[] output) {
        vectorCast(I2L, ISPEC64, LSPEC128, input, output);
    }

    @Run(test = "testI64toL128")
    public static void runI64toL128() throws Throwable {
        runCastHelper(I2L, ISPEC64, LSPEC128);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI128toL256(byte[] input, byte[] output) {
        vectorCast(I2L, ISPEC128, LSPEC256, input, output);
    }

    @Run(test = "testI128toL256")
    public static void runI128toL256() throws Throwable {
        runCastHelper(I2L, ISPEC128, LSPEC256);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI256toL512(byte[] input, byte[] output) {
        vectorCast(I2L, ISPEC256, LSPEC512, input, output);
    }

    @Run(test = "testI256toL512")
    public static void runI256toL512() throws Throwable {
        runCastHelper(I2L, ISPEC256, LSPEC512);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI64toF64(byte[] input, byte[] output) {
        vectorCast(I2F, ISPEC64, FSPEC64, input, output);
    }

    @Run(test = "testI64toF64")
    public static void runI64toF64() throws Throwable {
        runCastHelper(I2F, ISPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI128toF128(byte[] input, byte[] output) {
        vectorCast(I2F, ISPEC128, FSPEC128, input, output);
    }

    @Run(test = "testI128toF128")
    public static void runI128toF128() throws Throwable {
        runCastHelper(I2F, ISPEC128, FSPEC128);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI256toF256(byte[] input, byte[] output) {
        vectorCast(I2F, ISPEC256, FSPEC256, input, output);
    }

    @Run(test = "testI256toF256")
    public static void runI256toF256() throws Throwable {
        runCastHelper(I2F, ISPEC256, FSPEC256);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI512toF512(byte[] input, byte[] output) {
        vectorCast(I2F, ISPEC512, FSPEC512, input, output);
    }

    @Run(test = "testI512toF512")
    public static void runI512toF512() throws Throwable {
        runCastHelper(I2F, ISPEC512, FSPEC512);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI64toD64(byte[] input, byte[] output) {
        vectorCast(I2D, ISPEC64, DSPEC64, input, output);
    }

    @Run(test = "testI64toD64")
    public static void runI64toD64() throws Throwable {
        runCastHelper(I2D, ISPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI64toD128(byte[] input, byte[] output) {
        vectorCast(I2D, ISPEC64, DSPEC128, input, output);
    }

    @Run(test = "testI64toD128")
    public static void runI64toD128() throws Throwable {
        runCastHelper(I2D, ISPEC64, DSPEC128);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI128toD256(byte[] input, byte[] output) {
        vectorCast(I2D, ISPEC128, DSPEC256, input, output);
    }

    @Run(test = "testI128toD256")
    public static void runI128toD256() throws Throwable {
        runCastHelper(I2D, ISPEC128, DSPEC256);
    }

    @Test
    @IR(counts = {I2X_NODE, "1"})
    public static void testI256toD512(byte[] input, byte[] output) {
        vectorCast(I2D, ISPEC256, DSPEC512, input, output);
    }

    @Run(test = "testI256toD512")
    public static void runI256toD512() throws Throwable {
        runCastHelper(I2D, ISPEC256, DSPEC512);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL64toB64(byte[] input, byte[] output) {
        vectorCast(L2B, LSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testL64toB64")
    public static void runL64toB64() throws Throwable {
        runCastHelper(L2B, LSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL128toB64(byte[] input, byte[] output) {
        vectorCast(L2B, LSPEC128, BSPEC64, input, output);
    }

    @Run(test = "testL128toB64")
    public static void runL128toB64() throws Throwable {
        runCastHelper(L2B, LSPEC128, BSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL256toB64(byte[] input, byte[] output) {
        vectorCast(L2B, LSPEC256, BSPEC64, input, output);
    }

    @Run(test = "testL256toB64")
    public static void runL256toB64() throws Throwable {
        runCastHelper(L2B, LSPEC256, BSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL512toB64(byte[] input, byte[] output) {
        vectorCast(L2B, LSPEC512, BSPEC64, input, output);
    }

    @Run(test = "testL512toB64")
    public static void runL512toB64() throws Throwable {
        runCastHelper(L2B, LSPEC512, BSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL64toS64(byte[] input, byte[] output) {
        vectorCast(L2S, LSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testL64toS64")
    public static void runL64toS64() throws Throwable {
        runCastHelper(L2S, LSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL128toS64(byte[] input, byte[] output) {
        vectorCast(L2S, LSPEC128, SSPEC64, input, output);
    }

    @Run(test = "testL128toS64")
    public static void runL128toS64() throws Throwable {
        runCastHelper(L2S, LSPEC128, SSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL256toS64(byte[] input, byte[] output) {
        vectorCast(L2S, LSPEC256, SSPEC64, input, output);
    }

    @Run(test = "testL256toS64")
    public static void runL256toS64() throws Throwable {
        runCastHelper(L2S, LSPEC256, SSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL512toS128(byte[] input, byte[] output) {
        vectorCast(L2S, LSPEC512, SSPEC128, input, output);
    }

    @Run(test = "testL512toS128")
    public static void runL512toS128() throws Throwable {
        runCastHelper(L2S, LSPEC512, SSPEC128);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL64toI64(byte[] input, byte[] output) {
        vectorCast(L2I, LSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testL64toI64")
    public static void runL64toI64() throws Throwable {
        runCastHelper(L2I, LSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL128toI64(byte[] input, byte[] output) {
        vectorCast(L2I, LSPEC128, ISPEC64, input, output);
    }

    @Run(test = "testL128toI64")
    public static void runL128toI64() throws Throwable {
        runCastHelper(L2I, LSPEC128, ISPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL256toI128(byte[] input, byte[] output) {
        vectorCast(L2I, LSPEC256, ISPEC128, input, output);
    }

    @Run(test = "testL256toI128")
    public static void runL256toI128() throws Throwable {
        runCastHelper(L2I, LSPEC256, ISPEC128);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL512toI256(byte[] input, byte[] output) {
        vectorCast(L2I, LSPEC512, ISPEC256, input, output);
    }

    @Run(test = "testL512toI256")
    public static void runL512toI256() throws Throwable {
        runCastHelper(L2I, LSPEC512, ISPEC256);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL64toF64(byte[] input, byte[] output) {
        vectorCast(L2F, LSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testL64toF64")
    public static void runL64toF64() throws Throwable {
        runCastHelper(L2F, LSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL128toF64(byte[] input, byte[] output) {
        vectorCast(L2F, LSPEC128, FSPEC64, input, output);
    }

    @Run(test = "testL128toF64")
    public static void runL128toF64() throws Throwable {
        runCastHelper(L2F, LSPEC128, FSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL256toF128(byte[] input, byte[] output) {
        vectorCast(L2F, LSPEC256, FSPEC128, input, output);
    }

    @Run(test = "testL256toF128")
    public static void runL256toF128() throws Throwable {
        runCastHelper(L2F, LSPEC256, FSPEC128);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL512toF256(byte[] input, byte[] output) {
        vectorCast(L2F, LSPEC512, FSPEC256, input, output);
    }

    @Run(test = "testL512toF256")
    public static void runL512toF256() throws Throwable {
        runCastHelper(L2F, LSPEC512, FSPEC256);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL64toD64(byte[] input, byte[] output) {
        vectorCast(L2D, LSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testL64toD64")
    public static void runL64toD64() throws Throwable {
        runCastHelper(L2D, LSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL128toD128(byte[] input, byte[] output) {
        vectorCast(L2D, LSPEC128, DSPEC128, input, output);
    }

    @Run(test = "testL128toD128")
    public static void runL128toD128() throws Throwable {
        runCastHelper(L2D, LSPEC128, DSPEC128);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL256toD256(byte[] input, byte[] output) {
        vectorCast(L2D, LSPEC256, DSPEC256, input, output);
    }

    @Run(test = "testL256toD256")
    public static void runL256toD256() throws Throwable {
        runCastHelper(L2D, LSPEC256, DSPEC256);
    }

    @Test
    @IR(counts = {L2X_NODE, "1"})
    public static void testL512toD512(byte[] input, byte[] output) {
        vectorCast(L2D, LSPEC512, DSPEC512, input, output);
    }

    @Run(test = "testL512toD512")
    public static void runL512toD512() throws Throwable {
        runCastHelper(L2D, LSPEC512, DSPEC512);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF64toB64(byte[] input, byte[] output) {
        vectorCast(F2B, FSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testF64toB64")
    public static void runF64toB64() throws Throwable {
        runCastHelper(F2B, FSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF128toB64(byte[] input, byte[] output) {
        vectorCast(F2B, FSPEC128, BSPEC64, input, output);
    }

    @Run(test = "testF128toB64")
    public static void runF128toB64() throws Throwable {
        runCastHelper(F2B, FSPEC128, BSPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF256toB64(byte[] input, byte[] output) {
        vectorCast(F2B, FSPEC256, BSPEC64, input, output);
    }

    @Run(test = "testF256toB64")
    public static void runF256toB64() throws Throwable {
        runCastHelper(F2B, FSPEC256, BSPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF512toB128(byte[] input, byte[] output) {
        vectorCast(F2B, FSPEC512, BSPEC128, input, output);
    }

    @Run(test = "testF512toB128")
    public static void runF512toB128() throws Throwable {
        runCastHelper(F2B, FSPEC512, BSPEC128);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF64toS64(byte[] input, byte[] output) {
        vectorCast(F2S, FSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testF64toS64")
    public static void runF64toS64() throws Throwable {
        runCastHelper(F2S, FSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF128toS64(byte[] input, byte[] output) {
        vectorCast(F2S, FSPEC128, SSPEC64, input, output);
    }

    @Run(test = "testF128toS64")
    public static void runF128toS64() throws Throwable {
        runCastHelper(F2S, FSPEC128, SSPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF256toS128(byte[] input, byte[] output) {
        vectorCast(F2S, FSPEC256, SSPEC128, input, output);
    }

    @Run(test = "testF256toS128")
    public static void runF256toS128() throws Throwable {
        runCastHelper(F2S, FSPEC256, SSPEC128);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF512toS256(byte[] input, byte[] output) {
        vectorCast(F2S, FSPEC512, SSPEC256, input, output);
    }

    @Run(test = "testF512toS256")
    public static void runF512toS256() throws Throwable {
        runCastHelper(F2S, FSPEC512, SSPEC256);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF64toL64(byte[] input, byte[] output) {
        vectorCast(F2L, FSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testF64toL64")
    public static void runF64toL64() throws Throwable {
        runCastHelper(F2L, FSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF64toL128(byte[] input, byte[] output) {
        vectorCast(F2L, FSPEC64, LSPEC128, input, output);
    }

    @Run(test = "testF64toL128")
    public static void runF64toL128() throws Throwable {
        runCastHelper(F2L, FSPEC64, LSPEC128);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF128toL256(byte[] input, byte[] output) {
        vectorCast(F2L, FSPEC128, LSPEC256, input, output);
    }

    @Run(test = "testF128toL256")
    public static void runF128toL256() throws Throwable {
        runCastHelper(F2L, FSPEC128, LSPEC256);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF256toL512(byte[] input, byte[] output) {
        vectorCast(F2L, FSPEC256, LSPEC512, input, output);
    }

    @Run(test = "testF256toL512")
    public static void runF256toL512() throws Throwable {
        runCastHelper(F2L, FSPEC256, LSPEC512);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF64toI64(byte[] input, byte[] output) {
        vectorCast(F2I, FSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testF64toI64")
    public static void runF64toI64() throws Throwable {
        runCastHelper(F2I, FSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF128toI128(byte[] input, byte[] output) {
        vectorCast(F2I, FSPEC128, ISPEC128, input, output);
    }

    @Run(test = "testF128toI128")
    public static void runF128toI128() throws Throwable {
        runCastHelper(F2I, FSPEC128, ISPEC128);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF256toI256(byte[] input, byte[] output) {
        vectorCast(F2I, FSPEC256, ISPEC256, input, output);
    }

    @Run(test = "testF256toI256")
    public static void runF256toI256() throws Throwable {
        runCastHelper(F2I, FSPEC256, ISPEC256);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF512toI512(byte[] input, byte[] output) {
        vectorCast(F2I, FSPEC512, ISPEC512, input, output);
    }

    @Run(test = "testF512toI512")
    public static void runF512toI512() throws Throwable {
        runCastHelper(F2I, FSPEC512, ISPEC512);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF64toD64(byte[] input, byte[] output) {
        vectorCast(F2D, FSPEC64, DSPEC64, input, output);
    }

    @Run(test = "testF64toD64")
    public static void runF64toD64() throws Throwable {
        runCastHelper(F2D, FSPEC64, DSPEC64);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF64toD128(byte[] input, byte[] output) {
        vectorCast(F2D, FSPEC64, DSPEC128, input, output);
    }

    @Run(test = "testF64toD128")
    public static void runF64toD128() throws Throwable {
        runCastHelper(F2D, FSPEC64, DSPEC128);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF128toD256(byte[] input, byte[] output) {
        vectorCast(F2D, FSPEC128, DSPEC256, input, output);
    }

    @Run(test = "testF128toD256")
    public static void runF128toD256() throws Throwable {
        runCastHelper(F2D, FSPEC128, DSPEC256);
    }

    @Test
    @IR(counts = {F2X_NODE, "1"})
    public static void testF256toD512(byte[] input, byte[] output) {
        vectorCast(F2D, FSPEC256, DSPEC512, input, output);
    }

    @Run(test = "testF256toD512")
    public static void runF256toD512() throws Throwable {
        runCastHelper(F2D, FSPEC256, DSPEC512);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD64toB64(byte[] input, byte[] output) {
        vectorCast(D2B, DSPEC64, BSPEC64, input, output);
    }

    @Run(test = "testD64toB64")
    public static void runD64toB64() throws Throwable {
        runCastHelper(D2B, DSPEC64, BSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD128toB64(byte[] input, byte[] output) {
        vectorCast(D2B, DSPEC128, BSPEC64, input, output);
    }

    @Run(test = "testD128toB64")
    public static void runD128toB64() throws Throwable {
        runCastHelper(D2B, DSPEC128, BSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD256toB64(byte[] input, byte[] output) {
        vectorCast(D2B, DSPEC256, BSPEC64, input, output);
    }

    @Run(test = "testD256toB64")
    public static void runD256toB64() throws Throwable {
        runCastHelper(D2B, DSPEC256, BSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD512toB64(byte[] input, byte[] output) {
        vectorCast(D2B, DSPEC512, BSPEC64, input, output);
    }

    @Run(test = "testD512toB64")
    public static void runD512toB64() throws Throwable {
        runCastHelper(D2B, DSPEC512, BSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD64toS64(byte[] input, byte[] output) {
        vectorCast(D2S, DSPEC64, SSPEC64, input, output);
    }

    @Run(test = "testD64toS64")
    public static void runD64toS64() throws Throwable {
        runCastHelper(D2S, DSPEC64, SSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD128toS64(byte[] input, byte[] output) {
        vectorCast(D2S, DSPEC128, SSPEC64, input, output);
    }

    @Run(test = "testD128toS64")
    public static void runD128toS64() throws Throwable {
        runCastHelper(D2S, DSPEC128, SSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD256toS64(byte[] input, byte[] output) {
        vectorCast(D2S, DSPEC256, SSPEC64, input, output);
    }

    @Run(test = "testD256toS64")
    public static void runD256toS64() throws Throwable {
        runCastHelper(D2S, DSPEC256, SSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD512toS128(byte[] input, byte[] output) {
        vectorCast(D2S, DSPEC512, SSPEC128, input, output);
    }

    @Run(test = "testD512toS128")
    public static void runD512toS128() throws Throwable {
        runCastHelper(D2S, DSPEC512, SSPEC128);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD64toI64(byte[] input, byte[] output) {
        vectorCast(D2I, DSPEC64, ISPEC64, input, output);
    }

    @Run(test = "testD64toI64")
    public static void runD64toI64() throws Throwable {
        runCastHelper(D2I, DSPEC64, ISPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD128toI64(byte[] input, byte[] output) {
        vectorCast(D2I, DSPEC128, ISPEC64, input, output);
    }

    @Run(test = "testD128toI64")
    public static void runD128toI64() throws Throwable {
        runCastHelper(D2I, DSPEC128, ISPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD256toI128(byte[] input, byte[] output) {
        vectorCast(D2I, DSPEC256, ISPEC128, input, output);
    }

    @Run(test = "testD256toI128")
    public static void runD256toI128() throws Throwable {
        runCastHelper(D2I, DSPEC256, ISPEC128);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD512toI256(byte[] input, byte[] output) {
        vectorCast(D2I, DSPEC512, ISPEC256, input, output);
    }

    @Run(test = "testD512toI256")
    public static void runD512toI256() throws Throwable {
        runCastHelper(D2I, DSPEC512, ISPEC256);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD64toF64(byte[] input, byte[] output) {
        vectorCast(D2F, DSPEC64, FSPEC64, input, output);
    }

    @Run(test = "testD64toF64")
    public static void runD64toF64() throws Throwable {
        runCastHelper(D2F, DSPEC64, FSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD128toF64(byte[] input, byte[] output) {
        vectorCast(D2F, DSPEC128, FSPEC64, input, output);
    }

    @Run(test = "testD128toF64")
    public static void runD128toF64() throws Throwable {
        runCastHelper(D2F, DSPEC128, FSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD256toF128(byte[] input, byte[] output) {
        vectorCast(D2F, DSPEC256, FSPEC128, input, output);
    }

    @Run(test = "testD256toF128")
    public static void runD256toF128() throws Throwable {
        runCastHelper(D2F, DSPEC256, FSPEC128);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD512toF256(byte[] input, byte[] output) {
        vectorCast(D2F, DSPEC512, FSPEC256, input, output);
    }

    @Run(test = "testD512toF256")
    public static void runD512toF256() throws Throwable {
        runCastHelper(D2F, DSPEC512, FSPEC256);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD64toL64(byte[] input, byte[] output) {
        vectorCast(D2L, DSPEC64, LSPEC64, input, output);
    }

    @Run(test = "testD64toL64")
    public static void runD64toL64() throws Throwable {
        runCastHelper(D2L, DSPEC64, LSPEC64);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD128toL128(byte[] input, byte[] output) {
        vectorCast(D2L, DSPEC128, LSPEC128, input, output);
    }

    @Run(test = "testD128toL128")
    public static void runD128toL128() throws Throwable {
        runCastHelper(D2L, DSPEC128, LSPEC128);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD256toL256(byte[] input, byte[] output) {
        vectorCast(D2L, DSPEC256, LSPEC256, input, output);
    }

    @Run(test = "testD256toL256")
    public static void runD256toL256() throws Throwable {
        runCastHelper(D2L, DSPEC256, LSPEC256);
    }

    @Test
    @IR(counts = {D2X_NODE, "1"})
    public static void testD512toL512(byte[] input, byte[] output) {
        vectorCast(D2L, DSPEC512, LSPEC512, input, output);
    }

    @Run(test = "testD512toL512")
    public static void runD512toL512() throws Throwable {
        runCastHelper(D2L, DSPEC512, LSPEC512);
    }
}
