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
package jdk.jfr.api.consumer.streaming;

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Recording;
import jdk.jfr.internal.consumer.RepositoryFiles;
import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Verifies that RepositoryFiles does not oscillate between
 *          forgetting and rediscovering chunk files between scans
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal.consumer
 * @run main/othervm jdk.jfr.api.consumer.streaming.TestRepositoryFilesRescan
 */
public class TestRepositoryFilesRescan {

    public static void main(String... args) throws Exception {
        Path dir = Files.createTempDirectory("jfr-rescan-test");
        try {
            createChunkFile(dir, "chunk1.jfr");
            createChunkFile(dir, "chunk2.jfr");
            testNoOscillation(dir);
        } finally {
            deleteDirectory(dir);
        }
    }

    /**
     * firstPath(0, false) will return non-null only when updatePaths discovers
     * genuinely new chunk files; with no files added or removed between
     * calls the result must be null after the first successful call.
     */
    private static void testNoOscillation(Path dir) {
        RepositoryFiles rf = new RepositoryFiles(dir, false);
        Asserts.assertNotNull(rf.firstPath(0, false), "Call 1: expected non-null (initial discovery of .jfr files)");

        Path p2 = rf.firstPath(0, false);
        Asserts.assertNull(p2, "Call 2: expected null (no new chunks), got " + p2);

        // Call 3: still no new files.  This confirms call 2 did not wipe pathLookup, making all files look "new" again.
        Path p3 = rf.firstPath(0, false);
        Asserts.assertNull(p3, "Call 3: expected null (no new chunks), got " + p3
            + " — pathLookup is oscillating between populated and empty");
    }

    private static void createChunkFile(Path dir, String name) throws Exception {
        try (Recording r = new Recording()) {
            r.start();
            Thread.sleep(20);
            r.stop();
            r.dump(dir.resolve(name));
        }
    }

    private static void deleteDirectory(Path dir) {
        try {
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception e) {
                        // Do nothing
                    }
                });
            }
            Files.deleteIfExists(dir);
        } catch (Exception e) {
            // best-effort cleanup
        }
    }
}
