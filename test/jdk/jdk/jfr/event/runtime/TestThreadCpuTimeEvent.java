/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.event.runtime;

import com.sun.management.ThreadMXBean;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr
 *          jdk.management
 *
 * @run main/othervm jdk.jfr.event.runtime.TestThreadCpuTimeEvent
 */

/**
 */
public class TestThreadCpuTimeEvent {

    public static void main(String[] args) throws Throwable {
        testSimple();
        testCompareWithMXBean();
        testEventAtThreadExit();
    }

    private static final long eventPeriodMillis = 50;
    private static final String cpuConsumerThreadName = "cpuConsumer";

    // The cpu consumer will run for eventPeriodMillis times this factor to ensure that we see some
    // events even if the scheduler isn't cooperating.
    private static final long cpuConsumerRunFactor = 10;

    // The cpu consumer will run at least this number of loops, even if it takes longer than
    // the requested period of time (in case the thread didn't get scheduled within the allotted time).
    private static final long cpuConsumerMinCount = 1000000;

    static class CpuConsumingThread extends Thread {

        Duration runTime;
        CyclicBarrier barrier;
        volatile long counter;

        CpuConsumingThread(Duration runTime, CyclicBarrier barrier, String threadName) {
            super(threadName);
            this.runTime = runTime;
            this.barrier = barrier;
        }

