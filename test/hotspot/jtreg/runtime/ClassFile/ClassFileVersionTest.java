/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test getting the class file version for java.lang.Class API
 * @modules java.base/java.lang:open
 * @compile classFileVersions.jcod
 * @run main/othervm --enable-preview ClassFileVersionTest
 */

import java.lang.reflect.*;

public class ClassFileVersionTest {

    static Method m;

    public static void testIt(String className, int expectedResult) throws Exception {
        Class<?> testClass = Class.forName(className);
        int ver = (int)m.invoke(testClass);
        if (ver != expectedResult) {
            throw new RuntimeException(
                "expected " + expectedResult + ", got " + ver + " for class " + className);
        }
    }

    public static void main(String argv[]) throws Throwable {
        Class<?> cl = java.lang.Class.class;
        m = cl.getDeclaredMethod("getClassFileVersion", new Class[0]);
        m.setAccessible(true);

        testIt("Version64", 64);
        testIt("Version59", 59);
        // test minor version of 65535.
        testIt("Version64_65535", -65472);  // -65472 = 0xFFFF0040

        // test primitive array.  should return latest version.
        int ver = (int)m.invoke((new int[3]).getClass());
        if (ver != 64) {
            throw new RuntimeException("expected 64, got " + ver + " for primitive array");
        }

        // test object array.  should return class file version of component.
        ver = (int)m.invoke((new Version59[2]).getClass());
        if (ver != 59) {
            throw new RuntimeException("expected 59, got " + ver + " for object array");
        }
    }
}
