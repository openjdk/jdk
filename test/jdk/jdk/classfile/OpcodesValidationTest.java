/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * @test
 * @summary Testing Classfile constant instruction opcodes.
 * @run testng OpcodesValidationTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import static java.lang.constant.ConstantDescs.CD_void;
import java.lang.constant.MethodTypeDesc;

import java.lang.reflect.AccessFlag;
import jdk.classfile.Classfile;
import jdk.classfile.Opcode;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static jdk.classfile.Opcode.*;

/**
 *
 */
public class OpcodesValidationTest {

    @DataProvider(name = "positiveCases")
    public static Object[][] positiveCases() {
        return new Object[][] {
            {ACONST_NULL, null},
            {SIPUSH, (int)Short.MIN_VALUE},
            {SIPUSH, (int)Short.MAX_VALUE},
            {BIPUSH, (int)Byte.MIN_VALUE},
            {BIPUSH, (int)Byte.MAX_VALUE},
            {ICONST_M1, -1},
            {ICONST_0, 0},
            {ICONST_1, 1},
            {ICONST_2, 2},
            {ICONST_3, 3},
            {ICONST_4, 4},
            {ICONST_5, 5},
            {LCONST_0, 0l},
            {LCONST_0, 0},
            {LCONST_1, 1l},
            {LCONST_1, 1},
            {FCONST_0, 0.0f},
            {FCONST_1, 1.0f},
            {FCONST_2, 2.0f},
            {DCONST_0, 0.0d},
            {DCONST_1, 1.0d},
        };
    }

    @DataProvider(name = "negativeCases")
    public static Object[][] negativeCases() {
        return new Object[][] {
            {ACONST_NULL, 0},
            {SIPUSH, (int)Short.MIN_VALUE - 1},
            {SIPUSH, (int)Short.MAX_VALUE + 1},
            {BIPUSH, (int)Byte.MIN_VALUE - 1},
            {BIPUSH, (int)Byte.MAX_VALUE + 1},
            {ICONST_M1, -1l},
            {ICONST_0, 0l},
            {ICONST_1, 1l},
            {ICONST_2, 2l},
            {ICONST_3, 3l},
            {ICONST_4, 4l},
            {ICONST_5, 5l},
            {LCONST_0, null},
            {LCONST_0, 1l},
            {LCONST_1, 1.0d},
            {LCONST_1, 0},
            {FCONST_0, 0.0d},
            {FCONST_1, 1.01f},
            {FCONST_2, 2},
            {DCONST_0, 0.0f},
            {DCONST_1, 1.0f},
            {DCONST_1, 1},
        };
    }

    @Test(dataProvider = "positiveCases")
    public void testPositive(Opcode opcode, Object constant) {
        Classfile.build(ClassDesc.of("MyClass"),
                        cb -> cb.withFlags(AccessFlag.PUBLIC)
                                .withMethod("<init>", MethodTypeDesc.of(CD_void), 0,
                                      mb -> mb.withCode(
                                              codeb -> codeb.constantInstruction(opcode, (ConstantDesc) constant))));
    }

    @Test(dataProvider = "negativeCases", expectedExceptions = IllegalArgumentException.class)
    public void testNegative(Opcode opcode, Object constant) {
        Classfile.build(ClassDesc.of("MyClass"),
                        cb -> cb.withFlags(AccessFlag.PUBLIC)
                                .withMethod("<init>", MethodTypeDesc.of(CD_void), 0,
                        mb -> mb .withCode(
                                codeb -> codeb.constantInstruction(opcode, (ConstantDesc)constant))));
    }
}
