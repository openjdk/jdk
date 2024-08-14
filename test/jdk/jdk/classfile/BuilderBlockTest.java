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
 * @bug 8337225
 * @summary Testing ClassFile builder blocks.
 * @run junit BuilderBlockTest
 */
import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;

import helpers.ByteArrayClassLoader;
import jdk.internal.classfile.impl.LabelImpl;
import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BuilderBlockTest
 */
class BuilderBlockTest {

    static final String testClassName = "BuilderBlockTest$TestClass";
    static final Path testClassPath = Path.of(URI.create(BuilderBlockTest.class.getResource(testClassName + ".class").toString()));

    @Test
    void testStartEnd() throws Exception {
        // Ensure that start=0 at top level, end is undefined until code is done, then end=1
        Label startEnd[] = new Label[2];

        byte[] bytes = ClassFile.of().build(ClassDesc.of("Foo"), cb -> {
            cb.withMethod("foo", MethodTypeDesc.of(CD_void), 0,
                          mb -> mb.withCode(xb -> {
                              startEnd[0] = xb.startLabel();
                              startEnd[1] = xb.endLabel();
                              xb.return_();
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
                              xb.nop();
                              xb.block(xxb -> {
                                  startEnd[2] = xxb.startLabel();
                                  startEnd[3] = xxb.endLabel();
                                  xxb.nop();
                              });
                              xb.return_();
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
                          ACC_PUBLIC | ACC_STATIC,
                          mb -> mb.withCode(xb -> xb.iload(0)
                                                    .ifThen(xxb -> xxb.iconst_1().ireturn())
                                                    .iconst_2()
                                                    .ireturn()));
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
                          ACC_PUBLIC | ACC_STATIC,
                          mb -> mb.withCode(xb -> xb.iload(0)
                                                    .ifThenElse(xxb -> xxb.iconst_1().ireturn(),
                                                                xxb -> xxb.iconst_2().ireturn())));
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
                    ACC_PUBLIC | ACC_STATIC,
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
                          ACC_PUBLIC | ACC_STATIC,
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
                    ACC_PUBLIC | ACC_STATIC,
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
                    ACC_PUBLIC | ACC_STATIC,
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
                    ACC_PUBLIC | ACC_STATIC,
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

    private static final CodeTransform ALLOCATE_LOCAL_EXAMINER = CodeTransform.ofStateful(() -> new CodeTransform() {
        boolean foundItem = false;

        @Override
        public void atStart(CodeBuilder builder) {
            foundItem = false;
        }

        @Override
        public void accept(CodeBuilder cob, CodeElement coe) {
            cob.with(coe);
            if (coe instanceof ConstantInstruction.LoadConstantInstruction ldc
                    && ldc.constantEntry() instanceof StringEntry se
                    && se.utf8().equalsString("Output")) {
                assertFalse(foundItem);
                foundItem = true;
                var i = cob.allocateLocal(TypeKind.IntType);
                assertEquals(7, i, "Allocated new int slot");
            }
        }

        @Override
        public void atEnd(CodeBuilder builder) {
            assertTrue(foundItem);
        }
    });

    // Test updating local variable slot management from
    // source code models in transformingCode;
    // CodeBuilder.transform(CodeModel, CodeTransform) is
    // not managed for now
    @Test
    void testAllocateLocalTransformingCodeAttribute() throws IOException {
        var cf = ClassFile.of();
        var code = cf.parse(testClassPath)
                .methods()
                .stream()
                .filter(f -> f.methodName().equalsString("work"))
                .findFirst()
                .orElseThrow()
                .findAttribute(Attributes.code())
                .orElseThrow();
        ClassFile.of().build(ClassDesc.of("Foo"), cb -> cb
                .withMethod("foo", MethodTypeDesc.ofDescriptor("(IJI)V"), 0, mb -> mb
                        .transformCode(code, ALLOCATE_LOCAL_EXAMINER)));
    }

    @Test
    void testAllocateLocalTransformingBufferedCode() throws IOException {
        var cf = ClassFile.of();
        var testClass = cf.parse(testClassPath);
        ClassTransform bufferingTransform = (clb, cle) -> {
            if (cle instanceof MethodModel mm && mm.methodName().equalsString("work")) {
                clb.withMethodBody(mm.methodName(), mm.methodType(), mm.flags().flagsMask(), cob -> {
                    int d = cob.allocateLocal(TypeKind.IntType);
                    int e = cob.allocateLocal(TypeKind.IntType);

                    assertEquals(5, d);
                    assertEquals(6, e);

                    mm.code().ifPresent(code -> code.forEach(cob));
                });
            }
        };
        cf.transformClass(testClass, bufferingTransform.andThen(ClassTransform.transformingMethods(MethodTransform.transformingCode(ALLOCATE_LOCAL_EXAMINER))));
    }

    public static class TestClass {
        public void work(int a, long b, int c) {
            int d = Math.addExact(a, 25);
            int e = Math.multiplyExact(d, c);
            System.out.println("Output");
            System.out.println(e + b);
            throw new IllegalArgumentException("foo");
        }
    }
}
