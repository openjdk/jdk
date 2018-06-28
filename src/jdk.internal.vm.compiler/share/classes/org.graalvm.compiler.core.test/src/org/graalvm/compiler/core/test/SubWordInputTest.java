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
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class SubWordInputTest extends CustomizedBytecodePatternTest {

    @Parameterized.Parameters(name = "{0}, {1}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int i : new int[]{0xFFFF0000, 0xFFFF0001, 0x0000FFFF}) {
            ret.add(new Object[]{JavaKind.Boolean, i});
            ret.add(new Object[]{JavaKind.Byte, i});
            ret.add(new Object[]{JavaKind.Short, i});
            ret.add(new Object[]{JavaKind.Char, i});
        }
        return ret;
    }

    private static final String GET = "get";
    private static final String WRAPPER = "wrapper";

    private final JavaKind kind;
    private final int value;

    public SubWordInputTest(JavaKind kind, int value) {
        this.kind = kind;
        this.value = value;
    }

    @Test
    public void testSubWordInput() throws ClassNotFoundException {
        Class<?> testClass = getClass(SubWordInputTest.class.getName() + "$" + kind.toString() + "Getter");
        ResolvedJavaMethod wrapper = getResolvedJavaMethod(testClass, WRAPPER);
        Result expected = executeExpected(wrapper, null, value);
        // test standalone callee
        getCode(getResolvedJavaMethod(testClass, GET), null, false, true, getInitialOptions());
        assertEquals(executeExpected(wrapper, null, value), expected);
        // test with inlining
        testAgainstExpected(wrapper, expected, Collections.<DeoptimizationReason> emptySet(), null, value);
    }

    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        final char typeChar = kind.getTypeChar();

        // Generates a method that returns the input subword immediately.
        String getDescriptor = "(" + typeChar + ")" + typeChar;
        MethodVisitor get = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, GET, getDescriptor, null, null);
        get.visitCode();
        get.visitVarInsn(ILOAD, 0);
        get.visitInsn(IRETURN);
        get.visitMaxs(1, 1);
        get.visitEnd();

        // Genearates a method that compares the return value of the preceding method by passing the
        // input value, and a manual masking of the input value.
        MethodVisitor snippet = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, WRAPPER, "(I)Z", null, null);
        snippet.visitCode();
        snippet.visitVarInsn(ILOAD, 0);
        snippet.visitMethodInsn(INVOKESTATIC, internalClassName, GET, getDescriptor, false);

        snippet.visitVarInsn(ILOAD, 0);
        SubWordTestUtil.convertToKind(snippet, kind);
        SubWordTestUtil.testEqual(snippet);

        snippet.visitMaxs(2, 1);
        snippet.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
