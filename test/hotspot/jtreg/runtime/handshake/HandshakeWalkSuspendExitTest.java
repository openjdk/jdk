/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test HandshakeWalkSuspendExitTest
 * @summary This test tries to stress the handshakes with new and exiting threads while suspending them.
 * @library /testlibrary /test/lib
 * @build HandshakeWalkSuspendExitTest
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI HandshakeWalkSuspendExitTest
 */

import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

public class HandshakeWalkSuspendExitTest  implements Runnable {

    static final int _test_threads = 8;
    static final int _test_exit_threads = 128;
    static Thread[] _threads = new Thread[_test_threads];
    static volatile boolean exit_now = false;
    static java.util.concurrent.Semaphore _sem = new java.util.concurrent.Semaphore(0);

    @Override
    public void run() {
        WhiteBox wb = WhiteBox.getWhiteBox();
        while (!exit_now) {
            _sem.release();
            // We only suspend threads on even index and not ourself.
            // Otherwise we can accidentially suspend all threads.
            for (int i = 0; i < _threads.length; i += 2) {
                wb.handshakeWalkStack(null /* ignored */, true /* stackwalk all threads */);
                if (Thread.currentThread() != _threads[i]) {
                    _threads[i].suspend();
                    _threads[i].resume();
                }
            }
            for (int i = 0; i < _threads.length; i += 2) {
                wb.handshakeWalkStack(_threads[i] /* thread to stackwalk */, false /* stackwalk one thread */);
                if (Thread.currentThread() != _threads[i]) {
                    _threads[i].suspend();
                    _threads[i].resume();
                }
            }
        }
    }

    public static void main(String... args) throws Exception {
        HandshakeWalkSuspendExitTest test = new HandshakeWalkSuspendExitTest();

        for (int i = 0; i < _threads.length; i++) {
            _threads[i] = new Thread(test);
            _threads[i].start();
        }
        for (int i = 0; i < _test_threads; i++) {
            _sem.acquire();
        }
        Thread[] exit_threads = new Thread[_test_exit_threads];
        for (int i = 0; i < _test_exit_threads; i++) {
            exit_threads[i] = new Thread(new Runnable() { public void run() {} });
            exit_threads[i].start();
        }
        exit_now = true;
        for (int i = 0; i < _threads.length; i++) {
            _threads[i].join();
        }
        for (int i = 0; i < exit_threads.length; i++) {
            exit_threads[i].join();
        }
    }
}
