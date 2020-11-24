/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, NTT DATA.
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
 * @summary Test jdk.OutOfMemory event
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules java.base/jdk.internal.misc java.compiler
 * @run main/othervm -Xmx200m -XX:StartFlightRecording
 *      jdk.jfr.event.runtime.TestOutOfMemory
 */
package jdk.jfr.event.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import jdk.jfr.consumer.RecordingStream;

public class TestOutOfMemory implements Runnable {

    public void run() {
        List<byte[]> list = new ArrayList<>();
        try {
            while (true) {
                list.add(new byte[10 * 1024 * 1024]);
            }
        } catch (OutOfMemoryError e) {
            // expected
            System.out.println("OutOfMemoryError was thrown in consumer thread");
        }
    }

    public static void main(String... args) throws Exception {
        CountDownLatch eventArrived = new CountDownLatch(1);

        try (RecordingStream r = new RecordingStream()) {
            r.onEvent("jdk.OutOfMemory", e -> eventArrived.countDown());
            r.startAsync();

            Thread heapConsumer = new Thread(new TestOutOfMemory(), "Java heap consumer");
            heapConsumer.start();

            eventArrived.await();
            heapConsumer.join();
        }
    }
}
