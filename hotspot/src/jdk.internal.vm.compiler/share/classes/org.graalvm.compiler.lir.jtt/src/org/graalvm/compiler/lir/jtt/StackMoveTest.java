/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.jtt;

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class StackMoveTest extends LIRTest {
    private static PlatformKind byteKind;
    private static PlatformKind shortKind;

    @Before
    public void setUp() {
        byteKind = getBackend().getTarget().arch.getPlatformKind(JavaKind.Byte);
        shortKind = getBackend().getTarget().arch.getPlatformKind(JavaKind.Short);
    }

    private static class StackCopySpec extends LIRTestSpecification {
        @Override
        public void generate(LIRGeneratorTool gen, Value a) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            ValueKind<?> valueKind = getValueKind(a);

            // create slots
            VirtualStackSlot s1 = frameMapBuilder.allocateSpillSlot(valueKind);
            VirtualStackSlot s2 = frameMapBuilder.allocateSpillSlot(valueKind);

            // start emit
            gen.emitMove(s1, a);
            Value copy1 = gen.emitMove(s1);
            gen.append(gen.getSpillMoveFactory().createStackMove(s2, s1));
            Variable result = gen.emitMove(s2);
            // end emit

            // set output and result
            setResult(result);
            setOutput("slotcopy", copy1);
            setOutput("slot1", s1);
            setOutput("slot2", s2);
        }

        protected ValueKind<?> getValueKind(Value value) {
            return value.getValueKind();
        }
    }

    private static final LIRTestSpecification stackCopy = new StackCopySpec();

    /*
     * int
     */

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static int copyInt(LIRTestSpecification spec, int a) {
        return a;
    }

    public int[] testInt(int a, int[] out) {
        out[0] = copyInt(stackCopy, a);
        out[1] = getOutput(stackCopy, "slotcopy", a);
        out[2] = getOutput(stackCopy, "slot1", a);
        out[3] = getOutput(stackCopy, "slot2", a);
        return out;
    }

    @Test
    public void runInt() throws Throwable {
        runTest("testInt", Integer.MIN_VALUE, supply(() -> new int[4]));
        runTest("testInt", -1, supply(() -> new int[4]));
        runTest("testInt", 0, supply(() -> new int[4]));
        runTest("testInt", 1, supply(() -> new int[4]));
        runTest("testInt", Integer.MAX_VALUE, supply(() -> new int[4]));
    }

    /*
     * long
     */

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static long copyLong(LIRTestSpecification spec, long a) {
        return a;
    }

    public long[] testLong(long a, long[] out) {
        out[0] = copyLong(stackCopy, a);
        out[1] = getOutput(stackCopy, "slotcopy", a);
        out[2] = getOutput(stackCopy, "slot1", a);
        out[3] = getOutput(stackCopy, "slot2", a);
        return out;
    }

    @Test
    public void runLong() throws Throwable {
        runTest("testLong", Long.MIN_VALUE, supply(() -> new long[3]));
        runTest("testLong", -1L, supply(() -> new long[3]));
        runTest("testLong", 0L, supply(() -> new long[3]));
        runTest("testLong", 1L, supply(() -> new long[3]));
        runTest("testLong", Long.MAX_VALUE, supply(() -> new long[3]));
    }

    /*
     * float
     */

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static float copyFloat(LIRTestSpecification spec, float a) {
        return a;
    }

    public float[] testFloat(float a, float[] out) {
        out[0] = copyFloat(stackCopy, a);
        out[1] = getOutput(stackCopy, "slotcopy", a);
        out[2] = getOutput(stackCopy, "slot1", a);
        out[3] = getOutput(stackCopy, "slot2", a);
        return out;
    }

    @Test
    public void runFloat() throws Throwable {
        runTest("testFloat", Float.MIN_VALUE, supply(() -> new float[3]));
        runTest("testFloat", -1f, supply(() -> new float[3]));
        runTest("testFloat", -0.1f, supply(() -> new float[3]));
        runTest("testFloat", 0f, supply(() -> new float[3]));
        runTest("testFloat", 0.1f, supply(() -> new float[3]));
        runTest("testFloat", 1f, supply(() -> new float[3]));
        runTest("testFloat", Float.MAX_VALUE, supply(() -> new float[3]));
    }

    /*
     * double
     */

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static double copyDouble(LIRTestSpecification spec, double a) {
        return a;
    }

    public double[] testDouble(double a, double[] out) {
        out[0] = copyDouble(stackCopy, a);
        out[1] = getOutput(stackCopy, "slotcopy", a);
        out[2] = getOutput(stackCopy, "slot1", a);
        out[3] = getOutput(stackCopy, "slot2", a);
        return out;
    }

    @Test
    public void runDouble() throws Throwable {
        runTest("testDouble", Double.MIN_VALUE, supply(() -> new double[3]));
        runTest("testDouble", -1., supply(() -> new double[3]));
        runTest("testDouble", -0.1, supply(() -> new double[3]));
        runTest("testDouble", 0., supply(() -> new double[3]));
        runTest("testDouble", 0.1, supply(() -> new double[3]));
        runTest("testDouble", 1., supply(() -> new double[3]));
        runTest("testDouble", Double.MAX_VALUE, supply(() -> new double[3]));
    }

    /*
     * short
     */

    private static final LIRTestSpecification shortStackCopy = new StackCopySpec() {
        @Override
        protected ValueKind<?> getValueKind(Value value) {
            return LIRKind.value(shortKind);
        }
    };

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static short copyShort(LIRTestSpecification spec, short a) {
        return a;
    }

    public short[] testShort(short a, short[] out) {
        out[0] = copyShort(shortStackCopy, a);
        out[1] = getOutput(shortStackCopy, "slotcopy", a);
        out[2] = getOutput(shortStackCopy, "slot1", a);
        out[3] = getOutput(shortStackCopy, "slot2", a);
        return out;
    }

    @Test
    public void runShort() throws Throwable {
        runTest("testShort", Short.MIN_VALUE, supply(() -> new short[3]));
        runTest("testShort", (short) -1, supply(() -> new short[3]));
        runTest("testShort", (short) 0, supply(() -> new short[3]));
        runTest("testShort", (short) 1, supply(() -> new short[3]));
        runTest("testShort", Short.MAX_VALUE, supply(() -> new short[3]));
    }

    /*
     * byte
     */

    private static final LIRTestSpecification byteStackCopy = new StackCopySpec() {
        @Override
        protected ValueKind<?> getValueKind(Value value) {
            return LIRKind.value(byteKind);
        }
    };

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static byte copyByte(LIRTestSpecification spec, byte a) {
        return a;
    }

    public byte[] testByte(byte a, byte[] out) {
        out[0] = copyByte(byteStackCopy, a);
        out[1] = getOutput(byteStackCopy, "slotcopy", a);
        out[2] = getOutput(byteStackCopy, "slot1", a);
        out[3] = getOutput(byteStackCopy, "slot2", a);
        return out;
    }

    @Test
    public void runByte() throws Throwable {
        runTest("testByte", Byte.MIN_VALUE, supply(() -> new byte[3]));
        runTest("testByte", (byte) -1, supply(() -> new byte[3]));
        runTest("testByte", (byte) 0, supply(() -> new byte[3]));
        runTest("testByte", (byte) 1, supply(() -> new byte[3]));
        runTest("testByte", Byte.MAX_VALUE, supply(() -> new byte[3]));
    }
}
