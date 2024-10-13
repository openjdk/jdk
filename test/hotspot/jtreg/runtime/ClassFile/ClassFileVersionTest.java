/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @compile --enable-preview -source ${jdk.version} ClassFileVersionTest.java
 * @run main/othervm --enable-preview ClassFileVersionTest
 */

import java.lang.reflect.*;

public class ClassFileVersionTest {
    public static final int LOWER_16 = 0x0000_FFFF;
    /*
     * Include a use of a preview API so that the minor class file
     * version of the class file for this class gets set during
     * compilation. If a particular class becomes non-preview, any
     * currently preview class can be substituted in.
     */
    private static final Class<?> PREVIEW_API = java.lang.ScopedValue.class;
    static Method m;

    public static void testIt(String className, int expectedResult) throws Exception {
        testIt(Class.forName(className), expectedResult);
    }

    public static void testIt(Class<?> testClass, int expectedResult) throws Exception {
        int ver = (int)m.invoke(testClass);
        if (ver != expectedResult) {
            int exp_minor = (expectedResult >> 16) & LOWER_16;
            int exp_major = expectedResult & LOWER_16;
            int got_minor = (ver >> 16) & LOWER_16;
            int got_major = ver & LOWER_16;
            throw new RuntimeException(
                "Expected " + exp_minor + ":" + exp_major + " but got " + got_minor + ":" + got_major);
        }
    }

    public static void main(String argv[]) throws Throwable {
        Class<?> cl = java.lang.Class.class;
        m = cl.getDeclaredMethod("getClassFileVersion", new Class[0]);
        m.setAccessible(true);

        int latestMajor = ClassFileFormatVersion.latest().major();

        testIt(Object.class, latestMajor);
        // ClassFileVersionTest use preview features so its minor version should be 0xFFFF
        testIt(ClassFileVersionTest.class, (~LOWER_16) | latestMajor);
        testIt("Version64", 64);
        testIt("Version59", 59);
        testIt("Version45_3", 0x0003_002D);  // 3:45

        // test primitive array.  should return latest version.
        int ver = (int)m.invoke((new int[3]).getClass());
        if (ver != latestMajor) {
            int got_minor = (ver >> 16) & LOWER_16;
            int got_major = ver & LOWER_16;
            throw new RuntimeException(
                "Expected 0:" + latestMajor + ", but got " + got_minor + ":" + got_major + " for primitive array");
        }

        // test object array.  should return class file version of component.
        ver = (int)m.invoke((new Version59[2]).getClass());
        if (ver != 59) {
            int got_minor = (ver >> 16) & LOWER_16;
            int got_major = ver & LOWER_16;
            throw new RuntimeException(
                "Expected 0:59, but got " + got_minor + ":" + got_major + " for object array");
        }

        // test multi-dimensional object array.  should return class file version of component.
        ver = (int)m.invoke((new Version59[3][2]).getClass());
        if (ver != 59) {
            int got_minor = (ver >> 16) & LOWER_16;
            int got_major = ver & LOWER_16;
            throw new RuntimeException(
                "Expected 0:59, but got " + got_minor + ":" + got_major + " for object array");
        }
    }
}
