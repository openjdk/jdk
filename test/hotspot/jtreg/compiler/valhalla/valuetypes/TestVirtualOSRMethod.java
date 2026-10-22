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
 * @test
 * @bug 8392084
 * @summary Test freezing a c2 OSR frame for a method marked as needing stack repair
 * @enablePreview
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::foo ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class TestVirtualOSRMethod {
    static volatile int sleepCount;

    static Object foo(Integer i1, Integer i2, Integer i3, AtomicBoolean stop) {
        Object obj = new Object();
        for (int i = 0; i < (100_000 * i1) + 1; i++) {
            if ((i % 50_000) == 0) {
                sleepCount++;
                if (i == 100_000 * i1) {
                    stop.set(true);
                }
                LockSupport.parkNanos(10_000_000L);
            }
        }
        return obj;
    }

    static void test1() throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        Thread vthread = Thread.startVirtualThread(() -> foo(1, 1, 1, stop));
        while (!stop.get()) {
            Thread.onSpinWait();
        }
        System.gc();
        vthread.join();
    }

    static void test2() throws Exception {
        int localCount = 0;
        AtomicBoolean stop = new AtomicBoolean();
        Thread vthread = Thread.startVirtualThread(() -> foo(4, 4, 4, stop));
        while (!stop.get()) {
            if (localCount != sleepCount) {
                localCount = sleepCount;
                System.gc();
            }
            Thread.onSpinWait();
        }
        vthread.join();
    }

    public static void main(String[] args) throws Exception {
        test1();
        test2();
    }
}
