/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @run junit/othervm -ea -esa test.java.lang.invoke.ArrayLengthTest
 */
package test.java.lang.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayLengthTest {

    static Object[] arrayClasses() {
        return new Object[] {
                int[].class,
                long[].class,
                float[].class,
                double[].class,
                boolean[].class,
                byte[].class,
                short[].class,
                char[].class,
                Object[].class,
                StringBuffer[].class
        };
    }

    @ParameterizedTest
    @MethodSource("arrayClasses")
    public void testArrayLength(Class<?> arrayClass) throws Throwable {
        MethodHandle arrayLength = MethodHandles.arrayLength(arrayClass);
        assertEquals(int.class, arrayLength.type().returnType());
        assertEquals(arrayClass, arrayLength.type().parameterType(0));
        Object array = MethodHandles.arrayConstructor(arrayClass).invoke(10);
        assertEquals(10, arrayLength.invoke(array));
    }

    @ParameterizedTest
    @MethodSource("arrayClasses")
    public void testArrayLengthInvokeNPE(Class<?> arrayClass) throws Throwable {
        MethodHandle arrayLength = MethodHandles.arrayLength(arrayClass);
        assertThrows(NullPointerException.class, () -> arrayLength.invoke(null));
    }

    @Test
    public void testArrayLengthNoArray() {
        assertThrows(IllegalArgumentException.class, () -> MethodHandles.arrayLength(String.class));
    }

    @Test
    public void testArrayLengthNPE() {
        assertThrows(NullPointerException.class, () -> MethodHandles.arrayLength(null));
    }

    @Test
    public void testNullReference() throws Throwable {
        MethodHandle arrayLength = MethodHandles.arrayLength(String[].class);
        assertThrows(NullPointerException.class, () -> {
            int len = (int)arrayLength.invokeExact((String[])null);
        });
    }
}
