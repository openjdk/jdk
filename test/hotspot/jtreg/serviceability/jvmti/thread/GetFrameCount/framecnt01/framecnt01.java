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
 * @summary converted from VM Testbase nsk/jvmti/GetFrameCount/framecnt001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercise JVMTI function GetFrameCount.
 *     The function is tested for current thread, other thread. For platform and virtual threads.
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile framecnt01.java
 * @run main/othervm/native -agentlib:framecnt01 framecnt01
 */

import java.util.concurrent.locks.LockSupport;

public class framecnt01 {

    native static boolean checkFrames0(Thread thread, boolean shouldSuspend, int expected);

    static void checkFrames(Thread thread, boolean shouldSuspend, int expected) {
        if(!checkFrames0(thread, shouldSuspend, expected)) {
            throw new RuntimeException("Check failed for " + thread + " " + shouldSuspend + " " + expected);
        }
    }

    static {
        System.loadLibrary("framecnt01");
    }
    static volatile boolean vThread1Started = false;
    static volatile boolean pThread1Started = false;

    public static void main(String args[]) throws Exception {

        // Test GetFrameCount on virtual live thread
        Thread vThread = Thread.ofVirtual().name("VirtualThread-Live").start(() -> {
           checkFrames(Thread.currentThread(), false, 10);
        });
        vThread.join();

        // Test GetFrameCount on virtual frozen thread
        Thread vThread1 = Thread.ofVirtual().name("VirtualThread-Frozen").start(() -> {
            vThread1Started = true;
            LockSupport.park();
        });
        while (!vThread1Started) {
            Thread.sleep(1);
        }
        // Let vthread1 to park
        while(vThread1.getState() != Thread.State.WAITING) {
            Thread.sleep(1);
        }

        // this is too fragile, implementation can change at any time.
        checkFrames(vThread1, false, 13);
        LockSupport.unpark(vThread1);
        vThread1.join();

        // Test GetFrameCount on live platform thread
        Thread pThread = Thread.ofPlatform().name("PlatformThread-Live").start(() -> {
            checkFrames(Thread.currentThread(), false, 6);
        });
        pThread.join();

        // Test GetFrameCount on parked platform thread
        Thread pThread1 = Thread.ofPlatform().name("PlatformThread-Parked").start(() -> {
                pThread1Started = true;
                LockSupport.park();
        });
        while (!pThread1Started) {
            Thread.sleep(1);
        }

        while(pThread1.getState() != Thread.State.WAITING) {
            Thread.sleep(1);
        }
        checkFrames(pThread1, false, 6);
        LockSupport.unpark(pThread1);
        pThread1.join();


        // Test GetFrameCount on some depth stack fixed by sync
        FixedDepthThread.checkFrameCount(0);
        FixedDepthThread.checkFrameCount(500);
    }
}

class FixedDepthThread implements Runnable {
    int depth;
    Object startedFlag;
    Object checkFlag;
    Thread thread;

    // Each stack has 3 frames additional to expected depth
    // 0: FixedDepthThread: run()V
    // 1: java/lang/Thread: run()V
    // 2: java/lang/Thread: runWith()V
    static final int ADDITIONAL_STACK_COUNT = 3;

    private FixedDepthThread(String name, int depth, Object checkFlag) {
        this.thread = Thread.ofPlatform().name(name).unstarted(this);
        this.depth = depth;
        this.startedFlag = new Object();
        this.checkFlag = checkFlag;
    }

    private void startAndWait() {
        synchronized(startedFlag) {
            thread.start();
            try {
                startedFlag.wait();

            } catch(InterruptedException e) {}

        }
    }

    public void run() {
        if (depth > 0) {
            depth--;
            run();
        }
        synchronized(startedFlag) {
            startedFlag.notify();  // let main thread know that all frames are in place
        }
        synchronized(checkFlag) {  // wait for the check done
        }
    }

    static void checkFrameCount(int depth) {
        final Object checkFlag = new Object();
        FixedDepthThread fixedDepthThread = new FixedDepthThread("FixedDepthThread-" + depth, depth, checkFlag);
        synchronized(checkFlag) {
            fixedDepthThread.startAndWait();
            framecnt01.checkFrames(fixedDepthThread.thread, false, depth + ADDITIONAL_STACK_COUNT);
            framecnt01.checkFrames(fixedDepthThread.thread, true, depth + ADDITIONAL_STACK_COUNT);
        }
    }
}
