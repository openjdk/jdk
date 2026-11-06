/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package jdk.jfr.event.profiling;

import java.util.Random;
import java.util.zip.Deflater;

import jdk.jfr.Recording;

import static jdk.test.lib.jfr.EventNames.CPUTimeSample;

/*
 * @test
 * @bug 8392187
 * @summary Repeatedly disenroll CPU-time sampler while threads are executing native code
 * @requires vm.hasJFR & vm.flagless & os.family == "linux"
 * @library /test/lib
 * @run main/othervm/timeout=600 jdk.jfr.event.profiling.TestCPUTimeSamplingStop
 */
public class TestCPUTimeSamplingStop {

    private static final int TEST_ITERATIONS = 100;
    private static final int LOOP_ITERATIONS = 100;
    private static final int DELAY_MILLIS = 2;

    public static void main(String[] args) throws Exception {
        int threads = Math.max(Runtime.getRuntime().availableProcessors() - 1, 2);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(TestCPUTimeSamplingStop::nativeBurner, "Native CPU burner " + i);
            t.setDaemon(true);
            t.start();
        }

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            runTest();
        }
    }

    private static void runTest() throws InterruptedException {
        try (Recording r = new Recording()) {
            r.start();
            for (int i = 0; i < LOOP_ITERATIONS; i++) {
                r.disable(CPUTimeSample);
                r.enable(CPUTimeSample).with("throttle", "1ms");
                Thread.sleep(DELAY_MILLIS);
            }
            r.stop();
        }
    }

    // Burn CPU cycles inside a long native call (compression of a large array)
    private static void nativeBurner() {
        byte[] input = new byte[1024 * 1024];
        byte[] output = new byte[input.length * 9 / 8];
        new Random().nextBytes(input);

        try (Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION)) {
            while (true) {
                deflater.reset();
                deflater.setInput(input);
                deflater.finish();
                while (!deflater.finished()) {
                    deflater.deflate(output);
                }
            }
        }
    }
}
