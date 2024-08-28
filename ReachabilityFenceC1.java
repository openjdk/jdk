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


import java.lang.ref.Reference;
import java.lang.ref.Cleaner;


/*
$ ~/dev/jdk/open/ /Users/tholenst/dev/jdk7/build/macosx-aarch64-debug/jdk/bin/java -XX:+UseNewCode -XX:TieredStopAtLevel=1 -XX:+UseLoopInvariantCodeMotion  -XX:CompileCommand=compileonly,*ReachabilityFenceC1::* -Xbatch  ReachabilityFenceC1.java
!!! CLEAN !!!
!!! GONE !!!
Exception in thread "main" java.lang.AssertionError: 42 != 1
	at ReachabilityFenceC1.test1(ReachabilityFenceC1.java:66)
	at ReachabilityFenceC1.test(ReachabilityFenceC1.java:55)
	at ReachabilityFenceC1.main(ReachabilityFenceC1.java:111)
*/
public class ReachabilityFenceC1 {
    static class A {
        public B obj;
        public A(B obj) {
            this.obj = obj;
        }
    }

    static class B {
        public final int id;

        public static int[] arr = new int[1024];

        public B(int id) {
            this.id = id;
        }
    }


    static void test(A foo, int[] arr, int limit) {
        test1(foo, arr, arr, limit);
    }


    static void test1(A foo, int[] arr, int[] arr1, int limit) {
        int val = B.arr[0];

        for (long j = 0; j < limit; j += 1) {
            for (int i = 1; i < arr.length; i++) {
                B bar = foo.obj;
                arr[i] = bar.id * arr[i];
                if (B.arr[0] != val) throw new AssertionError(arr[0] + " != " + val);
                Reference.reachabilityFence(bar);
            }
        }
    }

    public static void main(String[] args) {
        final A foo = new A(new B(1));
        Cleaner.create().register(foo.obj, () -> {
            B.arr[0] = 42;
            System.out.println("!!! GONE !!!");
        });

        for (int j = 0; j < foo.obj.arr.length; j += 1) {
            foo.obj.arr[j] = 1;
        }

        for (int i = 0; i < 20_000; i++) {
            test(foo, foo.obj.arr, 100);
        }

        Thread threadUpdate = new Thread() {
            public void run() {
                try {
                    foo.obj = null;
                    System.out.println("!!! CLEAN !!!");
                    while (true) {
                        Thread.sleep(50);
                        System.gc();
                    }
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        };
        threadUpdate.setDaemon(true);

        threadUpdate.start();

        test(foo, foo.obj.arr, 10_000_000);
    }
}
