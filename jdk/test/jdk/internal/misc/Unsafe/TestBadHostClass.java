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
 * @test
 * @bug 8058575
 * @summary Test that bad host classes cause exceptions to get thrown.
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.org.objectweb.asm
 * @run main TestBadHostClass
 */


import java.lang.*;
import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;
import jdk.internal.org.objectweb.asm.ClassWriter;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

// Test that bad host classes cause exceptions.
public class TestBadHostClass {

    private static final Unsafe unsafe = Unsafe.getUnsafe();

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

    static public void badHostClass(Class<?> hostClass) {
        // choose a class name in the same package as the host class
        String className;
        if (hostClass != null) {
            String prefix = packageName(hostClass);
            if (prefix.length() > 0)
                prefix = prefix.replace('.', '/') + "/";
            className = prefix + "Anon";
        } else {
           className = "Anon";
        }

        // create the class
        String superName = "java/lang/Object";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                                         + ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                 className, null, superName, null);
        byte[] classBytes = cw.toByteArray();
        int cpPoolSize = constantPoolSize(classBytes);
        Class<?> anonClass
            = unsafe.defineAnonymousClass(hostClass, classBytes, new Object[cpPoolSize]);
    }

    public static void main(String args[]) throws Exception {
        // host class is an array of java.lang.Objects.
        try {
            badHostClass(Object[].class);
        } catch (IllegalArgumentException ex) {
        }

        // host class is an array of objects of this class.
        try {
            badHostClass(TestBadHostClass[].class);
        } catch (IllegalArgumentException ex) {
        }

        // host class is null.
        try {
            badHostClass(null);
        } catch (NullPointerException ex) {
        }

        // host class is a primitive array class.
        try {
            badHostClass(int[].class);
        } catch (IllegalArgumentException ex) {
        }
    }
}
