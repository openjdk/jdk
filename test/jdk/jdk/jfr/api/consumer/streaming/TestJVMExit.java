/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.api.consumer.streaming;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.jfr.consumer.EventStream;

/**
 * @test
 * @summary Test that a stream ends/closes when an application exists.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.jfr jdk.attach java.base/jdk.internal.misc
 * @build jdk.jfr.api.consumer.streaming.TestProcess
 *
 * @run main/othervm -Dsun.tools.attach.attachTimeout=100000 jdk.jfr.api.consumer.streaming.TestJVMExit
 */
public class TestJVMExit {

    public static void main(String... args) throws Exception {
        while (true) {
            try {
                testExit();
                return;
            } catch (RuntimeException e) {
                String message = String.valueOf(e.getMessage());
                // If the test application crashes during startup, retry.
                if (!message.contains("is no longer alive")) {
                    throw e;
                }
                System.out.println("Application not alive when trying to get repository. Retrying.");
            }
        }
    }

    private static void testExit() throws Exception {
        try (TestProcess process = new TestProcess("exit-application")) {
            AtomicInteger eventCounter = new AtomicInteger();
            try (EventStream es = EventStream.openRepository(process.getRepository())) {
                // Start from first event in repository
                es.setStartTime(Instant.EPOCH);
                es.onEvent(e -> {
                    if (eventCounter.incrementAndGet() == TestProcess.NUMBER_OF_EVENTS) {
                        process.exit();
                    }
                });
                es.start();
            }
        }
    }
}
