/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import javax.swing.JFileChooser;

/*
 * @test
 * @bug 8325179
 * @requires os.family == "windows"
 * @summary Verifies there's only one BasicDirectoryModel.FilesLoader thread
 *          at any given moment
 * @run main/othervm -Djava.awt.headless=true LoaderThreadCount
 */
public final class LoaderThreadCount extends ThreadGroup {
    /** Initial number of files. */
    private static final long NUMBER_OF_FILES = 500;

    /**
     * Number of threads running {@code fileChooser.rescanCurrentDirectory()}.
     */
    private static final int NUMBER_OF_THREADS = 5;

    /** Number of snapshots with live threads. */
    private static final int SNAPSHOTS = 20;

    /** The barrier to synchronise scanner threads and capturing live threads. */
    private static final CyclicBarrier start = new CyclicBarrier(NUMBER_OF_THREADS + 1);

    /** List of scanner threads. */
    private static final List<Thread> threads = new ArrayList<>(NUMBER_OF_THREADS);

    /**
     * Stores an exception caught by any of the threads.
     * If more exceptions are caught, they're added as suppressed exceptions.
     */
    private static final AtomicReference<Throwable> exception =
            new AtomicReference<>();

    /**
     * Stores an {@code IOException} thrown while removing the files.
     */
    private static final AtomicReference<IOException> ioException =
            new AtomicReference<>();


    public static void main(String[] args) throws Throwable {
        try {
            // Start the test in its own thread group to catch and handle
            // all thrown exceptions, in particular in
            // BasicDirectoryModel.FilesLoader which is created by Swing.
            ThreadGroup threadGroup = new LoaderThreadCount();
            Thread runner = new Thread(threadGroup,
                                       LoaderThreadCount::wrapper,
                                       "Test Runner");
            runner.start();
            runner.join();
        } catch (Throwable throwable) {
            handleException(throwable);
        }

        if (ioException.get() != null) {
            System.err.println("An error occurred while removing files:");
            ioException.get().printStackTrace();
        }

        if (exception.get() != null) {
            throw exception.get();
        }
    }

    private static void wrapper() {
        final long timeStart = System.currentTimeMillis();
        try {
            runTest(timeStart);
        } catch (Throwable throwable) {
            handleException(throwable);
        } finally {
            System.out.printf("Duration: %,d\n",
                              (System.currentTimeMillis() - timeStart));
        }
    }

    private static void runTest(final long timeStart) throws Throwable {
        final Path temp = Files.createDirectory(Paths.get("fileChooser-concurrency-" + timeStart));

        try {
            createFiles(temp);

            final JFileChooser fc = new JFileChooser(temp.toFile());

            threads.addAll(Stream.generate(() -> new Thread(new Scanner(fc)))
                                 .limit(NUMBER_OF_THREADS)
                                 .toList());
            threads.forEach(Thread::start);

            // Create snapshots of live threads
            List<Thread[]> threadsCapture =
                    Stream.generate(LoaderThreadCount::getThreadSnapshot)
                          .limit(SNAPSHOTS)
                          .toList();

            threads.forEach(Thread::interrupt);

            List<Long> loaderCount =
                    threadsCapture.stream()
                                  .map(ta -> Arrays.stream(ta)
                                                   .filter(Objects::nonNull)
                                                   .map(Thread::getName)
                                                   .filter(tn -> tn.startsWith("Basic L&F File Loading Thread"))
                                                   .count())
                                  .filter(c -> c > 0)
                                  .toList();

            if (loaderCount.isEmpty()) {
                throw new RuntimeException("Invalid results: no loader threads detected");
            }

            System.out.println("Number of snapshots: " + loaderCount.size());

            long ones = loaderCount.stream()
                                   .filter(n -> n == 1)
                                   .count();
            long twos = loaderCount.stream()
                                   .filter(n -> n == 2)
                                   .count();
            long count = loaderCount.stream()
                                    .filter(n -> n > 2)
                                    .count();
            System.out.println("Number of snapshots where number of loader threads:");
            System.out.println("  = 1: " + ones);
            System.out.println("  = 2: " + twos);
            System.out.println("  > 2: " + count);
            if (count > loaderCount.size() / 2) {
                throw new RuntimeException("Detected " + count + " snapshots "
                                           + "with several loading threads");
            }
        } catch (Throwable e) {
            threads.forEach(Thread::interrupt);
            throw e;
        } finally {
            deleteFiles(temp);
            deleteFile(temp);
        }
    }

    private static Thread[] getThreadSnapshot() {
        try {
            start.await();
            // Allow for the scanner threads to initiate re-scanning
            Thread.sleep(10);

            Thread[] array = new Thread[Thread.activeCount()];
            Thread.currentThread()
                  .getThreadGroup()
                  .enumerate(array, false);

            // Additional delay between captures
            Thread.sleep(500);

            return array;
        } catch (InterruptedException | BrokenBarrierException e) {
            handleException(e);
            throw new RuntimeException("getThreadSnapshot is interrupted");
        }
    }


    private LoaderThreadCount() {
        super("bdmConcurrency");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        handleException(t, e);
    }

    private static void handleException(Throwable throwable) {
        handleException(Thread.currentThread(), throwable);
    }

    private static void handleException(final Thread thread,
                                        final Throwable throwable) {
        System.err.println("Exception in " + thread.getName() + ": "
                           + throwable.getClass()
                           + (throwable.getMessage() != null
                              ? ": " + throwable.getMessage()
                              : ""));
        if (!exception.compareAndSet(null, throwable)) {
            exception.get().addSuppressed(throwable);
        }
        threads.stream()
               .filter(t -> t != thread)
               .forEach(Thread::interrupt);
    }


    private record Scanner(JFileChooser fileChooser)
            implements Runnable {

        @Override
        public void run() {
            try {
                do {
                    start.await();
                    fileChooser.rescanCurrentDirectory();
                } while (!Thread.interrupted());
            } catch (InterruptedException | BrokenBarrierException e) {
                // Just exit the loop
            }
        }
    }

    private static void createFiles(final Path parent) {
        LongStream.range(0, LoaderThreadCount.NUMBER_OF_FILES)
                  .mapToObj(n -> parent.resolve(n + ".file"))
                  .forEach(LoaderThreadCount::createFile);
    }

    private static void createFile(final Path file) {
        try {
            Files.createFile(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteFiles(final Path parent) throws IOException {
        try (var stream = Files.walk(parent)) {
            stream.filter(p -> p != parent)
                  .forEach(LoaderThreadCount::deleteFile);
        }
    }

    private static void deleteFile(final Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            if (!ioException.compareAndSet(null, e)) {
                ioException.get().addSuppressed(e);
            }
        }
    }
}
