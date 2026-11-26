/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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
 * @bug 8398335
 * @summary GetAllStackTraces reports the frames of a carrier thread whose
 *          virtual thread is in a mount or unmount transition
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm/native -agentlib:GetAllStackTracesCarrierTest GetAllStackTracesCarrierTest
 */

import java.util.concurrent.atomic.AtomicBoolean;

/*
 * While a virtual thread mounts or unmounts, its frames are on the carrier's
 * stack but the carrier already, or still, has its own JVMTI identity. A live
 * carrier always has frames down to its run() method, so an empty trace, or
 * one that does not end in run(), is missing frames. A transition cannot be
 * stopped in, so the test samples many carrier traces.
 */
public class GetAllStackTracesCarrierTest {
    private static final long MAX_MILLIS = 20_000;
    private static final long ENOUGH_CARRIER_TRACES = 200_000;

    // Samples all threads once and checks every carrier thread's trace.
    static native void sample();
    // Number of carrier traces checked, and of those missing frames.
    static native long carrierTraces();
    static native long wrongTraces();
    static native String firstWrongTrace();

    static volatile long sink;

    static void recurse(int depth) {
        if (depth == 0) {
            Thread.yield(); // unmount and mount again
            sink++;
        } else {
            recurse(depth - 1);
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        Thread[] vthreads = new Thread[64];
        for (int i = 0; i < vthreads.length; i++) {
            int depth = 5 + i % 20;
            vthreads[i] = Thread.ofVirtual().start(() -> {
                while (!stop.get()) {
                    recurse(depth);
                }
            });
        }
        Thread[] samplers = new Thread[4];
        long deadline = System.currentTimeMillis() + MAX_MILLIS;
        for (int i = 0; i < samplers.length; i++) {
            samplers[i] = Thread.ofPlatform().start(() -> {
                while (System.currentTimeMillis() < deadline && wrongTraces() == 0
                       && carrierTraces() < ENOUGH_CARRIER_TRACES) {
                    sample();
                }
            });
        }
        for (Thread t : samplers) {
            t.join();
        }
        stop.set(true);
        for (Thread t : vthreads) {
            t.join();
        }

        System.out.println("carrier traces: " + carrierTraces() + ", missing frames: " + wrongTraces());
        if (wrongTraces() != 0) {
            throw new RuntimeException(wrongTraces() + " carrier traces are missing frames, first: " + firstWrongTrace());
        }
        if (carrierTraces() == 0) {
            throw new RuntimeException("no carrier thread was sampled");
        }
    }
}
