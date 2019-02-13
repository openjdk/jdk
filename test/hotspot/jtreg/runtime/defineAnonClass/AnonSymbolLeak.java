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

// This is copied from DefineAnon to test Symbol Refcounting for the package prepended name.

package p1;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.misc.Unsafe;


class T {
    static           protected void test0() { System.out.println("test0 (public)"); }
    static           protected void test1() { System.out.println("test1 (protected)"); }
    static /*package-private*/ void test2() { System.out.println("test2 (package)"); }
    static             private void test3() { System.out.println("test3 (private)"); }
}

public class AnonSymbolLeak {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

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

    public static void test() throws Throwable {
        // AnonClass is injected into package p1.
        System.out.println("Injecting from the same package (p1):");
        Class<?> p1cls = getAnonClass(T.class, "AnonClass");
        p1cls.getMethod("test").invoke(null);
    }

    public static void main(java.lang.String[] unused) throws Throwable {
        test();
    }
}
