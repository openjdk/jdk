/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2;

import java.lang.ref.Reference;
import java.lang.ref.Cleaner;

/*
 * @test
 * @bug 8290892
 * @summary Tests to ensure that reachabilityFence() correctly keeps objects from being collected prematurely.
 *
 * @run main/othervm -XX:CompileCommand=compileonly,*TestReachabilityFence::* compiler.c2.TestReachabilityFence
 */

public class TestReachabilityFence {
    // Nested class A containing an instance of class B
    static class A {
        public B obj;
        public A(B obj) {
            this.obj = obj;
        }
    }

    // Nested class B containing an integer ID and a static integer array
    static class B {
        public final int id;

        static int[] arr = new int[1024];

        public B(int id) {
            this.id = id;
        }
    }

    // Retrieve the test case number from system properties, defaulting to 1
    static final int CASE = Integer.getInteger("CASE", 1);

    // Test dispatcher based on the CASE value
    static void test(A foo, int[] arr, int limit) {
        switch (CASE) {
            case 0: test0(foo, arr, arr, limit); break;
            case 1: test1(foo, arr, arr, limit); break;
            case 2: test2(foo, arr, arr, limit); break;
            case 3: test3(foo, arr, arr, limit); break;

            case 10: test10(foo, arr, arr, limit); break;

            default: throw new Error();
        }
    }

    // Each test case function manipulates an array using the 'id' of object 'B'
    // and checks the reachability fence functionality.

    // Simple manipulation without reachability fence
    static void test0(A foo, int[] arr, int[] arr1, int limit) {
        int arr0 = arr[0];

        for (int i = 0; i < limit; i++) {
            B bar = foo.obj;
            int id = bar.id;
            int idx = i % 1024;
            arr[idx] = id * arr[idx];
        }

        if (arr1[0] != arr0) {
            throw new AssertionError(arr[0] + " != " + arr0);
        }
    }

    // Manipulation with reachability fence after each operation
    static void test1(A foo, int[] arr, int[] arr1, int limit) {
        int arr0 = arr[0];

        for (int j = 0; j < limit; j += arr.length) {
            for (int i = 0; i < arr.length; i++) {
                B bar = foo.obj;
                int id = bar.id;
                int idx = i;
                arr[idx] = id * arr[idx] ;
                Reference.reachabilityFence(bar);
            }
        }

        if (arr1[0] != arr0) {
            throw new AssertionError(arr[0] + " != " + arr0);
        }
    }

    static boolean flag = true;

    // Test cases 2 and 3 are variations on the above with the reachability fence.

    static void test2(A foo, int[] arr, int[] arr1, int limit) {
        int arr0 = arr[0];

        for (int j = 0; j < limit; j += arr.length) {
            for (int i = 0; i < arr.length; i++) {
                B bar = foo.obj;
                int id = bar.id;
                int idx = i;
                arr[idx] = id * arr[idx];
                Reference.reachabilityFence(bar);
            }
        }

        if (arr1[0] != arr0) {
            throw new AssertionError(arr[0] + " != " + arr0);
        }
    }

    static void test3(A foo, int[] arr, int[] arr1, int limit) {
        int arr0 = arr[0];

        B bar = foo.obj;
        for (int j = 0; j < limit; j += arr.length) {
            for (int i = 0; i < arr.length; i++) {
                int id = bar.id;
                int idx = i;// % 1024;
                arr[idx] = id * arr[idx] ;
                Reference.reachabilityFence(bar);
            }
        }

        if (arr1[0] != arr0) {
            throw new AssertionError(arr[0] + " != " + arr0);
        }
    }

    static void noinline(Object o) {}

    // Test case to trigger a NullPointerException (NPE) if 'bar' is prematurely collected
    static void test10(A foo, int[] arr, int[] arr1, int limit) {
        int arr0 = arr[0];

        for (int j = 0; j < limit; j += arr.length) {
            for (int i = 0; i < arr.length; i++) {
                B bar = foo.obj;
                bar.getClass(); // NPE
                Reference.reachabilityFence(bar);
            }
        }

        if (arr1[0] != arr0) {
            throw new AssertionError(arr[0] + " != " + arr0);
        }
    }

    public static void main(String[] args) {
        // Setup a test environment with object 'A' containing 'B' and register a cleaner
        final A foo = new A(new B(1));
        Cleaner.create().register(foo.obj, () -> {
            System.out.println("!!! GONE !!!");
            B.arr[0] = 1_000_000 + foo.obj.id;
        });

        // Perform intensive testing to potentially trigger garbage collection
        for (int i = 0; i < 20_000; i++) {
            test(foo, foo.obj.arr, foo.obj.arr.length);
        }

        // Thread to continuously trigger garbage collection
        Thread threadGC = new Thread() {
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(50);
                        System.gc();
                    }
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        };
        threadGC.setDaemon(true);

        // Thread to simulate object reference changes during execution
        Thread threadUpdate = new Thread() {
            public void run() {
                try {
                    Thread.sleep(1000);
                    int newId = foo.obj.id + 1;
                    foo.obj = null; // newB; Simulate losing the reference
                    System.out.println("!!! CLEAN !!!");
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        };
        threadUpdate.setDaemon(true);

        threadGC.start();
        threadUpdate.start();

        // Final test call with max integer limit
        System.out.println(0 + "");
        test(foo, foo.obj.arr, Integer.MAX_VALUE);

        System.out.println("Test completed.");
    }
}