        CpuConsumingThread(Duration runTime, CyclicBarrier barrier) {
            this(runTime, barrier, cpuConsumerThreadName);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    barrier.await();
                    Instant start = Instant.now();
                    counter = 0;
                    while ((Duration.between(start, Instant.now()).compareTo(runTime) < 0) ||
                            (counter < cpuConsumerMinCount)) {
                        counter++;
                    }
                    barrier.await();
                }
            } catch (BrokenBarrierException e) {
                // Another thread has been interrupted - wait for us to be interrupted as well
                while (!interrupted()) {
                    yield();
                }
            } catch (InterruptedException e) {
                // Normal way of stopping the thread
            }
        }
    }

    // For a given thread, check that accumulated processTime >= cpuTime >= userTime.
    // This may not hold for a single event instance due to differences in counter resolution
    static void verifyPerThreadInvariant(List<RecordedEvent> events, String threadName) {
        List<RecordedEvent> filteredEvents = events.stream()
                .filter(e -> e.getThread().getJavaName().equals(threadName))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .collect(Collectors.toList());

        int numCpus = Runtime.getRuntime().availableProcessors();
        Iterator<RecordedEvent> i = filteredEvents.iterator();
        while (i.hasNext()) {
            RecordedEvent event = i.next();

            Float systemLoad = (Float)event.getValue("system");
            Float userLoad = (Float)event.getValue("user");

            Asserts.assertLessThan(systemLoad + userLoad, 1.01f / numCpus); // 100% + rounding errors
        }
    }

    static Duration getAccumulatedTime(List<RecordedEvent> events, String threadName, String fieldName) {
        List<RecordedEvent> filteredEvents = events.stream()
                .filter(e -> e.getThread().getJavaName().equals(threadName))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .collect(Collectors.toList());

        int numCpus = Runtime.getRuntime().availableProcessors();
        Iterator<RecordedEvent> i = filteredEvents.iterator();
        RecordedEvent cur = i.next();
        Duration totalTime = Duration.ZERO;
        while (i.hasNext()) {
            RecordedEvent prev = cur;
            cur = i.next();

            Duration sampleTime = Duration.between(prev.getStartTime(), cur.getStartTime());
            Float load = (Float)cur.getValue(fieldName);

            // Adjust load to be thread-relative (fully loaded thread would give 100%)
            Float totalLoadForThread = load * numCpus;
            Duration threadTime = Duration.ofMillis((long) (sampleTime.toMillis() * totalLoadForThread));
            totalTime = totalTime.plus(threadTime);
        }

        return totalTime;
    }

    static List<RecordedEvent> generateEvents(int minimumEventCount, CyclicBarrier barrier) throws Throwable {
        int retryCount = 0;

        while (true) {
            Recording recording = new Recording();

            // Default period is once per chunk
            recording.enable(EventNames.ThreadCPULoad).withPeriod(Duration.ofMillis(eventPeriodMillis));
            recording.start();

            // Run a single pass
            barrier.await();
            barrier.await();

            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);

            long numEvents = events.stream()
                    .filter(e -> e.getThread().getJavaName().equals(cpuConsumerThreadName))
                    .count();

            // If the JFR periodicals thread is really starved, we may not get enough events.
            // In that case, we simply retry the operation.
            if (numEvents < minimumEventCount) {
                System.out.println("Not enough events recorded, trying again...");
                if (retryCount++ > 10) {
                    Asserts.fail("Retry count exceeded");
                    throw new RuntimeException();
                }
            } else {
                return events;
            }
        }
    }

    static void testSimple() throws Throwable {
        Duration testRunTime = Duration.ofMillis(eventPeriodMillis * cpuConsumerRunFactor);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CpuConsumingThread thread = new CpuConsumingThread(testRunTime, barrier);
        thread.start();

        List<RecordedEvent> events = generateEvents(1, barrier);
        verifyPerThreadInvariant(events, cpuConsumerThreadName);

        thread.interrupt();
        thread.join();
    }

    static void testCompareWithMXBean() throws Throwable {
        Duration testRunTime = Duration.ofMillis(eventPeriodMillis * cpuConsumerRunFactor);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CpuConsumingThread thread = new CpuConsumingThread(testRunTime, barrier);
        thread.start();

        List<RecordedEvent> beforeEvents = generateEvents(2, barrier);
        verifyPerThreadInvariant(beforeEvents, cpuConsumerThreadName);

        // Run a second single pass
        barrier.await();
        barrier.await();

        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        Duration cpuTime = Duration.ofNanos(bean.getThreadCpuTime(thread.getId()));
        Duration userTime = Duration.ofNanos(bean.getThreadUserTime(thread.getId()));

        // Check something that should hold even in the presence of unfortunate scheduling
        Asserts.assertGreaterThanOrEqual(cpuTime.toMillis(), eventPeriodMillis);
        Asserts.assertGreaterThanOrEqual(userTime.toMillis(), eventPeriodMillis);

        Duration systemTimeBefore = getAccumulatedTime(beforeEvents, cpuConsumerThreadName, "system");
        Duration userTimeBefore = getAccumulatedTime(beforeEvents, cpuConsumerThreadName, "user");
        Duration cpuTimeBefore = userTimeBefore.plus(systemTimeBefore);

        Asserts.assertLessThan(cpuTimeBefore, cpuTime);
        Asserts.assertLessThan(userTimeBefore, userTime);
        Asserts.assertGreaterThan(cpuTimeBefore, Duration.ZERO);

        thread.interrupt();
        thread.join();
    }

    static void testEventAtThreadExit() throws Throwable {
        Recording recording = new Recording();

        recording.enable(EventNames.ThreadCPULoad).withPeriod(Duration.ofHours(10));
        recording.start();

        Duration testRunTime = Duration.ofMillis(eventPeriodMillis * cpuConsumerRunFactor);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CpuConsumingThread thread = new CpuConsumingThread(testRunTime, barrier);

        // Run a single pass
        thread.start();
        barrier.await();
        barrier.await();

        thread.interrupt();
        thread.join();

        recording.stop();

        List<RecordedEvent> events = Events.fromRecording(recording);
        verifyPerThreadInvariant(events, cpuConsumerThreadName);

        int exitingCount = 0;
        for (RecordedEvent event : events) {
            RecordedThread eventThread = event.getThread();
            if (eventThread.getJavaName().equals(cpuConsumerThreadName)) {
                exitingCount++;
            }
        }
        Asserts.assertEquals(exitingCount, 1);
    }
}
