/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.api.consumer.recordingstream;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.Event;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests RecordingStream::close()
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.recordingstream.TestClose
 */
public class TestClose {

    private static class CloseEvent extends Event {
    }

    public static void main(String... args) throws Exception {
        testCloseUnstarted();
        testCloseStarted();
        testCloseTwice();
        testCloseStreaming();
        testCloseMySelf();
    }

    private static void testCloseMySelf() throws Exception {
        log("Entering testCloseMySelf()");
        CountDownLatch l1 = new CountDownLatch(1);
        CountDownLatch l2 = new CountDownLatch(1);
        RecordingStream r = new RecordingStream();
        r.onEvent(e -> {
            try {
                l1.await();
                r.close();
                l2.countDown();
            } catch (InterruptedException ie) {
                throw new Error(ie);
            }
        });
        r.startAsync();
        CloseEvent c = new CloseEvent();
        c.commit();
        l1.countDown();
        l2.await();
        log("Leaving testCloseMySelf()");
    }

    private static void testCloseStreaming() throws Exception {
        log("Entering testCloseStreaming()");
        CountDownLatch streaming = new CountDownLatch(1);
        RecordingStream r = new RecordingStream();
        AtomicLong count = new AtomicLong();
        r.onEvent(e -> {
            if (count.incrementAndGet() > 100) {
                streaming.countDown();
            }
        });
        r.startAsync();
        var streamingLoop = CompletableFuture.runAsync(() -> {
            while (true) {
                CloseEvent c = new CloseEvent();
                c.commit();
            }
        });
        streaming.await();
        r.close();
        streamingLoop.cancel(true);
        log("Leaving testCloseStreaming()");
    }

    private static void testCloseStarted() {
        log("Entering testCloseStarted()");
        RecordingStream r = new RecordingStream();
        r.startAsync();
        r.close();
        log("Leaving testCloseStarted()");
    }

    private static void testCloseUnstarted() {
        log("Entering testCloseUnstarted()");
        RecordingStream r = new RecordingStream();
        r.close();
        log("Leaving testCloseUnstarted()");
    }

    private static void testCloseTwice() {
        log("Entering testCloseTwice()");
        RecordingStream r = new RecordingStream();
        r.startAsync();
        r.close();
        r.close();
        log("Leaving testCloseTwice()");
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}
