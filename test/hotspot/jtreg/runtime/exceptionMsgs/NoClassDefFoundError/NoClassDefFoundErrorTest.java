/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8056900 8338888
 * @summary Verifies message returned with NoClassDefFoundError exception.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 * @run main/native/othervm -Xlog:exceptions=info NoClassDefFoundErrorTest
 */

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.internal.misc.Unsafe;

public class NoClassDefFoundErrorTest {

    // Use the specified name
    static native void callDefineClass(String className);
    static native void callFindClass(String className);
    // Use a name longer than a Java string - returns false
    // if native allocation failed.
    static native boolean tryCallDefineClass();
    static native boolean tryCallFindClass();

    static {
        System.loadLibrary("NoClassDefFoundErrorTest");
    }


    public static void main(String args[]) throws Exception {
        Unsafe unsafe = Unsafe.getUnsafe();

        byte klassbuf[] = InMemoryJavaCompiler.compile("TestClass", "class TestClass { }");

        // Create a class name of length 65536.
        StringBuilder tooBigClassName = new StringBuilder("z");
        for (int x = 0; x < 16; x++) {
            tooBigClassName = tooBigClassName.append(tooBigClassName);
        }

        System.out.println("Test JVM_DefineClass() with long name");
        try {
            unsafe.defineClass(tooBigClassName.toString(), klassbuf, 4, klassbuf.length - 4, null, null);
            throw new RuntimeException("defineClass did not throw expected NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            if (!e.getMessage().contains("Class name exceeds maximum length of ")) {
                throw new RuntimeException("Wrong NoClassDefFoundError: " + e.getMessage());
            }
        }

        System.out.println("Test JNI_DefineClass() with long name");
        try {
            callDefineClass(tooBigClassName.toString());
            throw new RuntimeException("DefineClass did not throw expected NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            if (!e.getMessage().contains("Class name exceeds maximum length of ")) {
                throw new RuntimeException("Wrong NoClassDefFoundError: " + e.getMessage());
            }
        }

        System.out.println("Test JNI_FindClass() with long name");
        try {
            callFindClass(tooBigClassName.toString());
            throw new RuntimeException("FindClass did not throw expected NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            if (!e.getMessage().contains("Class name exceeds maximum length of ")) {
                throw new RuntimeException("Wrong NoClassDefFoundError: " + e.getMessage());
            }
        }

        System.out.println("Test JNI_FindClass() with null name");
        try {
            callFindClass(null);
            throw new RuntimeException("FindClass did not throw expected NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            if (!e.getMessage().contains("No class name given")) {
                throw new RuntimeException("Wrong NoClassDefFoundError: " + e.getMessage());
            }
        }

        System.out.println("Test JNI_DefineClass() with giant name");
        try {
            if (tryCallDefineClass()) {
                throw new RuntimeException("DefineClass did not throw expected NoClassDefFoundError");
            } else {
                System.out.println("Test skipped due to native allocation failure");
            }
        } catch (NoClassDefFoundError e) {
            if (!e.getMessage().contains("Class name exceeds maximum length of ")) {
                throw new RuntimeException("Wrong NoClassDefFoundError: " + e.getMessage());
            }
        }

        System.out.println("Test JNI_FindClass() with giant name");
        try {
            if (tryCallFindClass()) {
                throw new RuntimeException("FindClass did not throw expected NoClassDefFoundError");
            } else {
                System.out.println("Test skipped due to native allocation failure");
            }
        } catch (NoClassDefFoundError e) {
            if (!e.getMessage().contains("Class name exceeds maximum length of ")) {
                throw new RuntimeException("Wrong NoClassDefFoundError: " + e.getMessage());
            }
        }
    }
}
