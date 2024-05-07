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
 * @summary Testing ClassFile class building.
 * @run junit WriteTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import helpers.TestConstants;
import java.lang.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.SourceFileAttribute;
import org.junit.jupiter.api.Test;

import static helpers.TestConstants.MTD_VOID;
import static java.lang.constant.ConstantDescs.*;
import static java.lang.classfile.Opcode.*;
import static java.lang.classfile.TypeKind.IntType;
import static java.lang.classfile.TypeKind.ReferenceType;
import static java.lang.classfile.TypeKind.VoidType;

class WriteTest {

    @Test
    void testJavapWrite() {

        byte[] bytes = ClassFile.of().build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.aload(0)
                                              .invokespecial(CD_Object, "<init>",
                                                                 MethodTypeDesc.ofDescriptor("()V"), false)
                                              .return_()
                      )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                  Label loopTop = c0.newLabel();
                                  Label loopEnd = c0.newLabel();
                                  c0
                                          .iconst_1()         // 0
                                          .istore(1)          // 1
                                          .iconst_1()         // 2
                                          .istore(2)          // 3
                                          .labelBinding(loopTop)
                                          .iload(2)           // 4
                                          .bipush(10)         // 5
                                          .if_icmpge(loopEnd) // 6
                                          .iload(1)           // 7
                                          .iload(2)           // 8
                                          .imul()             // 9
                                          .istore(1)          // 10
                                          .iinc(2, 1)    // 11
                                          .goto_(loopTop)     // 12
                                          .labelBinding(loopEnd)
                                          .getstatic(TestConstants.CD_System, "out", TestConstants.CD_PrintStream)   // 13
                                          .iload(1)
                                          .invokevirtual(TestConstants.CD_PrintStream, "println", TestConstants.MTD_INT_VOID)  // 15
                                          .return_();
                              }));
        });
    }

    @Test
    void testPrimitiveWrite() {

        byte[] bytes = ClassFile.of().build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC)
              .with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.aload(0)
                                              .invokespecial(CD_Object, "<init>", MTD_VOID, false)
                                              .return_()
                      )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                  Label loopTop = c0.newLabel();
                                  Label loopEnd = c0.newLabel();
                                  c0
                                          .iconst_1()        // 0
                                          .istore(1)          // 1
                                          .iconst_1()        // 2
                                          .istore(2)          // 3
                                          .labelBinding(loopTop)
                                          .iload(2)           // 4
                                          .bipush(10)         // 5
                                          .if_icmpge(loopEnd) // 6
                                          .iload(1)           // 7
                                          .iload(2)           // 8
                                          .imul()             // 9
                                          .istore(1)          // 10
                                          .iinc(2, 1)    // 11
                                          .goto_(loopTop)     // 12
                                          .labelBinding(loopEnd)
                                          .getstatic(TestConstants.CD_System, "out", TestConstants.CD_PrintStream)   // 13
                                          .iload(1)
                                          .invokevirtual(TestConstants.CD_PrintStream, "println", TestConstants.MTD_INT_VOID)  // 15
                                          .return_();
                              }));
        });
    }
}
