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
 * As spot in 8259353. We need to do a shrink and an expand together to not accidentally
 * zero out elements in the physical registers that may not be zero in general cases.
 * In some methods, 2 consecutive ReinterpretNodes may be optimized out.
 */
public class TestVectorDoubleExpandShrink {
    @Test
    @IR(failOn = REINTERPRET_NODE)
    public static void testB64toB128(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC64, BSPEC128, input, output);
    }

    @Run(test = "testB64toB128")
    public static void runB64toB128() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC64, BSPEC128);
    }

    @Test
    @IR(failOn = REINTERPRET_NODE)
    public static void testB64toB256(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC64, BSPEC256, input, output);
    }

    @Run(test = "testB64toB256")
    public static void runB64toB256() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC64, BSPEC256);
    }

    @Test
    @IR(failOn = REINTERPRET_NODE)
    public static void testB64toB512(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC64, BSPEC512, input, output);
    }

    @Run(test = "testB64toB512")
    public static void runB64toB512() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC64, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "2"})
    public static void testB128toB64(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC128, BSPEC64, input, output);
    }

    @Run(test = "testB128toB64")
    public static void runB128toB64() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC128, BSPEC64);
    }

    @Test
    @IR(failOn = REINTERPRET_NODE)
    public static void testB128toB256(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC128, BSPEC256, input, output);
    }

    @Run(test = "testB128toB256")
    public static void runB128toB256() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC128, BSPEC256);
    }

    @Test
    @IR(failOn = REINTERPRET_NODE)
    public static void testB128toB512(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC128, BSPEC512, input, output);
    }

    @Run(test = "testB128toB512")
    public static void runB128toB512() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC128, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "2"})
    public static void testB256toB64(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC256, BSPEC64, input, output);
    }

    @Run(test = "testB256toB64")
    public static void runB256toB64() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC256, BSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "2"})
    public static void testB256toB128(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC256, BSPEC128, input, output);
    }

    @Run(test = "testB256toB128")
    public static void runB256toB128() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC256, BSPEC128);
    }

    @Test
    @IR(failOn = REINTERPRET_NODE)
    public static void testB256toB512(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC256, BSPEC512, input, output);
    }

    @Run(test = "testB256toB512")
    public static void runB256toB512() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC256, BSPEC512);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "2"})
    public static void testB512toB64(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC512, BSPEC64, input, output);
    }

    @Run(test = "testB512toB64")
    public static void runB512toB64() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC512, BSPEC64);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "2"})
    public static void testB512toB128(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC512, BSPEC128, input, output);
    }

    @Run(test = "testB512toB128")
    public static void runB512toB128() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC512, BSPEC128);
    }

    @Test
    @IR(counts = {REINTERPRET_NODE, "2"})
    public static void testB512toB256(byte[] input, byte[] output) {
        vectorDoubleExpandShrink(BSPEC512, BSPEC256, input, output);
    }

    @Run(test = "testB512toB256")
    public static void runB512toB256() throws Throwable {
        runDoubleExpandShrinkHelper(BSPEC512, BSPEC256);
    }
}
