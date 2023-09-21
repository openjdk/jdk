/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Unit test for checking parallel use of Files::lines
 * @run junit LinesParallel
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LinesParallel {

    // file used by the tests
    private Path tmpFile;

    @BeforeEach
    void setup() throws IOException {
        tmpFile = Files.createTempFile("text_file", null);
        Files.write(tmpFile, genBytes(20_000), StandardOpenOption.CREATE);
    }

    @AfterEach
    void cleanup() {
        try {
            Files.deleteIfExists(tmpFile);
        } catch (IOException ioe) {
            // This might happen on Windows.
            System.err.println("Unable to delete file. Will let it remain: " + tmpFile);
            ioe.printStackTrace();
        }
    }

    @Test
    void basicEquality() throws IOException {
        List<String> expected;
        List<String> actual;

        try (var lines = Files.lines(tmpFile, StandardCharsets.UTF_8)) {
            expected = lines.toList();
        }

        try (var lines = Files.lines(tmpFile, StandardCharsets.UTF_8)) {
            actual = lines.parallel().toList();
        }
        assertEquals(expected, actual);
    }

    @Test
        /*
         * The objective of this test is to ensure it is very likely the mapped
         * memory region in the underlying Spliterator is released in a proper way.
         *
         * This works by creating parallel streams of various lengths and then
         * , at the same time, invoking GC frequently (the mapped memory region can
         * be freed both explicitly via the `Stream::close` method and via the GC).
         */
    void fuzzer() throws IOException {

        AtomicBoolean ready = new AtomicBoolean();
        Thread.ofPlatform().factory().newThread(() -> {
            while (!ready.get()) {
                System.gc();
                // Hammer GC
                LockSupport.parkNanos(10_000); // 10 us
            }
        });

        int elements;
        try (var lines = Files.lines(tmpFile, StandardCharsets.UTF_8)) {
            elements = Math.toIntExact(lines.count());
        }

        long cnt = 0;

        for (int i = 0; i < 100; i++) {
            // Properly close the Stream
            try (var lines = Files.lines(tmpFile, StandardCharsets.UTF_8)) {
                List<String> list = lines.parallel()
                        .limit(((long) elements * i / 100))
                        .toList();
                cnt += list.size();
            }

            // Leave the Stream dangling
            List<String> list = Files.lines(tmpFile, StandardCharsets.UTF_8)
                    .parallel()
                    .limit(((long) elements * i / 100))
                    .toList();
            cnt += list.size();
        }

        // On Windows, there might be a problem with deleting
        // a file that is mapped. So, wait here for a while
        // so that all files are more likely to be unmapped via GC.
        LockSupport.parkNanos(10_000_000); // 10 ms

        // Make the background thread exit
        ready.set(true);

        // Consume the counter
        System.out.println(cnt);
    }

    /**
     * Returns a byte[] of at least the given size with random content and
     * with newlines at random places
     */
    private static byte[] genBytes(int size) {
        Random rnd = new Random(42);
        int maxLen = 127;
        byte[] arr = new byte[size + maxLen + 2];
        int c = 0;
        while (c < size) {
            int lineLen = rnd.nextInt(3, 127);
            int end = c + lineLen;
            for (; c < end; c++) {
                arr[c] = 'a';
            }
            arr[c++] = '\r';
            arr[c++] = '\n';
        }
        // No harm we might have one or more \0 at the end
        return arr;
    }

}
