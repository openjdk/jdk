package jdk.jfr.threading;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @bug 8398060
 * @summary Verify virtual-thread event commits remain safe during concurrent class initialization
 * @requires vm.flagless
 * @requires vm.hasJFR & vm.continuations
 * @library /test/lib
 * @run main/othervm -Xint -Djdk.virtualThreadScheduler.parallelism=8
 *      jdk.jfr.threading.TestVirtualThreadCommit
 */
public class TestVirtualThreadCommit {
    private static final int THREADS = 8;

    static final class CommitEvent extends Event {
        double value;
    }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            recording.enable(CommitEvent.class).withoutStackTrace();
            recording.start();
            CyclicBarrier barrier = new CyclicBarrier(THREADS);
            AtomicInteger sequence = new AtomicInteger();
            var expected = new ConcurrentHashMap<Double, Long>();
            var futures = executor.invokeAll(Collections.nCopies(THREADS, () -> {
                CommitEvent event = new CommitEvent();
                event.value = sequence.incrementAndGet();
                expected.put(event.value, Thread.currentThread().threadId());
                barrier.await();
                event.commit();
                return null;
            }));
            for (var future : futures) {
                future.get();
            }
            recording.stop();
            var events = Events.fromRecording(recording);
            Asserts.assertEquals(events.size(), THREADS, "Unexpected event count");
            for (var event : events) {
                Asserts.assertEquals(event.getEventType().getName(), CommitEvent.class.getName());
                Long threadId = expected.remove(event.getDouble("value"));
                Asserts.assertNotNull(threadId, "Unexpected or duplicate payload");
                var thread = event.getThread();
                Asserts.assertNotNull(thread);
                Asserts.assertTrue(thread.isVirtual(), "Expected a virtual thread");
                Asserts.assertEquals(thread.getJavaThreadId(), threadId.longValue(), "Wrong event thread");
            }
            Asserts.assertTrue(expected.isEmpty(), "Missing events");
        }
    }
}
