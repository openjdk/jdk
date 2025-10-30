/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test id=Xint_outer_inner
 * @requires vm.flagless
 * @summary Tests recursive locking in -Xint in outer then inner mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -Xint
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 5 1
 */

/*
 * @test id=Xint_alternate_AB
 * @requires vm.flagless
 * @summary Tests recursive locking in -Xint in alternate A and B mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -Xint
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 5 2
 */

/*
 * @test id=C1_outer_inner
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @summary Tests recursive locking in C1 in outer then inner mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:TieredStopAtLevel=1
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 5 1
 */

/*
 * @test id=C1_alternate_AB
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @summary Tests recursive locking in C1 in alternate A and B mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:TieredStopAtLevel=1
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 5 2
 */

/*
 * @test id=C2_outer_inner
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @summary Tests recursive locking in C2 in outer then inner mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:-EliminateNestedLocks
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 5 1
 */

/*
 * @test id=C2_alternate_AB
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @summary Tests recursive locking in C2 in alternate A and B mode.
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:-EliminateNestedLocks
 *     -Xms256m -Xmx256m
 *     TestRecursiveLocking 5 2
 */

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

public class TestRecursiveLocking {
    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final int constLockStackCapacity = WB.getLockStackCapacity();
    static final int def_mode = 2;
    static final int def_n_secs = 30;
    static final SyncThread syncThread = new SyncThread();

    // This SynchronizedObject class and the OUTER followed by INNER testing
    // model is adapted from runtime/lockStack/TestLockStackCapacity.java.
    static class SynchronizedObject {
        private int counter;

        synchronized void runInner(int depth, SynchronizedObject outer) {
            counter++;

            // There is limit on recursion, so "outer" must be
            // inflated here.
            outer.assertInflated();

            // We haven't reached the stack lock capacity (recursion
            // level), so we shouldn't be inflated here. Except for
            // monitor mode, which is always inflated.
            assertNotInflated();
            if (depth == 1) {
                return;
            } else {
                runInner(depth - 1, outer);
            }
            assertNotInflated();
        }

        synchronized void runOuter(int depth, SynchronizedObject inner) {
            counter++;

            assertNotInflated();
            if (depth == 1) {
                inner.runInner(constLockStackCapacity, this);
            } else {
                runOuter(depth - 1, inner);
            }
            assertInflated();
        }

        // This test nests x recursive locks of INNER, in x recursive
        // locks of OUTER. The number x is taken from the max number
        // of elements in the lock stack.
        public void runOuterInnerTest() {
            final SynchronizedObject OUTER = new SynchronizedObject();
            final SynchronizedObject INNER = new SynchronizedObject();

            // Just checking since they are new objects:
            OUTER.assertNotInflated();
            INNER.assertNotInflated();

            synchronized (OUTER) {
                OUTER.counter++;

                OUTER.assertNotInflated();
                INNER.assertNotInflated();
                OUTER.runOuter(constLockStackCapacity - 1, INNER);
                OUTER.assertInflated();
                INNER.assertNotInflated();
            }

            // Verify that the nested monitors have been properly released:
            syncThread.verifyCanBeSynced(OUTER);
            syncThread.verifyCanBeSynced(INNER);

            Asserts.assertEquals(OUTER.counter, constLockStackCapacity);
            Asserts.assertEquals(INNER.counter, constLockStackCapacity);
        }

        synchronized void runA(int depth, SynchronizedObject B) {
            counter++;


            // First time we lock A, A is the only one on the lock
            // stack.
            if (counter == 1) {
                assertNotInflated();
            } else {
                // Second time we want to lock A, the lock stack
                // looks like this [A, B]. Lightweight locking
                // doesn't allow interleaving ([A, B, A]), instead
                // it inflates A and removes it from the lock
                // stack. Which leaves us with only [B] on the
                // lock stack. After more recursions it will grow
                // to [B, B ... B].
                assertInflated();
            }


            // Call runB() at the same depth as runA's depth:
            B.runB(depth, this);
        }

        synchronized void runB(int depth, SynchronizedObject A) {
            counter++;


            // Legacy tolerates endless recursions. While testing
            // lightweight we don't go deeper than the size of the
            // lock stack, which in this test case will be filled
            // with a number of B-elements. See comment in runA()
            // above for more info.
            assertNotInflated();

            if (depth == 1) {
                // Reached LockStackCapacity in depth so we're done.
                return;
            } else {
                A.runA(depth - 1, this);
            }
        }

        // This test alternates by locking A and B.
        public void runAlternateABTest() {
            final SynchronizedObject A = new SynchronizedObject();
            final SynchronizedObject B = new SynchronizedObject();

            // Just checking since they are new objects:
            A.assertNotInflated();
            B.assertNotInflated();

            A.runA(constLockStackCapacity, B);

            // Verify that the nested monitors have been properly released:
            syncThread.verifyCanBeSynced(A);
            syncThread.verifyCanBeSynced(B);

            Asserts.assertEquals(A.counter, constLockStackCapacity);
            Asserts.assertEquals(B.counter, constLockStackCapacity);

            // Here A can be either inflated or not because A is not
            // locked anymore and subject to deflation.

            B.assertNotInflated();

        }

