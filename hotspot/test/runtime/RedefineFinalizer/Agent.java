/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassDefinition;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public class Agent implements Opcodes {

    private static byte[] makeNewMartyr() {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "Martyr", null, "java/lang/Object", null);
        cw.visitSource(null, null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            Label lab0 = new Label();
            mv.visitLabel(lab0);
            mv.visitLineNumber(1, lab0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        {
            mv = cw.visitMethod(ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null);
            mv.visitCode();
            Label lab0 = new Label();
            mv.visitLabel(lab0);
            mv.visitLineNumber(6, lab0);
            mv.visitLdcInsn("Redefinition done");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        {
            mv = cw.visitMethod(ACC_PROTECTED, "finalize", "()V", null, null);
            mv.visitCode();
            Label lab0 = new Label();
            mv.visitLabel(lab0);
            mv.visitLineNumber(8, lab0);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("Finalizer called");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }


    public static void premain(String args, Instrumentation inst) throws Exception {
        agentmain(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) throws Exception {
        ClassDefinition martyrDef =
              new ClassDefinition(Class.forName("Martyr"), makeNewMartyr());
        inst.redefineClasses(martyrDef);
    }
}
