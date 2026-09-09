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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Recording;
import jdk.jfr.FlightRecorder;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordingStream;

/**
 * Test application that tries to interact with JFR after shutdown.
 */
@SuppressWarnings("resource")
public class AfterShutdown {
    private static boolean fail;

    public static void main(String... args) throws Exception {
        Recording fresh = new Recording();

        Recording started = new Recording();
        started.start();

        Recording stopped = new Recording();
        stopped.start();
        stopped.stop();

        Recording closed = new Recording();
        closed.close();

        Recording fullCycle = new Recording();
        fullCycle.start();
        fullCycle.stop();
        fullCycle.close();

        Recording copyable = new Recording();
        copyable.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    awaitRepositoryRemoved();
                    assertClosedBehavior(fresh, "new recording");
                    assertClosedBehavior(started, "started recording");
                    assertClosedBehavior(stopped, "stopped recording");
                    assertClosedBehavior(closed, "closed recording");
                    assertClosedBehavior(fullCycle, "full-cycle recording");
                    testCopy(copyable);
                    testSnapshot();
                    testLateRecording();
                    testLateRecordingStream();
                } catch (Throwable t) {
                    fail = true;
                    t.printStackTrace();
                }
                if (!fail) {
                    System.out.println("PASS");
                }
            }
        });
    }

    private static void testLateRecording() {
        Recording r = new Recording();
        assertClosedBehavior(r, "late recording");
    }

    private static void testCopy(Recording r) {
        try (Recording copy = r.copy(false)) {
            assertClosedBehavior(copy, "copied recording with stopped=true");
        }
        try (Recording copy = r.copy(true)) {
            assertClosedBehavior(copy, "copied recording with stopped=false");
        }
    }

    private static void testSnapshot() {
        try (Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot()) {
            assertClosedBehavior(snapshot, "snapshot");
        }
    }

    public static void assertClosedBehavior(Recording recording, String kind) {
        assertClosed(recording);
        try {
            recording.start();
            fail("Expected IllegalStateException if " + kind + " is started after shutdown.");
        } catch (IllegalStateException ise) {
            // As expected
        }
        try {
            recording.dump(Path.of("file.jfr"));
            fail("Expected IOException if " + kind + " is dumped after shutdown");
        } catch (IOException ioe) {
            // As expected
        }
        try {
            recording.stop();
            fail("Expected IllegalStateException if " + kind + " is stopped after shutdown");
        } catch (IllegalStateException ioe) {
            // As expected
        }
        try {
            recording.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should be able to close " + kind + " after shutdown.");
        }
    }

    private static void testLateRecordingStream() {
        RecordingStream rs = new RecordingStream();
        assertClosedBehavior(rs, "late recording stream");
    }

    public static void assertClosedBehavior(RecordingStream rs, String kind) {
        try {
            rs.start();
            fail("Expected IllegalStateException if " + kind + " is started after shutdown.");
        } catch (IllegalStateException ise) {
            // As expected
        }
        try {
            rs.dump(Path.of("file.jfr"));
            fail("Expected IOException if " + kind + " is dumped after shutdown");
        } catch (IOException ioe) {
            // As expected
        }
        try {
            rs.stop();
            fail("Expected IllegalStateException if " + kind + " is stopped after shutdown");
        } catch (IllegalStateException ioe) {
            // As expected
        }
        try {
            rs.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should be able to close " + kind + " after shutdown.");
        }
    }

    private static void awaitRepositoryRemoved() {
        String path = System.getProperty("jdk.jfr.repository");
        Path repository = Path.of(path);
        while (Files.exists(repository)) {
            System.out.println("Repository still exist. Waiting 100 ms ...");
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Repository removed.");
    }

    private static void assertClosed(Recording r) {
        if (r.getState() != RecordingState.CLOSED) {
            fail("Recording was in state " + r.getState() + " but expected it to be CLOSED");
        }
    }

    private static void fail(String message) {
        System.out.println("FAIL: " + message);
        fail = true;
    }
}
