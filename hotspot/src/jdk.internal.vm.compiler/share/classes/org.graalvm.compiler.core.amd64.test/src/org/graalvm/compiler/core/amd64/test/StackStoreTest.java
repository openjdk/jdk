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
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class StackStoreTest extends LIRTest {
    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    private static final LIRTestSpecification stackCopy0 = new LIRTestSpecification() {
        @Override
        public void generate(LIRGeneratorTool gen, Value a) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            // create slots
            VirtualStackSlot s1 = frameMapBuilder.allocateSpillSlot(a.getValueKind());
            VirtualStackSlot s2 = frameMapBuilder.allocateSpillSlot(LIRKind.value(AMD64Kind.WORD));
            // move stuff around
            gen.emitMove(s1, a);
            gen.emitMoveConstant(s2, JavaConstant.forShort(Short.MIN_VALUE));
            setResult(gen.emitMove(s1));
            gen.emitBlackhole(s1);
            gen.emitBlackhole(s2);
        }
    };

    private static final LIRTestSpecification stackCopy1 = new LIRTestSpecification() {
        @Override
        public void generate(LIRGeneratorTool gen, Value a) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            // create slots
            VirtualStackSlot s1 = frameMapBuilder.allocateSpillSlot(a.getValueKind());
            VirtualStackSlot s2 = frameMapBuilder.allocateSpillSlot(LIRKind.value(AMD64Kind.WORD));
            // move stuff around
            gen.emitMove(s1, a);
            Value v = gen.emitLoadConstant(LIRKind.value(AMD64Kind.WORD), JavaConstant.forShort(Short.MIN_VALUE));
            gen.emitMove(s2, v);
            setResult(gen.emitMove(s1));
            gen.emitBlackhole(s1);
            gen.emitBlackhole(s2);
        }
    };

    private static final LIRTestSpecification stackCopy2 = new LIRTestSpecification() {
        @Override
        public void generate(LIRGeneratorTool gen, Value a) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            // create slots
            VirtualStackSlot s1 = frameMapBuilder.allocateSpillSlot(a.getValueKind());
            VirtualStackSlot s2 = frameMapBuilder.allocateSpillSlot(LIRKind.value(AMD64Kind.WORD));
            // move stuff around
            gen.emitMoveConstant(s2, JavaConstant.forShort(Short.MIN_VALUE));
            gen.emitMove(s1, a);
            setResult(gen.emitMove(s2));
            gen.emitBlackhole(s1);
            gen.emitBlackhole(s2);
        }
    };

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static int testShortStackSlot(LIRTestSpecification spec, int a) {
        return a;
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static short testShortStackSlot2(LIRTestSpecification spec, int a) {
        return Short.MIN_VALUE;
    }

    public int test0(int a) {
        return testShortStackSlot(stackCopy0, a);
    }

    @Test
    public void run0() throws Throwable {
        runTest("test0", 0xDEADDEAD);
    }

    public int test1(int a) {
        return testShortStackSlot(stackCopy1, a);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test1", 0xDEADDEAD);
    }

    public int test2(int a) {
        return testShortStackSlot2(stackCopy2, a);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test2", 0xDEADDEAD);
    }

}
