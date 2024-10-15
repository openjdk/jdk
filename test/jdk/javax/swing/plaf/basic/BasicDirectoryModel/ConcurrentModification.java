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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import javax.swing.JFileChooser;

/*
 * @test
 * @bug 8323670 8307091 8240690
 * @requires os.family == "mac" | os.family == "linux"
 * @summary Verifies thread-safety of BasicDirectoryModel (JFileChooser)
 * @run main/othervm -Djava.awt.headless=true ConcurrentModification
 */
public final class ConcurrentModification extends ThreadGroup {
    /** Initial number of files. */
    private static final long NUMBER_OF_FILES = 50;
    /** Maximum number of files created on a timer tick. */
    private static final long LIMIT_FILES = 10;

    /** Timer period (delay) for creating new files. */
    private static final long TIMER_PERIOD = 250;

    /**
     * Number of threads running {@code fileChooser.rescanCurrentDirectory()}.
     */
    private static final int NUMBER_OF_THREADS = 5;
    /** Number of repeated calls to {@code rescanCurrentDirectory}. */
    private static final int NUMBER_OF_REPEATS = 2_000;
    /** Maximum amount a thread waits before initiating rescan. */
    private static final long LIMIT_SLEEP = 100;


    /** The barrier to start all the scanner threads simultaneously. */
    private static final CyclicBarrier start = new CyclicBarrier(NUMBER_OF_THREADS);
    /** The barrier to wait for all the scanner threads to complete, plus main thread. */
    private static final CyclicBarrier end = new CyclicBarrier(NUMBER_OF_THREADS + 1);

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
            ThreadGroup threadGroup = new ConcurrentModification();
            Thread runner = new Thread(threadGroup,
                                       ConcurrentModification::wrapper,
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

        final Timer timer = new Timer("File creator");

        try {
            createFiles(temp);

            final JFileChooser fc = new JFileChooser(temp.toFile());

            IntStream.range(0, NUMBER_OF_THREADS)
                     .forEach(i -> {
                         Thread thread = new Thread(new Scanner(fc));
                         threads.add(thread);
                         thread.start();
                     });

            timer.scheduleAtFixedRate(new CreateFilesTimerTask(temp),
                                      0, TIMER_PERIOD);

            end.await();
        } catch (Throwable e) {
            threads.forEach(Thread::interrupt);
            throw e;
        } finally {
            timer.cancel();

            deleteFiles(temp);
            deleteFile(temp);
        }
    }


    private ConcurrentModification() {
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
                start.await();

                int counter = 0;
                try {
                    do {
                        fileChooser.rescanCurrentDirectory();
                        Thread.sleep((long) (Math.random() * LIMIT_SLEEP));
                    } while (++counter < NUMBER_OF_REPEATS
                             && !Thread.interrupted());
                } catch (InterruptedException e) {
                    // Just exit the loop
                }
            } catch (Throwable throwable) {
                handleException(throwable);
            } finally {
                try {
                    end.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    handleException(e);
                }
            }
        }
    }

    private static void createFiles(final Path parent) {
        createFiles(parent, 0, NUMBER_OF_FILES);
    }

    private static void createFiles(final Path parent,
                                    final long start,
                                    final long end) {
        LongStream.range(start, end)
                  .forEach(n -> createFile(parent.resolve(n + ".file")));
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
                  .forEach(ConcurrentModification::deleteFile);
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

    private static final class CreateFilesTimerTask extends TimerTask {
        private final Path temp;
        private long no;

        public CreateFilesTimerTask(Path temp) {
            this.temp = temp;
            no = NUMBER_OF_FILES;
        }

        @Override
        public void run() {
            try {
                long count = (long) (Math.random() * LIMIT_FILES);
                createFiles(temp, no, no + count);
                no += count;
            } catch (Throwable t) {
                handleException(t);
            }
        }
    }
}
