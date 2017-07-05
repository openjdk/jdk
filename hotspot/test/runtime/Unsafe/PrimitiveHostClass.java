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

import java.awt.Component;
import java.lang.reflect.Field;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.misc.Unsafe;

/*
 * @test PrimitiveHostClass
 * @bug 8140665
 * @summary Throws IllegalArgumentException if host class is a primitive class.
 * @library /testlibrary
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @compile -XDignore.symbol.file PrimitiveHostClass.java
 * @run main/othervm PrimitiveHostClass
 */

public class PrimitiveHostClass {

    static final Unsafe U;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            U = (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void testVMAnonymousClass(Class<?> hostClass) {

        // choose a class name in the same package as the host class
        String prefix = packageName(hostClass);
        if (prefix.length() > 0)
            prefix = prefix.replace('.', '/') + "/";
        String className = prefix + "Anon";

        // create the class
        String superName = "java/lang/Object";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                                         + ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                 className, null, superName, null);
        byte[] classBytes = cw.toByteArray();
        int cpPoolSize = constantPoolSize(classBytes);
        Class<?> anonClass =
            U.defineAnonymousClass(hostClass, classBytes, new Object[cpPoolSize]);
    }

    private static String packageName(Class<?> c) {
        if (c.isArray()) {
            return packageName(c.getComponentType());
        } else {
            String name = c.getName();
            int dot = name.lastIndexOf('.');
            if (dot == -1) return "";
            return name.substring(0, dot);
        }
    }

    private static int constantPoolSize(byte[] classFile) {
        return ((classFile[8] & 0xFF) << 8) | (classFile[9] & 0xFF);
    }

    public static void main(String args[]) {
        testVMAnonymousClass(PrimitiveHostClass.class);
        try {
            testVMAnonymousClass(int.class);
            throw new RuntimeException(
                "Expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}
