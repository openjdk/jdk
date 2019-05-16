/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TwoSlotMarkerClearingTest extends CustomizedBytecodePatternTest {

    @Test
    public void testTwoSlotMarkerClearing() throws ClassNotFoundException {
        Class<?> testClass = getClass("Test");
        ResolvedJavaMethod t1 = getResolvedJavaMethod(testClass, "t1");
        parseForCompile(t1);
        ResolvedJavaMethod t2 = getResolvedJavaMethod(testClass, "t2");
        parseForCompile(t2);
    }

    @Override
    protected byte[] generateClass(String className) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, className, null, "java/lang/Object", null);

        String getDescriptor = "(" + "JII" + ")" + "I";
        MethodVisitor t1 = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "t1", getDescriptor, null, null);
        t1.visitCode();
        t1.visitVarInsn(ILOAD, 2);
        t1.visitVarInsn(ISTORE, 0);
        t1.visitVarInsn(ILOAD, 0);
        Label label = new Label();
        t1.visitJumpInsn(IFGE, label);
        t1.visitVarInsn(ILOAD, 0);
        t1.visitInsn(IRETURN);
        t1.visitLabel(label);
        t1.visitVarInsn(ILOAD, 3);
        t1.visitInsn(IRETURN);
        t1.visitMaxs(4, 1);
        t1.visitEnd();

        getDescriptor = "(" + "IJIJ" + ")" + "J";
        MethodVisitor t2 = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "t2", getDescriptor, null, null);
        t2.visitCode();
        t2.visitVarInsn(LLOAD, 1);
        t2.visitVarInsn(LSTORE, 0);
        t2.visitVarInsn(ILOAD, 3);
        Label label1 = new Label();
        t2.visitJumpInsn(IFGE, label1);
        t2.visitVarInsn(LLOAD, 0);
        t2.visitInsn(LRETURN);
        t2.visitLabel(label1);
        t2.visitVarInsn(LLOAD, 4);
        t2.visitInsn(LRETURN);
        t2.visitMaxs(6, 2);
        t2.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
