/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary Test verifies that JVMTI raw monitor wait/notify works for
 * suspended virtual thread.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile WaitNotifySuspendedVThreadTest.java
 * @run main/othervm/native
 *     -Djdk.virtualThreadScheduler.maxPoolSize=1
 *     -agentlib:WaitNotifySuspendedVThread WaitNotifySuspendedVThreadTest
 */


import java.util.ArrayList;

public class WaitNotifySuspendedVThreadTest {

    static {
        System.loadLibrary("WaitNotifySuspendedVThread");
    }

    public static Object holder = new Object();


    public static void main(String argv[]) throws InterruptedException {

        WaitNotifySuspendedVThreadTask.setBreakpoint();
        WaitNotifySuspendedVThreadTask task = new WaitNotifySuspendedVThreadTask();
        Thread t = Thread.ofVirtual().start(task);
        WaitNotifySuspendedVThreadTask.notifyRawMonitors(t);
        t.join();
    }
}


class WaitNotifySuspendedVThreadTask implements Runnable {
    static native void setBreakpoint();
    static native void notifyRawMonitors(Thread t);

    void methBreakpoint() {
        WaitNotifySuspendedVThreadTest.holder = new Object();
    }

    public void run() {
        methBreakpoint();
    }
}
