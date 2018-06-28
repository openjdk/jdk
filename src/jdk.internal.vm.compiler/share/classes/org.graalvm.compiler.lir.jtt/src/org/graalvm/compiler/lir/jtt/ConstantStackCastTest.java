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

import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

/**
 * Tests move from a constant to a wider stack slot (e.g. byte constant to integer stack slot).
 */
public class ConstantStackCastTest extends LIRTest {
    private static PlatformKind byteKind;
    private static final LoadConstantStackSpec stackCopyByte = new LoadConstantStackSpec();

    @Before
    public void setup() {
        // Necessary to get the PlatformKind on which we're currently running on
        byteKind = getBackend().getTarget().arch.getPlatformKind(JavaKind.Byte);
        stackCopyByte.dstKind = LIRKind.fromJavaKind(getBackend().getTarget().arch, JavaKind.Int);
        stackCopyByte.srcKind = LIRKind.fromJavaKind(getBackend().getTarget().arch, JavaKind.Byte);
    }

    private static class LoadConstantStackSpec extends LIRTestSpecification {
        LIRKind dstKind;
        LIRKind srcKind;

        @Override
        public void generate(LIRGeneratorTool gen, Value value) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            // create slots
            VirtualStackSlot s1 = frameMapBuilder.allocateSpillSlot(dstKind);
            // move stuff around
            Value srcValue;
            if (isJavaConstant(value)) {
                srcValue = getConstant(srcKind, asJavaConstant(value));
            } else {
                srcValue = value;
            }
            gen.emitMove(s1, srcValue);
            gen.emitBlackhole(s1);
            setResult(gen.emitMove(s1));
        }

        private static ConstantValue getConstant(LIRKind srcKind, JavaConstant c) {
            if (srcKind.getPlatformKind() == byteKind) {
                JavaConstant byteConst = JavaConstant.forByte((byte) c.asInt());
                return new ConstantValue(srcKind, byteConst);
            } else {
                throw GraalError.shouldNotReachHere("Kind not supported: " + srcKind);
            }
        }

    }

    @LIRIntrinsic
    public static byte testCopyByte(@SuppressWarnings("unused") LoadConstantStackSpec spec, byte value) {
        return value;
    }

    public byte testByte(byte value) {
        return testCopyByte(stackCopyByte, value);
    }

    @Test
    public void runByte() throws Throwable {
        runTest("testByte", (byte) 0);
    }

}
