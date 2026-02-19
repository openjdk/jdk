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
    public static void main(String... args) throws Exception {
       Recording r1 = new Recording();
        r1.start();
        Recording r2 = new Recording();
        r2.start();
        Recording r3 = new Recording();
        r3.start();
        Recording r4 = new Recording();
        r4.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                awaitRepositoryRemoved();
                testNew();
                testDump(r1);
                testStop(r2);
                testClose(r3);
                testCopy(r4);
                testSnapshot();
            }
        });
    }

    private static void testNew() {
        try {
            new Recording();
            fail("Expected IllegalStateException if recording is created after repository removal.");
        } catch (IllegalStateException ise) {
            // As expected
        }
        try {
            new RecordingStream();
            fail("Expected IllegalStateException if recording is created after repository removal.");
        } catch (IllegalStateException ise) {
            // As expected
        }
    }

    private static void testDump(Recording r) {
        try {
            r.dump(Path.of("file.jfr"));
            fail("Should not be able to dump recording after shutdown");
        } catch (IOException ioe) {
            assertClosed(r);
        }
    }

    private static void testStop(Recording r) {
        try {
            r.stop();
            fail("Should not be able to stop recording after shutdown.");
        } catch (IllegalStateException ioe) {
            assertClosed(r);
        }
    }

    private static void testClose(Recording r) {
        try {
            r.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not be close recording after shutdown.");
        }
    }

    private static void testCopy(Recording r) {
        try {
            Recording copy = r.copy(true);
            assertClosed(copy);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Recording copy should not throw Exception.");
        }
    }

    private static void testSnapshot() {
        Recording r = FlightRecorder.getFlightRecorder().takeSnapshot();
        assertClosed(r);
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
        // This will typically hang the application because of shutdown lock.
        System.exit(1);
    }
}
