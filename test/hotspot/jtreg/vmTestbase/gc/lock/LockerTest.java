/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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
package gc.lock;

import jdk.test.whitebox.WhiteBox;
import nsk.share.runner.*;
import nsk.share.gc.*;
import nsk.share.gc.gp.GarbageUtils;
import nsk.share.gc.lock.*;
import nsk.share.test.ExecutionController;

/**
 * Test how GC is affected by locking.
 *
 * A number of threads is started. Each one locks and eats memory.
 */
public class LockerTest extends ThreadedGCTest implements LockersAware {

    private Lockers lockers;
    private long objectSize = 1000;

    private class Worker implements Runnable {

        byte[] rezerve = new byte[1024];
        private Locker locker = lockers.createLocker(rezerve);

        public Worker() {
            locker.enable();
        }

        public void run() {
            ExecutionController stresser = getExecutionController();
            // Use only 30% of the heap.
            final long testMemorySize = 3 * Runtime.getRuntime().maxMemory() / 10;

            while (stresser.continueExecution()) {
                locker.lock();
                GarbageUtils.engageGC(testMemorySize);
                locker.unlock();
            }
        }
    }

    protected Runnable createRunnable(int i) {
        return new Worker();
    }

    public void setLockers(Lockers lockers) {
        this.lockers = lockers;
    }

    public static void main(String[] args) {
        RunParams.getInstance().setRunMemDiagThread(false);
        GC.runTest(new LockerTest(), args);
    }
}
