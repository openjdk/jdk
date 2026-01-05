/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.concurrent.CountDownLatch;

/*
 * @test
 * @bug 8356222
 * @summary Test jcmd Thread.print command for "waiting on the Class initialization monitor" case
 * @requires vm.continuations
 * @library /test/lib
 * @run main ClassInitMonitorVThread
 */

class LongInitClass {
    static {
        ClassInitMonitorVThread.longInitClass_waiting = true;
        while (ClassInitMonitorVThread.longInitClass_wait) {
            try {
                Thread.sleep(10);
            } catch (Exception ex) {
            }
        }
        ClassInitMonitorVThread.longInitClass_waiting = false;
    }

    LongInitClass() {}
}

public class ClassInitMonitorVThread {
    static volatile boolean longInitClass_wait;
    static volatile boolean longInitClass_waiting;

    public static void main(String[] args) throws InterruptedException {
        try {
            // 1st thread starts class initialization
            longInitClass_wait = true;
            longInitClass_waiting = false;
            Thread vthread1 = Thread.ofVirtual().name("Loader1").start(new Loader(null));
            while (!longInitClass_waiting) {
                Thread.sleep(10);
            }

            // 2nd thread is blocked at class initialization
            // thread state is "RUNNING", so just wait some time after the thread is ready
            CountDownLatch loaderReady = new CountDownLatch(1);
            Thread vthread2 = Thread.ofVirtual().name("Loader2").start(new Loader(loaderReady));
            loaderReady.await();

            Thread.sleep(100);
            // try up to 20 times to avoid failures on slow environment
            for (int iter = 20; iter > 0; iter--) {
                try {
                    verify(vthread2);
                    break;
                } catch (RuntimeException ex) {
                    if (iter == 0) {
                        throw ex;
                    }
                    System.out.println("Failed with: " + ex.getMessage() + ", retrying...");
                    System.out.println();
                }
                Thread.sleep(1000);
            }
        } finally {
            longInitClass_wait = false;
        }
    }

    static void verify(Thread vthread2) {
        boolean silent = true;
        OutputAnalyzer output = new PidJcmdExecutor().execute("Thread.print -l", silent);
        String out = output.getStdout();
        String carrierPrefix = "Carrying virtual thread #" + vthread2.threadId();
        String vthreadPrefix = "Mounted virtual thread " + "#" + vthread2.threadId();
        int carrierStart = out.indexOf(carrierPrefix);
        int vthreadStart = out.indexOf(vthreadPrefix);
        int vthreadEnd = out.indexOf("\n\n", vthreadStart);
        String carrierOut = out.substring(carrierStart, vthreadStart);
        String vthreadOut = out.substring(vthreadStart, vthreadEnd);

        System.out.println("carrier: " + carrierOut);
        System.out.println("vthread: " + vthreadOut);

        String waitText = "- waiting on the Class initialization monitor for LongInitClass";

        if (!vthreadOut.contains(waitText)) {
            throw new RuntimeException("Vthread does not contain the lock");
        }
        if (carrierOut.contains(waitText)) {
            throw new RuntimeException("Carrier does contain the lock");
        }
    }

    static class Loader implements Runnable {
        CountDownLatch ready;
        Loader(CountDownLatch ready) {
            this.ready = ready;
        }
        public void run() {
            try {
                if (ready != null) {
                    ready.countDown();
                }
                Class<?> myClass = Class.forName("LongInitClass");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
