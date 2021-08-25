/*
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6993267
 * @summary Unit test for Sun-specific ExtendedCopyOption.INTERRUPTIBLE option
 * @modules jdk.unsupported
 * @library ..
 */

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import com.sun.nio.file.ExtendedCopyOption;

public class InterruptCopy {

    private static final long FILE_SIZE_TO_COPY = 1024L * 1024L * 1024L;
    private static final int INTERRUPT_DELAY_IN_MS = 50;
    private static final int DURATION_MAX_IN_MS = 3*INTERRUPT_DELAY_IN_MS;
    private static final int CANCEL_DELAY_IN_MS = 10;

    public static void main(String[] args) throws Exception {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            FileStore store = Files.getFileStore(dir);
            System.out.format("Checking space (%s)\n", store);
            long usableSpace = store.getUsableSpace();
            if (usableSpace < 2*FILE_SIZE_TO_COPY) {
                System.out.println("Insufficient disk space to run test.");
                return;
            }
            doTest(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }

    static void doTest(Path dir) throws Exception {
        final Path source = dir.resolve("foo");
        final Path target = dir.resolve("bar");

        // create source file (don't create it as sparse file because we
        // require the copy to take a long time)
        System.out.println("Creating source file...");
        byte[] buf = new byte[32*1024];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(source)) {
            do {
                out.write(buf);
                total += buf.length;
            } while (total < FILE_SIZE_TO_COPY);
        }
        System.out.println("Source file created.");

        ScheduledExecutorService pool =
            Executors.newSingleThreadScheduledExecutor();

        try {
            // copy source to target in main thread, interrupting it
            // after a delay
            final Thread me = Thread.currentThread();
            final CountDownLatch interruptLatch = new CountDownLatch(1);
            Future<?> wakeup = pool.submit(new Runnable() {
                public void run() {
                    try {
                        interruptLatch.await();
                        Thread.sleep(INTERRUPT_DELAY_IN_MS);
                    } catch (InterruptedException ignore) {
                    }
                    System.out.printf("Interrupting at %d ms...%n",
                        System.currentTimeMillis());
                    me.interrupt();
                }
            });
            try {
                interruptLatch.countDown();
                long theBeginning = System.currentTimeMillis();
                System.out.printf("Copying file at %d ms...%n", theBeginning);
                Files.copy(source, target, ExtendedCopyOption.INTERRUPTIBLE);
                long theEnd = System.currentTimeMillis();
                System.out.printf("Done copying at %d ms...%n", theEnd);
                long duration = theEnd - theBeginning;
                if (duration > DURATION_MAX_IN_MS)
                    throw new RuntimeException("Copy was not interrupted");
            } catch (IOException e) {
                boolean interrupted = Thread.interrupted();
                if (!interrupted)
                    throw new RuntimeException("Interrupt status was not set");
                System.out.println("Copy failed (this is expected).");
            }
            try {
                wakeup.get();
            } catch (InterruptedException ignore) { }
            Thread.interrupted();

            // copy source to target via task in thread pool, interrupting it
            // after a delay using cancel(true)
            CountDownLatch cancelLatch = new CountDownLatch(1);
            Future<Void> result = pool.submit(new Callable<Void>() {
                public Void call() throws IOException {
                    cancelLatch.countDown();
                    System.out.printf("Copying file at %d ms...%n",
                        System.currentTimeMillis());
                    Files.copy(source, target, ExtendedCopyOption.INTERRUPTIBLE,
                        StandardCopyOption.REPLACE_EXISTING);
                    System.out.printf("Done copying at %d ms...%n",
                        System.currentTimeMillis());
                    return null;
                }
            });
            try {
                cancelLatch.await();
                Thread.sleep(CANCEL_DELAY_IN_MS);
            } catch (InterruptedException ignore) {
            }
            if (result.isDone())
                throw new RuntimeException("Copy finished before cancellation");
            System.out.printf("Cancelling at %d ms...%n",
                System.currentTimeMillis());
            boolean cancelled  = result.cancel(true);
            if (cancelled)
                System.out.println("Copy cancelled.");
            else {
                result.get();
                throw new RuntimeException("Copy was not cancelled");
            }
        } finally {
            pool.shutdown();
        }
    }
}
