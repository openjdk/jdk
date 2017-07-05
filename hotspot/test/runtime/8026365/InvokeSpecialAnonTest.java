/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8026365
 * @summary Test invokespecial of host class method from an anonymous class
 * @author  Robert Field
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @compile -XDignore.symbol.file InvokeSpecialAnonTest.java
 * @run main ClassFileInstaller InvokeSpecialAnonTest AnonTester
 * @run main/othervm -Xbootclasspath/a:. -Xverify:all InvokeSpecialAnonTest
 */
import jdk.internal.org.objectweb.asm.*;
import java.lang.reflect.Constructor;
import jdk.internal.misc.Unsafe;

public class InvokeSpecialAnonTest implements Opcodes {

    static byte[] anonClassBytes() throws Exception {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_8, ACC_FINAL + ACC_SUPER, "Anon", null, "java/lang/Object", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "m", "(LInvokeSpecialAnonTest;)I", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, "InvokeSpecialAnonTest", "privMethod", "()I");
            mv.visitInsn(IRETURN);
            mv.visitMaxs(2, 3);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }

    private int privMethod() { return 1234; }

    public static void main(String[] args) throws Exception {
        Class<?> klass = InvokeSpecialAnonTest.class;
        try {
           Class<?> result = AnonTester.defineTest(klass, anonClassBytes());
           System.out.println("Passed.");
        } catch (Exception e) {
           e.printStackTrace();
           throw e;
        }
    }
}


class AnonTester {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static Class<?> defineTest(Class<?> targetClass, byte[] classBytes) throws Exception {
        return UNSAFE.defineAnonymousClass(targetClass, classBytes, null);
    }
}
