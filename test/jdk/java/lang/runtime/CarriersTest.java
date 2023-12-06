/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test features provided by the Carriers class.
 * @modules java.base/java.lang.runtime
 * @enablePreview true
 * @compile --patch-module java.base=${test.src} CarriersTest.java
 * @run main/othervm --patch-module java.base=${test.class.path} java.lang.runtime.CarriersTest
 */

package java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;

public class CarriersTest {
    public static void main(String[] args) throws Throwable {
        primitivesTest();
        primitivesTestLarge();
        limitsTest();
    }

    static void assertTrue(boolean test, String message) {
        if (!test) {
            throw new RuntimeException(message);
        }
    }

    static final int MAX_COMPONENTS = 254;

    static void primitivesTest() throws Throwable {
        MethodType methodType =
                MethodType.methodType(Object.class, byte.class, short.class,
                        char.class, int.class, long.class,
                        float.class, double.class,
                        boolean.class, String.class);
        MethodHandle constructor = Carriers.initializingConstructor(methodType);
        Object object = (Object)constructor.invokeExact((byte)0xFF, (short)0xFFFF,
                'C', 0xFFFFFFFF, 0xFFFFFFFFFFFFFFFFL,
                1.0f / 3.0f, 1.0 / 3.0,
                true, "abcde");
        List<MethodHandle> components = Carriers.components(methodType);
        assertTrue((byte)components.get(0).invokeExact(object) == (byte)0xFF,
                "primitive byte test failure");
        assertTrue((short)components.get(1).invokeExact(object) == (short)0xFFFF,
                "primitive short test failure");
        assertTrue((char)components.get(2).invokeExact(object) == 'C',
                "primitive char test failure");
        assertTrue((int)components.get(3).invokeExact(object) == 0xFFFFFFFF,
                "primitive int test failure");
        assertTrue((long)components.get(4).invokeExact(object) == 0xFFFFFFFFFFFFFFFFL,
                "primitive long test failure");
        assertTrue((float)components.get(5).invokeExact(object) == 1.0f / 3.0f,
                "primitive float test failure");
        assertTrue((double)components.get(6).invokeExact(object) == 1.0 / 3.0,
                "primitive double test failure");
        assertTrue((boolean)components.get(7).invokeExact(object),
                "primitive boolean test failure");
        assertTrue("abcde".equals((String)components.get(8).invokeExact(object)),
                "primitive String test failure");
    }

    static void primitivesTestLarge() throws Throwable {
        MethodType methodType =
                MethodType.methodType(Object.class, byte.class, short.class,
                        char.class, int.class, long.class,
                        float.class, double.class,
                        boolean.class, String.class,
                        Object.class, Object.class,Object.class,Object.class,
                        Object.class, Object.class,Object.class,Object.class,
                        Object.class, Object.class,Object.class,Object.class,
                        Object.class, Object.class,Object.class,Object.class,
                        Object.class, Object.class,Object.class,Object.class,
                        Object.class, Object.class,Object.class,Object.class,
                        Object.class, Object.class,Object.class,Object.class
                );
        MethodHandle constructor = Carriers.initializingConstructor(methodType);
        Object object = (Object)constructor.invokeExact((byte)0xFF, (short)0xFFFF,
                'C', 0xFFFFFFFF, 0xFFFFFFFFFFFFFFFFL,
                1.0f / 3.0f, 1.0 / 3.0,
                true, "abcde",
                (Object)null, (Object)null, (Object)null, (Object)null,
                (Object)null, (Object)null, (Object)null, (Object)null,
                (Object)null, (Object)null, (Object)null, (Object)null,
                (Object)null, (Object)null, (Object)null, (Object)null,
                (Object)null, (Object)null, (Object)null, (Object)null,
                (Object)null, (Object)null, (Object)null, (Object)null,
                (Object)null, (Object)null, (Object)null, (Object)null
        );
        List<MethodHandle> components = Carriers.components(methodType);
        assertTrue((byte)components.get(0).invokeExact(object) == (byte)0xFF,
                "large primitive byte test failure");
        assertTrue((short)components.get(1).invokeExact(object) == (short)0xFFFF,
                "large primitive short test failure");
        assertTrue((char)components.get(2).invokeExact(object) == 'C',
                "large primitive char test failure");
        assertTrue((int)components.get(3).invokeExact(object) == 0xFFFFFFFF,
                "large primitive int test failure");
        assertTrue((long)components.get(4).invokeExact(object) == 0xFFFFFFFFFFFFFFFFL,
                "large primitive long test failure");
        assertTrue((float)components.get(5).invokeExact(object) == 1.0f / 3.0f,
                "large primitive float test failure");
        assertTrue((double)components.get(6).invokeExact(object) == 1.0 / 3.0,
                "large primitive double test failure");
        assertTrue((boolean)components.get(7).invokeExact(object),
                "large primitive boolean test failure");
        assertTrue("abcde".equals((String)components.get(8).invokeExact(object)),
                "large primitive String test failure");
    }

    static void limitsTest() {
        boolean passed;

        passed = false;
        try {
            Class<?>[] ptypes = new Class<?>[MAX_COMPONENTS + 1];
            Arrays.fill(ptypes, Object.class);
            MethodType methodType = MethodType.methodType(Object.class, ptypes);
            MethodHandle constructor = Carriers.constructor(methodType);
        } catch (IllegalArgumentException ex) {
            passed = true;
        }

        if (!passed) {
            throw new RuntimeException("failed to report too many components ");
        }

        passed = false;
        try {
            Class<?>[] ptypes = new Class<?>[MAX_COMPONENTS / 2 + 1];
            Arrays.fill(ptypes, long.class);
            MethodType methodType = MethodType.methodType(Object.class, ptypes);
            MethodHandle constructor = Carriers.constructor(methodType);
        } catch (IllegalArgumentException ex) {
            passed = true;
        }

        if (!passed) {
            throw new RuntimeException("failed to report too many components ");
        }
    }
}
