/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import jdk.vm.ci.meta.JavaKind;

@RunWith(Parameterized.class)
public class SubWordArrayStoreTest extends CustomizedBytecodePatternTest {

    @Parameterized.Parameters(name = "{0}, {1}, {2}, {3}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int i : new int[]{0xFFFF0000, 0xFFFF0001, 0x0000FFFF, 0x01020304}) {
            for (boolean unsafeStore : new boolean[]{false, true}) {
                for (boolean unsafeLoad : new boolean[]{false, true}) {
                    ret.add(new Object[]{JavaKind.Boolean, i, unsafeStore, unsafeLoad});
                    ret.add(new Object[]{JavaKind.Byte, i, unsafeStore, unsafeLoad});
                    ret.add(new Object[]{JavaKind.Short, i, unsafeStore, unsafeLoad});
                    ret.add(new Object[]{JavaKind.Char, i, unsafeStore, unsafeLoad});
                }
            }
        }
        return ret;
    }

    private static final String SNIPPET = "snippet";

    private final JavaKind kind;
    private final int value;
    private final boolean unsafeStore;
    private final boolean unsafeLoad;

    public SubWordArrayStoreTest(JavaKind kind, int value, boolean unsafeStore, boolean unsafeLoad) {
        this.kind = kind;
        this.value = value;
        this.unsafeStore = unsafeStore;
        this.unsafeLoad = unsafeLoad;
    }

    @Test
    public void testArrayStore() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordArrayStoreTest.class.getName() + "$" + kind.toString() + "Getter");
        test(getResolvedJavaMethod(testClass, SNIPPET), null);
    }

    private static long arrayBaseOffset(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return UNSAFE.arrayBaseOffset(boolean[].class);
            case Byte:
                return UNSAFE.arrayBaseOffset(byte[].class);
            case Short:
                return UNSAFE.arrayBaseOffset(short[].class);
            case Char:
                return UNSAFE.arrayBaseOffset(char[].class);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    static int toASMType(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return T_BOOLEAN;
            case Byte:
                return T_BYTE;
            case Short:
                return T_SHORT;
            case Char:
                return T_CHAR;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private static int toArrayStoreOpcode(JavaKind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
                return BASTORE;
            case Short:
                return SASTORE;
            case Char:
                return CASTORE;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private static int toArrayLoadOpcode(JavaKind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
                return BALOAD;
            case Short:
                return SALOAD;
            case Char:
                return CALOAD;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        final String fieldName = "array";
        final String fieldDescriptor = "[" + kind.getTypeChar();

        FieldVisitor field = cw.visitField(ACC_PUBLIC | ACC_STATIC, fieldName, fieldDescriptor, null, null);
        field.visitEnd();

        MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitIntInsn(BIPUSH, 16);
        clinit.visitIntInsn(NEWARRAY, toASMType(kind));
        clinit.visitFieldInsn(PUTSTATIC, internalClassName, fieldName, fieldDescriptor);
        clinit.visitInsn(RETURN);
        clinit.visitMaxs(1, 0);
        clinit.visitEnd();

        MethodVisitor snippet = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, SNIPPET, "()Z", null, null);
        snippet.visitCode();

        if (unsafeStore) {
            SubWordTestUtil.getUnsafe(snippet);
            snippet.visitFieldInsn(GETSTATIC, internalClassName, fieldName, fieldDescriptor);
            snippet.visitLdcInsn(arrayBaseOffset(kind));
            snippet.visitLdcInsn(value);
            snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "put" + SubWordTestUtil.getUnsafePutMethodName(kind), "(Ljava/lang/Object;J" + kind.getTypeChar() + ")V", false);
        } else {
            snippet.visitFieldInsn(GETSTATIC, internalClassName, fieldName, fieldDescriptor);
            snippet.visitInsn(ICONST_0);
            snippet.visitLdcInsn(value);
            snippet.visitInsn(toArrayStoreOpcode(kind));
        }

        if (unsafeLoad) {
            SubWordTestUtil.getUnsafe(snippet);
            snippet.visitFieldInsn(GETSTATIC, internalClassName, fieldName, fieldDescriptor);
            snippet.visitLdcInsn(arrayBaseOffset(kind));
            snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "get" + SubWordTestUtil.getUnsafePutMethodName(kind), "(Ljava/lang/Object;J)" + kind.getTypeChar(), false);
        } else {
            snippet.visitFieldInsn(GETSTATIC, internalClassName, fieldName, fieldDescriptor);
            snippet.visitInsn(ICONST_0);
            snippet.visitInsn(toArrayLoadOpcode(kind));
        }

        snippet.visitLdcInsn(value);
        SubWordTestUtil.convertToKind(snippet, kind);
        SubWordTestUtil.testEqual(snippet);

        snippet.visitMaxs(5, 0);
        snippet.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
