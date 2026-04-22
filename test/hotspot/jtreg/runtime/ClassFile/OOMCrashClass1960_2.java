/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/*
This is class is a dumper for the original OOMCrashClass1960_2.class, i.e. its dump() method
produces a byte array with the original class file data.

To get this source code, one needs to run the following command:
java jdk.internal.org.objectweb.asm.util.ASMifier OOMCrashClass1960_2.class >> OOMCrashClass1960_2.java

The resulting java source code is large (>2 mb), so certain refactoring is applied.
 */

public class OOMCrashClass1960_2 implements Opcodes {

    public static byte[] dump() throws Exception {

        ClassWriter classWriter = new ClassWriter(0);
        MethodVisitor methodVisitor;

        classWriter.visit(V1_1, ACC_PUBLIC | ACC_SUPER, "OOMCrashClass1960_2", null, "java/lang/Object", null);

        classWriter.visitSource("<generated>", null);

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "main0", "([Ljava/lang/String;)V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitInsn(AALOAD);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
            methodVisitor.visitVarInsn(ISTORE, 1);
            Label label1 = new Label();
            methodVisitor.visitJumpInsn(GOTO, label1);
            Label label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitInsn(RETURN);

            // This line overflows the jump target for jsr

            Label prevLabel = label2;
            for (int i = 0; i < 1959; ++i) {
                Label curLabel = getCurLabel(methodVisitor, prevLabel);
                prevLabel = curLabel;
            }

            Label label5877 = prevLabel;

            methodVisitor.visitLabel(label1);
            methodVisitor.visitVarInsn(ILOAD, 1);
            methodVisitor.visitJumpInsn(IFEQ, label5877);
            Label label5880 = new Label();
            methodVisitor.visitJumpInsn(JSR, label5880);
            Label label5881 = new Label();
            methodVisitor.visitJumpInsn(GOTO, label5881);
            methodVisitor.visitLabel(label5880);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitLabel(label5881);
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitJumpInsn(GOTO, label1);
            Label label5882 = new Label();
            methodVisitor.visitLabel(label5882);
            methodVisitor.visitLocalVariable("argv", "[Ljava/lang/String;", null, label0, label5882, 0);
            methodVisitor.visitMaxs(65535, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "LOOMCrashClass1960_2;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    public static Label getCurLabel(final MethodVisitor methodVisitor, final Label prevLabel) {
        final Label curLabel = new Label();
        methodVisitor.visitLabel(curLabel);
        methodVisitor.visitVarInsn(ILOAD, 1);
        methodVisitor.visitJumpInsn(IFEQ, prevLabel);
        Label tmpLabel1 = new Label();
        methodVisitor.visitJumpInsn(JSR, tmpLabel1);
        Label tmpLabel2 = new Label();
        methodVisitor.visitJumpInsn(GOTO, tmpLabel2);
        methodVisitor.visitLabel(tmpLabel1);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitLabel(tmpLabel2);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitJumpInsn(GOTO, curLabel);
        return curLabel;
    }
}
