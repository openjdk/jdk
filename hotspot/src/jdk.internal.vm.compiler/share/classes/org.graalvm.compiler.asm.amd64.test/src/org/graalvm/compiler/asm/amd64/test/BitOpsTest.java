/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.LZCNT;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.TZCNT;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.QWORD;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Field;
import java.util.EnumSet;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.test.AssemblerTest;
import org.graalvm.compiler.code.CompilationResult;

public class BitOpsTest extends AssemblerTest {
    private static boolean lzcntSupported;
    private static boolean tzcntSupported;

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", codeCache.getTarget().arch instanceof AMD64);
        EnumSet<CPUFeature> features = ((AMD64) codeCache.getTarget().arch).getFeatures();
        lzcntSupported = features.contains(CPUFeature.LZCNT);
        tzcntSupported = features.contains(CPUFeature.BMI1);
    }

    @Test
    public void lzcntlTest() {
        if (lzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    Register arg = asRegister(cc.getArgument(0));
                    LZCNT.emit(asm, DWORD, ret, arg);
                    asm.ret(0);
                    return asm.close(true);
                }
            };
            assertReturn("intStub", test, 31, 1);
        }
    }

    @Test
    public void lzcntlMemTest() {
        if (lzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    try {
                        Field f = IntField.class.getDeclaredField("x");
                        AMD64Address arg = new AMD64Address(asRegister(cc.getArgument(0)), (int) UNSAFE.objectFieldOffset(f));
                        LZCNT.emit(asm, DWORD, ret, arg);
                        asm.ret(0);
                        return asm.close(true);
                    } catch (Exception e) {
                        throw new RuntimeException("exception while trying to generate field access:", e);
                    }
                }
            };
            assertReturn("intFieldStub", test, 31, new IntField(1));
        }
    }

    @Test
    public void lzcntqTest() {
        if (lzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    Register arg = asRegister(cc.getArgument(0));
                    LZCNT.emit(asm, QWORD, ret, arg);
                    asm.ret(0);
                    return asm.close(true);
                }
            };
            assertReturn("longStub", test, 63, 1L);
        }
    }

    @Test
    public void lzcntqMemTest() {
        if (lzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    try {
                        Field f = LongField.class.getDeclaredField("x");
                        AMD64Address arg = new AMD64Address(asRegister(cc.getArgument(0)), (int) UNSAFE.objectFieldOffset(f));
                        LZCNT.emit(asm, QWORD, ret, arg);
                        asm.ret(0);
                        return asm.close(true);
                    } catch (Exception e) {
                        throw new RuntimeException("exception while trying to generate field access:", e);
                    }
                }
            };
            assertReturn("longFieldStub", test, 63, new LongField(1));
        }
    }

    @Test
    public void tzcntlTest() {
        if (tzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    Register arg = asRegister(cc.getArgument(0));
                    TZCNT.emit(asm, DWORD, ret, arg);
                    asm.ret(0);
                    return asm.close(true);
                }
            };
            assertReturn("intStub", test, 31, 0x8000_0000);
        }
    }

    @Test
    public void tzcntlMemTest() {
        if (tzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    try {
                        Field f = IntField.class.getDeclaredField("x");
                        AMD64Address arg = new AMD64Address(asRegister(cc.getArgument(0)), (int) UNSAFE.objectFieldOffset(f));
                        TZCNT.emit(asm, DWORD, ret, arg);
                        asm.ret(0);
                        return asm.close(true);
                    } catch (Exception e) {
                        throw new RuntimeException("exception while trying to generate field access:", e);
                    }
                }
            };
            assertReturn("intFieldStub", test, 31, new IntField(0x8000_0000));
        }
    }

    @Test
    public void tzcntqTest() {
        if (tzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    Register arg = asRegister(cc.getArgument(0));
                    TZCNT.emit(asm, QWORD, ret, arg);
                    asm.ret(0);
                    return asm.close(true);
                }
            };
            assertReturn("longStub", test, 63, 0x8000_0000_0000_0000L);
        }
    }

    @Test
    public void tzcntqMemTest() {
        if (tzcntSupported) {
            CodeGenTest test = new CodeGenTest() {

                @Override
                public byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc) {
                    AMD64Assembler asm = new AMD64Assembler(target);
                    Register ret = registerConfig.getReturnRegister(JavaKind.Int);
                    try {
                        Field f = LongField.class.getDeclaredField("x");
                        AMD64Address arg = new AMD64Address(asRegister(cc.getArgument(0)), (int) UNSAFE.objectFieldOffset(f));
                        TZCNT.emit(asm, QWORD, ret, arg);
                        asm.ret(0);
                        return asm.close(true);
                    } catch (Exception e) {
                        throw new RuntimeException("exception while trying to generate field access:", e);
                    }
                }
            };
            assertReturn("longFieldStub", test, 63, new LongField(0x8000_0000_0000_0000L));
        }
    }

    @SuppressWarnings("unused")
    public static int intStub(int arg) {
        return 0;
    }

    @SuppressWarnings("unused")
    public static int longStub(long arg) {
        return 0;
    }

    public static class IntField {
        public int x;

        IntField(int x) {
            this.x = x;
        }
    }

    public static class LongField {
        public long x;

        LongField(long x) {
            this.x = x;
        }
    }

    @SuppressWarnings("unused")
    public static int intFieldStub(IntField arg) {
        return 0;
    }

    @SuppressWarnings("unused")
    public static int longFieldStub(LongField arg) {
        return 0;
    }
}
