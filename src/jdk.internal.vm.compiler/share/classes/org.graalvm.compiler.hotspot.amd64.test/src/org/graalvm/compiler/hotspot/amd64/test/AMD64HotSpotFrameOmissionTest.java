/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64.test;

import static jdk.vm.ci.amd64.AMD64.rax;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.core.test.GraalCompilerTest;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Ensures that frame omission works in cases where it is expected to.
 */
public class AMD64HotSpotFrameOmissionTest extends GraalCompilerTest {

    interface CodeGenerator {

        void generateCode(AMD64Assembler asm);
    }

    public static void test1snippet() {
        return;
    }

    @Ignore
    @Test
    public void test1() {
        testHelper("test1snippet", new CodeGenerator() {

            @Override
            public void generateCode(AMD64Assembler asm) {
                asm.nop(5); // padding for mt-safe patching
                asm.ret(0);
            }
        });
    }

    public static int test2snippet(int x) {
        return x + 5;
    }

    @Ignore
    @Test
    public void test2() {
        testHelper("test2snippet", new CodeGenerator() {

            @Override
            public void generateCode(AMD64Assembler asm) {
                Register arg = getArgumentRegister(0, JavaKind.Int);
                asm.nop(5); // padding for mt-safe patching
                asm.addl(arg, 5);
                asm.movl(rax, arg);
                asm.ret(0);
            }
        });
    }

    public static long test3snippet(long x) {
        return 1 + x;
    }

    @Ignore
    @Test
    public void test3() {
        testHelper("test3snippet", new CodeGenerator() {

            @Override
            public void generateCode(AMD64Assembler asm) {
                Register arg = getArgumentRegister(0, JavaKind.Long);
                asm.nop(5); // padding for mt-safe patching
                asm.addq(arg, 1);
                asm.movq(rax, arg);
                asm.ret(0);
            }
        });
    }

    private void testHelper(String name, CodeGenerator gen) {
        ResolvedJavaMethod javaMethod = getResolvedJavaMethod(name);
        InstalledCode installedCode = getCode(javaMethod);

        TargetDescription target = getCodeCache().getTarget();
        AMD64Assembler asm = new AMD64Assembler(target);

        gen.generateCode(asm);
        byte[] expectedCode = asm.close(true);

        // Only compare up to expectedCode.length bytes to ignore
        // padding instructions adding during code installation
        byte[] actualCode = Arrays.copyOf(installedCode.getCode(), expectedCode.length);

        Assert.assertArrayEquals(expectedCode, actualCode);
    }

    private Register getArgumentRegister(int index, JavaKind kind) {
        RegisterArray regs = getCodeCache().getRegisterConfig().getCallingConventionRegisters(HotSpotCallingConventionType.JavaCall, kind);
        return regs.get(index);
    }
}
