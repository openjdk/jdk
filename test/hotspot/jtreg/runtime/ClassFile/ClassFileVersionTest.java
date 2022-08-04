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
            int exp_minor = (expectedResult >> 16) & 0x0000FFFF;
            int exp_major = expectedResult & 0x0000FFFF;
            int got_minor = (ver >> 16) & 0x0000FFFF;
            int got_major = ver & 0x0000FFFF;
            throw new RuntimeException(
                "Expected " + exp_minor + ":" + exp_major + " but got " + got_minor + ":" + got_major);
        }
    }

    public static void main(String argv[]) throws Throwable {
        Class<?> cl = java.lang.Class.class;
        m = cl.getDeclaredMethod("getClassFileVersion", new Class[0]);
        m.setAccessible(true);

        testIt("Version64", 64);
        testIt("Version59", 59);
        testIt("Version45_3", 0x3002D);  // 3:45
        // test minor version of 65535.
        testIt("Version64_65535", 0xFFFF0040);  // 0xFFFF0040 = 65535:64

        // test primitive array.  should return latest version.
        int ver = (int)m.invoke((new int[3]).getClass());
        if (ver != 64) {
            int got_minor = (ver >> 16) & 0x0000FFFF;
            int got_major = ver & 0x0000FFFF;
            throw new RuntimeException(
                "Expected 0:64, but got " + got_minor + ":" + got_major + " for primitive array");
        }

        // test object array.  should return class file version of component.
        ver = (int)m.invoke((new Version59[2]).getClass());
        if (ver != 59) {
            int got_minor = (ver >> 16) & 0x0000FFFF;
            int got_major = ver & 0x0000FFFF;
            throw new RuntimeException(
                "Expected 0:59, but got " + got_minor + ":" + got_major + " for object array");
        }

        // test multi-dimensional object array.  should return class file version of component.
        ver = (int)m.invoke((new Version59[3][2]).getClass());
        if (ver != 59) {
            int got_minor = (ver >> 16) & 0x0000FFFF;
            int got_major = ver & 0x0000FFFF;
            throw new RuntimeException(
                "Expected 0:59, but got " + got_minor + ":" + got_major + " for object array");
        }
    }
}
