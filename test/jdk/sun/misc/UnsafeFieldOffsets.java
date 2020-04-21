/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Ensure that sun.misc.Unsafe::objectFieldOffset and staticFieldOffset
 *          throw UnsupportedOperationException on Field of a hidden class
 * @modules jdk.unsupported
 * @run main UnsafeFieldOffsets
 */

import sun.misc.Unsafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UnsafeFieldOffsets {
    static class Fields {
        static final Object STATIC_FINAL = new Object();
        static Object STATIC_NON_FINAL = new Object();
        final Object FINAL = new Object();
        Object NON_FINAL = new Object();
    }

    private static Unsafe UNSAFE = getUnsafe();
    private static final Class<?> HIDDEN_CLASS = defineHiddenClass();

    private static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> defineHiddenClass() {
        String classes = System.getProperty("test.classes");
        Path cf = Paths.get(classes, "UnsafeFieldOffsets$Fields.class");
        try {
            byte[] bytes = Files.readAllBytes(cf);
            Class<?> c = MethodHandles.lookup().defineHiddenClass(bytes, true).lookupClass();
            assertHiddenClass(c);
            return c;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        // non-hidden class
        testStaticField(Fields.class, "STATIC_FINAL");
        testStaticField(Fields.class, "STATIC_NON_FINAL");
        testInstanceField(Fields.class, "FINAL");
        testInstanceField(Fields.class, "NON_FINAL");

        // hidden class
        testStaticField(HIDDEN_CLASS, "STATIC_FINAL");
        testStaticField(HIDDEN_CLASS, "STATIC_NON_FINAL");
        testInstanceField(HIDDEN_CLASS, "FINAL");
        testInstanceField(HIDDEN_CLASS, "NON_FINAL");
    }

    private static void testStaticField(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        try {
            UNSAFE.staticFieldOffset(f);
            assertNonHiddenClass(c);
        } catch (UnsupportedOperationException e) {
            assertHiddenClass(c);
        }
    }

    private static void testInstanceField(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        try {
            UNSAFE.objectFieldOffset(f);
            assertNonHiddenClass(c);
        } catch (UnsupportedOperationException e) {
            assertHiddenClass(c);
        }
    }

    private static void assertNonHiddenClass(Class<?> c) {
        if (c.isHidden())
            throw new RuntimeException("Expected UOE but not thrown: " + c);
    }

    private static void assertHiddenClass(Class<?> c) {
        if (!c.isHidden())
            throw new RuntimeException("Expected hidden class but is not: " + c);
    }
}
