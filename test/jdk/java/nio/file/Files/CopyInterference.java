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
 * @bug 8114830
 * @summary Verify FileAlreadyExistsException is not thrown for REPLACE_EXISTING
 * @library ..
 * @build CopyInterference
 * @run junit CopyInterference
 */
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.LinkOption.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CopyInterference {

    private static final int N_THREADS = 2;

    private static final AtomicBoolean running = new AtomicBoolean(true);

    private static class CopyTask implements Runnable {
        final Path source;
        final Path target;
        final CopyOption[] options;

        CopyTask(Path source, Path target, CopyOption[] options) {
            this.source = source;
            this.target = target;
            this.options = options;
        }

        @Override
        public void run() {
            try {
                while (running.get()) {
                    Files.copy(source, target, options);
                }
            } catch (FileAlreadyExistsException e) {
                throw new RuntimeException("Unexpected exception", e);
            } catch (FileSystemException e) {
                System.out.printf("Expected FileSystemException: \"%s\"%n",
                                  e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException("Unexpected exception", e);
            } finally {
                running.set(false);
            }
        }
    }

    private static Stream<Arguments> pathAndOptionsProvider()
        throws IOException {
        Path parent = Path.of(System.getProperty("test.dir", "."));
        Path dir = Files.createTempDirectory(parent, "foobargus");

        List<Arguments> list = new ArrayList<Arguments>();

        // regular file
        Path sourceFile = Files.createTempFile(dir, "foo", "baz");
        Class c = CopyInterference.class;
        String name = "CopyInterference.class";

        try (InputStream in = c.getResourceAsStream(name)) {
            Files.copy(in, sourceFile, REPLACE_EXISTING);
        }

        Arguments args = Arguments.of(sourceFile, dir.resolve("targetFile"),
                                      new CopyOption[] {REPLACE_EXISTING});
        list.add(args);

        // directory
        Path sourceDirectory = Files.createTempDirectory(dir, "fubar");
        args = Arguments.of(sourceDirectory, dir.resolve("targetDir"),
                            new CopyOption[] {REPLACE_EXISTING});
        list.add(args);

        if (TestUtil.supportsLinks(dir)) {
            // symbolic link, followed
            Path link = dir.resolve("link");
            Files.createSymbolicLink(link, sourceFile);
            args = Arguments.of(link, dir.resolve("linkFollowed"),
                                new CopyOption[] {REPLACE_EXISTING});
            list.add(args);

            // symbolic link, not followed
            args = Arguments.of(link, dir.resolve("linkNotFollowed"),
                                new CopyOption[] {REPLACE_EXISTING,
                                                  NOFOLLOW_LINKS});
            list.add(args);
        } else {
            System.out.println("Links not supported: not testing links");
        }

        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("pathAndOptionsProvider")
    void copy(Path source, Path target, CopyOption[] options)
        throws InterruptedException, IOException {

        Future<?>[] results = new Future<?>[N_THREADS];
        try (ExecutorService es = Executors.newFixedThreadPool(N_THREADS)) {
            CopyTask copyTask = new CopyTask(source, target, options);
            for (int i = 0; i < N_THREADS; i++)
                results[i] = es.submit(copyTask);
        }

        for (Future<?> res : results) {
            try {
                res.get();
            } catch (ExecutionException e) {
                throw new RuntimeException(res.exceptionNow());
            }
        }
    }
}
