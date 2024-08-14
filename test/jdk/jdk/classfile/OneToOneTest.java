/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile class writing and reading.
 * @run junit OneToOneTest
 */
import java.lang.constant.ClassDesc;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.SourceFileAttribute;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;

import static helpers.TestConstants.CD_PrintStream;
import static helpers.TestConstants.CD_System;
import static helpers.TestConstants.MTD_INT_VOID;
import static helpers.TestConstants.MTD_VOID;
import static java.lang.classfile.Opcode.*;

class OneToOneTest {

    @Test
    void testClassWriteRead() {
        var cc = ClassFile.of();
        byte[] bytes = cc.build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(52, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))

              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                                  .withCode(codeb -> codeb.aload(0)
                                                          .invokespecial(CD_Object, "<init>", MTD_VOID, false)
                                                          .return_()
                                  )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          ACC_PUBLIC | ACC_STATIC,
                          mb -> mb.withCode(c0 -> {
                                                Label loopTop = c0.newLabel();
                                                Label loopEnd = c0.newLabel();
                                                int fac = 1;
                                                int i = 2;
                                                c0.iconst_1()         // 0
                                                  .istore(fac)        // 1
                                                  .iconst_1()         // 2
                                                  .istore(i)          // 3
                                                  .labelBinding(loopTop)
                                                  .iload(i)           // 4
                                                  .bipush(10)         // 5
                                                  .if_icmpge(loopEnd) // 6
                                                  .iload(fac)         // 7
                                                  .iload(i)           // 8
                                                  .imul()             // 9
                                                  .istore(fac)        // 10
                                                  .iinc(i, 1)         // 11
                                                  .goto_(loopTop)     // 12
                                                  .labelBinding(loopEnd)
                                                  .getstatic(CD_System, "out", CD_PrintStream)   // 13
                                                  .iload(fac)
                                                  .invokevirtual(CD_PrintStream, "println", MTD_INT_VOID)  // 15
                                                  .return_();
                                            }
                          )
              );
                                       }
        );

        ClassModel cm = cc.parse(bytes);
        List<MethodModel> ms = cm.methods();
        assertEquals(ms.size(), 2);
        boolean found = false;
        for (MethodModel mm : ms) {
            if (mm.methodName().stringValue().equals("main") && mm.code().isPresent()) {
                found = true;
                var code = mm.code().get();
                var instructions = code.elementList().stream()
                                       .filter(e -> e instanceof Instruction)
                                       .map(e -> (Instruction)e)
                                       .toList();
                assertEquals(instructions.size(), 17);

                assertEquals(instructions.get(0).opcode(), ICONST_1);

                var i1 = (StoreInstruction) instructions.get(1);
                assertEquals(i1.opcode(), ISTORE_1);
                int lv1 = i1.slot();
                assertEquals(lv1, 1);

                ConstantInstruction i5 = (ConstantInstruction) instructions.get(5);
                assertEquals(i5.opcode(), BIPUSH);
                assertEquals(i5.constantValue(), 10);

                BranchInstruction i6 = (BranchInstruction) instructions.get(6);
                assertEquals(i6.opcode(), IF_ICMPGE);
                // assertEquals(code.instructionOffset(i6.target()), 14);  //FIXME: CodeModel gives BCI, should give instruction offset

                LoadInstruction i7 = (LoadInstruction) instructions.get(7);
                assertEquals(i7.opcode(), ILOAD_1);

                OperatorInstruction i9 = (OperatorInstruction) instructions.get(9);
                assertEquals(i9.opcode(), IMUL);

                FieldInstruction i13 = (FieldInstruction) instructions.get(13);
                assertEquals(i13.opcode(), GETSTATIC);
                assertEquals(i13.owner().asInternalName(), "java/lang/System");
                assertEquals(i13.name().stringValue(), "out");
                assertEquals(i13.type().stringValue(), "Ljava/io/PrintStream;");

                InvokeInstruction i15 = (InvokeInstruction) instructions.get(15);
                assertEquals(i15.opcode(), INVOKEVIRTUAL);
                assertEquals(i15.owner().asInternalName(), "java/io/PrintStream");
                assertEquals(i15.name().stringValue(), "println");
                assertEquals(i15.type().stringValue(), "(I)V");
            }
        }
        assertTrue(found);
    }
}
