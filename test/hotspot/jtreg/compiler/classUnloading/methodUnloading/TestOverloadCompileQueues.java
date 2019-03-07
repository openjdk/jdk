/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestOverloadCompileQueues
 * @bug 8163511
 * @summary Test overloading the C1 and C2 compile queues with tasks.
 * @run main/othervm -XX:-TieredCompilation -XX:CompileThreshold=2 -XX:CICompilerCount=1
 *                   compiler.classUnloading.methodUnloading.TestOverloadCompileQueues
 * @run main/othervm -XX:TieredCompileTaskTimeout=1000 -XX:CompileThresholdScaling=0.001 -XX:CICompilerCount=2
 *                   compiler.classUnloading.methodUnloading.TestOverloadCompileQueues
 */

package compiler.classUnloading.methodUnloading;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class TestOverloadCompileQueues {
    public static final int ITERS = 500; // Increase for longer stress testing

    // Some methods to fill up the compile queue
    public static void test0() { }
    public static void test1() { }
    public static void test2() { }
    public static void test3() { }
    public static void test4() { }
    public static void test5() { }
    public static void test6() { }
    public static void test7() { }
    public static void test8() { }
    public static void test9() { }
    public static void test10() { }
    public static void test11() { }
    public static void test12() { }
    public static void test13() { }
    public static void test14() { }
    public static void test15() { }
    public static void test16() { }
    public static void test17() { }
    public static void test18() { }
    public static void test19() { }

    public static void main(String[] args) throws Throwable {
        Class<?> thisClass = TestOverloadCompileQueues.class;
        ClassLoader defaultLoader = thisClass.getClassLoader();
        URL classesDir = thisClass.getProtectionDomain().getCodeSource().getLocation();

        for (int i = 0; i < ITERS; ++i) {
            // Load test class with own class loader
            URLClassLoader myLoader = URLClassLoader.newInstance(new URL[] {classesDir}, defaultLoader.getParent());
            Class<?> testClass = Class.forName(thisClass.getCanonicalName(), true, myLoader);

            // Execute all test methods to trigger compilation and fill up compile queue
            for (int j = 1; j < 20; ++j) {
                Method method = testClass.getDeclaredMethod("test" + j);
                method.invoke(null);
                method.invoke(null);
            }

            // Unload dead classes from ealier iterations
            System.gc();
        }
    }
}
