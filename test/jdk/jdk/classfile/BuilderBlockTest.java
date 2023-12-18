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
 * @summary Testing ClassFile builder blocks.
 * @run junit BuilderBlockTest
 */
import java.lang.constant.ClassDesc;

import static java.lang.constant.ConstantDescs.*;

import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import helpers.ByteArrayClassLoader;
import java.lang.classfile.AccessFlags;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import jdk.internal.classfile.impl.LabelImpl;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * BuilderBlockTest
 */
class BuilderBlockTest {

    static final String testClassName = "AdaptCodeTest$TestClass";
    static final Path testClassPath = Paths.get("target/test-classes/" + testClassName + ".class");

    @Test
    void testStartEnd() throws Exception {
        // Ensure that start=0 at top level, end is undefined until code is done, then end=1
        Label startEnd[] = new Label[2];

        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.of(CD_void), 0,
                          mb -> mb.withCode(xb -> {
                              startEnd[0] = xb.startLabel();
                              startEnd[1] = xb.endLabel();
                              xb.returnInstruction(TypeKind.VoidType);
                              assertEquals(((LabelImpl) startEnd[0]).getBCI(), 0);
                              assertEquals(((LabelImpl) startEnd[1]).getBCI(), -1);
                          }));
        });

        assertEquals(((LabelImpl) startEnd[0]).getBCI(), 0);
        assertEquals(((LabelImpl) startEnd[1]).getBCI(), 1);
    }

    @Test
    void testStartEndBlock() throws Exception {
        Label startEnd[] = new Label[4];

        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.of(CD_void), 0,
                          mb -> mb.withCode(xb -> {
                              startEnd[0] = xb.startLabel();
                              startEnd[1] = xb.endLabel();
                              xb.nopInstruction();
                              xb.block(xxb -> {
                                  startEnd[2] = xxb.startLabel();
                                  startEnd[3] = xxb.endLabel();
                                  xxb.nopInstruction();
                              });
                              xb.returnInstruction(TypeKind.VoidType);
                          }));
        });

        assertEquals(((LabelImpl) startEnd[0]).getBCI(), 0);
        assertEquals(((LabelImpl) startEnd[1]).getBCI(), 3);
        assertEquals(((LabelImpl) startEnd[2]).getBCI(), 1);
        assertEquals(((LabelImpl) startEnd[3]).getBCI(), 2);
    }

    @Test
    void testIfThenReturn() throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withMethod("foo", MethodTypeDesc.of(CD_int, CD_int),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(xb -> xb.iload(0)
                                                    .ifThen(xxb -> xxb.iconst_1().returnInstruction(TypeKind.IntType))
                                                    .iconst_2()
                                                    .returnInstruction(TypeKind.IntType)));
        });

        Method fooMethod = new ByteArrayClassLoader(BuilderBlockTest.class.getClassLoader(), "Foo", bytes)
                .getMethod("Foo", "foo");
        assertEquals(fooMethod.invoke(null, 3), 1);
        assertEquals(fooMethod.invoke(null, 0), 2);

    }

    @Test
    void testIfThenElseReturn() throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withMethod("foo", MethodTypeDesc.of(CD_int, CD_int),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(xb -> xb.iload(0)
                                                    .ifThenElse(xxb -> xxb.iconst_1().returnInstruction(TypeKind.IntType),
                                                                xxb -> xxb.iconst_2().returnInstruction(TypeKind.IntType))));
        });

        Method fooMethod = new ByteArrayClassLoader(BuilderBlockTest.class.getClassLoader(), "Foo", bytes)
                .getMethod("Foo", "foo");
        assertEquals(fooMethod.invoke(null, 3), 1);
        assertEquals(fooMethod.invoke(null, 0), 2);

    }

    @Test
    void testIfThenBadOpcode()  {
        ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withMethod("foo", MethodTypeDesc.of(CD_int, CD_int, CD_int),
                    AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                    mb -> mb.withCode(xb -> {
                        xb.iload(0);
                        xb.iload(1);
                        assertThrows(IllegalArgumentException.class, () -> {
                            xb.ifThen(
                                    Opcode.GOTO,
                                    xxb -> xxb.iconst_1().istore(2));
                        });
                        xb.iload(2);
                        xb.ireturn();
                    }));
        });
    }

    @Test
    void testIfThenElseImplicitBreak() throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withMethod("foo", MethodTypeDesc.of(CD_int, CD_int),
                          AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                          mb -> mb.withCode(xb -> xb.iload(0)
                                                    .ifThenElse(xxb -> xxb.iconst_1().istore(2),
                                                                xxb -> xxb.iconst_2().istore(2))
                                                    .iload(2)
                                                    .ireturn()));
        });

        Method fooMethod = new ByteArrayClassLoader(BuilderBlockTest.class.getClassLoader(), "Foo", bytes)
                .getMethod("Foo", "foo");
        assertEquals(fooMethod.invoke(null, 3), 1);
        assertEquals(fooMethod.invoke(null, 0), 2);

    }

    @Test
    void testIfThenElseExplicitBreak() throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withMethod("foo", MethodTypeDesc.of(CD_int, CD_int),
                    AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                    mb -> mb.withCode(xb -> xb.iload(0)
                            .ifThenElse(xxb -> xxb.iconst_1().istore(2).goto_(xxb.breakLabel()),
                                    xxb -> xxb.iconst_2().istore(2).goto_(xxb.breakLabel()))
                            .iload(2)
                            .ireturn()));
        });

        Method fooMethod = new ByteArrayClassLoader(BuilderBlockTest.class.getClassLoader(), "Foo", bytes)
                .getMethod("Foo", "foo");
        assertEquals(fooMethod.invoke(null, 3), 1);
        assertEquals(fooMethod.invoke(null, 0), 2);
    }

    @Test
    void testIfThenElseOpcode() throws Exception {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withMethod("foo", MethodTypeDesc.of(CD_int, CD_int, CD_int),
                    AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                    mb -> mb.withCode(xb ->
                            xb.iload(0)
                            .iload(1)
                            .ifThenElse(
                                    Opcode.IF_ICMPLT,
                                    xxb -> xxb.iconst_1().istore(2),
                                    xxb -> xxb.iconst_2().istore(2))
                            .iload(2)
                            .ireturn()));
        });

        Method fooMethod = new ByteArrayClassLoader(BuilderBlockTest.class.getClassLoader(), "Foo", bytes)
                .getMethod("Foo", "foo");
        assertEquals(fooMethod.invoke(null, 1, 10), 1);
        assertEquals(fooMethod.invoke(null, 9, 10), 1);
        assertEquals(fooMethod.invoke(null, 10, 10), 2);
        assertEquals(fooMethod.invoke(null, 11, 10), 2);
    }

    @Test
    void testIfThenElseBadOpcode()  {
        ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withMethod("foo", MethodTypeDesc.of(CD_int, CD_int, CD_int),
                    AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(),
                    mb -> mb.withCode(xb -> {
                        xb.iload(0);
                        xb.iload(1);
                        assertThrows(IllegalArgumentException.class, () -> {
                            xb.ifThenElse(
                                    Opcode.GOTO,
                                    xxb -> xxb.iconst_1().istore(2),
                                    xxb -> xxb.iconst_2().istore(2));
                        });
                        xb.iload(2);
                        xb.ireturn();
                    }));
        });
    }

    @Test
    void testAllocateLocal() {
        ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), ClassFile.ACC_STATIC,
                          mb -> mb.withCode(xb -> {
                              int slot1 = xb.allocateLocal(TypeKind.IntType);
                              int slot2 = xb.allocateLocal(TypeKind.LongType);
                              int slot3 = xb.allocateLocal(TypeKind.IntType);

                              assertEquals(slot1, 4);
                              assertEquals(slot2, 5);
                              assertEquals(slot3, 7);
                              xb.return_();
                          }));
        });
    }

    @Test
    void testAllocateLocalBlock() {
        ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), ClassFile.ACC_STATIC,
                          mb -> mb.withCode(xb -> {
                              xb.block(bb -> {
                                  int slot1 = bb.allocateLocal(TypeKind.IntType);
                                  int slot2 = bb.allocateLocal(TypeKind.LongType);
                                  int slot3 = bb.allocateLocal(TypeKind.IntType);

                                  assertEquals(slot1, 4);
                                  assertEquals(slot2, 5);
                                  assertEquals(slot3, 7);
                              });
                              int slot4 = xb.allocateLocal(TypeKind.IntType);
                              assertEquals(slot4, 4);
                              xb.return_();
                          }));
        });
    }

    @Test
    void testAllocateLocalIfThen() {
        ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), ClassFile.ACC_STATIC,
                          mb -> mb.withCode(xb -> {
                              xb.iconst_0();
                              xb.ifThenElse(bb -> {
                                                int slot1 = bb.allocateLocal(TypeKind.IntType);
                                                int slot2 = bb.allocateLocal(TypeKind.LongType);
                                                int slot3 = bb.allocateLocal(TypeKind.IntType);

                                                assertEquals(slot1, 4);
                                                assertEquals(slot2, 5);
                                                assertEquals(slot3, 7);
                                            },
                                            bb -> {
                                                int slot1 = bb.allocateLocal(TypeKind.IntType);

                                                assertEquals(slot1, 4);
                                            });
                              int slot4 = xb.allocateLocal(TypeKind.IntType);
                              assertEquals(slot4, 4);
                              xb.return_();
                          }));
        });
    }
}
