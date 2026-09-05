/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8271820
 * @library /test/lib/
 * @modules jdk.compiler
 * @build CustomLoaderTest jdk.test.lib.compiler.CompilerUtils
 * @run junit/othervm CustomLoaderTest
 *
 * @summary Test method whose parameter types and return type are not visible to the caller.
 */

import java.io.IOException;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.test.lib.compiler.CompilerUtils;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CustomLoaderTest {
    private static final Path CLASSES = Paths.get("classes");

    @BeforeAll
    public static void setup() throws IOException {
        String src = System.getProperty("test.src", ".");
        String classpath = System.getProperty("test.classes", ".");
        boolean rc = CompilerUtils.compile(Paths.get(src, "ReflectTest.java"), CLASSES, "-cp", classpath);
        if (!rc) {
            fail("fail compilation");
        }
        assertThrows(ClassNotFoundException.class, () -> Class.forName("ReflectTest$P"),
                "should not be visible to this loader");
    }

    @Test
    public void test() throws Exception {
        TestLoader loader1 = new TestLoader();
        TestLoader loader2 = new TestLoader();
        Method m1 = loader1.findMethod();
        Method m2 = loader2.findMethod();

        assertNotSame(m1.getDeclaringClass(), m2.getDeclaringClass());

        assertSame(loader1.c, m1.getDeclaringClass());
        assertSame(loader2.c, m2.getDeclaringClass());

        Object o1 = m1.invoke(loader1.c.newInstance(), loader1.p.newInstance(), loader1.q.newInstance());
        Object o2 = m2.invoke(loader2.c.newInstance(), loader2.p.newInstance(), loader2.q.newInstance());

        assertNotSame(o1.getClass(), o2.getClass());
        assertSame(loader1.r, o1.getClass());
        assertSame(loader2.r, o2.getClass());
    }

    static class TestLoader extends URLClassLoader {
        static URL[] toURLs() {
            try {
                return new URL[]{ CLASSES.toUri().toURL() };
            } catch (MalformedURLException e) {
                throw new Error(e);
            }
        }
        static AtomicInteger counter = new AtomicInteger(0);

        final Class<?> c;
        final Class<?> p;
        final Class<?> q;
        final Class<?> r;
        TestLoader() throws ClassNotFoundException {
            super("testloader-" + counter.getAndIncrement(), toURLs(), ClassLoader.getPlatformClassLoader());
            this.c = Class.forName("ReflectTest", true, this);
            this.p = Class.forName("ReflectTest$P", true, this);
            this.q = Class.forName("ReflectTest$Q", true, this);
            this.r = Class.forName("ReflectTest$R", true, this);
        }

        Method findMethod() throws ReflectiveOperationException {
            return c.getMethod("m", p, q);
        }
    }
}
