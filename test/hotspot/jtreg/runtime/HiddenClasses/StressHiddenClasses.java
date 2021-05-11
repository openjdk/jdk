/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress defining hidden classes.
 * @requires !vm.graal.enabled
 * @library /test/lib
 * @modules jdk.compiler
 * @run main/othervm/timeout=900 StressHiddenClasses
 */

import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.*;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

// This test is based on vmTestbase/vm/mlvm/anonloader/share/StressClassLoadingTest.java
public class StressHiddenClasses {

    private static final int PARSE_TIMEOUT = 0;
    private static final int ITERATIONS = 40000;

    static byte klassbuf[] = InMemoryJavaCompiler.compile("TestClass",
        "public class TestClass { " +
        "    public static void concat(String one, String two) throws Throwable { " +
        "        System.out.println(one + two);" +
        " } } ");


    public void run() throws Exception {
        for (int x = 0; x < ITERATIONS; x++) {
            Thread parserThread  = new Thread() {
                public void run() {
                    try {
                        Lookup lookup = MethodHandles.lookup();
                        Class<?> c = lookup.defineHiddenClass(klassbuf, true, NESTMATE).lookupClass();
                    } catch (Throwable e) {
                        throw new RuntimeException("Unexpected exception: " + e.toString());
                    }
                }
            };

            if (x % 1000 == 0) {
                System.out.println("Executing iteration: " + x);
            }
            parserThread.start();
            parserThread.join(PARSE_TIMEOUT);

            // This code won't get executed as long as PARSE_TIMEOUT == 0.
            if (parserThread.isAlive()) {
                System.out.println("parser thread may be hung!");
                StackTraceElement[] stack = parserThread.getStackTrace();
                System.out.println("parser thread stack len: " + stack.length);
                System.out.println(parserThread + " stack trace:");
                for (int i = 0; i < stack.length; ++i) {
                    System.out.println(parserThread + "\tat " + stack[i]);
                }

                parserThread.join(); // Wait until either thread finishes or test times out.
            }
        }
    }


    public static void main(String[] args) throws Throwable {
        StressHiddenClasses shc = new StressHiddenClasses();
        shc.run();
    }
}
