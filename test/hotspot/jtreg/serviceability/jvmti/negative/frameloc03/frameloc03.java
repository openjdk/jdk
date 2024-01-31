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
 * @summary converted from VM Testbase nsk/jvmti/GetFrameLocation/frameloc003.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI function
 *         GetFrameLocation(thread, depth, methodPtr, locationPtr).
 *     The test checks if the function returns:
 *       - JVMTI_ERROR_INVALID_THREAD if thread is not a thread object
 *       - JVMTI_ERROR_ILLEGAL_ARGUMENT if depth less than zero
 *       - JVMTI_ERROR_NULL_POINTER if any of pointers is null
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:frameloc03 frameloc03
 */

public class frameloc03 {

    static {
        System.loadLibrary("frameloc03");
    }

    public static Object lockStart = new Object();
    public static Object lockFinish = new Object();

    native static int check(Thread thread);

    public static void main(String args[]) {
        TestThread t = new TestThread();

        synchronized (lockStart) {
            t.start();
            try {
                lockStart.wait();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }

        }

        int result = check(t);

        synchronized (lockFinish) {
            lockFinish.notify();
        }
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }

        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }

    static class TestThread extends Thread {
        public void run() {
            synchronized (lockFinish) {
                synchronized (lockStart) {
                    lockStart.notify();
                }
                try {
                    lockFinish.wait();
                } catch (InterruptedException e) {
                    throw new Error("Unexpected: " + e);
                }
            }
        }
    }
}
