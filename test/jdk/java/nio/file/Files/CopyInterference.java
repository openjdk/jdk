
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
 */
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CopyInterference {

    private static final int N_THREADS = 2;

    private static final Path SOURCE;
    private static final Path TARGET;

    private static final AtomicBoolean running = new AtomicBoolean(true);

    static {
        try {
            Path dir = Path.of(System.getProperty("test.dir", "."));
            SOURCE = Files.createTempFile(dir, "foo", "baz");
            Files.delete(SOURCE);
            TARGET = Files.createTempFile(dir, "fu", "bar");
            Files.delete(TARGET);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final Runnable copyTask = new Runnable() {
        @Override
        public void run() {
            try {
                while (running.get()) {
                    Files.copy(SOURCE, TARGET,
                               StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (FileAlreadyExistsException e) {
                running.set(false);
                throw new RuntimeException("Unexpected exception", e);
            } catch (FileSystemException e) {
                System.out.printf("Expected FileSystemException: \"%s\"%n",
                                  e.getMessage());
            } catch (IOException e) {
                running.set(false);
                throw new RuntimeException("Unexpected exception", e);
            }
            running.set(false);
        }
    };

    public static void main(String[] args) throws Exception {
        Class c = CopyInterference.class;
        String name = "CopyInterference.class";

        try (InputStream in = c.getResourceAsStream(name)) {
            Files.copy(in, SOURCE, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(TARGET);

            ExecutorService es = Executors.newFixedThreadPool(N_THREADS);
            Future<?>[] results = new Future<?>[N_THREADS];
            for (int i = 0; i < N_THREADS; i++)
                results[i] = es.submit(copyTask);

            es.shutdown();
            es.awaitTermination(5, TimeUnit.SECONDS);

            // Check results
            for (Future<?> res : results) {
                try {
                    res.get();
                } catch (ExecutionException e) {
                    throw new RuntimeException(res.exceptionNow());
                }
            }
        }
    }
}
