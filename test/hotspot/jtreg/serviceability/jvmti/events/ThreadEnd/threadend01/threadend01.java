/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;

/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/ThreadEnd/threadend001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI event callback function ThreadEnd.
 *     The test checks if the event is ganerated by a terminating
 *     thread after its initial method has finished execution.
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:threadend01 threadend01
 */


public class threadend01 {

    final static int THREADS_LIMIT = 100;
    final static String NAME_PREFIX = "threadend01-";

    static {
        try {
            System.loadLibrary("threadend01");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load threadend01 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    native static void getReady(int i, String name);
    native static int check();

    static volatile int thrCount = THREADS_LIMIT;

    public static void main(String args[]) {
        int result = run(args, System.out);
        if (result != 0) {
            throw new RuntimeException("Unexpected status: " + result);
        }
    }

    public static int run(String args[], PrintStream out) {
        Thread t = new TestThread(NAME_PREFIX + thrCount);
        getReady(THREADS_LIMIT, NAME_PREFIX);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }
        return check();
    }

    static class TestThread extends Thread {
        public TestThread(String name) {
            super(name);
        }

        public void run() {
            thrCount--;
            if (thrCount > 0) {
                TestThread t = new TestThread(NAME_PREFIX + thrCount);
                t.start();
                try {
                    t.join();
                } catch (InterruptedException e) {
                    throw new Error("Unexpected: " + e);
                }
            }
        }
    }
}
