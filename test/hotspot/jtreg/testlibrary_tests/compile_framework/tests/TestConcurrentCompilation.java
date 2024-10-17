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

/*
 * @test
 * @summary Example test with multi-threaded use of the CompileFramework.
 *          Tests that the source and class directories are set up correctly.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compile_framework.tests.TestConcurrentCompilation
 */

package compile_framework.tests;

import compiler.lib.compile_framework.*;

import java.util.ArrayList;
import java.util.List;

public class TestConcurrentCompilation {

    // Generate a source java file as String
    public static String generate(int i) {
        return String.format("""
                             public class XYZ {
                                 public static int test() {
                                     return %d;
                                 }
                             }
                             """, i);
    }

    public static void test(int i) {
        System.out.println("Generate and compile XYZ for " + i);
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("XYZ", generate(i));
        comp.compile();

        // Now, sleep to give the other threads time to compile and store their class-files.
        System.out.println("Sleep for " + i);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            System.out.println("Sleep interrupted for " + i);
        }

        // Now, hopefully all threads have compiled and stored their class-files.
        // We can check if we get the expected result, i.e. the class-file from the current thread.
        System.out.println("Run XYZ.test for " + i);
        int j = (int)comp.invoke("XYZ", "test", new Object[] {});
        if (i != j) {
            System.out.println("Wrong value: " + i + " vs " + j);
            throw new RuntimeException("Wrong value: " + i + " vs " + j);
        }
        System.out.println("Success for " + i);
    }

    public static class MyRunnable implements Runnable {
        private int i;

        public MyRunnable(int i) {
            this.i = i;
        }

        public void run() {
            TestConcurrentCompilation.test(i);
        }
    }

    public static void main(String[] args) {
        System.out.println("Generating threads:");
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 3; i++) {
            Thread thread = new Thread(new MyRunnable(i));
            thread.start();
            threads.add(thread);
        }
        System.out.println("Waiting to join threads:");
        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("interrupted", e);
        }
        System.out.println("Success.");
    }
}
