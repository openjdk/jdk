/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8058828
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @run main/bootclasspath -Xbatch VMAnonymousClasses
 */

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import sun.misc.Unsafe;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;

public class VMAnonymousClasses {
    static final String TEST_METHOD_NAME = "constant";

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static int getConstantPoolSize(byte[] classFile) {
        // The first few bytes:
        // u4 magic;
        // u2 minor_version;
        // u2 major_version;
        // u2 constant_pool_count;
        return ((classFile[8] & 0xFF) << 8) | (classFile[9] & 0xFF);
    }

    static void test(Object value) throws ReflectiveOperationException {
        System.out.printf("Test: %s", value != null ? value.getClass() : "null");

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "Test", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, TEST_METHOD_NAME, "()Ljava/lang/Object;", null, null);

        String placeholder = "CONSTANT";
        int index = cw.newConst(placeholder);
        mv.visitLdcInsn(placeholder);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        byte[] classFile = cw.toByteArray();

        Object[] cpPatches = new Object[getConstantPoolSize(classFile)];
        cpPatches[index] = value;

        Class<?> test = UNSAFE.defineAnonymousClass(VMAnonymousClasses.class, classFile, cpPatches);

        Object expectedResult = (value != null) ? value : placeholder;
        for (int i = 0; i<15000; i++) {
            Object result = test.getMethod(TEST_METHOD_NAME).invoke(null);
            if (result != expectedResult) {
                throw new AssertionError(String.format("Wrong value returned: %s != %s", value, result));
            }
        }
        System.out.println(" PASSED");
    }

    public static void main(String[] args) throws ReflectiveOperationException  {
        // Objects
        test(new Object());
        test("TEST");
        test(new VMAnonymousClasses());
        test(null);

        // Class
        test(String.class);

        // Arrays
        test(new boolean[0]);
        test(new byte[0]);
        test(new char[0]);
        test(new short[0]);
        test(new int[0]);
        test(new long[0]);
        test(new float[0]);
        test(new double[0]);
        test(new Object[0]);

        // Multi-dimensional arrays
        test(new byte[0][0]);
        test(new Object[0][0]);

        // MethodHandle-related
        MethodType   mt = MethodType.methodType(void.class, String[].class);
        MethodHandle mh = MethodHandles.lookup().findStatic(VMAnonymousClasses.class, "main", mt);
        test(mt);
        test(mh);
        test(new ConstantCallSite(mh));
        test(new MutableCallSite(MethodType.methodType(void.class)));
        test(new VolatileCallSite(MethodType.methodType(void.class)));

        System.out.println("TEST PASSED");
    }
}
