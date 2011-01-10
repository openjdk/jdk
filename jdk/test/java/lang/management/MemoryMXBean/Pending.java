/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4530538
 * @summary Basic unit test of
 *          RuntimeMXBean.getObjectPendingFinalizationCount()
 *          1. GC and runFinalization() to get the current pending number
 *          2. Create some number of objects with reference and without ref.
 *          3. Clear all the references
 *          4. GC and runFinalization() and the finalizable objects should
 *             be garbage collected.
 * @author  Alexei Guibadoulline and Mandy Chung
 *
 */

import java.lang.management.*;

public class Pending {
    final static int NO_REF_COUNT = 600;
    final static int REF_COUNT = 600;
    final static int TOTAL_FINALIZABLE = (NO_REF_COUNT + REF_COUNT);
    private static int finalized = 0;
    private static MemoryMXBean mbean
        = ManagementFactory.getMemoryMXBean();

    private static final String INDENT = "      ";
    private static void printFinalizerInstanceCount() {
        if (!trace) return;

        int count = sun.misc.VM.getFinalRefCount();
        System.out.println(INDENT + "Finalizable object Count = " + count);

        count = sun.misc.VM.getPeakFinalRefCount();
        System.out.println(INDENT + "Peak Finalizable object Count = " + count);
    }

    private static boolean trace = false;
    public static void main(String argv[]) throws Exception {
        if (argv.length > 0 && argv[0].equals("trace")) {
            trace = true;
        }

        try {
            if (trace) {
                // Turn on verbose:gc to track GC
                mbean.setVerbose(true);
            }
            test();
        } finally {
            if (trace) {
                mbean.setVerbose(false);
            }
        }
        System.out.println("Test passed.");
    }

    private static void test() throws Exception {
        // Clean the memory and remove all objects that are pending
        // finalization
        System.gc();
        Runtime.getRuntime().runFinalization();

        // Let the finalizer to finish
        try {
            Thread.sleep(200);
        } catch (Exception e) {
            throw e;
        }

        // Create a number of new objects but no references to them
        int startCount = mbean.getObjectPendingFinalizationCount();

        System.out.println("Number of objects pending for finalization:");
        System.out.println("   Before creating object: " + startCount +
            " finalized = " + finalized);
        printFinalizerInstanceCount();

        for (int i = 0; i < NO_REF_COUNT; i++) {
            new MyObject();
        }

        Snapshot snapshot = getSnapshot();
        System.out.println("   Afer creating objects with no ref: " + snapshot);
        printFinalizerInstanceCount();

        Object[] objs = new Object[REF_COUNT];
        for (int i = 0; i < REF_COUNT; i++) {
            objs[i] = new MyObject();
        }
        snapshot = getSnapshot();
        System.out.println("   Afer creating objects with ref: " + snapshot);
        printFinalizerInstanceCount();

        // Now check the expected count - GC and runFinalization will be
        // invoked.
        checkFinalizerCount(NO_REF_COUNT, 0);

        // Clean the memory and remove all objects that are pending
        // finalization again
        objs = null;
        snapshot = getSnapshot();
        System.out.println("Clear all references finalized = " + snapshot);
        printFinalizerInstanceCount();

        checkFinalizerCount(TOTAL_FINALIZABLE, NO_REF_COUNT);

        snapshot = getSnapshot();
        printFinalizerInstanceCount();

        // Check the mbean now
        if (snapshot.curFinalized != TOTAL_FINALIZABLE) {
            throw new RuntimeException("Wrong number of finalized objects "
                                     + snapshot + ". Expected "
                                     + TOTAL_FINALIZABLE);
        }

        if (startCount != 0 || snapshot.curPending != 0) {
            throw new RuntimeException("Wrong number of objects pending "
                                     + "finalization start = " + startCount
                                     + " end = " + snapshot);
        }

    }

    private static void checkFinalizerCount(int expectedTotal, int curFinalized)
        throws Exception {
        int prevCount = -1;
        Snapshot snapshot = getSnapshot();
        if (snapshot.curFinalized != curFinalized) {
            throw new RuntimeException(
                    "Unexpected finalized objects: " + snapshot +
                    " but expected = " + curFinalized);
        }
        int MAX_GC_LOOP = 6;
        for (int i = 1;
             snapshot.curFinalized != expectedTotal && i <= MAX_GC_LOOP;
             i++) {
            System.gc();

            // Pause to give a chance to Finalizer thread to run
            pause();

            printFinalizerInstanceCount();
            // Race condition may occur; attempt to check this
            // a few times before throwing exception.
            for (int j = 0; j < 5; j++) {
                // poll for another current pending count
                snapshot = getSnapshot();
                if (snapshot.curFinalized == expectedTotal ||
                    snapshot.curPending != 0) {
                    break;
                }
            }
            System.out.println("   After GC " + i + ": " + snapshot);

            Runtime.getRuntime().runFinalization();

            // Pause to give a chance to Finalizer thread to run
            pause();

            snapshot = getSnapshot();
            if (snapshot.curFinalized == expectedTotal &&
                snapshot.curPending != 0) {
                throw new RuntimeException(
                    "Unexpected current number of objects pending for " +
                    "finalization: " + snapshot + " but expected = 0");
            }

            System.out.println("   After runFinalization " + i + ": " + snapshot);
            printFinalizerInstanceCount();

            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                throw e;
            }
        }
        if (snapshot.curFinalized != expectedTotal) {
            throw new RuntimeException(
                "Unexpected current number of objects pending for " +
                "finalization: " + snapshot + " but expected > 0");
        }
    }

    private static Object lock = new Object();
    private static class MyObject {
        Object[] dummy = new Object[10];
        public void finalize () {
            synchronized (lock) {
                finalized++;
            }
        }
    }

    static class Snapshot {
        public int curFinalized;
        public int curPending;
        Snapshot(int f, int p) {
            curFinalized = f;
            curPending = p;
        }
        public String toString() {
            return "Current finalized = " + curFinalized +
                   " Current pending = " + curPending;
        }
    }

    private static Snapshot getSnapshot() {
        synchronized (lock) {
            int curCount = mbean.getObjectPendingFinalizationCount();
            return new Snapshot(finalized, curCount);
        }
    }

    private static Object pauseObj = new Object();
    private static void pause() {
        // Enter lock a without blocking
        synchronized (pauseObj) {
            try {
                // may need to tune this timeout for different platforms
                pauseObj.wait(20);
            } catch (Exception e) {
                System.err.println("Unexpected exception.");
                e.printStackTrace(System.err);
            }
        }
    }
}
