/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4087516
 * @summary Incorrect locking leads to deadlock in monitorCacheMaybeExpand.
 * @author Anand Palaniswamy
 * @build MonitorCacheMaybeExpand_DeadLock
 * @run main/othervm MonitorCacheMaybeExpand_DeadLock
 */

/**
 * Background on the bug:
 *
 *     The thread local monitor cache had a locking bug (till
 *     1.2beta1) where two threads trying to expand the monitor cache
 *     at the same time would cause deadlock. The code paths that the
 *     two threads must be executing for this to happen is described
 *     in the bug report.
 *
 * Caveat and red-flag:
 *
 *     Since deadlocks are very timing dependent, there is a good
 *     chance this test case will not catch the bug most of the time
 *     -- on your machine and setting, it is _possible_ that the two
 *     threads might not try a monitorCacheExpand at the same
 *     time. But in practice, on Solaris native threads, this program
 *     deadlocks the VM in about 2 seconds pretty consistently,
 *     whether MP or not.
 *
 *     The rationale for running this test despite this rather large
 *     caveat is that at worst, it can do no harm.
 *
 * The idea:
 *
 *     Is to create two monitor hungry threads.
 *
 *     Originally Tom Rodriguez and I suspected that this weird state
 *     of two threads trying to expand monitor cache can happen only
 *     if:
 *
 *         Thread 1: Is in the middle of a monitorCacheMaybeExpand.
 *         Thread 2: Runs GC and tries to freeClasses(). This causes
 *                   sysFree() to be invoked, which in turn needs a
 *                   mutex_lock -- and oops, we end up deadlocking
 *                   with 1 on green_threads.
 *
 *     Which is why this test tries to cause class GC at regular
 *     intervals.
 *
 *     Turns out that the GC is not required. Two instances of the
 *     monitor hungry threads deadlock the VM pretty quick. :-) Infact
 *     the static initializer in the forName'd classes running
 *     alongside one of the hungry threads is sufficient to
 *     deadlock. Still keep the GC stuff just-in-case (and also
 *     because I wrote it :-).
 *
 */
public class MonitorCacheMaybeExpand_DeadLock {

    /**
     * A monitor-hungry thread.
     */
    static class LotsaMonitors extends Thread {

        /** How many recursions? Could cause Java stack overflow. */
        static final int MAX_DEPTH = 800;

        /** What is our depth? */
        int depth = 0;

        /** Thread ID */
        int tid;

        /** So output will have thread number. */
        public LotsaMonitors(int tid, int depth) {
            super("LotsaMonitors #" + new Integer(tid).toString());
            this.tid = tid;
            this.depth = depth;
        }

        /** Start a recursion that grabs monitors. */
        public void run() {
            System.out.println(">>>Starting " + this.toString() + " ...");
            Thread.currentThread().yield();
            this.recurse();
            System.out.println("<<<Finished " + this.toString());
        }

        /** Every call to this method grabs an extra monitor. */
        synchronized void recurse() {
            if (this.depth > 0) {
                new LotsaMonitors(tid, depth-1).recurse();
            }
        }
    }

    /**
     * The test.
     */
    public static void main(String[] args) {
        /* Start the two of these crazy threads. */
        new LotsaMonitors(1, LotsaMonitors.MAX_DEPTH).start();
        new LotsaMonitors(2, LotsaMonitors.MAX_DEPTH).start();

        /* And sit there and GC for good measure. */
        for (int i = 0; i < MAX_GC_ITERATIONS; i++) {
            new LotsaMonitors(i+3, LotsaMonitors.MAX_DEPTH).start();
            System.out.println(">>>Loading 10 classes and gc'ing ...");
            Class[] classes = new Class[10];
            fillClasses(classes);
            classes = null;
            System.gc();
            Thread.currentThread().yield();
            System.out.println("<<<Finished loading 10 classes and gc'ing");
        }
    }

    /** How many times to GC? */
    static final int MAX_GC_ITERATIONS = 10;

    /** Load some classes into the array. */
    static void fillClasses(Class[] classes) {
        for (int i = 0; i < classes.length; i++) {
            try {
                classes[i] = Class.forName(classnames[i]);
            } catch (ClassNotFoundException cnfe) {
                cnfe.printStackTrace();
            }
        }
    }

    /** Some random classes to load. */
    private static String[] classnames = {
        "java.text.DecimalFormat",
        "java.text.MessageFormat",
        "java.util.GregorianCalendar",
        "java.util.ResourceBundle",
        "java.text.Collator",
        "java.util.Date",
        "java.io.Reader",
        "java.io.Writer",
        "java.lang.IllegalAccessException",
        "java.lang.InstantiationException",
        "java.lang.ClassNotFoundException",
        "java.lang.CloneNotSupportedException",
        "java.lang.InterruptedException",
        "java.lang.NoSuchFieldException",
        "java.lang.NoSuchMethodException",
        "java.lang.RuntimeException",
        "java.lang.ArithmeticException",
        "java.lang.ArrayStoreException",
        "java.lang.ClassCastException",
        "java.lang.StringIndexOutOfBoundsException",
        "java.lang.NegativeArraySizeException",
        "java.lang.IllegalStateException",
        "java.lang.IllegalArgumentException",
        "java.lang.NumberFormatException",
        "java.lang.IllegalThreadStateException",
        "java.lang.IllegalMonitorStateException",
        "java.lang.SecurityException",
        "java.lang.ExceptionInInitializerError"
    };

}
