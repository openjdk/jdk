/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Test JNI IsVirtualThread
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} IsVirtualThread.java
 * @run main/native/othervm --enable-preview IsVirtualThread
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
        test(thread);   // started, probably parked
        LockSupport.unpark(thread);
        thread.join();
        test(thread);   // terminated

        // test virtual thread
        Thread vthread = Thread.ofVirtual().unstarted(LockSupport::park);
        test(vthread);   // not started
        vthread.start();
        test(vthread);   // started, probably parked
        LockSupport.unpark(vthread);
        vthread.join();
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
