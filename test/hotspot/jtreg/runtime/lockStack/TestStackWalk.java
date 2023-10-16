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
 *
 */

/*
 * @test
 * @bug 8317262
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+HandshakeALot -XX:GuaranteedSafepointInterval=1 TestStackWalk
 */

import jvmti.JVMTIUtils;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import java.util.concurrent.CountDownLatch;

public class TestStackWalk {
    static Thread worker1;
    static Thread worker2;
    static volatile boolean done;
    static volatile int counter = 0;
    static Object lock = new Object();

    public static void main(String... args) throws Exception {
        worker1 = new Thread(() -> syncedWorker());
        worker1.start();
        worker2 = new Thread(() -> syncedWorker());
        worker2.start();
        Thread worker3 = new Thread(() -> stackWalker());
        worker3.start();

        worker1.join();
        worker2.join();
        worker3.join();
    }

    public static void syncedWorker() {
        synchronized (lock) {
            while (!done) {
                counter++;
            }
        }
    }

    public static void stackWalker() {
        // Suspend workers so the one looping waiting for "done"
        // doesn't execute the handshake below, increasing the
        // chances the VMThread will do it.
        suspendWorkers();

        WhiteBox wb = WhiteBox.getWhiteBox();
        long end = System.currentTimeMillis() + 20000;
        while (end > System.currentTimeMillis()) {
            wb.handshakeWalkStack(worker1, false /* all_threads */);
            wb.handshakeWalkStack(worker2, false /* all_threads */);
        }

        resumeWorkers();
        done = true;
    }

    static void suspendWorkers() {
        JVMTIUtils.suspendThread(worker1);
        JVMTIUtils.suspendThread(worker2);
    }

    static void resumeWorkers() {
        JVMTIUtils.resumeThread(worker1);
        JVMTIUtils.resumeThread(worker2);
    }
}
