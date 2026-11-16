/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=c1
 * @bug 8391181
 * @summary Tests that memory state is killed appropriately for class loaders that might have side-effects.
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}$Test*::test
 *                   ${test.main.class}
 */

/*
 * @test id=c2
 * @bug 8391181
 * @summary Tests that memory state is killed appropriately for class loaders that might have side-effects.
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}$Test*::test
 *                   ${test.main.class}
 */

package compiler.c1;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jdk.test.lib.Asserts;

public class TestMemoryEffectsClassLoading {
    public static class Victim {
        public int field;
    }
    public static final Victim VICTIM = new Victim();

    public static Object SOME_OBJECT = new Object();
    private static final String TEST_CLASS = "compiler.c1.TestMemoryEffectsClassLoading";

    public static final void main(String[] args) throws Exception {
        final SideEffectingClassLoader loader = new SideEffectingClassLoader();
        var tests = List.of("TestClassConstant", "TestInstanceOf", "TestObjectArray", "TestMultiArray");
        for (final String className : tests) {
            final Class<?> klass = Class.forName(TEST_CLASS + "$" + className, false, loader);
            int result = (int) klass.getMethod("test").invoke(null);
            Asserts.assertEQ(42, result, "Wrong result for " + className + ":");
        }
    }

    public static class TestClassConstant {
        public static int test() {
            VICTIM.field = 0xcafe;
            Class<?> unused = LoadTargetClassConstant.class;
            return VICTIM.field;
        }
    }

    public static class TestInstanceOf {
        public static int test() {
            VICTIM.field = 0xdead;
            IO.println("before");
            boolean unused = SOME_OBJECT instanceof LoadTargetInstanceOf;
            return VICTIM.field;
        }
    }

    public static class TestObjectArray {
        public static int test() {
            VICTIM.field = 0xbad;
            Object unused = new LoadTargetObjectArray[0];
            return VICTIM.field;
        }
    }

    public static class TestMultiArray {
        public static int test() {
            VICTIM.field = 0xbad;
            Object _ = new LoadTargetMultiArray[0][0];
            return VICTIM.field;
        }
    }

    // Classes that trigger the side effect when loaded with the custom class loader below.
    public static class LoadTargetClassConstant {}
    public static class LoadTargetInstanceOf {}
    public static class LoadTargetObjectArray {}
    public static class LoadTargetMultiArray {}

    static class SideEffectingClassLoader extends ClassLoader {
        SideEffectingClassLoader() {
            super(TestMemoryEffectsClassLoading.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Load all Test* subclasses here so the target classes are loaded with this
            // loader. The rest is delegated to the system class loader.
            if (name.contains("$Test")) {
                Class<?> klass = findLoadedClass(name);
                if (klass == null) {
                    klass = findClass(name);
                }
                if (resolve) {
                    resolveClass(klass);
                }
                return klass;
            }

            // Side-effect only for the specific classes where we want to observe it.
            if (name.contains("$LoadTarget")) {
                VICTIM.field = 42;
                IO.println("side-effect in classloader");
            }

            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream in = getSystemResourceAsStream(resourceName)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
