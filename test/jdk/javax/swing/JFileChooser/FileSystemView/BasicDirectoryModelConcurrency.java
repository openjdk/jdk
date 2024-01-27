import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import javax.swing.JFileChooser;

/*
 * @test
 * @bug 4966171 8240690
 * @summary concurrency of BasicDirectoryModel and JFileChooser
 */
public final class BasicDirectoryModelConcurrency {
    private static final long NUMBER_OF_FILES = 1_000;
    private static final int NUMBER_OF_THREADS = 1;
    public static final int NUMBER_OF_REPEATS = 2_000;

    private static final List<Thread> threads = new ArrayList<>(NUMBER_OF_THREADS);

    private static final AtomicReference<Throwable> exception =
            new AtomicReference<>();

    public static void main(String[] args) throws Throwable {
        long timeStart = System.currentTimeMillis();
        final Path temp = Files.createTempDirectory("fileChooser-concurrency");
        final CyclicBarrier start = new CyclicBarrier(NUMBER_OF_THREADS);
        final CyclicBarrier end = new CyclicBarrier(NUMBER_OF_THREADS + 1);

        final Timer timer = new Timer("File creator");

        try {
            createFiles(temp);

            final JFileChooser fc = new JFileChooser(temp.toFile());

            int counter = NUMBER_OF_THREADS;
            while (counter-- > 0) {
                Thread thread = new Thread(new Scanner(start, end, fc));
                threads.add(thread);
                thread.start();
            }

            timer.scheduleAtFixedRate(new CreateFilesTimerTask(temp),
                                      0, 500);

            end.await();
        } catch (Throwable e) {
            threads.forEach(Thread::interrupt);
            throw e;
        } finally {
            timer.cancel();

            deleteFiles(temp);
            Files.delete(temp);
        }
        long diff = System.currentTimeMillis() - timeStart;
        System.out.printf("Duration: %,d\n", diff);
        if (exception.get() != null) {
            throw exception.get();
        }
    }

    private record Scanner(CyclicBarrier start,
                           CyclicBarrier end,
                           JFileChooser fileChooser)
            implements Runnable {

        @Override
        public void run() {
            try {
                start.await();

                int counter = 0;
                try {
                    do {
                        fileChooser.rescanCurrentDirectory();
                        Thread.sleep((long) (Math.random() * 10));
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

    private static void handleException(Throwable throwable) {
        if (!exception.compareAndSet(null, throwable)) {
            exception.get().addSuppressed(throwable);
        }
        threads.stream()
               .filter(t -> t != Thread.currentThread())
               .forEach(Thread::interrupt);
    }

    private static void createFiles(final Path parent) {
        LongStream.range(0, NUMBER_OF_FILES)
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
                int count = (int) (Math.random() * 20);
                while (count-- > 0) {
                    createFile(temp.resolve((++no) + ".file"));
                }
            } catch (Throwable t) {
                handleException(t);
            }
        }
    }
}
