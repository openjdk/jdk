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
 * @summary Testing Classfile local variable table.
 * @compile -g testdata/Lvt.java
 * @run junit LvtTest
 */
import helpers.ClassRecord;
import helpers.Transforms;
import jdk.internal.classfile.*;

import java.io.*;
import java.lang.constant.ClassDesc;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.Attributes;
import jdk.internal.classfile.attribute.SourceFileAttribute;
import jdk.internal.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.instruction.LocalVariable;
import jdk.internal.classfile.instruction.LocalVariableType;
import java.lang.reflect.AccessFlag;
import org.junit.jupiter.api.Test;

import static helpers.TestConstants.CD_ArrayList;
import static helpers.TestConstants.CD_PrintStream;
import static helpers.TestConstants.CD_System;
import static helpers.TestConstants.MTD_INT_VOID;
import static helpers.TestConstants.MTD_VOID;
import static helpers.TestUtil.ExpectedLvRecord;
import static helpers.TestUtil.ExpectedLvtRecord;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import static jdk.internal.classfile.Opcode.*;
import static jdk.internal.classfile.Opcode.INVOKEVIRTUAL;
import static jdk.internal.classfile.TypeKind.VoidType;
import static org.junit.jupiter.api.Assertions.*;

class LvtTest {
    static byte[] fileBytes;

