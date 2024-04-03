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
 * @summary reachabilityFence() doesn’t always work
 *
 * @run main/othervm compiler.c2.TestReachabilityFence
 */

public class TestReachabilityFence {
    static class A {
        public B obj;
        public A(B obj) {
            this.obj = obj;
        }
    }

    static class B {
        public final int id;

        static int[] arr = new int[1024];

        public B(int id) {
            this.id = id;
        }
    }

    static final int CASE = Integer.getInteger("CASE", 1);

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
        final A foo = new A(new B(1));
        Cleaner.create().register(foo.obj, () -> {
            System.out.println("!!! GONE !!!");
            B.arr[0] = 1_000_000 + foo.obj.id;
        });

        for (int i = 0; i < 20_000; i++) {
            test(foo, foo.obj.arr, foo.obj.arr.length);
        }

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

        //final int[] arr2 = new int[1024 * 1024 * 1024];

        Thread threadUpdate = new Thread() {
            public void run() {
                try {
                    //while (true) {
                        Thread.sleep(1000);

                        int newId = foo.obj.id + 1;

                        //B newB = new B(newId);
                        foo.obj = null; // newB;

                        System.out.println("!!! CLEAN !!!");
                    //}
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        };
        threadUpdate.setDaemon(true);

        threadGC.start();
        threadUpdate.start();

        System.out.println(0 + "");
        test(foo, foo.obj.arr, Integer.MAX_VALUE);
    }
}
