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

import jdk.jfr.consumer.RecordingStream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static jdk.test.lib.jfr.EventNames.CPUTimeSample;

/*
 * @test
 * @bug 8396375
 * @summary CPU-time sampler must profile threads attached via JNI
 * @requires vm.hasJFR & os.family == "linux"
 * @library /test/lib
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED jdk.jfr.event.profiling.TestCPUTimeSamplingJNIAttach before
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED jdk.jfr.event.profiling.TestCPUTimeSamplingJNIAttach after
 */
public class TestCPUTimeSamplingJNIAttach {

    private static final CountDownLatch attached = new CountDownLatch(1);
    private static final CountDownLatch sampled = new CountDownLatch(1);
    private static volatile long sink;

    public static void main(String[] args) throws Exception {
        boolean attachBefore = "before".equals(args[0]);

        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(CPUTimeSample).with("throttle", "10ms");
            rs.onEvent(CPUTimeSample, e -> {
                if ("AttachedThread".equals(e.getThread().getJavaName())) {
                    sampled.countDown();
                }
            });

            if (attachBefore) attachThread();
            rs.startAsync();
            if (!attachBefore) attachThread();

            if (!sampled.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timeout waiting for CPUTimeSample");
            }

            rs.stop();
        }
    }

    private static void attachThread() throws InterruptedException {
        // JNI_OnLoad creates a new native thread and attaches it to the JVM
        System.loadLibrary("SamplingAttach");
        attached.await();
    }

    // Called from JNI
    static void busyLoop() {
        attached.countDown();
        while (true) {
            sink += System.getProperties().hashCode();
        }
    }
}
