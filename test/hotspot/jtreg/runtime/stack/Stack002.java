/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM testbase nsk/stress/stack/stack002.
 * VM testbase keywords: [stress, quick, stack, nonconcurrent]
 * VM testbase readme:
 * DESCRIPTION
 *     Provoke StackOverflowError by infinite recursion in Java method,
 *     intercept the exception and continue to invoke that method until
 *     the test exceeds timeout, or until Java VM crashes.
 * COMMENTS
 *     I believe that the test causes HS crashes due to the following bug:
 *     4330318 (P2/S2) NSK test fails as An irrecoverable stack overflow
 *     See also bugs (lots of bugs!):
 *     Evaluated:
 *     4217960 [native stack overflow bug] reflection test causes crash
 *     Accepted:
 *     4285716 native stack overflow causes crash on Solaris
 *     4281578 Second stack overflow crashes HotSpot VM
 *     Closed (duplicate):
 *     4027933     Native stack overflows not detected or handled correctly
 *     4134353     (hpi) sysThreadCheckStack is a no-op on win32
 *     4185411     Various crashes when using recursive reflection.
 *     4167055     infinite recursion in FindClass
 *     4222359     Infinite recursion crashes jvm
 *     Closed (will not fix):
 *     4231968 StackOverflowError in a native method causes Segmentation Fault
 *     4254634     println() while catching StackOverflowError causes hotspot VM crash
 *     4302288 the second stack overflow causes Classic VM to exit on win32
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @run main/othervm/timeout=900 Stack002
 */

public class Stack002 {
    static final long timeout = 10000; // 10 seconds

    public static void main(String[] args) {
        Tester tester = new Tester();
        Timer timer = new Timer(tester);
        timer.start();
        tester.start();
        while (timer.isAlive()) {
            try {
                timer.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        System.out.println("Maximal depth: " + tester.maxdepth);
    }

    private static class Tester extends Thread {
        int maxdepth;
        public volatile boolean stop;

        public Tester() {
            maxdepth = 0;
            stop = false;
        }

        public void run() {
            recurse(0);
        }

        void recurse(int depth) {
            maxdepth = depth;
            try {
                if (stop) {
                    return;
                }
                recurse(depth + 1);
            } catch (StackOverflowError | OutOfMemoryError e) {
                recurse(depth + 1);
            }
        }
    }

    private static class Timer extends Thread {
        private Tester tester;

        public Timer(Tester tester) {
            this.tester = tester;
        }

        public void run() {
            long started;
            started = System.currentTimeMillis();
            while (System.currentTimeMillis() - started < timeout) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                };
            }
            tester.stop = true;
        }
    }
}
