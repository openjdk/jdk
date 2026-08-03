/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8291360
 * @summary Test getting a class's raw access flags using java.lang.Class API
 * @modules java.base/java.lang:open
 * @compile classAccessFlagsRaw.jcod
 * @run main/othervm ClassAccessFlagsRawTest
 */

import java.lang.reflect.*;
import java.util.Set;

public class ClassAccessFlagsRawTest {

    static Method m;

    public static void testIt(String className, int expectedResult) throws Exception {
        Class<?> testClass = Class.forName(className);
        int flags = (int)m.invoke(testClass);
        if (flags != expectedResult) {
            throw new RuntimeException(
                "expected 0x" + Integer.toHexString(expectedResult) + ", got 0x" + Integer.toHexString(flags) + " for class " + className);
        }
    }

    public static void main(String argv[]) throws Throwable {
        Class<?> cl = java.lang.Class.class;
        m = cl.getDeclaredMethod("getClassFileAccessFlags", new Class[0]);
        m.setAccessible(true);

        testIt("SUPERset", 0x21);  // ACC_SUPER 0x20 + ACC_PUBLIC 0x1
        testIt("SUPERnotset", Modifier.PUBLIC);

        // Test that primitive should return ACC_ABSTRACT | ACC_FINAL | ACC_PUBLIC.
        int[] arr = new int[3];
        if (!arr.getClass().getComponentType().isPrimitive()) {
            throw new RuntimeException("not primitive");
        }
        int flags = (int)m.invoke(arr.getClass().getComponentType());
        if (flags != (Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC)) {
            throw new RuntimeException(
                "expected 0x411, got 0x" + Integer.toHexString(flags) + " for primitive type");
        }

        // Test that primitive array raw access flags return 0.
        flags = (int)m.invoke(arr.getClass());
        if (flags != 0) {
            throw new RuntimeException(
                "expected 0x0 got 0x" + Integer.toHexString(flags) + " for primitive array");
        }

        // Test that the modifier flags return element type flags.
        flags = (int)arr.getClass().getModifiers();
        if (flags != (Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC)) {
            throw new RuntimeException(
                "expected 0x411, got 0x" + Integer.toHexString(flags) + " for primitive type");
        }

        // Test that AccessFlags set will return element type access flags.
        Set<AccessFlag> aacc = arr.getClass().accessFlags();
        if (!aacc.containsAll(Set.of(AccessFlag.FINAL, AccessFlag.ABSTRACT, AccessFlag.PUBLIC))) {
            throw new RuntimeException(
                "AccessFlags should contain FINAL, ABSTRACT and PUBLIC for primitive type");
        }

        // Test object array.  Raw access flags are 0 for arrays.
        flags = (int)m.invoke((new SUPERnotset[2]).getClass());
        if (flags != 0) {
            throw new RuntimeException(
                "expected 0x0, got 0x" + Integer.toHexString(flags) + " for object array");
        }

        // Test object array component type.
        flags = (int)m.invoke((new SUPERnotset[2]).getClass().getComponentType());
        if (flags != Modifier.PUBLIC) {
            throw new RuntimeException(
                "expected 0x1, got 0x" + Integer.toHexString(flags) + " for object array");
        }
    }
}
