/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jvmti/GetThreadState/thrstat003.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI function GetThreadState.
 *     The test checks if the function returns:
 *       - NEW              if thread has not run yet
 *       - STATE_SLEEPING   if Thread.sleep() has been called
 *       - STATE_TERMINATED if thread has exited
 * COMMENTS
 *     Converted the test to use GetThreadState instead of GetThreadStatus.
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} thrstat03.java
 * @run main/othervm/native --enable-preview  -agentlib:thrstat03 thrstat03 5
 */

public class thrstat03 {

    static final int NOT_STARTED = 0;
    static final int SLEEPING = 1;
    static final int ZOMBIE = 2;

    native static void init(int waitTime);
    native static boolean check(Thread thread, int status);

    public static Object lock = new Object();
    public static int waitTime = 2;

    public static void main(String args[]) {
      test(Thread.ofPlatform().unstarted(new TestThread()));
      test(Thread.ofVirtual().unstarted(new TestThread()));
    }

    public static void test(Thread t) {

        init(waitTime);

        check(t, NOT_STARTED);

        synchronized (lock) {
            t.start();
            try {
                lock.wait();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }

        }

        check(t, SLEEPING);

        t.interrupt();
        try {
            t.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }

        if (!check(t, t.isVirtual() ? NOT_STARTED : ZOMBIE)) {
            throw new RuntimeException();
        }
    }


    static class TestThread implements Runnable {
        public void run() {
            synchronized (lock) {
                lock.notify();
            }
            try {
                Thread.sleep(waitTime*60000);
            } catch (InterruptedException e) {
                // OK, it's expected
            }
        }
    }
}
