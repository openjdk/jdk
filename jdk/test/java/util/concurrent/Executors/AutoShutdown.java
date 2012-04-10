/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6399443
 * @run main/othervm AutoShutdown
 * @summary Check for auto-shutdown and gc of singleThreadExecutors
 * @author Martin Buchholz
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.Executors.*;
import java.util.concurrent.Phaser;

public class AutoShutdown {
    private static void waitForFinalizersToRun() {
        for (int i = 0; i < 2; i++)
            tryWaitForFinalizersToRun();
    }

    private static void tryWaitForFinalizersToRun() {
        System.gc();
        final CountDownLatch fin = new CountDownLatch(1);
        new Object() { protected void finalize() { fin.countDown(); }};
        System.gc();
        try { fin.await(); }
        catch (InterruptedException ie) { throw new Error(ie); }
    }

    private static void realMain(String[] args) throws Throwable {
        final Phaser phaser = new Phaser(3);
        Runnable trivialRunnable = new Runnable() {
            public void run() {
                phaser.arriveAndAwaitAdvance();
            }
        };
        int count0 = Thread.activeCount();
        Executor e1 = newSingleThreadExecutor();
        Executor e2 = newSingleThreadExecutor(defaultThreadFactory());
        e1.execute(trivialRunnable);
        e2.execute(trivialRunnable);
        phaser.arriveAndAwaitAdvance();
        equal(Thread.activeCount(), count0 + 2);
        e1 = e2 = null;
        for (int i = 0; i < 10 && Thread.activeCount() > count0; i++)
            tryWaitForFinalizersToRun();
        for (int i = 0; i < 10; ++i) { // give JVM a chance to settle.
            if (Thread.activeCount() == count0)
                return;
            Thread.sleep(1000);
        }
        equal(Thread.activeCount(), count0);
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