    static {
        try {
            fileBytes = Files.readAllBytes(Paths.get(URI.create(testdata.Lvt.class.getResource("Lvt.class").toString())));
        }
        catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void getLVTEntries() {
        ClassModel c = Classfile.of().parse(fileBytes);
        CodeModel co = c.methods().stream()
                        .filter(mm -> mm.methodName().stringValue().equals("m"))
                        .map(MethodModel::code)
                        .findFirst()
                        .get()
                        .orElseThrow();

        List<LocalVariable> lvs = new ArrayList<>();
        co.forEachElement(e -> {
            if (e instanceof LocalVariable l) lvs.add(l);
        });

        List<ExpectedLvRecord> expected = List.of(
                ExpectedLvRecord.of(5, "j", "I", 9, 21),
                ExpectedLvRecord.of(0, "this", "Ltestdata/Lvt;", 0, 31),
                ExpectedLvRecord.of(1, "a", "Ljava/lang/String;", 0, 31),
                ExpectedLvRecord.of(4, "d", "[C", 6, 25));

        // Exploits non-symmetric "equals" in ExpectedLvRecord
        assertTrue(expected.equals(lvs));
    }

    @Test
    void buildLVTEntries() throws Exception {
        var cc = Classfile.of();
        ClassModel c = cc.parse(fileBytes);

        // Compare transformed model and original with CodeBuilder filter
        byte[] newClass = cc.transform(c, Transforms.threeLevelNoop);
        ClassRecord orig = ClassRecord.ofClassModel(cc.parse(fileBytes), ClassRecord.CompatibilityFilter.By_ClassBuilder);
        ClassRecord transformed = ClassRecord.ofClassModel(cc.parse(newClass), ClassRecord.CompatibilityFilter.By_ClassBuilder);
        ClassRecord.assertEqualsDeep(transformed, orig);
    }

    @Test
    void testCreateLoadLVT() throws Exception {
        var cc = Classfile.of();
        byte[] bytes = cc.build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(52, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.loadInstruction(TypeKind.ReferenceType, 0)
                                              .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>", MTD_VOID, false)
                                              .returnInstruction(VoidType)
                      )
              )
              .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb
                              .withCode(c0 -> {
                                  ConstantPoolBuilder cpb = cb.constantPool();
                                  Utf8Entry slotName = cpb.utf8Entry("this");
                                  Utf8Entry desc = cpb.utf8Entry("LMyClass;");
                                  Utf8Entry i1n = cpb.utf8Entry("res");
                                  Utf8Entry i2 = cpb.utf8Entry("i");
                                  Utf8Entry intSig = cpb.utf8Entry("I");
                                  Label start = c0.newLabel();
                                  Label end = c0.newLabel();
                                  Label i1 = c0.newLabel();
                                  Label preEnd = c0.newLabel();
                                  Label loopTop = c0.newLabel();
                                  Label loopEnd = c0.newLabel();
                                  c0.localVariable(1, i1n, intSig, i1, preEnd) // LV Entries can be added before the labels
                                    .localVariable(2, i2, intSig, loopTop, preEnd)
                                    .labelBinding(start)
                                    .constantInstruction(ICONST_1, 1)         // 0
                                    .storeInstruction(TypeKind.IntType, 1)          // 1
                                    .labelBinding(i1)
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
                                    .fieldInstruction(GETSTATIC, CD_System, "out", CD_PrintStream)   // 13
                                    .loadInstruction(TypeKind.IntType, 1)
                                    .invokeInstruction(INVOKEVIRTUAL, CD_PrintStream, "println", MTD_INT_VOID, false)  // 15
                                    .labelBinding(preEnd)
                                    .returnInstruction(VoidType)
                                    .labelBinding(end)
                                    .localVariable(0, slotName, desc, start, end); // and lv entries can be added after the labels
                              }));
        });

        var c = cc.parse(bytes);
        var main = c.methods().get(1);
        var lvt = main.code().get().findAttribute(Attributes.LOCAL_VARIABLE_TABLE).get();
        var lvs = lvt.localVariables();

        assertEquals(lvs.size(), 3);
        List<ExpectedLvRecord> expected = List.of(
                ExpectedLvRecord.of(1, "res", "I", 2, 25),
                ExpectedLvRecord.of(2, "i", "I", 4, 23),
                ExpectedLvRecord.of(0, "this", "LMyClass;", 0, 28));

        // Exploits non-symmetric "equals" in ExpectedLvRecord
        assertTrue(expected.equals(lvs));
    }

    @Test
    void getLVTTEntries() {
        ClassModel c = Classfile.of().parse(fileBytes);
        CodeModel co = c.methods().stream()
                        .filter(mm -> mm.methodName().stringValue().equals("n"))
                        .map(MethodModel::code)
                        .findFirst()
                        .get()
                        .orElseThrow();

        List<LocalVariableType> lvts = new ArrayList<>();
        co.forEachElement(e -> {
            if (e instanceof LocalVariableType l) lvts.add(l);
        });

        /* From javap:

        LocalVariableTypeTable:
        Start  Length  Slot  Name   Signature
        51       8     6     f   Ljava/util/List<*>;
        0      64     1     u   TU;
        0      64     2     z   Ljava/lang/Class<+Ljava/util/List<*>;>;
        8      56     3     v   Ljava/util/ArrayList<Ljava/lang/Integer;>;
        17      47     4     s   Ljava/util/Set<-Ljava/util/Set;>;
        */

        List<ExpectedLvtRecord> expected = List.of(
                ExpectedLvtRecord.of(6, "f", "Ljava/util/List<*>;", 51, 8),
                ExpectedLvtRecord.of(1, "u", "TU;", 0, 64),
                ExpectedLvtRecord.of(2, "z", "Ljava/lang/Class<+Ljava/util/List<*>;>;", 0, 64),
                ExpectedLvtRecord.of(3, "v", "Ljava/util/ArrayList<Ljava/lang/Integer;>;", 8, 56),
                ExpectedLvtRecord.of(4, "s", "Ljava/util/Set<-Ljava/util/Set<*>;>;", 17, 47)
        );

        // Exploits non-symmetric "equals" in ExpectedLvRecord
        for (int i = 0; i < lvts.size(); i++) {
            assertTrue(expected.get(i).equals(lvts.get(i)));
        }
    }

    @Test
    void testCreateLoadLVTT() throws Exception {
        var cc = Classfile.of();
        byte[] bytes = cc.build(ClassDesc.of("MyClass"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(52, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))

              .withMethod("<init>", MethodTypeDesc.of(CD_void), 0, mb -> mb
                      .withCode(codeb -> codeb.loadInstruction(TypeKind.ReferenceType, 0)
                                              .invokeInstruction(INVOKESPECIAL, CD_Object, "<init>", MTD_VOID, false)
                                              .returnInstruction(VoidType)
                      )
              )

              .withMethod("m", MethodTypeDesc.of(CD_Object, CD_Object.arrayType()),
                          Classfile.ACC_PUBLIC,
                          mb -> mb.withFlags(AccessFlag.PUBLIC)
                                  .withCode(c0 -> {
                                      ConstantPoolBuilder cpb = cb.constantPool();
                                      Utf8Entry slotName = cpb.utf8Entry("this");
                                      Utf8Entry desc = cpb.utf8Entry("LMyClass;");
                                      Utf8Entry juList = cpb.utf8Entry("Ljava/util/List;");
                                      Utf8Entry TU = cpb.utf8Entry("TU;");
                                      Utf8Entry sig = cpb.utf8Entry("Ljava/util/List<+Ljava/lang/Object;>;");
                                      Utf8Entry l = cpb.utf8Entry("l");
                                      Utf8Entry jlObject = cpb.utf8Entry("Ljava/lang/Object;");
                                      Utf8Entry u = cpb.utf8Entry("u");

                                      Label start = c0.newLabel();
                                      Label end = c0.newLabel();
                                      Label beforeRet = c0.newLabel();

                                      c0.localVariable(2, l, juList, beforeRet, end)
                                        .localVariableType(1, u, TU, start, end)
                                        .labelBinding(start)
                                        .newObjectInstruction(ClassDesc.of("java.util.ArrayList"))
                                        .stackInstruction(DUP)
                                        .invokeInstruction(INVOKESPECIAL, CD_ArrayList, "<init>", MTD_VOID, false)
                                        .storeInstruction(TypeKind.ReferenceType, 2)
                                        .labelBinding(beforeRet)
                                        .localVariableType(2, l, sig, beforeRet, end)
                                        .loadInstruction(TypeKind.ReferenceType, 1)
                                        .returnInstruction(TypeKind.ReferenceType)
                                        .labelBinding(end)
                                        .localVariable(0, slotName, desc, start, end)
                                        .localVariable(1, u, jlObject, start, end);
                                  }));
        });
        var c = cc.parse(bytes);
        var main = c.methods().get(1);
        var lvtt = main.code().get().findAttribute(Attributes.LOCAL_VARIABLE_TYPE_TABLE).get();
        var lvts = lvtt.localVariableTypes();

        /* From javap:

        LocalVariableTypeTable:
        Start  Length  Slot  Name   Signature
            0      10     1     u   TU;
            8       2     2     l   Ljava/util/List<+Ljava/lang/Object;>;
         */

        List<ExpectedLvtRecord> expected = List.of(
                ExpectedLvtRecord.of(1, "u", "TU;", 0, 10),
                ExpectedLvtRecord.of(2, "l", "Ljava/util/List<+Ljava/lang/Object;>;", 8, 2)
        );

        // Exploits non-symmetric "equals" in ExpectedLvRecord
        for (int i = 0; i < lvts.size(); i++) {
            assertTrue(expected.get(i).equals(lvts.get(i)));
        }
    }

    @Test
    void skipDebugSkipsLVT() {
        ClassModel c = Classfile.of(Classfile.DebugElementsOption.DROP_DEBUG).parse(fileBytes);

        c.forEachElement(e -> {
            if (e instanceof MethodModel m) {
                m.forEachElement(el -> {
                    if (el instanceof CodeModel cm) {
                        cm.forEachElement(elem -> {
                            assertFalse(elem instanceof LocalVariable);
                            assertFalse(elem instanceof LocalVariableType);
                        });
                    }
                });
            }
        });
    }
}
