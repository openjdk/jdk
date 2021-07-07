/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
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

/* @test
 * @summary test accessing protected static method from superclass
 * @run testng/othervm test.java.lang.invoke.AccessProtectedStaticMethodFromSuper
 */

package test.java.lang.invoke;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

public class AccessProtectedStaticMethodFromSuper {

    public static class A {
        protected static void func() {
        }
    }

    public static class B extends A {
        public static Runnable get() {
            // Generate method body by ForwardingMethodGenerator.generate
            return A::func;
        }
    }

    public static void main(String... args) throws Throwable {
        final URL empty[] = {};
        final class Loader extends URLClassLoader {

            private final Class<?> responsibility;

            public Loader(final ClassLoader parent, final Class<?> responsibility) {
                super(empty, parent);
                this.responsibility = responsibility;
            }

            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                try {
                    if (name.equals(responsibility.getName())) {
                        final byte bytes[] = getBytesFromClass(responsibility);
                        return defineClass(null, bytes, 0, bytes.length);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return super.findClass(name);
            }

        }
        final ClassLoader a = new Loader(null, A.class), b = new Loader(a, B.class);
        final Class<?> bClass = b.loadClass(B.class.getName());
        final MethodHandle get = MethodHandles.lookup().findStatic(bClass, "get", MethodType.methodType(Runnable.class));
        final Runnable runnable = (Runnable) get.invoke();
        runnable.run();
    }

    private static byte[] getBytesFromClass(final Class<?> clazz) throws IOException {
        try (final var input = clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".class")) {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            final byte data[] = new byte[1 << 12];
            while ((nRead = input.read(data, 0, data.length)) != -1)
                buffer.write(data, 0, nRead);
            buffer.flush();
            return buffer.toByteArray();
        }
    }
}