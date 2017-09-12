/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test DefineAnon
 * @bug 8058575
 * @library /testlibrary
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.management
 * @compile -XDignore.symbol.file=true DefineAnon.java
 * @run main/othervm p1.DefineAnon
 */

package p1;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import sun.misc.Unsafe;


class T {
    static           protected void test0() { System.out.println("test0 (public)"); }
    static           protected void test1() { System.out.println("test1 (protected)"); }
    static /*package-private*/ void test2() { System.out.println("test2 (package)"); }
    static             private void test3() { System.out.println("test3 (private)"); }
}

public class DefineAnon {

    private static Unsafe getUnsafe() {
        try {
            java.lang.reflect.Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);
            return (Unsafe) singleoneInstanceField.get(null);
        } catch (Throwable ex) {
            throw new RuntimeException("Was unable to get Unsafe instance.");
        }
    }

    static Unsafe UNSAFE = DefineAnon.getUnsafe();

    static Class<?> getAnonClass(Class<?> hostClass, final String className) {
        final String superName = "java/lang/Object";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER, className, null, superName, null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "p1/T", "test0", "()V", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "p1/T", "test1", "()V", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "p1/T", "test2", "()V", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "p1/T", "test3", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        final byte[] classBytes = cw.toByteArray();
        Class<?> invokerClass = UNSAFE.defineAnonymousClass(hostClass, classBytes, new Object[0]);
        UNSAFE.ensureClassInitialized(invokerClass);
        return invokerClass;
    }

    public static void main(String[] args) throws Throwable {
        Throwable fail = null;

        // Anonymous class has the privileges of its host class, so test[0123] should all work.
        System.out.println("Injecting from the same package (p1):");
        Class<?> p1cls = getAnonClass(T.class, "p1/AnonClass");
        try {
            p1cls.getMethod("test").invoke(null);
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail = ex;  // throw this to make test fail, since subtest failed
        }

        // Anonymous class has different package name from host class.  Should throw
        // IllegalArgumentException.
        System.out.println("Injecting from the wrong package (p2):");
        try {
            Class<?> p2cls = getAnonClass(DefineAnon.class, "p2/AnonClass");
            p2cls.getMethod("test").invoke(null);
            System.out.println("Failed, did not get expected IllegalArgumentException");
        } catch (java.lang.IllegalArgumentException e) {
            if (e.getMessage().contains("Host class p1/DefineAnon and anonymous class p2/AnonClass")) {
                System.out.println("Got expected IllegalArgumentException: " + e.getMessage());
            } else {
                throw new RuntimeException("Unexpected message: " + e.getMessage());
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail = ex;  // throw this to make test fail, since subtest failed
        }

        // Inject a class in the unnamed package into p1.T.  It should be able
        // to access all methods in p1.T.
        System.out.println("Injecting unnamed package into correct host class:");
        try {
            Class<?> p3cls = getAnonClass(T.class, "AnonClass");
            p3cls.getMethod("test").invoke(null);
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail = ex;  // throw this to make test fail, since subtest failed
        }

        // Try using an array class as the host class.  This should throw IllegalArgumentException.
        try {
            Class<?> p3cls = getAnonClass(String[].class, "AnonClass");
            throw new RuntimeException("Expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException ex) {
        }

        if (fail != null) throw fail;  // make test fail, since subtest failed
    }
}
