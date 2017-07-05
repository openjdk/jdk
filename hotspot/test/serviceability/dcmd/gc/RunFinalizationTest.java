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

import org.testng.annotations.Test;
import org.testng.Assert;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.java.testlibrary.dcmd.CommandExecutor;
import com.oracle.java.testlibrary.dcmd.JMXExecutor;

/*
 * @test
 * @summary Test of diagnostic command GC.run_finalization
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build com.oracle.java.testlibrary.*
 * @build com.oracle.java.testlibrary.dcmd.*
 * @run testng RunFinalizationTest
 */
public class RunFinalizationTest {
    static ReentrantLock lock = new ReentrantLock();
    static Condition cond = lock.newCondition();
    static volatile boolean wasFinalized = false;
    static volatile boolean wasInitialized = false;

    class MyObject {
        public MyObject() {
            /* Make sure object allocation/deallocation is not optimized out */
            wasInitialized = true;
        }

        protected void finalize() {
            lock.lock();
            wasFinalized = true;
            cond.signalAll();
            lock.unlock();
        }
    }

    public static MyObject o;

    public void run(CommandExecutor executor) {
        lock.lock();
        o = new MyObject();
        o = null;
        System.gc();
        executor.execute("GC.run_finalization");

        int waited = 0;
        int waitTime = 15;

        try {
            System.out.println("Waiting for signal from finalizer");

            while (!cond.await(waitTime, TimeUnit.SECONDS)) {
                waited += waitTime;
                System.out.println(String.format("Waited %d seconds", waited));
            }

            System.out.println("Received signal");
        } catch (InterruptedException e) {
            Assert.fail("Test error: Interrupted while waiting for signal from finalizer", e);
        } finally {
            lock.unlock();
        }

        if (!wasFinalized) {
            Assert.fail("Test failure: Object was not finalized");
        }
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
