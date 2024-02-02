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
 * @summary concurrency of BasicDirectoryModel and JFileChooser
 */
public final class BasicDirectoryModelConcurrency extends ThreadGroup {
    /** Initial number of files. */
    private static final long NUMBER_OF_FILES = 100;
    /** Maximum number of files created on a timer tick. */
    private static final long LIMIT_FILES = 20;

    /** Timer period (delay) for creating new files. */
    private static final long TIMER_PERIOD = 500;

    /**
     * Number of threads running {@code fileChooser.rescanCurrentDirectory()}.
     */
    private static final int NUMBER_OF_THREADS = 2;
    /** Number of repeated calls to {@code rescanCurrentDirectory}. */
    private static final int NUMBER_OF_REPEATS = 5_000;
    /** Maximum amount a thread waits before initiating rescan. */
    private static final long LIMIT_SLEEP = 100;


    private static final CyclicBarrier start = new CyclicBarrier(NUMBER_OF_THREADS);
    private static final CyclicBarrier end = new CyclicBarrier(NUMBER_OF_THREADS + 1);

    private static final List<Thread> threads = new ArrayList<>(NUMBER_OF_THREADS);

    private static final AtomicReference<Throwable> exception =
            new AtomicReference<>();


    public static void main(String[] args) throws Throwable {
        try {
            ThreadGroup threadGroup = new BasicDirectoryModelConcurrency();
            Thread runner = new Thread(threadGroup, BasicDirectoryModelConcurrency::wrapper);
            runner.start();
            runner.join();
        } catch (Throwable throwable) {
            handleException(throwable);
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
            Files.delete(temp);
        }
    }


    private BasicDirectoryModelConcurrency() {
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
                  .forEach(BasicDirectoryModelConcurrency::deleteFile);
        }
    }

    private static void deleteFile(final Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
