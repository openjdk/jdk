/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Testing ClassFile builder parameters.
 * @run junit BuilderParamTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.lang.classfile.ClassFile;

import helpers.CodeBuilderType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BuilderParamTest
 */
class BuilderParamTest {
    @EnumSource
    @ParameterizedTest
    void test(CodeBuilderType type) {
        var cc = ClassFile.of();
        cc.build(ClassDesc.of("Foo"), type.asClassHandler("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), 0, xb -> {
            assertEquals(0, xb.receiverSlot(), "this");
            assertEquals(1, xb.parameterSlot(0), "int");
            assertEquals(2, xb.parameterSlot(1), "long");
            assertEquals(4, xb.parameterSlot(2), "int");
            xb.return_();
        }));
        cc.build(ClassDesc.of("Foo"), type.asClassHandler("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), ACC_STATIC, xb -> {
            assertEquals(0, xb.parameterSlot(0), "int");
            assertEquals(1, xb.parameterSlot(1), "long");
            assertEquals(3, xb.parameterSlot(2), "int");
            xb.return_();
        }));
    }
}
