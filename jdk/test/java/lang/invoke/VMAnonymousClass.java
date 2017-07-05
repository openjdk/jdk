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

/* @test
 * @bug 8046903
 * @summary VM anonymous class members can't be statically invocable
 * @run junit test.java.lang.invoke.VMAnonymousClass
 */
package test.java.lang.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import org.junit.Test;
import sun.misc.Unsafe;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class VMAnonymousClass {
    public static void main(String[] args) throws Throwable {
        VMAnonymousClass test = new VMAnonymousClass();
        test.testJavaLang();
        test.testJavaUtil();
        test.testSunMisc();
        test.testJavaLangInvoke();
        System.out.println("TEST PASSED");
    }

    // Test VM anonymous classes from different packages
    // (see j.l.i.InvokerBytecodeGenerator::isStaticallyInvocable).
    @Test public void testJavaLang()       throws Throwable { test("java/lang");        }
    @Test public void testJavaUtil()       throws Throwable { test("java/util");        }
    @Test public void testSunMisc()        throws Throwable { test("sun/misc");         }
    @Test public void testJavaLangInvoke() throws Throwable { test("java/lang/invoke"); }

    private static Unsafe unsafe = getUnsafe();

    private static void test(String pkg) throws Throwable {
        byte[] bytes = dumpClass(pkg);
        // Define VM anonymous class in privileged context (on BCP).
        Class anonClass = unsafe.defineAnonymousClass(Object.class, bytes, null);

        MethodType t = MethodType.methodType(Object.class, int.class);
        MethodHandle target = MethodHandles.lookup().findStatic(anonClass, "get", t);

        // Wrap target into LF (convert) to get "target" referenced from LF
        MethodHandle wrappedMH = target.asType(MethodType.methodType(Object.class, Integer.class));

        // Invoke enough times to provoke LF compilation to bytecode.
        for (int i = 0; i<100; i++) {
            Object r = wrappedMH.invokeExact((Integer)1);
        }
    }

    /*
     * Constructs bytecode for the following class:
     * public class pkg.MyClass {
     *     MyClass() {}
     *     public Object get(int i) { return null; }
     * }
     */
    public static byte[] dumpClass(String pkg) {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(52, ACC_SUPER | ACC_PUBLIC, pkg+"/MyClass", null, "java/lang/Object", null);
        {
            mv = cw.visitMethod(0, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "get", "(I)Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitInsn(ACONST_NULL);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static synchronized Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Unable to get Unsafe instance.", e);
        }
    }
}
