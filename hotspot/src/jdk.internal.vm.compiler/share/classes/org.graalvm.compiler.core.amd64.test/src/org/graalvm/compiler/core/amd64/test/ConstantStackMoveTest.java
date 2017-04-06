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
package org.graalvm.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.jtt.LIRTest;
import org.graalvm.compiler.lir.jtt.LIRTestSpecification;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;

public class ConstantStackMoveTest extends LIRTest {
    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    private static class LoadConstantStackSpec extends LIRTestSpecification {
        protected final Object primitive;

        LoadConstantStackSpec(Object primitive) {
            this.primitive = primitive;
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            // create slots
            PrimitiveConstant constantValue = JavaConstant.forBoxedPrimitive(primitive);
            VirtualStackSlot s1 = frameMapBuilder.allocateSpillSlot(LIRKind.fromJavaKind(gen.target().arch, constantValue.getJavaKind()));
            // move stuff around
            gen.emitMoveConstant(s1, constantValue);
            gen.emitBlackhole(s1);
            setResult(gen.emitMove(s1));
        }
    }

    private static final class LoadConstantStackSpecByte extends LoadConstantStackSpec {
        LoadConstantStackSpecByte(byte primitive) {
            super(primitive);
        }

        byte get() {
            return (Byte) primitive;
        }
    }

    private static final class LoadConstantStackSpecShort extends LoadConstantStackSpec {
        LoadConstantStackSpecShort(short primitive) {
            super(primitive);
        }

        short get() {
            return (Short) primitive;
        }
    }

    private static final class LoadConstantStackSpecInteger extends LoadConstantStackSpec {
        LoadConstantStackSpecInteger(int primitive) {
            super(primitive);
        }

        int get() {
            return (Integer) primitive;
        }
    }

    private static final class LoadConstantStackSpecLong extends LoadConstantStackSpec {
        LoadConstantStackSpecLong(long primitive) {
            super(primitive);
        }

        long get() {
            return (Long) primitive;
        }
    }

    private static final class LoadConstantStackSpecFloat extends LoadConstantStackSpec {
        LoadConstantStackSpecFloat(float primitive) {
            super(primitive);
        }

        float get() {
            return (Float) primitive;
        }
    }

    private static final class LoadConstantStackSpecDouble extends LoadConstantStackSpec {
        LoadConstantStackSpecDouble(double primitive) {
            super(primitive);
        }

        double get() {
            return (Double) primitive;
        }
    }

    private static final LoadConstantStackSpecByte stackCopyByte = new LoadConstantStackSpecByte(Byte.MAX_VALUE);
    private static final LoadConstantStackSpecShort stackCopyShort = new LoadConstantStackSpecShort(Short.MAX_VALUE);
    private static final LoadConstantStackSpecInteger stackCopyInt = new LoadConstantStackSpecInteger(Integer.MAX_VALUE);
    private static final LoadConstantStackSpecLong stackCopyLong = new LoadConstantStackSpecLong(Long.MAX_VALUE);
    private static final LoadConstantStackSpecFloat stackCopyFloat = new LoadConstantStackSpecFloat(Float.MAX_VALUE);
    private static final LoadConstantStackSpecDouble stackCopyDouble = new LoadConstantStackSpecDouble(Double.MAX_VALUE);

    @LIRIntrinsic
    public static byte testCopyByte(LoadConstantStackSpecByte spec) {
        return spec.get();
    }

    public byte testByte() {
        return testCopyByte(stackCopyByte);
    }

    @Test
    public void runByte() throws Throwable {
        runTest("testByte");
    }

    @LIRIntrinsic
    public static short testCopyShort(LoadConstantStackSpecShort spec) {
        return spec.get();
    }

    public short testShort() {
        return testCopyShort(stackCopyShort);
    }

    @Test
    public void runShort() throws Throwable {
        runTest("testShort");
    }

    @LIRIntrinsic
    public static int testCopyInt(LoadConstantStackSpecInteger spec) {
        return spec.get();
    }

    public int testInt() {
        return testCopyInt(stackCopyInt);
    }

    @Test
    public void runInt() throws Throwable {
        runTest("testInt");
    }

    @LIRIntrinsic
    public static long testCopyLong(LoadConstantStackSpecLong spec) {
        return spec.get();
    }

    public long testLong() {
        return testCopyLong(stackCopyLong);
    }

    @Test
    public void runLong() throws Throwable {
        runTest("testLong");
    }

    @LIRIntrinsic
    public static float testCopyFloat(LoadConstantStackSpecFloat spec) {
        return spec.get();
    }

    public float testFloat() {
        return testCopyFloat(stackCopyFloat);
    }

    @Test
    public void runFloat() throws Throwable {
        runTest("testFloat");
    }

    @LIRIntrinsic
    public static double testCopyDouble(LoadConstantStackSpecDouble spec) {
        return spec.get();
    }

    public double testDouble() {
        return testCopyDouble(stackCopyDouble);
    }

    @Test
    public void runDouble() throws Throwable {
        runTest("testDouble");
    }

}
