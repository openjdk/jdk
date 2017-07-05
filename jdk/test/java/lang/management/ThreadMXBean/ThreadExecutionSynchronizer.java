/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary This class is used to synchronize execution of two threads.
 * @author  Swamy Venkataramanappa
 */

import java.util.concurrent.Semaphore;

public class ThreadExecutionSynchronizer {

    private volatile boolean waiting;
    private final Semaphore semaphore;

    public ThreadExecutionSynchronizer() {
        semaphore = new Semaphore(1);
        waiting = false;
    }

    // Synchronizes two threads execution points.
    // Basically any thread could get scheduled to run and
    // it is not possible to know which thread reaches expected
    // execution point. So whichever thread reaches a execution
    // point first wait for the second thread. When the second thread
    // reaches the expected execution point will wake up
    // the thread which is waiting here.
    void stopOrGo() {
        semaphore.acquireUninterruptibly(); // Thread can get blocked.
        if (!waiting) {
            waiting = true;
            // Wait for second thread to enter this method.
            while(!semaphore.hasQueuedThreads()) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException xx) {}
            }
            semaphore.release();
        } else {
            waiting = false;
            semaphore.release();
        }
    }

    // Wrapper function just for code readability.
    void waitForSignal() {
        stopOrGo();
        goSleep(50);
    }

    void signal() {
        stopOrGo();
        goSleep(50);
    }

    private static void goSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Unexpected exception.");
        }
    }
}
