/* @test
 * @library /test/lib
 * @run main/othervm --enable-native-access=ALL-UNNAMED Starvation 100000
 */

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import jdk.test.lib.thread.VThreadPinner;

public class Starvation {
    public static void main(String[] args) throws Exception {
        int iterations = Integer.parseInt(args[0]);

        for (int i = 0; i < iterations; i++) {
            var exRef = new AtomicReference<Exception>();
            Thread thread =  Thread.startVirtualThread(() -> {
                try {
                    runTest();
                } catch (Exception e) {
                    exRef.set(e);
                }
            });
            while (!thread.join(Duration.ofSeconds(1))) {
                System.out.format("%s iteration %d waiting for %s%n", Instant.now(), i, thread);
            }
            Exception ex = exRef.get();
            if (ex != null) {
                throw ex;
            }
        }
    }

    static void runTest() throws InterruptedException {
        int nprocs = Runtime.getRuntime().availableProcessors();

        var threads = new ArrayList<Thread>();
        Object lock = new Object();
        synchronized (lock) {
            for (int i = 0; i < nprocs - 1; i++) {
                var started = new CountDownLatch(1);
                Thread thread = Thread.startVirtualThread(() -> {
                    started.countDown();
                    VThreadPinner.runPinned(() -> {
                        synchronized (lock) {
                        }
                    });
                });
                started.await();
                threads.add(thread);
            }
        }

        for (Thread t : threads) {
            t.join();
        }
    }
}
