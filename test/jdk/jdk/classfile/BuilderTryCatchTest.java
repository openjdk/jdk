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
 * @run junit BuilderTryCatchTest
 */

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.constant.ConstantDescs.CD_Double;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;

class BuilderTryCatchTest {

    static final ClassDesc CD_IOOBE = IndexOutOfBoundsException.class.describeConstable().get();
    static final ClassDesc CD_NPE = NullPointerException.class.describeConstable().get();
    static final MethodTypeDesc MTD_String = MethodType.methodType(String.class).describeConstable().get();

    @Test
    void testTryCatchCatchAll() throws Throwable {
        byte[] bytes = generateTryCatchMethod(catchBuilder -> {
            catchBuilder.catching(CD_IOOBE, tb -> {
                tb.pop();

                tb.constantInstruction(Opcode.LDC, "IndexOutOfBoundsException");
                tb.returnInstruction(TypeKind.ReferenceType);
            }).catchingAll(tb -> {
                tb.pop();

                tb.constantInstruction(Opcode.LDC, "any");
                tb.returnInstruction(TypeKind.ReferenceType);
            });
        });

        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(String.class, String[].class));

        assertEquals(main.invoke(new String[]{"BODY"}), "BODY");
        assertEquals(main.invoke(new String[]{}), "IndexOutOfBoundsException");
        assertEquals(main.invoke(null), "any");
    }

    @Test
    void testTryCatchCatchAllReachable() throws Throwable {
        byte[] bytes = generateTryCatchMethod(catchBuilder -> {
            catchBuilder.catching(CD_IOOBE, tb -> {
                tb.pop();

                tb.constantInstruction(Opcode.LDC, "IndexOutOfBoundsException");
                tb.astore(1);
            }).catchingAll(tb -> {
                tb.pop();

                tb.constantInstruction(Opcode.LDC, "any");
                tb.astore(1);
            });
        });

        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(String.class, String[].class));

        assertEquals(main.invoke(new String[]{"BODY"}), "BODY");
        assertEquals(main.invoke(new String[]{}), "IndexOutOfBoundsException");
        assertEquals(main.invoke(null), "any");
    }

    @Test
    void testTryMutliCatchReachable() throws Throwable {
        byte[] bytes = generateTryCatchMethod(catchBuilder ->
            catchBuilder.catchingMulti(List.of(CD_IOOBE, CD_NPE), tb -> {
                tb.invokevirtual(CD_Object, "toString", MTD_String);
                tb.astore(1);
            }));

        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(String.class, String[].class));

        assertTrue(main.invoke(new String[]{}).toString().contains("IndexOutOfBoundsException"));
        assertTrue(main.invoke(null).toString().contains("NullPointerException"));
    }

    @Test
    void testTryCatch() throws Throwable {
        byte[] bytes = generateTryCatchMethod(catchBuilder -> {
            catchBuilder.catching(CD_IOOBE, tb -> {
                tb.pop();

                tb.constantInstruction(Opcode.LDC, "IndexOutOfBoundsException");
                tb.returnInstruction(TypeKind.ReferenceType);
            });
        });

        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(String.class, String[].class));

        assertEquals(main.invoke(new String[]{"BODY"}), "BODY");
        assertEquals(main.invoke(new String[]{}), "IndexOutOfBoundsException");
        assertThrows(NullPointerException.class,
                () -> main.invoke(null));
    }

    @Test
    void testTryCatchAll() throws Throwable {
        byte[] bytes = generateTryCatchMethod(catchBuilder -> {
            catchBuilder.catchingAll(tb -> {
                tb.pop();

                tb.constantInstruction(Opcode.LDC, "any");
                tb.returnInstruction(TypeKind.ReferenceType);
            });
        });

        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(String.class, String[].class));

        assertEquals(main.invoke(new String[]{"BODY"}), "BODY");
        assertEquals(main.invoke(new String[]{}), "any");
        assertEquals(main.invoke(null), "any");
    }

    @Test
    void testTryEmptyCatch() {
        byte[] bytes = generateTryCatchMethod(catchBuilder -> {});

        boolean anyGotos = ClassFile.of().parse(bytes).methods().stream()
                .flatMap(mm -> mm.code().stream())
                .flatMap(CompoundElement::elementStream)
                .anyMatch(codeElement ->
                        (codeElement instanceof BranchInstruction bi && bi.opcode() == Opcode.GOTO) ||
                                (codeElement instanceof ExceptionCatch));
        assertFalse(anyGotos);
    }

    @Test
    void testEmptyTry() {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("C"), cb -> {
            cb.withMethod("main", MethodTypeDesc.of(CD_String, CD_String.arrayType()),
                    AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(), mb -> {
                        mb.withCode(xb -> {
                            int stringSlot = xb.allocateLocal(TypeKind.ReferenceType);
                            xb.constantInstruction("S");
                            xb.astore(stringSlot);

                            assertThrows(IllegalArgumentException.class, () -> {
                                xb.trying(tb -> {
                                }, catchBuilder -> {
                                    fail();

                                    catchBuilder.catchingAll(tb -> {
                                        tb.pop();

                                        tb.constantInstruction(Opcode.LDC, "any");
                                        tb.returnInstruction(TypeKind.ReferenceType);
                                    });
                                });
                            });

                            xb.aload(stringSlot);
                            xb.returnInstruction(TypeKind.ReferenceType);
                        });
                    });
        });
    }

    @Test
    void testLocalAllocation() throws Throwable {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("C"), cb -> {
            cb.withMethod("main", MethodTypeDesc.of(CD_String, CD_String.arrayType()),
                    AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(), mb -> {
                        mb.withCode(xb -> {
                            int stringSlot = xb.allocateLocal(TypeKind.ReferenceType);
                            xb.constantInstruction("S");
                            xb.astore(stringSlot);

                            xb.trying(tb -> {
                                int intSlot = tb.allocateLocal(TypeKind.IntType);

                                tb.aload(0);
                                tb.constantInstruction(0);
                                // IndexOutOfBoundsException
                                tb.aaload();
                                // NullPointerException
                                tb.invokevirtual(CD_String, "length", MethodType.methodType(int.class).describeConstable().get());
                                tb.istore(intSlot);

                                tb.iload(intSlot);
                                tb.invokestatic(CD_Integer, "toString", MethodType.methodType(String.class, int.class).describeConstable().get());
                                tb.astore(stringSlot);
                            }, catchBuilder -> {
                                catchBuilder.catching(CD_IOOBE, tb -> {
                                    tb.pop();

                                    int doubleSlot = tb.allocateLocal(TypeKind.DoubleType);
                                    tb.constantInstruction(Math.PI);
                                    tb.dstore(doubleSlot);

                                    tb.dload(doubleSlot);
                                    tb.invokestatic(CD_Double, "toString", MethodType.methodType(String.class, double.class).describeConstable().get());
                                    tb.astore(stringSlot);
                                }).catchingAll(tb -> {
                                    tb.pop();

                                    int refSlot = tb.allocateLocal(TypeKind.ReferenceType);
                                    tb.constantInstruction("REF");
                                    tb.astore(refSlot);

                                    tb.aload(refSlot);
                                    tb.invokevirtual(CD_String, "toString", MTD_String);
                                    tb.astore(stringSlot);
                                });
                            });

                            xb.aload(stringSlot);
                            xb.returnInstruction(TypeKind.ReferenceType);
                        });
                    });
        });

        Files.write(Path.of("x.class"), bytes);
        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(String.class, String[].class));

        assertEquals(main.invoke(new String[]{"BODY"}), Integer.toString(4));
        assertEquals(main.invoke(new String[]{}), Double.toString(Math.PI));
        assertEquals(main.invoke(null), "REF");
    }

    static byte[] generateTryCatchMethod(Consumer<CodeBuilder.CatchBuilder> c) {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("C"), cb -> {
            cb.withMethod("main", MethodTypeDesc.of(CD_String, CD_String.arrayType()),
                    AccessFlags.ofMethod(AccessFlag.PUBLIC, AccessFlag.STATIC).flagsMask(), mb -> {
                        mb.withCode(xb -> {
                            int stringSlot = xb.allocateLocal(TypeKind.ReferenceType);
                            xb.constantInstruction("S");
                            xb.astore(stringSlot);

                            xb.trying(tb -> {
                                tb.aload(0);
                                tb.constantInstruction(0);
                                // IndexOutOfBoundsException
                                tb.aaload();
                                // NullPointerException
                                tb.invokevirtual(CD_String, "toString", MTD_String);
                                tb.astore(stringSlot);
                            }, c);

                            xb.aload(stringSlot);
                            xb.returnInstruction(TypeKind.ReferenceType);
                        });
                    });
        });

        return bytes;
    }
}