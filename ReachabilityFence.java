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

public class ReachabilityFence {
    static MyClass obj = new MyClass();

    static class MyClass {
        static boolean[] collected = new boolean[100];
    }

    static void test(int limit) {
        for (long j = 0; j < limit; j++) {
            for (int i = 0; i < 100; i++) {
                MyClass myObject = obj;
                if (myObject == null) return;
                {
                  // This would be some code that requires that myObject stays live
                  if (MyClass.collected[i]) throw new RuntimeException("myObject collected before reachabilityFence was reached!");
                }
                Reference.reachabilityFence(myObject);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Set 'MyClass.collected[0]' to true if 'obj' is garbage collected
        Cleaner.create().register(obj, () -> {
            System.out.println("obj was garbage collected");
            MyClass.collected[0] = true;
        });

        // Warmup to trigger compilation
        for (int i = 0; i < 20000; i++) {
            test(100);
        }

        // Clear reference to 'obj' and make sure it's garbage collected
        Thread gcThread = new Thread() {
            public void run() {
                try {
                    obj = null;
                    System.out.println("obj set to null");
                    while (true) {
                        Thread.sleep(50);
                        System.gc();
                    }
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        };
        gcThread.setDaemon(true);
        gcThread.start();

        test(10_000_000);

        // Wait
        while (!MyClass.collected[0]) { }
    }
}
