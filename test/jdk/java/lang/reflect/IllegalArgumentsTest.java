/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277964
 * @summary Test IllegalArgumentException be thrown when an argument is invalid
 * @run testng/othervm IllegalArgumentsTest
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.testng.annotations.Test;

public class IllegalArgumentsTest {
    static class T {
        public T(int i) {}

        public static void m(int i) {}

        public void m1(String s) {}
    }

    @Test
    public void wrongArgumentType() throws ReflectiveOperationException {
        for (int i = 0; i < 100_000; ++i) {
            try {
                Constructor<T> ctor = T.class.getConstructor(int.class);
                ctor.newInstance(int.class);    // wrong argument type
                throw new RuntimeException("Expected IAE not thrown");
            } catch (IllegalArgumentException e) {}
        }

        for (int i = 0; i < 100_000; ++i) {
            try {
                Method method = T.class.getMethod("m", int.class);
                method.invoke(null, int.class); // wrong argument type
                throw new RuntimeException("Expected IAE not thrown");
            } catch (IllegalArgumentException e) {}
        }
    }

    @Test
    public void nullArguments() throws ReflectiveOperationException {
        for (int i = 0; i < 100_000; ++i) {
            try {
                Constructor<T> ctor = T.class.getConstructor(int.class);
                ctor.newInstance(new Object[] {null});
                throw new RuntimeException("Expected IAE not thrown");
            } catch (IllegalArgumentException e) {}
        }

        for (int i = 0; i < 100_000; ++i) {
            try {
                Method method = T.class.getMethod("m", int.class);
                method.invoke(null, new Object[] {null});
                throw new RuntimeException("Expected IAE not thrown");
            } catch (IllegalArgumentException e) {}
        }
    }

    @Test
    public void illegalArguments() throws ReflectiveOperationException {
        for (int i = 0; i < 100_000; ++i) {
            try {
                Constructor<T> ctor = T.class.getConstructor(int.class);
                ctor.newInstance(new Object[] { 10, 20});
                throw new RuntimeException("Expected IAE not thrown");
            } catch (IllegalArgumentException e) {}
        }

        for (int i = 0; i < 100_000; ++i) {
            try {
                Method method = T.class.getMethod("m", int.class);
                method.invoke(null, new Object[] { 10, 20});
                throw new RuntimeException("Expected IAE not thrown");
            } catch (IllegalArgumentException e) {}
        }
    }

    @Test
    public void wrongReceiver() throws ReflectiveOperationException {
        for (int i = 0; i < 100_000; ++i) {
            try {
                Method method = T.class.getMethod("m1", String.class);
                method.invoke(this, "bad receiver");
                throw new RuntimeException("Expected IAE not thrown");
            } catch (IllegalArgumentException e) {}
        }
    }
}
