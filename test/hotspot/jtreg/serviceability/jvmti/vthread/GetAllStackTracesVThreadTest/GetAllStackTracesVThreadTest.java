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
 * @test id=default
 * @bug 8385208
 * @summary Verify JVMTI GetAllStackTraces works in the presence of virtual threads
 * @run main/othervm/native -agentlib:GetAllStackTracesVThreadTest GetAllStackTracesVThreadTest
 */

/**
 * @test id=no-vmcontinuations
 * @bug 8385208
 * @summary Verify JVMTI GetAllStackTraces with bound virtual threads (-VMContinuations)
 * @requires vm.continuations
 * @run main/othervm/native -agentlib:GetAllStackTracesVThreadTest -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations GetAllStackTracesVThreadTest
 */

import java.util.concurrent.CountDownLatch;

public class GetAllStackTracesVThreadTest {
    static final int VTHREAD_COUNT = 20;
    static final String NAME_PREFIX = "TestVThread-";

    static native boolean checkGetAllStackTraces(Thread[] vthreads, int count);

    public static void main(String[] args) throws Exception {
        CountDownLatch ready = new CountDownLatch(VTHREAD_COUNT);
        CountDownLatch done = new CountDownLatch(1);
        Thread[] vthreads = new Thread[VTHREAD_COUNT];

        for (int i = 0; i < VTHREAD_COUNT; i++) {
            String name = NAME_PREFIX + i;
            vthreads[i] = Thread.ofVirtual().name(name).start(() -> {
                ready.countDown();
                try {
                    done.await();
                } catch (InterruptedException e) {
                    // ignore
                }
            });
        }

        // Wait for all virtual threads to be running
        ready.await();

        // Call GetAllStackTraces and check results
        boolean result = checkGetAllStackTraces(vthreads, VTHREAD_COUNT);

        // Let threads finish
        done.countDown();
        for (Thread vt : vthreads) {
            vt.join();
        }

        if (!result) {
            throw new RuntimeException("GetAllStackTraces check failed - see native output for details");
        }
        System.out.println("Test passed");
    }
}