        void assertNotInflated() {
            Asserts.assertFalse(WB.isMonitorInflated(this));
        }

        void assertInflated() {
            Asserts.assertTrue(WB.isMonitorInflated(this));
        }
    }

    static void usage() {
        System.err.println();
        System.err.println("Usage: java TestRecursiveLocking [n_secs]");
        System.err.println("       java TestRecursiveLocking n_secs [mode]");
        System.err.println();
        System.err.println("where:");
        System.err.println("    n_secs  ::= > 0");
        System.err.println("            Default n_secs is " + def_n_secs + ".");
        System.err.println("    mode    ::= 1 - outer and inner");
        System.err.println("            ::= 2 - alternate A and B");
        System.err.println("            Default mode is " + def_mode + ".");
        System.exit(1);
    }

    public static void main(String... argv) throws Exception {
        int mode = def_mode;
        int n_secs = def_n_secs;

        if (argv.length != 0 && argv.length != 1 && argv.length != 2) {
            usage();
        } else if (argv.length > 0) {
            try {
                n_secs = Integer.parseInt(argv[0]);
                if (n_secs <= 0) {
                    throw new NumberFormatException("Not > 0: '" + argv[0]
                                                    + "'");
                }
            } catch (NumberFormatException nfe) {
                System.err.println();
                System.err.println(nfe);
                System.err.println("ERROR: '" + argv[0]
                                   + "': invalid n_secs value.");
                usage();
            }

            if (argv.length > 1) {
                try {
                    mode = Integer.parseInt(argv[1]);
                    if (mode != 1 && mode != 2) {
                        throw new NumberFormatException("Not 1 -> 2: '"
                                                        + argv[1] + "'");
                    }
                } catch (NumberFormatException nfe) {
                    System.err.println();
                    System.err.println(nfe);
                    System.err.println("ERROR: '" + argv[1]
                                       + "': invalid mode value.");
                    usage();
                }
            }
        }

        System.out.println("INFO: LockStackCapacity=" + constLockStackCapacity);
        System.out.println("INFO: n_secs=" + n_secs);
        System.out.println("INFO: mode=" + mode);

        long loopCount = 0;
        long endTime = System.currentTimeMillis() + n_secs * 1000;

        syncThread.waitForStart();

        while (System.currentTimeMillis() < endTime) {
            loopCount++;
            SynchronizedObject syncObj = new SynchronizedObject();
            switch (mode) {
            case 1:
                syncObj.runOuterInnerTest();
                break;

            case 2:
                syncObj.runAlternateABTest();
                break;

            default:
                throw new RuntimeException("bad mode parameter: " + mode);
            }
        }

        syncThread.setDone();
        try {
            syncThread.join();
        } catch (InterruptedException ie) {
            // This should not happen.
            ie.printStackTrace();
        }

        System.out.println("INFO: main executed " + loopCount + " loops in "
                           + n_secs + " seconds.");
    }
}

class SyncThread extends Thread {
    static final boolean verbose = false;  // set to true for debugging
    private boolean done = false;
    private boolean haveWork = false;
    private Object obj;
    private Object waiter = new Object();

    public void run() {
        if (verbose) System.out.println("SyncThread: running.");
        synchronized (waiter) {
            // Let main know that we are running:
            if (verbose) System.out.println("SyncThread: notify main running.");
            waiter.notify();

            while (!done) {
                if (verbose) System.out.println("SyncThread: waiting.");
                try {
                    waiter.wait();
                } catch (InterruptedException ie) {
                    // This should not happen.
                    ie.printStackTrace();
                }
                if (haveWork) {
                    if (verbose) System.out.println("SyncThread: working.");
                    synchronized (obj) {
                    }
                    if (verbose) System.out.println("SyncThread: worked.");
                    haveWork = false;
                    waiter.notify();
                    if (verbose) System.out.println("SyncThread: notified.");
                }
                else if (verbose) {
                    System.out.println("SyncThread: notified without work.");
                }
            }
        }
        if (verbose) System.out.println("SyncThread: exiting.");
    }

    public void setDone() {
        synchronized (waiter) {
            if (verbose) System.out.println("main: set done.");
            done = true;
            waiter.notify();
        }
    }

    public void verifyCanBeSynced(Object obj) {
        synchronized (waiter) {
            if (verbose) System.out.println("main: queueing up work.");
            this.obj = obj;
            haveWork = true;
            if (verbose) System.out.println("main: notifying SyncThread.");
            waiter.notify();
            if (verbose) System.out.println("main: waiting for SyncThread.");
            while (haveWork) {
                try {
                    waiter.wait();
                } catch (InterruptedException ie) {
                    // This should not happen.
                    ie.printStackTrace();
                }
            }
            if (verbose) System.out.println("main: waited for SyncThread.");
        }
    }

    public void waitForStart() {
        synchronized (waiter) {
            this.start();

            // Wait for SyncThread to actually get running:
            if (verbose) System.out.println("main: wait for SyncThread start.");
            try {
                waiter.wait();
            } catch (InterruptedException ie) {
                // This should not happen.
                ie.printStackTrace();
            }
            if (verbose) System.out.println("main: waited for SyncThread start.");
        }
    }
}
