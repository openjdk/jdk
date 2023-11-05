/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.jmx.streaming;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;

import javax.management.MBeanServerConnection;

import jdk.management.jfr.RemoteRecordingStream;

/**
 * @test
 * @key jfr
 * @summary Sanity tests RemoteRecordingStream::start()
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jmx.streaming.TestStart
 */
public class TestStart {

    private final static MBeanServerConnection CONNECTION = ManagementFactory.getPlatformMBeanServer();

    public static void main(String... args) throws Exception {
        testStart();
        testStartTwice();
        testStartClosed();
    }

    private static void testStart() throws IOException {
        try (var r = new RemoteRecordingStream(CONNECTION)) {
            r.onFlush(() -> {
                System.out.print("Started.");
                r.close();
            });
            System.out.println("About to start ...");
            r.start();
            System.out.println("Finished!");
        }
    }

    private static void testStartTwice() throws Exception {
        var latch = new CountDownLatch(1);
        try (var r = new RemoteRecordingStream(CONNECTION)) {
            r.onFlush(() -> latch.countDown());
            Runnable starter = () -> {
                r.start();
            };
            new Thread(starter).start();
            latch.await();
            try {
                r.start();
            } catch (IllegalStateException ise) {
                // OK, as expected
                return;
            }
            throw new Exception("Expected IllegalStateException when starting same stream twice");
        }
    }

    private static void testStartClosed() throws Exception {
        var latch = new CountDownLatch(1);
        try (var r = new RemoteRecordingStream(CONNECTION)) {
            r.onFlush(() -> latch.countDown());
            Runnable starter = () -> {
                r.start();
            };
            new Thread(starter).start();
            latch.await();
            r.close();
            try {
                r.start();
            } catch (IllegalStateException ise) {
                // OK, as expected
                return;
            }
            throw new Exception("Expected IllegalStateException when starting closed stream");
        }
    }
}
