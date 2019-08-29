/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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


package org.graalvm.compiler.asm.aarch64.test;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.test.AssemblerTest;
import org.graalvm.compiler.code.CompilationResult;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

public class AArch64BitCountAssemblerTest extends AssemblerTest {
    @Before
    public void checkAArch64() {
        assumeTrue("skipping non AArch64 specific test", codeCache.getTarget().arch instanceof AArch64);
    }

    public interface AArch64CodeGenTestCase {
        CodeGenTest create();

        int getExpected();
    }

    private class AArch64BitCountCodeGenTestCase<T extends Number> implements AArch64CodeGenTestCase {
        final T value;
        final int size;

        AArch64BitCountCodeGenTestCase(T x, int size) {
            assert x instanceof Integer || x instanceof Long;
            this.value = x;
            this.size = size;
        }

        T getValue() {
            return value;
        }

        @Override
        public CodeGenTest create() {
            return (CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) -> {
                AArch64MacroAssembler masm = new AArch64MacroAssembler(target);
                Register dst = registerConfig.getReturnRegister(JavaKind.Int);
                Register src = asRegister(cc.getArgument(0));
                // Generate a nop first as AArch64 Hotspot requires instruction at nmethod verified
                // entry to be a jump or nop. (See https://github.com/oracle/graal/issues/1439)
                masm.nop();
                RegisterArray registers = registerConfig.filterAllocatableRegisters(AArch64Kind.V64_BYTE, registerConfig.getAllocatableRegisters());
                masm.popcnt(size, dst, src, registers.get(registers.size() - 1));
                masm.ret(AArch64.lr);
                return masm.close(true);
            };
        }

        @Override
        public int getExpected() {
            if (value instanceof Integer) {
                return Integer.bitCount((Integer) value);
            } else if (value instanceof Long) {
                return Long.bitCount((Long) value);
            }
            return -1;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBitCount() {
        AArch64CodeGenTestCase[] tests = {
                        new AArch64BitCountCodeGenTestCase<>(0, JavaKind.Int.getByteCount() * Byte.SIZE),
                        new AArch64BitCountCodeGenTestCase<>(1522767384, JavaKind.Int.getByteCount() * Byte.SIZE),
                        new AArch64BitCountCodeGenTestCase<>(0L, JavaKind.Long.getByteCount() * Byte.SIZE),
                        new AArch64BitCountCodeGenTestCase<>(81985529216486895L, JavaKind.Long.getByteCount() * Byte.SIZE),
        };

        assertReturn("intStub", tests[0].create(), tests[0].getExpected(), ((AArch64BitCountCodeGenTestCase<Integer>) tests[0]).getValue());
        assertReturn("intStub", tests[1].create(), tests[1].getExpected(), ((AArch64BitCountCodeGenTestCase<Integer>) tests[1]).getValue());
        assertReturn("longStub", tests[2].create(), tests[2].getExpected(), ((AArch64BitCountCodeGenTestCase<Long>) tests[2]).getValue());
        assertReturn("longStub", tests[3].create(), tests[3].getExpected(), ((AArch64BitCountCodeGenTestCase<Long>) tests[3]).getValue());
    }

    @SuppressWarnings("unused")
    public static int intStub(int x) {
        return 0;
    }

    @SuppressWarnings("unused")
    public static int longStub(long x) {
        return 0;
    }
}
