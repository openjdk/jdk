/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class SubWordReturnTest extends CustomizedBytecodePatternTest {

    private final JavaKind kind;
    private final int value;

    @Parameters(name = "{0}, {1}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int i : new int[]{1000000, 1000001, -1000000, -1}) {
            ret.add(new Object[]{JavaKind.Boolean, i});
            ret.add(new Object[]{JavaKind.Byte, i});
            ret.add(new Object[]{JavaKind.Short, i});
            ret.add(new Object[]{JavaKind.Char, i});
        }
        return ret;
    }

    public SubWordReturnTest(JavaKind kind, int value) {
        this.kind = kind;
        this.value = value;
    }

    @Test
    public void testSubWordReturn() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordReturnTest.class.getName() + "$" + kind.toString() + "Getter");
        ResolvedJavaMethod method = getResolvedJavaMethod(testClass, "testSnippet");
        test(method, null);
    }

    /**
     * {@link #generateClass} generates a class looking like this for the types boolean, byte,
     * short, and char.
     */
    static class ByteGetter {

        // private static int intField = 1000000;

        private static byte get() {
            // GETSTATIC intField
            // IRETURN
            return 0;
        }

        public static int testByteSnippet() {
            return get();
        }
    }

    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        FieldVisitor intField = cw.visitField(ACC_PRIVATE | ACC_STATIC, "intField", "I", null, value);
        intField.visitEnd();

        MethodVisitor get = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "get", "()" + kind.getTypeChar(), null, null);
        get.visitCode();
        get.visitFieldInsn(GETSTATIC, internalClassName, "intField", "I");
        get.visitInsn(IRETURN);
        get.visitMaxs(1, 0);
        get.visitEnd();

        MethodVisitor snippet = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "testSnippet", "()I", null, null);
        snippet.visitCode();
        snippet.visitMethodInsn(INVOKESTATIC, internalClassName, "get", "()" + kind.getTypeChar(), false);
        snippet.visitInsn(IRETURN);
        snippet.visitMaxs(1, 0);
        snippet.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

}
