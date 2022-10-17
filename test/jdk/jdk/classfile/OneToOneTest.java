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
 * @summary Testing Classfile class writing and reading.
 * @run testng OneToOneTest
 */
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import jdk.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;
import jdk.classfile.ClassModel;
import jdk.classfile.Classfile;
import jdk.classfile.Instruction;
import jdk.classfile.Label;
import jdk.classfile.MethodModel;
import jdk.classfile.TypeKind;
import jdk.classfile.attribute.SourceFileAttribute;
import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.classfile.instruction.ConstantInstruction;
import jdk.classfile.instruction.StoreInstruction;
import jdk.classfile.instruction.BranchInstruction;
import jdk.classfile.instruction.LoadInstruction;
import jdk.classfile.instruction.OperatorInstruction;
import jdk.classfile.instruction.FieldInstruction;
import jdk.classfile.instruction.InvokeInstruction;

import static helpers.TestConstants.CD_PrintStream;
import static helpers.TestConstants.CD_System;
import static helpers.TestConstants.MTD_INT_VOID;
import static helpers.TestConstants.MTD_VOID;
import static jdk.classfile.Opcode.*;

public class OneToOneTest {

    @Test
    public void testClassWriteRead() {

        byte[] bytes = Classfile.build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(52, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))

              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                                  .withCode(codeb -> codeb.loadInstruction(TypeKind.ReferenceType, 0)
                                                          .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>", MTD_VOID, false)
                                                          .returnInstruction(TypeKind.VoidType)
                                  )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.STATIC, AccessFlag.PUBLIC).flagsMask(),
                          mb -> mb.withCode(c0 -> {
                                                Label loopTop = c0.newLabel();
                                                Label loopEnd = c0.newLabel();
                                                int fac = 1;
                                                int i = 2;
                                                c0.constantInstruction(ICONST_1, 1)         // 0
                                                  .storeInstruction(TypeKind.IntType, fac)        // 1
                                                  .constantInstruction(ICONST_1, 1)         // 2
                                                  .storeInstruction(TypeKind.IntType, i)          // 3
                                                  .labelBinding(loopTop)
                                                  .loadInstruction(TypeKind.IntType, i)           // 4
                                                  .constantInstruction(BIPUSH, 10)         // 5
                                                  .branchInstruction(IF_ICMPGE, loopEnd) // 6
                                                  .loadInstruction(TypeKind.IntType, fac)         // 7
                                                  .loadInstruction(TypeKind.IntType, i)           // 8
                                                  .operatorInstruction(IMUL)             // 9
                                                  .storeInstruction(TypeKind.IntType, fac)        // 10
                                                  .incrementInstruction(i, 1)    // 11
                                                  .branchInstruction(GOTO, loopTop)     // 12
                                                  .labelBinding(loopEnd)
                                                  .fieldInstruction(GETSTATIC, CD_System, "out", CD_PrintStream)   // 13
                                                  .loadInstruction(TypeKind.IntType, fac)
                                                  .invokeInstruction(INVOKEVIRTUAL, CD_PrintStream, "println", MTD_INT_VOID, false)  // 15
                                                  .returnInstruction(TypeKind.VoidType);
                                            }
                          )
              );
                                       }
        );

        ClassModel cm = Classfile.parse(bytes);
        List<MethodModel> ms = cm.methods();
        Assert.assertEquals(ms.size(), 2);
        boolean found = false;
        for (MethodModel mm : ms) {
            if (mm.methodName().stringValue().equals("main") && mm.code().isPresent()) {
                found = true;
                var code = mm.code().get();
                var instructions = code.elementList().stream()
                                       .filter(e -> e instanceof Instruction)
                                       .map(e -> (Instruction)e)
                                       .toList();
                Assert.assertEquals(instructions.size(), 17);

                Assert.assertEquals(instructions.get(0).opcode(), ICONST_1);

                var i1 = (StoreInstruction) instructions.get(1);
                Assert.assertEquals(i1.opcode(), ISTORE_1);
                int lv1 = i1.slot();
                Assert.assertEquals(lv1, 1);

                ConstantInstruction i5 = (ConstantInstruction) instructions.get(5);
                Assert.assertEquals(i5.opcode(), BIPUSH);
                Assert.assertEquals(i5.constantValue(), 10);

                BranchInstruction i6 = (BranchInstruction) instructions.get(6);
                Assert.assertEquals(i6.opcode(), IF_ICMPGE);
                // Assert.assertEquals(code.instructionOffset(i6.target()), 14);  //FIXME: CodeModel gives BCI, should give instruction offset

                LoadInstruction i7 = (LoadInstruction) instructions.get(7);
                Assert.assertEquals(i7.opcode(), ILOAD_1);

                OperatorInstruction i9 = (OperatorInstruction) instructions.get(9);
                Assert.assertEquals(i9.opcode(), IMUL);

                FieldInstruction i13 = (FieldInstruction) instructions.get(13);
                Assert.assertEquals(i13.opcode(), GETSTATIC);
                Assert.assertEquals(i13.owner().asInternalName(), "java/lang/System");
                Assert.assertEquals(i13.name().stringValue(), "out");
                Assert.assertEquals(i13.type().stringValue(), "Ljava/io/PrintStream;");

                InvokeInstruction i15 = (InvokeInstruction) instructions.get(15);
                Assert.assertEquals(i15.opcode(), INVOKEVIRTUAL);
                Assert.assertEquals(i15.owner().asInternalName(), "java/io/PrintStream");
                Assert.assertEquals(i15.name().stringValue(), "println");
                Assert.assertEquals(i15.type().stringValue(), "(I)V");
            }
        }
        Assert.assertTrue(found);
    }
}
