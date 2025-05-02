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

/**
 * @test
 * @bug 8340698
 * @summary JVMTI FRAME_POP event is sometimes missed if NotifyFramePop is called as a method is returning
 * @requires vm.jvmti
 * @library /test/lib
 * @compile NotifyFramePopStressTest.java
 * @run main/othervm/native -agentlib:NotifyFramePopStressTest NotifyFramePopStressTest
 */

import jtreg.SkippedException;

public class NotifyFramePopStressTest {
    static volatile boolean done = false;

    public static void main(String args[]) {
        if (!canGenerateFramePopEvents()) {
            throw new SkippedException("FramePop event is not supported");
        }
        Thread testThread = Thread.currentThread();
        Thread controlThread = new Thread(() -> control(testThread), "Control Thread");

        setFramePopNotificationMode(testThread);
        controlThread.start();
        sleep(10);

        for (int i = 0; i < 10*1000; i++) {
            foo();
            bar();
        }
        done = true;

        try {
            controlThread.join();
        } catch (InterruptedException e) {
        }

        if (failed()) {
            throw new RuntimeException("Test FAILED: see log for details");
        } else {
            log("Test PASSED");
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private static void control(Thread thread) {
        int notifyCount = 0;

        log("control has started");
        while (!done) {
            suspend(thread);
            if (done) {
                // Double check after suspending the thread. We don't want to do the notify
                // if the main thread thinks it is done. An untimely notify during the
                // join() call will result in a deadlock.
                resume(thread);
                break;
            }
            if (notifyFramePop(thread)) {
                notifyCount++;
                log("control incremented notifyCount to " + notifyCount);
            }
            resume(thread);
            int waitCount = 0;
            while (notifyCount != getPopCount()) {
                sleep(1);
                waitCount++;
                if (waitCount > 1000) {
                    break;
                }
            }
            if (waitCount > 100) {
                log("About to fail. notifyCount=" + notifyCount + " getPopCount()=" + getPopCount());
                throw new RuntimeException("Test FAILED: Waited too long for notify: " + waitCount);
            }
        }
        log("control has finished: " + notifyCount);
    }

    private native static void suspend(Thread thread);
    private native static void resume(Thread thread);
    private native static int getPopCount();
    private native static boolean failed();
    private native static boolean canGenerateFramePopEvents();
    private native static void setFramePopNotificationMode(Thread thread);
    private native static boolean notifyFramePop(Thread thread);

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static int fetchIntFoo() {
        return 13;
    }

    private static int fetchIntBar() {
        return 33;
    }

    private static int foo() {
        return fetchIntFoo();
    }

    private static int bar() {
        return fetchIntBar();
    }
}

