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

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.nio.charset.StandardCharsets.US_ASCII;

/*
 * @test
 * @bug 8347712
 * @summary verify that different instances of java.util.zip.ZipFile do not share
 *          the same instance of (non-thread-safe) java.nio.charset.CharsetEncoder/CharsetDecoder
 * @run junit ZipFileSharedSourceTest
 */
public class ZipFileSharedSourceTest {

    static Path createZipFile(final Charset charset) throws Exception {
        final Path zipFilePath = Files.createTempFile(Path.of("."), "8347712", ".zip");
        try (OutputStream os = Files.newOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(os, charset)) {
            final int numEntries = 10240;
            for (int i = 1; i <= numEntries; i++) {
                final ZipEntry entry = new ZipEntry("entry-" + i);
                zos.putNextEntry(entry);
                zos.write("foo bar".getBytes(US_ASCII));
                zos.closeEntry();
            }
        }
        return zipFilePath;
    }

    static List<Arguments> charsets() {
        return List.of(
                Arguments.of(StandardCharsets.UTF_8),
                Arguments.of(StandardCharsets.ISO_8859_1),
                Arguments.of(US_ASCII)
        );
    }

    /**
     * In this test, multiple concurrent threads each create an instance of java.util.zip.ZipFile
     * with the given {@code charset} for the same underlying ZIP file. Each of the threads
     * then iterate over the entries of their ZipFile instance. The test verifies that such access,
     * where each thread is accessing an independent ZipFile instance corresponding to the same
     * underlying ZIP file, doesn't lead to unexpected failures contributed by concurrent
     * threads.
     */
    @ParameterizedTest
    @MethodSource("charsets")
    void testMultipleZipFileInstances(final Charset charset) throws Exception {
        final Path zipFilePath = createZipFile(charset);
        final int numTasks = 200;
        final CountDownLatch startLatch = new CountDownLatch(numTasks);
        final List<Future<Void>> results = new ArrayList<>();
        try (final ExecutorService executor =
                     Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory())) {
            for (int i = 0; i < numTasks; i++) {
                final var task = new ZipEntryIteratingTask(zipFilePath, charset,
                        startLatch);
                results.add(executor.submit(task));
            }
            System.out.println(numTasks + " tasks submitted, waiting for them to complete");
            for (final Future<Void> f : results) {
                f.get();
            }
        }
        System.out.println("All " + numTasks + " tasks completed successfully");
    }

    private static final class ZipEntryIteratingTask implements Callable<Void> {
        private final Path file;
        private final Charset charset;
        private final CountDownLatch startLatch;

        private ZipEntryIteratingTask(final Path file, final Charset charset,
                                      final CountDownLatch startLatch) {
            this.file = file;
            this.charset = charset;
            this.startLatch = startLatch;
        }

        @Override
        public Void call() throws Exception {
            // let other tasks know we are ready to run
            this.startLatch.countDown();
            // wait for other tasks to be ready to run
            this.startLatch.await();
            // create a new instance of ZipFile and iterate over the entries
            try (final ZipFile zf = new ZipFile(this.file.toFile(), this.charset)) {
                final var entries = zf.entries();
                while (entries.hasMoreElements()) {
                    final ZipEntry ze = entries.nextElement();
                    // additionally exercise the ZipFile.getEntry() method
                    zf.getEntry(ze.getName());
                }
            }
            return null;
        }
    }
}
