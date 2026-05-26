/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8385208
 * @summary Verify JVMTI GetAllStackTraces does not include virtual threads
 * @requires vm.continuations
 * @run main/othervm/native -agentlib:GetAllStackTracesTest GetAllStackTracesTest
 */

public class GetAllStackTracesTest {
    static final int VTHREAD_COUNT = 4;
    static final String NAME_PREFIX = "GetAllStackTracesVT-";
    static final Object lock = new Object();
    static volatile int readyCount = 0;

    // Returns true if no virtual threads found in GetAllStackTraces (expected)
    static native boolean checkGetAllStackTraces(Thread[] vthreads, int count);

    public static void main(String[] args) throws Exception {
        Thread[] vthreads = new Thread[VTHREAD_COUNT];

        // Hold the lock so virtual threads block on synchronized (pinning them)
        synchronized (lock) {
            for (int i = 0; i < VTHREAD_COUNT; i++) {
                String name = NAME_PREFIX + i;
                vthreads[i] = Thread.ofVirtual().name(name).start(() -> {
                    readyCount++;
                    synchronized (lock) {
                        // will enter here after main releases the lock
                    }
                });
            }

            // Wait for all virtual threads to be ready and blocked
            while (readyCount < VTHREAD_COUNT) {
                Thread.sleep(10);
            }
            for (Thread vt : vthreads) {
                while (vt.getState() != Thread.State.BLOCKED) {
                    Thread.sleep(10);
                }
            }

            // Verify GetAllStackTraces does NOT include virtual threads
            if (!checkGetAllStackTraces(vthreads, VTHREAD_COUNT)) {
                throw new RuntimeException("GetAllStackTraces unexpectedly included virtual threads");
            }
        }

        for (Thread vt : vthreads) {
            vt.join();
        }
        System.out.println("Test passed: GetAllStackTraces correctly excludes virtual threads");
    }
}
