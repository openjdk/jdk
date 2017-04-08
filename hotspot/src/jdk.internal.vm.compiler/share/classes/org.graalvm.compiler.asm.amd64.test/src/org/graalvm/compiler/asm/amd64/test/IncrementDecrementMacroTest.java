/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.amd64.test;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Field;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.test.AssemblerTest;
import org.graalvm.compiler.code.CompilationResult;

public class IncrementDecrementMacroTest extends AssemblerTest {

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", codeCache.getTarget().arch instanceof AMD64);
    }

    public static class LongField {
        public long x;

        LongField(long x) {
            this.x = x;
        }
    }

    private static class IncrementCodeGenTest implements CodeGenTest {
        final int value;

        IncrementCodeGenTest(int value) {
            this.value = value;
        }

        @Override
        public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
            AMD64MacroAssembler asm = new AMD64MacroAssembler(target);
            Register ret = registerConfig.getReturnRegister(JavaKind.Int);
            try {
                Field f = LongField.class.getDeclaredField("x");
                AMD64Address arg = new AMD64Address(asRegister(cc.getArgument(0)), (int) UNSAFE.objectFieldOffset(f));
                asm.incrementq(arg, value);
                asm.movq(ret, arg);
                asm.ret(0);
                return asm.close(true);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to generate field access:", e);
            }
        }
    }

    private void assertIncrement(long initValue, int increment) {
        assertReturn("longFieldStubIncrement", new IncrementCodeGenTest(increment), initValue + increment, new LongField(initValue));
    }

    private void assertIncrements(int increment) {
        assertIncrement(0x4242_4242_4242_4242L, increment);
    }

    @SuppressWarnings("unused")
    public static long longFieldStubIncrement(LongField arg) {
        return 0;
    }

    private static class DecrementCodeGenTest implements CodeGenTest {
        final int value;

        DecrementCodeGenTest(int value) {
            this.value = value;
        }

        @Override
        public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
            AMD64MacroAssembler asm = new AMD64MacroAssembler(target);
            Register ret = registerConfig.getReturnRegister(JavaKind.Int);
            try {
                Field f = LongField.class.getDeclaredField("x");
                AMD64Address arg = new AMD64Address(asRegister(cc.getArgument(0)), (int) UNSAFE.objectFieldOffset(f));
                asm.decrementq(arg, value);
                asm.movq(ret, arg);
                asm.ret(0);
                return asm.close(true);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to generate field access:", e);
            }
        }
    }

    private void assertDecrement(long initValue, int increment) {
        assertReturn("longFieldStubDecrement", new DecrementCodeGenTest(increment), initValue - increment, new LongField(initValue));
    }

    private void assertDecrements(int increment) {
        assertDecrement(0x4242_4242_4242_4242L, increment);
    }

    @SuppressWarnings("unused")
    public static long longFieldStubDecrement(LongField arg) {
        return 0;
    }

    @Test
    public void incrementMemTest0() {
        int increment = 0;
        assertIncrements(increment);
    }

    @Test
    public void incrementMemTest1() {
        int increment = 1;
        assertIncrements(increment);
    }

    @Test
    public void incrementMemTest2() {
        int increment = 2;
        assertIncrements(increment);
    }

    @Test
    public void incrementMemTest3() {
        int increment = Integer.MAX_VALUE;
        assertIncrements(increment);
    }

    @Test
    public void incrementMemTest4() {
        int increment = Integer.MIN_VALUE;
        assertIncrements(increment);
    }

    @Test
    public void incrementMemTest5() {
        int increment = -1;
        assertIncrements(increment);
    }

    @Test
    public void incrementMemTest6() {
        int increment = -2;
        assertIncrements(increment);
    }

    @Test
    public void incrementMemTest7() {
        int increment = -0x1000_0000;
        assertIncrements(increment);
    }

    @Test
    public void decrementMemTest0() {
        int decrement = 0;
        assertDecrements(decrement);
    }

    @Test
    public void decrementMemTest1() {
        int decrement = 1;
        assertDecrements(decrement);
    }

    @Test
    public void decrementMemTest2() {
        int decrement = 2;
        assertDecrements(decrement);
    }

    @Test
    public void decrementMemTest3() {
        int decrement = Integer.MAX_VALUE;
        assertDecrements(decrement);
    }

    @Test
    public void decrementMemTest4() {
        int decrement = Integer.MIN_VALUE;
        assertDecrements(decrement);
    }

    @Test
    public void decrementMemTest5() {
        int decrement = -1;
        assertDecrements(decrement);
    }

    @Test
    public void decrementMemTest6() {
        int decrement = -2;
        assertDecrements(decrement);
    }

    @Test
    public void decrementMemTest7() {
        int decrement = -0x1000_0000;
        assertDecrements(decrement);
    }

}
