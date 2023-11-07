/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing Classfile constant instruction opcodes.
 * @run junit OpcodesValidationTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import static java.lang.constant.ConstantDescs.CD_void;
import java.lang.constant.MethodTypeDesc;

import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.Opcode;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.Assertions.*;
import static jdk.internal.classfile.Opcode.*;
import java.util.stream.Stream;

public class OpcodesValidationTest {

    record Case(Opcode opcode, Object constant) {}

    static Stream<Case> positiveCases() {
        return Stream.of(
            new Case(ACONST_NULL, null),
            new Case(SIPUSH, (int)Short.MIN_VALUE),
            new Case(SIPUSH, (int)Short.MAX_VALUE),
            new Case(BIPUSH, (int)Byte.MIN_VALUE),
            new Case(BIPUSH, (int)Byte.MAX_VALUE),
            new Case(ICONST_M1, -1),
            new Case(ICONST_0, 0),
            new Case(ICONST_1, 1),
            new Case(ICONST_2, 2),
            new Case(ICONST_3, 3),
            new Case(ICONST_4, 4),
            new Case(ICONST_5, 5),
            new Case(LCONST_0, 0l),
            new Case(LCONST_0, 0),
            new Case(LCONST_1, 1l),
            new Case(LCONST_1, 1),
            new Case(FCONST_0, 0.0f),
            new Case(FCONST_1, 1.0f),
            new Case(FCONST_2, 2.0f),
            new Case(DCONST_0, 0.0d),
            new Case(DCONST_1, 1.0d)
        );
    }

    static Stream<Case> negativeCases() {
        return Stream.of(
            new Case(ACONST_NULL, 0),
            new Case(SIPUSH, (int)Short.MIN_VALUE - 1),
            new Case(SIPUSH, (int)Short.MAX_VALUE + 1),
            new Case(BIPUSH, (int)Byte.MIN_VALUE - 1),
            new Case(BIPUSH, (int)Byte.MAX_VALUE + 1),
            new Case(ICONST_M1, -1l),
            new Case(ICONST_0, 0l),
            new Case(ICONST_1, 1l),
            new Case(ICONST_2, 2l),
            new Case(ICONST_3, 3l),
            new Case(ICONST_4, 4l),
            new Case(ICONST_5, 5l),
            new Case(LCONST_0, null),
            new Case(LCONST_0, 1l),
            new Case(LCONST_1, 1.0d),
            new Case(LCONST_1, 0),
            new Case(FCONST_0, 0.0d),
            new Case(FCONST_1, 1.01f),
            new Case(FCONST_2, 2),
            new Case(DCONST_0, 0.0f),
            new Case(DCONST_1, 1.0f),
            new Case(DCONST_1, 1)
        );
    }

    @TestFactory
    Stream<DynamicTest> testPositiveCases() {
        return positiveCases().map(c -> dynamicTest(c.toString(), () -> testPositiveCase(c.opcode, c.constant)));
    }

    private void testPositiveCase(Opcode opcode, Object constant) {
        Classfile.of().build(ClassDesc.of("MyClass"),
                        cb -> cb.withFlags(AccessFlag.PUBLIC)
                                .withMethod("<init>", MethodTypeDesc.of(CD_void), 0,
                                      mb -> mb.withCode(
                                              codeb -> codeb.constantInstruction(opcode, (ConstantDesc) constant))));
    }


    @TestFactory
    Stream<DynamicTest> testNegativeCases() {
        return negativeCases().map(c -> dynamicTest(
            c.toString(),
            () -> assertThrows(IllegalArgumentException.class, () -> testNegativeCase(c.opcode, c.constant))
        ));
    }

    private void testNegativeCase(Opcode opcode, Object constant) {
        Classfile.of().build(ClassDesc.of("MyClass"),
                        cb -> cb.withFlags(AccessFlag.PUBLIC)
                                .withMethod("<init>", MethodTypeDesc.of(CD_void), 0,
                        mb -> mb .withCode(
                                codeb -> codeb.constantInstruction(opcode, (ConstantDesc)constant))));
    }
}
