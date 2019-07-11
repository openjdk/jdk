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


package org.graalvm.compiler.replacements.test;

import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.graalvm.compiler.core.test.CustomizedBytecodePatternTest;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.test.SubprocessUtil.Subprocess;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class InvokerSignatureMismatchTest {

    @Test
    public void test() throws Throwable {
        List<String> args = withoutDebuggerArguments(getVMCommandLine());
        String classPath = System.getProperty("java.class.path");
        classPath = classPath + File.pathSeparator + TestISMBL.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        args.add("-Xbootclasspath/a:" + classPath);
        args.add("-XX:-TieredCompilation");
        args.add("-XX:+EnableJVMCI");
        args.add("-XX:+UseJVMCICompiler");

        args.add(TestISMBL.class.getName());
        Subprocess proc = SubprocessUtil.java(args);
        if (proc.exitCode != 0) {
            System.out.println(proc);
        }
    }
}

class TestISMBL extends CustomizedBytecodePatternTest {

    public static void main(String[] args) {
        try {
            new TestISMBL().test();
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private void test() throws Throwable {
        getClass("java/lang/invoke/MHHelper");
        Class<?> testClass = getClass("ISMTest");

        ResolvedJavaMethod mL = getResolvedJavaMethod(testClass, "mainLink");
        ResolvedJavaMethod mI = getResolvedJavaMethod(testClass, "mainInvoke");
        executeActual(mL, null, 100);
        executeActual(mI, null, 100);
    }

    @Override
    protected Class<?> getClass(String className) throws ClassNotFoundException {
        if (className.equals("java/lang/invoke/MHHelper")) {
            return super.getClassBL(className, MethodHandles.lookup());
        } else {
            return super.getClass(className);
        }
    }

    @Override
    protected byte[] generateClass(String className) {
        String[] exceptions = new String[]{"java/lang/Throwable"};
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, className, null, "java/lang/Object", null);

        if (className.equals("java/lang/invoke/MHHelper")) {
            MethodVisitor internalMemberName = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "internalMemberName", "(Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;", null, exceptions);
            internalMemberName.visitCode();
            internalMemberName.visitVarInsn(ALOAD, 0);
            internalMemberName.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "internalMemberName", "()Ljava/lang/invoke/MemberName;", false);
            internalMemberName.visitInsn(ARETURN);
            internalMemberName.visitMaxs(1, 1);
            internalMemberName.visitEnd();

            MethodVisitor linkToStatic = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "linkToStatic", "(FLjava/lang/Object;)I", null, exceptions);
            linkToStatic.visitCode();
            linkToStatic.visitVarInsn(FLOAD, 0);
            linkToStatic.visitVarInsn(ALOAD, 1);
            linkToStatic.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandle", "linkToStatic", "(FLjava/lang/Object;)I", false);
            linkToStatic.visitInsn(IRETURN);
            linkToStatic.visitMaxs(1, 1);
            linkToStatic.visitEnd();

            MethodVisitor invokeBasicI = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "invokeBasicI", "(Ljava/lang/invoke/MethodHandle;F)I", null, exceptions);
            invokeBasicI.visitCode();
            invokeBasicI.visitVarInsn(ALOAD, 0);
            invokeBasicI.visitVarInsn(FLOAD, 1);
            invokeBasicI.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeBasic", "(F)I", false);
            invokeBasicI.visitInsn(IRETURN);
            invokeBasicI.visitMaxs(1, 1);
            invokeBasicI.visitEnd();

        } else {
            assert className.equals("ISMTest") : className;
            cw.visitField(ACC_FINAL | ACC_STATIC, "INT_MH", "Ljava/lang/invoke/MethodHandle;", null, null).visitAnnotation("Ljava/lang/invoke/Stable.class;", true).visitEnd();
            MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, exceptions);
            clinit.visitCode();
            clinit.visitInsn(ACONST_NULL);
            clinit.visitVarInsn(ASTORE, 0);
            clinit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
            clinit.visitLdcInsn(Type.getObjectType(className));
            clinit.visitLdcInsn("bodyI");
            clinit.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
            clinit.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
            clinit.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
            clinit.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic",
                            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
            clinit.visitFieldInsn(PUTSTATIC, className, "INT_MH", "Ljava/lang/invoke/MethodHandle;");
            clinit.visitInsn(RETURN);
            clinit.visitMaxs(1, 1);
            clinit.visitEnd();

            MethodVisitor mainLink = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "mainLink", "(I)I", null, exceptions);
            mainLink.visitCode();
            mainLink.visitFieldInsn(GETSTATIC, className, "INT_MH", "Ljava/lang/invoke/MethodHandle;");
            mainLink.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MHHelper", "internalMemberName", "(Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;", false);
            mainLink.visitVarInsn(ASTORE, 1);
            mainLink.visitVarInsn(ILOAD, 0);
            mainLink.visitInsn(I2F);
            mainLink.visitVarInsn(ALOAD, 1);
            mainLink.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MHHelper", "linkToStatic", "(FLjava/lang/Object;)I", false);
            mainLink.visitInsn(IRETURN);
            mainLink.visitMaxs(1, 1);
            mainLink.visitEnd();

            MethodVisitor mainInvoke = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "mainInvoke", "(I)I", null, exceptions);
            mainInvoke.visitCode();
            mainInvoke.visitFieldInsn(GETSTATIC, className, "INT_MH", "Ljava/lang/invoke/MethodHandle;");
            mainInvoke.visitVarInsn(ILOAD, 0);
            mainInvoke.visitInsn(I2F);
            mainInvoke.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MHHelper", "invokeBasicI", "(Ljava/lang/invoke/MethodHandle;F)I", false);
            mainInvoke.visitInsn(IRETURN);
            mainInvoke.visitMaxs(1, 1);
            mainInvoke.visitEnd();

            MethodVisitor bodyI = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "bodyI", "(I)I", null, null);
            bodyI.visitCode();
            bodyI.visitVarInsn(ILOAD, 0);
            bodyI.visitIntInsn(SIPUSH, 1023);
            bodyI.visitInsn(IAND);
            bodyI.visitInsn(IRETURN);
            bodyI.visitMaxs(1, 1);
            bodyI.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }
}
