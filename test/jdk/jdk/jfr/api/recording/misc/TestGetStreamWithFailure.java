/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.recording.misc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.function.BiConsumer;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Recording;

/**
 * @test
 * @summary Verify that resources are not leaked in case of failure
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.recording.misc.TestGetStreamWithFailure
 */
public class TestGetStreamWithFailure {
    private interface TestCase {
        void execute(Recording recording, List<Path> chunks) throws Exception;
    }

    private static class FillerEvent extends Event {
    }

    public static void main(String[] args) throws Exception {
        testMissingChunk();
        testClosedStream();
        testNoChunks();
    }

    // Simulates a user deleting all chunks for a recording
    private static void testNoChunks() throws Exception {
        testStream((r, chunks) -> {
            deleteAllChunks();
            try {
                InputStream is = r.getStream(null, null);
                throw new Exception("Expected exception when all chunks are missing");
            } catch (IOException ioe) {
                if (!ioe.getMessage().equals("Recording data missing on disk.")) {
                    throw new Exception("Unexpected exception: " + ioe.getMessage());
                }
            }
        });
    }

    private static void deleteAllChunks() throws Exception {
        for (Path chunk : findChunks()) {
            Files.delete(chunk);
        }
    }

    // Simulates a user deleting a single chunk file on disk.
    private static void testMissingChunk() throws Exception {
        testStream((r, chunks) -> {
            Files.delete(chunks.get(1));
            try (InputStream is = r.getStream(null, null)) {
                is.readAllBytes();
            }
        });
    }

    // Simulates a user closing a stream before all the data has been read, for
    // example, if InputStream::read throws an IOException.
    private static void testClosedStream() throws Exception {
        testStream((r, chunks) -> {
            int size = (int) (r.getSize());
            try (InputStream is = r.getStream(null, null)) {
                is.readNBytes(size / 2);
            }
        });
    }

    private static void testStream(TestCase testCase) throws Exception {
        try (Recording r = createRecordingData()) {
            List<Path> chunks = findChunks();
            if (chunks.size() < 3) {
                throw new Exception("Expected recording to have at least three chunks");
            }
            testCase.execute(r, chunks);
            r.close();
            if (!findChunks().isEmpty()) {
                throw new Exception("Chunks left behind.");
            }
            deleteAllChunks();
        }
    }

    private static List<Path> findChunks() throws Exception {
        String repository = System.getProperty("jdk.jfr.repository");
        if (repository == null) {
            throw new Exception("No system property for JFR repository");
        }
        Path dir = Path.of(repository);
        return Files.walk(dir).filter(p -> p.toString().endsWith(".jfr")).toList();
    }

    private static Recording createRecordingData() throws IOException, ParseException, InterruptedException {
        Configuration c = Configuration.getConfiguration("default");
        Recording r = new Recording();
        r.start();
        emitEvents(); // Chunk 1
        try (Recording s = new Recording()) {
            s.start();
            emitEvents(); // Chunk 2
        }
        emitEvents(); // Chunk 3
        r.stop();
        return r;
    }

    private static void emitEvents() throws InterruptedException {
        for (int i = 0; i < 100_000; i++) {
            FillerEvent e = new FillerEvent();
            e.commit();
        }
    }
}
