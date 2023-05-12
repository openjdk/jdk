/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8284161
 * @summary Test JNI IsVirtualThread
 * @library /test/lib
 * @enablePreview
 * @run main/native/othervm IsVirtualThread
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @library /test/lib
 * @enablePreview
 * @run main/native/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations IsVirtualThread
 */

import jdk.test.lib.Asserts;

import java.util.concurrent.locks.LockSupport;

public class IsVirtualThread {
    public static void main(String[] args) throws Exception {
        test(Thread.currentThread());

        // test platform thread
        Thread thread = Thread.ofPlatform().unstarted(LockSupport::park);
        test(thread);   // not started
        thread.start();
        try {
            test(thread);   // started, probably parked
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
        test(thread);   // terminated

        // test virtual thread
        Thread vthread = Thread.ofVirtual().unstarted(LockSupport::park);
        test(vthread);   // not started
        vthread.start();
        try {
            test(vthread);   // started, probably parked
        } finally {
            LockSupport.unpark(vthread);
            vthread.join();
        }
        test(vthread);   // terminated
    }

    private static void test(Thread thread) {
        System.out.println("test: " + thread);
        boolean isVirtual = isVirtualThread(thread);
        boolean expected = thread.isVirtual();
        Asserts.assertEQ(expected, isVirtual, "JNI IsVirtualThread() not equals to Thread.isVirtual()");
    }

    private static native boolean isVirtualThread(Thread thread);

    static {
        System.loadLibrary("IsVirtualThread");
    }
}
