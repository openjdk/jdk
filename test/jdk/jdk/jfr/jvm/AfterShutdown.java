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
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.FlightRecorder;
import jdk.jfr.RecordingState;

/**
 * Test application that tries to interact with JFR after shutdown.
 */
public class AfterShutdown {
    public static void main(String... args) {
        Recording r = new Recording();
        r.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                awaitRepositoryRemoved();
                try {
                    r.dump(Path.of("file.jfr"));
                    System.out.println("FAIL: Should not be able to dump after shutdown.");
                    System.exit(1);
                } catch (IOException ioe) {
                    if (r.getState() != RecordingState.CLOSED) {
                        System.out.println("FAIL: Expected recording to be closed.");
                        System.out.println("Recording state: " + r.getState());
                        System.exit(1);
                    }
                    List<Recording> recordings = FlightRecorder.getFlightRecorder().getRecordings();
                    if (!recordings.isEmpty()) {
                        System.out.println("FAIL: Expected no available recordings.");
                        System.exit(1);
                    }
                }
                try {
                    new Recording();
                    System.out.println("FAIL: Expected IllegalStateException if recording is created after repository removal.");
                    System.exit(1);
                } catch (IllegalStateException ise) {
                    // As expected
                }
            }

            private void awaitRepositoryRemoved() {
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
        });
    }
}
