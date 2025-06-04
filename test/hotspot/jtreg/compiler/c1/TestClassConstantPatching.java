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
 */

/**
 * @test
 * @bug 8282218
 *
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,compiler.c1.TestClassConstantPatching$Test::run
 *                      compiler.c1.TestClassConstantPatching
 */
package compiler.c1;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

public class TestClassConstantPatching {
    public static int COUNTER = 0;

    static class CustomLoader extends ClassLoader {
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(Test.class.getName())) {
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    return c;
                }
                try {
                    COUNTER++; // update counter on loading

                    InputStream in = getSystemResourceAsStream(name.replace('.', File.separatorChar) + ".class");
                    byte[] buf = in.readAllBytes();
                    return defineClass(name, buf, 0, buf.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name);
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    public static class Test {
        static class T {}

        public static Class<?> run(boolean cond) {
            int before = COUNTER;
            Class<?> c = null;
            if (cond) {
                c = T.class;
            }
            int after = COUNTER;
            if (cond && before == after) {
                throw new AssertionError("missed update");
            }
            return c;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        ClassLoader cl = new CustomLoader();

        Class.forName(TestClassConstantPatching.class.getName(), true, cl); // preload counter holder class

        Class<?> test = Class.forName(Test.class.getName(), true, cl);

        Method m = test.getDeclaredMethod("run", boolean.class);

        m.invoke(null, false);
        m.invoke(null, true);

        System.out.println("TEST PASSED");
    }
}

