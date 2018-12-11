/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.jdk;

import java.lang.reflect.Field;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;

/*
 */
public class UnsafeAccess01 extends JTTTest {

    private static int randomValue = 100;
    private static final long offset;
    private static Object staticObject = new TestClass();

    static {
        Field field = null;
        try {
            field = TestClass.class.getDeclaredField("field");
        } catch (NoSuchFieldException e) {
        } catch (SecurityException e) {
        }
        offset = UNSAFE.objectFieldOffset(field);
    }

    private static class TestClass {
        private int field = 42;
    }

    public static int test() {
        final TestClass object = new TestClass();
        final int value = UNSAFE.getInt(object, offset);
        return value;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

    @Test
    public void runDiamond() throws Throwable {
        runTest("testDiamond");
    }

    public static int testDiamond() {

        final Object object = staticObject;
        final int oldValue = ((TestClass) object).field;

        if (randomValue == 100) {
            UNSAFE.putInt(object, offset, 41);
        } else {
            UNSAFE.putInt(object, offset, 40);
        }
        UNSAFE.putInt(object, offset, 42);
        return oldValue;
    }
}
