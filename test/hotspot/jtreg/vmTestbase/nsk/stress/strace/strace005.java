/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress
 *
 * @summary converted from VM testbase nsk/stress/strace/strace005.
 * VM testbase keywords: [stress, strace]
 * VM testbase readme:
 * DESCRIPTION
 *     The test checks up java.lang.Thread.getStackTrace() method for many threads,
 *     that recursively invoke pure java and native methods by turns in running
 *     mode ("alive" stack).
 *     The test fails if:
 *     - amount of stack trace elements is more than depth of recursion plus
 *       four elements corresponding to invocations of Thread.run(), Thread.wait(),
 *       Thread.exit(), Thread.yield() and ThreadGroup.remove() methods;
 *     - there is at least one element corresponding to invocation of unexpected
 *       method.
 *     This test is almost the same as nsk.stress.strace.strace001 and
 *     nsk.stress.strace.strace003 except for the recursive methods are
 *     pure java and native one.
 *
 * @library /vmTestbase
 *          /test/lib
 * @run main/othervm/native nsk.stress.strace.strace005
 */

package nsk.stress.strace;


/**
 * The test checks up <code>java.lang.Thread.getStackTrace()</code> method for many threads,
 * that recursively invoke pure java and native methods by turns in running mode
 * ("alive" stack).
 * <p>
 * <p>The test creates <code>THRD_COUNT</code> instances of <code>strace005Thread</code>
 * class, tries to get their stack traces and checks up that returned array contains
 * correct stack frames.</p>
 */
public class strace005 extends StraceBase {

    static final int DEPTH = 500;
    static final int THRD_COUNT = 100;
    static final int REPEAT_COUNT = 10;

    static volatile boolean isLocked = false;

    static Object waitStart = new Object();

    static strace005Thread[] threads;
    static StackTraceElement[][] snapshots = new StackTraceElement[THRD_COUNT][];

    volatile int achivedCount = 0;

    public static void main(String[] args) {
        strace005 test = new strace005();
        boolean res = true;

        for (int j = 0; j < REPEAT_COUNT; j++) {
            test.startThreads();

            if (!test.makeSnapshot(j + 1)) res = false;

            display("waiting for threads finished\n");
            test.finishThreads();
        }

        if (!res) {
            new RuntimeException("***>>>Test failed<<<***");
        }

    }

    void startThreads() {
        threads = new strace005Thread[THRD_COUNT];
        achivedCount = 0;

        String tmp_name;
        for (int i = 0; i < THRD_COUNT; i++) {
            tmp_name = "strace005Thread" + Integer.toString(i);
            threads[i] = new strace005Thread(this, tmp_name);
//            threads[i].setPriority(Thread.MIN_PRIORITY);
        }

        for (int i = 0; i < THRD_COUNT; i++) {
            threads[i].start();
        }

        waitFor("all threads started ...");
        synchronized (waitStart) {
            isLocked = true;
            waitStart.notifyAll();
        }
        try {
            Thread.yield();
            Thread.sleep(1);
        } catch (InterruptedException e) {
            complain("" + e);
        }
    }

    void waitFor(String msg) {
        if (msg.length() > 0)
            display("waiting for " + msg);

        while (achivedCount < THRD_COUNT) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                complain("" + e);
            }
        }
        achivedCount = 0;
    }

    boolean makeSnapshot(int repeat_number) {
        // wait for native resolution completed (all threads have finished recursiveMethod2)
        boolean isNativeResolved = false;
        while (!isNativeResolved) {
            try {
                isNativeResolved = true;
                for (int i = 0; i < threads.length; ++i)
                    if (!threads[i].isNativeResolved)
                        isNativeResolved = false;
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }

        for (int i = 0; i < threads.length; i++) {
            snapshots[i] = threads[i].getStackTrace();
        }

        return checkTraces(repeat_number);
    }

    boolean checkTraces(int repeat_number) {
        StackTraceElement[] elements;

        boolean res = true;
        display(">>> snapshot " + repeat_number);
        int expectedCount = DEPTH + 1;

        for (int i = 0; i < threads.length; i++) {
            elements = snapshots[i];

            if (elements == null || elements.length == 0)
                continue;

            if (elements.length > 0) {
                display("\tchecking " + threads[i].getName()
                        + "(trace elements: " + elements.length + ")");
            }

            if (elements.length > expectedCount) {
                complain(threads[i].getName() + ">Contains more then " +
                        +expectedCount + " elements");
            }

            for (int j = 0; j < elements.length; j++) {
                if (!checkElement(elements[j])) {
                    complain(threads[i].getName() + ">Unexpected method name: "
                            + elements[j].getMethodName());
                    complain("\tat " + j + " position");
                    if (elements[j].isNativeMethod()) {
                        complain("\tline number: (native method)");
                        complain("\tclass name: " + elements[j].getClassName());
                    } else {
                        complain("\tline number: " + elements[j].getLineNumber());
                        complain("\tclass name: " + elements[j].getClassName());
                        complain("\tfile name: " + elements[j].getFileName());
                    }
                    res = false;
                }
            }
        }
        return res;
    }

    void finishThreads() {
        try {
            for (int i = 0; i < threads.length; i++) {
                if (threads[i].isAlive())
                    threads[i].join(waitTime / THRD_COUNT);
            }
        } catch (InterruptedException e) {
            complain("" + e);
        }
        isLocked = false;
    }

}

/**
 * The test creates many instances of <code>strace005Thread</code> class and tries
 * to get their stack traces.
 */
class strace005Thread extends Thread {

    private int currentDepth = 0;
    public boolean isNativeResolved = false;

    strace005 test;

    static {
        try {
            System.loadLibrary("strace005");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Could not load strace005 library");
            System.err.println("java.library.path:"
                    + System.getProperty("java.library.path"));
            throw e;
        }
    }

    strace005Thread(strace005 test, String name) {
        this.test = test;
        setName(name);
    }

    public void run() {

        recursiveMethod1();

    }

    void recursiveMethod1() {

        currentDepth++;

        if (currentDepth == 1) {
            synchronized (test) {
                test.achivedCount++;
            }

            int alltime = 0;
            while (!strace005.isLocked) {
                synchronized (test) {
                    try {
                        test.wait(1);
                        alltime++;
                    } catch (InterruptedException e) {
                        strace005.complain("" + e);
                    }
                    if (alltime > strace005.waitTime) {
                        throw new RuntimeException("out of wait time");
                    }
                }
            }
        } else if (currentDepth > 1 && !isNativeResolved)
            isNativeResolved = true;

        if (strace005.DEPTH - currentDepth > 0) {
            try {
                Thread.yield();
                recursiveMethod2();
            } catch (StackOverflowError e) {
                // ignore this exception
            }
        }

        currentDepth--;
    }

    native void recursiveMethod2();
}
