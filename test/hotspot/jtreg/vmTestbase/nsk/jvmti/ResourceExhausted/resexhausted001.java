/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
package nsk.jvmti.ResourceExhausted;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import nsk.share.Consts;
import nsk.share.test.Stresser;

public class resexhausted001 {
    static {
        System.loadLibrary("resexhausted");
    }

    static final long MAX_ITERATIONS = 2000000L; // Resasonable limit on number of threads

    static Object hanger = new Object();
    static boolean threadsDone;
    static AtomicInteger threadCount = new AtomicInteger();

    public static int run(String args[], PrintStream out) {

        Stresser stress = new Stresser(args);

        int count = 0;
        threadsDone = false;
        threadCount.set(0);
        Helper.resetExhaustedEvent();

        System.out.println("Creating threads...");
        stress.start(MAX_ITERATIONS);
        try {
            while ( stress.iteration() ) {
                makeThread();
            }

            System.out.println("Can't reproduce OOME due to a limit on iterations/execution time. Test was useless.");
            return Consts.TEST_PASSED;

        } catch (OutOfMemoryError e) {
            count = threadCount.get();
            threadsDone = true;
            synchronized (hanger) {
                hanger.notifyAll();
            }
        } finally {
            stress.finish();
        }

        while ( threadCount.get() > 2 ) {
            try { Thread.sleep(100); } catch ( InterruptedException swallow ) {}
        }

        System.gc();
        if (!Helper.checkResult("creating " + count + " threads")) {
            return Consts.TEST_FAILED;
        }

        return Consts.TEST_PASSED;
    }

    static Thread makeThread() {
        final Thread thr = new Thread(new Runnable() {
            public void run() {
                threadCount.getAndIncrement();
                while (!threadsDone) {
                    try {
                        synchronized (hanger) {
                            hanger.wait();
                        }
                    } catch (InterruptedException ignored) {}
                }
                threadCount.getAndDecrement();
            }
        }, "fleece");
        thr.start();
        return thr;
    }

    public static void main(String[] args) {
        args = nsk.share.jvmti.JVMTITest.commonInit(args);

        int result = run(args, System.out);
        System.out.println(result == Consts.TEST_PASSED ? "TEST PASSED" : "TEST FAILED");
        System.exit(result + Consts.JCK_STATUS_BASE);
    }
}
