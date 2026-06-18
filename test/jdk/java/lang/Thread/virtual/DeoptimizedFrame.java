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
 * @bug 8377715
 * @summary Test that thawing deoptimized frame preserves the deopt status
 * @requires vm.continuations
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm/native -agentlib:DeoptimizedFrame DeoptimizedFrame
 */

import jdk.test.lib.Asserts;

public class DeoptimizedFrame {
    private static final Object lock = new Object();
    private static A receiver = new A();
    private static volatile int result;

    private static native int setupReferences(Thread t, Object o);
    private static native void waitForVThread();
    private static native void notifyVThread();

    public static void foo() {
        synchronized (lock) {
            result = receiver.m();
        }
    }

    public static void main(String[] args) throws Exception {
        warmUp();
        Asserts.assertTrue(receiver.m() == 1, "unexpected value=" + receiver.m());

        Thread vthread = Thread.ofVirtual().unstarted(() -> foo());
        int res = setupReferences(vthread, lock);
        Asserts.assertTrue(res == 0, "error setting references");

        synchronized (lock) {
            vthread.start();
            waitForVThread();
            receiver = new B();
            notifyVThread();
        }
        vthread.join();
        Asserts.assertTrue(result == 3, "unexpected result=" + result);
    }

    private static void warmUp() throws Exception {
        for (int i = 0; i < 30_000; i++) {
            foo();
        }
    }

    static class A {
        int m() {
            return 1;
        }
    }

    static class B extends A {
        int m() {
            return 3;
        }
    }
}