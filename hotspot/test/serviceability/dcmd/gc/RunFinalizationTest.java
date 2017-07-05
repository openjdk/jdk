/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.Utils;

/*
 * @test
 * @summary Test of diagnostic command GC.run_finalization
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build jdk.test.lib.*
 * @build jdk.test.lib.dcmd.*
 * @run main/othervm RunFinalizationTest
 */
public class RunFinalizationTest {
    private static final long TIMEOUT = Utils.adjustTimeout(15000); // 15s
    private static final Phaser ph = new Phaser(3);
    static volatile boolean wasFinalized = false;
    static volatile boolean wasInitialized = false;

    static class MyObject {
        public MyObject() {
            /* Make sure object allocation/deallocation is not optimized out */
            wasInitialized = true;
        }

        protected void finalize() {
            if (!Thread.currentThread().getName().equals("Finalizer")) {
                wasFinalized = true;
                ph.arrive();
            } else {
                ph.arriveAndAwaitAdvance();
            }
        }
    }

    public static MyObject o;

    private static void run(CommandExecutor executor) {
        o = new MyObject();
        o = null;
        System.gc();
        executor.execute("GC.run_finalization");

        System.out.println("Waiting for signal from finalizer");

        long targetTime = System.currentTimeMillis() + TIMEOUT;
        while (System.currentTimeMillis() < targetTime) {
            try {
                ph.awaitAdvanceInterruptibly(ph.arrive(), 200, TimeUnit.MILLISECONDS);
                System.out.println("Received signal");
                break;
            } catch (InterruptedException e) {
                fail("Test error: Interrupted while waiting for signal from finalizer", e);
            } catch (TimeoutException e) {
                System.out.println("Haven't received signal in 200ms. Retrying ...");
            }
        }

        if (!wasFinalized) {
            fail("Test failure: Object was not finalized");
        }
    }

    public static void main(String ... args) {
        MyObject o = new MyObject();
        o = null;
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            run(new JMXExecutor());
        }));
    }

    private static void fail(String msg, Exception e) {
        throw new Error(msg, e);
    }

    private static void fail(String msg) {
        throw new Error(msg);
    }
}
