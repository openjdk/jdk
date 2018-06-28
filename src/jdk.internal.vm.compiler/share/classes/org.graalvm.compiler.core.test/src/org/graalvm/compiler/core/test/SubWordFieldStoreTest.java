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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import jdk.vm.ci.meta.JavaKind;

@RunWith(Parameterized.class)
public class SubWordFieldStoreTest extends CustomizedBytecodePatternTest {

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

    public SubWordFieldStoreTest(JavaKind kind, int value, boolean unsafeStore, boolean unsafeLoad) {
        this.kind = kind;
        this.value = value;
        this.unsafeStore = unsafeStore;
        this.unsafeLoad = unsafeLoad;
    }

    @Test
    public void testFieldStore() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordFieldStoreTest.class.getName() + "$" + kind.toString() + "Getter");
        test(getResolvedJavaMethod(testClass, SNIPPET), null);
    }

    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        final String fieldName = "field";
        final String fieldDescriptor = Character.toString(kind.getTypeChar());

        FieldVisitor field = cw.visitField(ACC_PUBLIC | ACC_STATIC, fieldName, fieldDescriptor, null, value);
        field.visitEnd();

        MethodVisitor snippet = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, SNIPPET, "()Z", null, new String[]{"java/lang/NoSuchFieldException"});
        snippet.visitCode();

        if (unsafeStore) {
            snippet.visitLdcInsn(Type.getObjectType(internalClassName));
            snippet.visitLdcInsn(fieldName);
            snippet.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
            snippet.visitVarInsn(ASTORE, 0);

            SubWordTestUtil.getUnsafe(snippet);
            snippet.visitVarInsn(ALOAD, 0);
            snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "staticFieldBase", "(Ljava/lang/reflect/Field;)Ljava/lang/Object;", false);
            snippet.visitVarInsn(ASTORE, 1);

            SubWordTestUtil.getUnsafe(snippet);
            snippet.visitVarInsn(ALOAD, 0);
            snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "staticFieldOffset", "(Ljava/lang/reflect/Field;)J", false);
            snippet.visitVarInsn(LSTORE, 2);

            SubWordTestUtil.getUnsafe(snippet);
            snippet.visitVarInsn(ALOAD, 1);
            snippet.visitVarInsn(LLOAD, 2);
            snippet.visitLdcInsn(value);
            snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "put" + SubWordTestUtil.getUnsafePutMethodName(kind), "(Ljava/lang/Object;J" + kind.getTypeChar() + ")V", false);
        } else {
            snippet.visitLdcInsn(value);
            snippet.visitFieldInsn(PUTSTATIC, internalClassName, fieldName, fieldDescriptor);
        }

        if (unsafeLoad) {
            if (!unsafeStore) {
                snippet.visitLdcInsn(Type.getObjectType(internalClassName));
                snippet.visitLdcInsn(fieldName);
                snippet.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
                snippet.visitVarInsn(ASTORE, 0);

                SubWordTestUtil.getUnsafe(snippet);
                snippet.visitVarInsn(ALOAD, 0);
                snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "staticFieldBase", "(Ljava/lang/reflect/Field;)Ljava/lang/Object;", false);
                snippet.visitVarInsn(ASTORE, 1);

                SubWordTestUtil.getUnsafe(snippet);
                snippet.visitVarInsn(ALOAD, 0);
                snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "staticFieldOffset", "(Ljava/lang/reflect/Field;)J", false);
                snippet.visitVarInsn(LSTORE, 2);
            }
            SubWordTestUtil.getUnsafe(snippet);
            snippet.visitVarInsn(ALOAD, 1);
            snippet.visitVarInsn(LLOAD, 2);
            snippet.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "get" + SubWordTestUtil.getUnsafePutMethodName(kind), "(Ljava/lang/Object;J)" + kind.getTypeChar(), false);
        } else {
            snippet.visitFieldInsn(GETSTATIC, internalClassName, fieldName, fieldDescriptor);
        }

        snippet.visitLdcInsn(value);
        SubWordTestUtil.convertToKind(snippet, kind);
        SubWordTestUtil.testEqual(snippet);

        snippet.visitMaxs(5, 4);
        snippet.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
