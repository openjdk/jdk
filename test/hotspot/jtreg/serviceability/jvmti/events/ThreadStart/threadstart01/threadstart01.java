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

/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/ThreadStart/threadstart001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras, quarantine]
 * VM Testbase comments: 8016181
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI event callback function ThreadStart.
 *     The test checks if the event is ganerated by a new
 *     thread before its initial method executes.
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:threadstart01 threadstart01
 */

public class threadstart01 {

    final static int THREADS_LIMIT = 100;
    final static String NAME_PREFIX = "threadstart01-";

    static {
        System.loadLibrary("threadstart01");
    }

    native static void getReady(int i, String name);
    native static int check();

    static volatile int thrCount = 0;

    public static void main(String args[]) {
        TestThread t = new TestThread(NAME_PREFIX + thrCount);
        getReady(THREADS_LIMIT, NAME_PREFIX);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }
        int result = check();
        if (result != 0) {
            throw new RuntimeException("Unexpected status: " + result);
        }
    }

    static class TestThread extends Thread {
        public TestThread(String name) {
            super(name);
        }
        public void run() {
            thrCount++;
            if (thrCount < THREADS_LIMIT) {
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
