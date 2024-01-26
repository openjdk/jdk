import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import javax.swing.JFileChooser;

/*
 * @test
 * @bug 4966171 8240690
 * @summary concurrency of BasicDirectoryModel and JFileChooser
 */
public final class BasicDirectoryModelConcurrency {
    private static final long NUMBER_OF_FILES = 1_000;
    private static final int NUMBER_OF_THREADS = 10;

    public static void main(String[] args) throws Exception {
        final Path temp = Files.createTempDirectory("fileChooser-concurrency");
        final CyclicBarrier barrier = new CyclicBarrier(NUMBER_OF_THREADS);
        final List<Thread> threads = new ArrayList<>(NUMBER_OF_THREADS);

        final Timer timer = new Timer("File creator");

        try {
            createFiles(temp);

            final JFileChooser fc = new JFileChooser(temp.toFile());
            Stream.generate(() -> new Thread(new Scanner(barrier, fc)))
                  .limit(NUMBER_OF_THREADS)
                  .forEach(threads::add);

            timer.scheduleAtFixedRate(new TimerTask() {
                private long no = NUMBER_OF_FILES;

                @Override
                public void run() {
                    createFile(temp.resolve((++no) + ".file"));
                }
            }, 5, 5_000);

            threads.forEach(Thread::start);

            threads.forEach(BasicDirectoryModelConcurrency::join);
        } catch (Exception e) {
            threads.forEach(Thread::interrupt);
            throw e;
        } finally {
            timer.cancel();

            deleteFiles(temp);
            Files.delete(temp);
        }
    }

    private record Scanner(CyclicBarrier barrier, JFileChooser fileChooser)
            implements Runnable {

        @Override
        public void run() {
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }

            System.out.println("> rescan");
            int counter = 0;
            do {
                fileChooser.rescanCurrentDirectory();
            } while (++counter < 100 && !Thread.currentThread().isInterrupted());
            System.out.println("< rescan" + counter);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
}
