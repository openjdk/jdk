/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=platform
 * @summary Verifies JVMTI ClearAllFramePops clears all FramePop requests
 * @library /test/lib
 * @run main/othervm/native -agentlib:ClearAllFramePops ClearAllFramePops platform
 */
/*
 * @test id=virtual
 * @summary Verifies JVMTI ClearAllFramePops clears all FramePop requests
 * @library /test/lib
 * @run main/othervm/native -agentlib:ClearAllFramePops ClearAllFramePops virtual
 */

public class ClearAllFramePops {

    final static int MAX_THREADS_LIMIT = 10;
    final static int NESTING_DEPTH = 5;
    final static String TEST_THREAD_NAME_BASE = "Test Thread #";

    native static void clearAllFramePops();
    native static void getReady();
    native static void check();

    public static void main(String args[]) {
        boolean isVirtual = args.length > 0 && args[0].equals("virtual");
        final int THREADS_LIMIT = Math.min(Runtime.getRuntime().availableProcessors() + 1, MAX_THREADS_LIMIT);
        Thread[] t = new Thread[THREADS_LIMIT];
        getReady();
        Thread.Builder builder = (isVirtual ? Thread.ofVirtual() : Thread.ofPlatform())
                .name(TEST_THREAD_NAME_BASE, 0);
        for (int i = 0; i < THREADS_LIMIT; i++) {
            t[i] = builder.start(new TestTask());
        }
        for (int i = 0; i < THREADS_LIMIT; i++) {
            try {
                t[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
        }
        check();
    }

    static class TestTask implements Runnable {
        int nestingCount = 0;

        public void run() {
            if (nestingCount < NESTING_DEPTH) {
                nestingCount++;
                run();
            } else {
                clearAllFramePops();
            }
        }
    }
}
