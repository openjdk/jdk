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

package jdk.jfr.jvm;

import jdk.jfr.Event;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.internal.StringPool;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.thread.TestThread;

/*
 * @test
 * @bug 8397850
 * @summary Verify thread exit after a Java event is discarded with a buffer lease outstanding
 * @requires vm.flagless & vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main/othervm -XX:+CompactStrings -XX:FlightRecorderOptions=threadbuffersize=8k jdk.jfr.jvm.TestEventWriterLeaseExit
 */
public class TestEventWriterLeaseExit {
    private static final int THREAD_BUFFER_SIZE = 8 * 1024;

    // From jdk.jfr.internal.event.EventWriter.MAX_EVENT_SIZE
    private static final int MAX_EVENT_SIZE = (1 << 28) - 1;

    // The lease string must bypass string pooling and request more
    // than the entire thread-local buffer to acquire a large buffer lease.
    private static final int LEASE_LENGTH = Math.max(StringPool.MAX_LIMIT, THREAD_BUFFER_SIZE + 1);

    // EventWriter.putStringValue() reserves up to three bytes per character.
    // The size check in putStringValue() must fail for the reject field, and this rejection is what we target.
    // That rejection leaves the outstanding buffer lease that was acquired when writing the lease string hanging until thread exit.
    // With CompactStrings, the reject Java string itself occupies about 85 MiB.
    private static final int REJECT_LENGTH = MAX_EVENT_SIZE / 3 + 1;

    static class TwoStringsEvent extends Event {
        // Declaration order matters: lease must be serialized before reject,
        // so the rejected write leaves an outstanding buffer lease.
        public String lease;
        public String reject;
    }

    public static void main(String[] args) throws Throwable {
        TwoStringsEvent oversized = new TwoStringsEvent();
        oversized.lease = "a".repeat(LEASE_LENGTH);
        oversized.reject = "b".repeat(REJECT_LENGTH);

        TestThread worker = new TestThread(oversized::commit, "lease-exit-worker");
        boolean[] workerEnded = {false};

        try (RecordingStream stream = new RecordingStream()) {
            stream.enable(TwoStringsEvent.class).withoutStackTrace();
            stream.enable(EventNames.ThreadEnd);

            stream.onMetadata((var metadata) -> {
                // Metadata can be delivered more than once.
                // Therefore, start the worker thread only once.
                if (worker.getState() == Thread.State.NEW) {
                    Asserts.assertTrue(oversized.shouldCommit(), "Test event must be enabled");
                    worker.start();
                }
            });
            stream.onEvent(TwoStringsEvent.class.getName(), (var event) -> {
                throw new AssertionError("Oversized event should have been discarded");
            });
            stream.onEvent(EventNames.ThreadEnd, (var event) -> {
                if (worker.getName().equals(event.getThread().getJavaName())) {
                    workerEnded[0] = true;
                    stream.close();
                }
            });
            stream.onError((var error) -> {
                throw new AssertionError("Recording stream failed", error);
            });

            stream.start();
            Asserts.assertTrue(workerEnded[0], "Worker ThreadEnd event was not received");

            // ThreadEnd is also recorded if the worker failed. Propagate any
            // exception captured by TestThread before it reached thread exit.
            Throwable failure = worker.getUncaught();
            if (failure != null) {
                throw failure;
            }
        }
    }
}