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
 * @summary Testing Classfile class building.
 * @run junit WriteTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import helpers.TestConstants;
import jdk.internal.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.attribute.SourceFileAttribute;
import org.junit.jupiter.api.Test;

import static helpers.TestConstants.MTD_VOID;
import static java.lang.constant.ConstantDescs.*;
import static jdk.internal.classfile.Opcode.*;
import static jdk.internal.classfile.TypeKind.IntType;
import static jdk.internal.classfile.TypeKind.ReferenceType;
import static jdk.internal.classfile.TypeKind.VoidType;

class WriteTest {

    @Test
    void testJavapWrite() {

        byte[] bytes = Classfile.of().build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.loadInstruction(TypeKind.ReferenceType, 0)
                                              .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>",
                                                                 MethodTypeDesc.ofDescriptor("()V"), false)
                                              .returnInstruction(VoidType)
                      )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                  Label loopTop = c0.newLabel();
                                  Label loopEnd = c0.newLabel();
                                  c0
                                          .constantInstruction(ICONST_1, 1)         // 0
                                          .storeInstruction(TypeKind.IntType, 1)          // 1
                                          .constantInstruction(ICONST_1, 1)         // 2
                                          .storeInstruction(TypeKind.IntType, 2)          // 3
                                          .labelBinding(loopTop)
                                          .loadInstruction(TypeKind.IntType, 2)           // 4
                                          .constantInstruction(BIPUSH, 10)         // 5
                                          .branchInstruction(IF_ICMPGE, loopEnd) // 6
                                          .loadInstruction(TypeKind.IntType, 1)           // 7
                                          .loadInstruction(TypeKind.IntType, 2)           // 8
                                          .operatorInstruction(IMUL)             // 9
                                          .storeInstruction(TypeKind.IntType, 1)          // 10
                                          .incrementInstruction(2, 1)    // 11
                                          .branchInstruction(GOTO, loopTop)     // 12
                                          .labelBinding(loopEnd)
                                          .fieldInstruction(GETSTATIC, TestConstants.CD_System, "out", TestConstants.CD_PrintStream)   // 13
                                          .loadInstruction(TypeKind.IntType, 1)
                                          .invokeInstruction(INVOKEVIRTUAL, TestConstants.CD_PrintStream, "println", TestConstants.MTD_INT_VOID, false)  // 15
                                          .returnInstruction(VoidType);
                              }));
        });
    }

    @Test
    void testPrimitiveWrite() {

        byte[] bytes = Classfile.of().build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC)
              .with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.loadInstruction(ReferenceType, 0)
                                              .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>", MTD_VOID, false)
                                              .returnInstruction(VoidType)
                      )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                  Label loopTop = c0.newLabel();
                                  Label loopEnd = c0.newLabel();
                                  c0
                                          .constantInstruction(ICONST_1, 1)        // 0
                                          .storeInstruction(IntType, 1)          // 1
                                          .constantInstruction(ICONST_1, 1)        // 2
                                          .storeInstruction(IntType, 2)          // 3
                                          .labelBinding(loopTop)
                                          .loadInstruction(IntType, 2)           // 4
                                          .constantInstruction(BIPUSH, 10)         // 5
                                          .branchInstruction(IF_ICMPGE, loopEnd) // 6
                                          .loadInstruction(IntType, 1)           // 7
                                          .loadInstruction(IntType, 2)           // 8
                                          .operatorInstruction(IMUL)             // 9
                                          .storeInstruction(IntType, 1)          // 10
                                          .incrementInstruction(2, 1)    // 11
                                          .branchInstruction(GOTO, loopTop)     // 12
                                          .labelBinding(loopEnd)
                                          .fieldInstruction(GETSTATIC, TestConstants.CD_System, "out", TestConstants.CD_PrintStream)   // 13
                                          .loadInstruction(IntType, 1)
                                          .invokeInstruction(INVOKEVIRTUAL, TestConstants.CD_PrintStream, "println", TestConstants.MTD_INT_VOID, false)  // 15
                                          .returnInstruction(VoidType);
                              }));
        });
    }
}
