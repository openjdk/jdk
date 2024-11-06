/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/**
  * @test
  * @bug 8340313
  * @summary Test that concurrent patching of oop immediates is thread-safe in C1.
  * @run main/othervm/timeout=480 -Xcomp -XX:CompileCommand=compileonly,TestConcurrentPatching::* -XX:TieredStopAtLevel=1 TestConcurrentPatching
  */

class MyClass { }

class Holder {
    public static final MyClass OBJ1 = null;
    public static final MyClass OBJ2 = null;
    public static final MyClass OBJ3 = null;
    public static final MyClass OBJ4 = null;
    public static final MyClass OBJ5 = null;
    public static final MyClass OBJ6 = null;
    public static final MyClass OBJ7 = null;
    public static final MyClass OBJ8 = null;
    public static final MyClass OBJ9 = null;
    public static final MyClass OBJ10 = null;
    public static final MyClass OBJ11 = null;
    public static final MyClass OBJ12 = null;
    public static final MyClass OBJ13 = null;
    public static final MyClass OBJ14 = null;
    public static final MyClass OBJ15 = null;
    public static final MyClass OBJ16 = null;
    public static final MyClass OBJ17 = null;
    public static final MyClass OBJ18 = null;
    public static final MyClass OBJ19 = null;
    public static final MyClass OBJ20 = null;
}

public class TestConcurrentPatching {
    // Increase to 100_000 for a good chance of reproducing the issue with a single run
    static final int ITERATIONS = 1000;

    static Object field;

    // 'Holder' class is unloaded on first execution and therefore field
    // accesses require patching when the method is C1 compiled (with -Xcomp).
    public static void test() {
        field = Holder.OBJ1;
        field = Holder.OBJ2;
        field = Holder.OBJ3;
        field = Holder.OBJ4;
        field = Holder.OBJ5;
        field = Holder.OBJ6;
        field = Holder.OBJ7;
        field = Holder.OBJ8;
        field = Holder.OBJ9;
        field = Holder.OBJ10;
        field = Holder.OBJ11;
        field = Holder.OBJ12;
        field = Holder.OBJ13;
        field = Holder.OBJ14;
        field = Holder.OBJ15;
        field = Holder.OBJ16;
        field = Holder.OBJ17;
        field = Holder.OBJ18;
        field = Holder.OBJ19;
        field = Holder.OBJ20;
    }

    // Appendix of invokedynamic call sites is unloaded on first execution and
    // therefore requires patching when the method is C1 compiled (with -Xcomp).
    public static void testIndy() throws Throwable {
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
          field = (Runnable) () -> { };
    }

    // Run 'test' by multiple threads to trigger concurrent patching of field accesses
    static void runWithThreads(Method method) {
        ArrayList<Thread> threads = new ArrayList<>();
        for (int threadIdx = 0; threadIdx < 10; threadIdx++) {
            threads.add(new Thread(() -> {
                try {
                    method.invoke(null);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        Class<?> thisClass = TestConcurrentPatching.class;
        ClassLoader defaultLoader = thisClass.getClassLoader();
        URL classesDir = thisClass.getProtectionDomain().getCodeSource().getLocation();

        // Load the test class multiple times with a separate class loader to make sure
        // that the 'Holder' class is unloaded for each compilation of method 'test'
        // and that the appendix of the invokedynamic call site is unloaded for each
        // compilation of method 'testIndy'.
        for (int i = 0; i < ITERATIONS; ++i) {
            URLClassLoader myLoader = URLClassLoader.newInstance(new URL[] {classesDir}, defaultLoader.getParent());
            Class<?> testClass = Class.forName(thisClass.getCanonicalName(), true, myLoader);
            runWithThreads(testClass.getDeclaredMethod("test"));
            runWithThreads(testClass.getDeclaredMethod("testIndy"));
        }
    }
}
