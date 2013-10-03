/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.lang.reflect.InvocationTargetException;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * @test
 * @bug 8025260
 * @summary Ensure that AbstractMethodError is thrown, not NullPointerException, through MethodHandles::jump_from_method_handle code path
 *
 * @compile -XDignore.symbol.file ByteClassLoader.java I.java C.java TestAMEnotNPE.java
 * @run main/othervm TestAMEnotNPE
 */

public class TestAMEnotNPE implements Opcodes {

    /**
     * The bytes for D, a NOT abstract class extending abstract class C
     * without supplying an implementation for abstract method m.
     * There is a default method in the interface I, but it should lose to
     * the abstract class.

     class D extends C {
        D() { super(); }
        // does not define m
     }

     * @return
     * @throws Exception
     */
    public static byte[] bytesForD() throws Exception {

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        MethodVisitor mv;

        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "D", null, "C", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "C", "<init>", "()V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }


    /**
     * The bytecodes for an invokeExact of a particular methodHandle, I.m, invoked on a D

        class T {
           T() { super(); } // boring constructor
           int test() {
              MethodHandle mh = `I.m():int`;
              D d = new D();
              return mh.invokeExact(d); // Should explode here, AbstractMethodError
           }
        }

     * @return
     * @throws Exception
     */
    public static byte[] bytesForT() throws Exception {

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        MethodVisitor mv;

        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "T", null, "java/lang/Object", null);
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(0,0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "test", "()I", null, null);
            mv.visitCode();
            mv.visitLdcInsn(new Handle(Opcodes.H_INVOKEINTERFACE, "I", "m", "()I"));
            mv.visitTypeInsn(NEW, "D");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "D", "<init>", "()V");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", "(LI;)I");
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0,0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static void main(String args[] ) throws Throwable {
        ByteClassLoader bcl = new ByteClassLoader();
        Class<?> d = bcl.loadBytes("D", bytesForD());
        Class<?> t = bcl.loadBytes("T", bytesForT());
        try {
          Object result = t.getMethod("test").invoke(null);
          System.out.println("Expected AbstractMethodError wrapped in InvocationTargetException, saw no exception");
          throw new Error("Missing expected exception");
        } catch (InvocationTargetException e) {
            Throwable th = e.getCause();
            if (th instanceof AbstractMethodError) {
                th.printStackTrace(System.out);
                System.out.println("PASS, saw expected exception (AbstractMethodError, wrapped in InvocationTargetException).");
            } else {
                System.out.println("Expected AbstractMethodError wrapped in InvocationTargetException, saw " + th);
                throw th;
            }
        }
    }
}
