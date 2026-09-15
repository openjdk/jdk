/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.profiling;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.RecurseThread;

/*
 * Exercises sampling events across the following matrix:
 *
 *   ExecutionSample x platform thread
 *   ExecutionSample x virtual thread
 *   CPUTimeSample   x platform thread
 *   CPUTimeSample   x virtual thread
 *
 * Each cell verifies that the event identifies the sampled thread and that
 * its stack trace contains the expected recursive Java frames. ExecutionSample
 * additionally verifies that eventThread and sampledThread identify the same
 * thread.
 */
public class StackTraceTestMatrix {
    private final static int MAX_DEPTH = 64; // currently hardcoded in jvm

    private final String eventName;
    private final String threadFieldName;
    private final boolean virtual;

    private StackTraceTestMatrix(String eventName, String threadFieldName, boolean virtual) {
        this.eventName = eventName;
        this.threadFieldName = threadFieldName;
        this.virtual = virtual;
    }

    public static void runAllThreadKinds(String eventName, String threadFieldName) throws Throwable {
        runPlatformThreads(eventName, threadFieldName);
        new StackTraceTestMatrix(eventName, threadFieldName, true).run();
    }

    public static void runPlatformThreads(String eventName, String threadFieldName) throws Throwable {
        new StackTraceTestMatrix(eventName, threadFieldName, false).run();
    }

    public void run() throws Throwable {
        RecurseThread[] threads = new RecurseThread[3];
        // Virtual threads execute the RecurseThread workloads on distinct Thread objects.
        Thread[] javaThreads = new Thread[threads.length];
        for (int i = 0; i < threads.length; ++i) {
            int depth = MAX_DEPTH - 1 + i;
            threads[i] = new RecurseThread(depth);
            String name = "recursethread-" + depth;
            Thread thread = virtual ? Thread.ofVirtual().unstarted(threads[i]) : threads[i];
            thread.setName(name);
            javaThreads[i] = thread;
            thread.start();
        }

        for (RecurseThread thread : threads) {
            while (!thread.isInRunLoop()) {
                Thread.sleep(20);
            }
        }

        assertStackTraces(threads, javaThreads);

        for (int i = 0; i < threads.length; ++i) {
            threads[i].quit();
            javaThreads[i].join();
        }
    }

    private void assertStackTraces(RecurseThread[] threads, Thread[] javaThreads) throws Throwable {
        while (true) {
            try (Recording recording = new Recording()) {
                if (eventName.equals(EventNames.CPUTimeSample)) {
                    recording.enable(eventName).with("throttle", "50ms");
                } else {
                    recording.enable(eventName).withPeriod(Duration.ofMillis(50));
                }
                recording.start();
                Thread.sleep(500);
                recording.stop();
                if (hasValidStackTraces(recording, threads, javaThreads)) {
                    break;
                }
            }
        };
    }

    private boolean hasValidStackTraces(Recording recording, RecurseThread[] threads, Thread[] javaThreads) throws Throwable {
        boolean[] isEventFound = new boolean[threads.length];

        for (RecordedEvent event : Events.fromRecording(recording)) {
            System.out.println("Event: " + event);
            if (EventNames.ExecutionSample.equals(event.getEventType().getName())) {
                long sampledThreadId =
                        Events.assertField(event, "sampledThread.javaThreadId").getValue();
                long eventThreadId =
                        Events.assertField(event, "eventThread.javaThreadId").getValue();
                Asserts.assertEquals(eventThreadId, sampledThreadId,
                        "eventThread and sampledThread must identify the same thread");
            }
            String threadName = Events.assertField(event, threadFieldName + ".javaName").getValue();
            long threadId = Events.assertField(event, threadFieldName + ".javaThreadId").getValue();

            for (int threadIndex = 0; threadIndex < threads.length; ++threadIndex) {
                RecurseThread currThread = threads[threadIndex];
                Thread javaThread = javaThreads[threadIndex];
                if (threadId == javaThread.threadId()) {
                    System.out.println("ThreadName=" + javaThread.getName() + ", depth=" + currThread.totalDepth);
                    Asserts.assertEquals(threadName, javaThread.getName(), "Wrong thread name");
                    if ("recurseEnd".equals(getTopMethodName(event))) {
                        isEventFound[threadIndex] = true;
                        checkEvent(event, currThread.totalDepth);
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < threads.length; ++i) {
            String msg = "threadIndex=%d, recurseDepth=%d, isEventFound=%b%n";
            System.out.printf(msg, i, threads[i].totalDepth, isEventFound[i]);
        }
        for (int i = 0; i < threads.length; ++i) {
            if(!isEventFound[i]) {
               // no assertion, let's retry.
               // Could be race condition, i.e safe point during Thread.sleep
               System.out.println("Failed to validate all threads, will retry.");
               return false;
            }
        }
        return true;
    }

    public String getTopMethodName(RecordedEvent event) {
        List<RecordedFrame> frames = event.getStackTrace().getFrames();
        Asserts.assertFalse(frames.isEmpty(), "JavaFrames was empty");
        return frames.getFirst().getMethod().getName();
    }

    private void checkEvent(RecordedEvent event, int expectedDepth) throws Throwable {
        RecordedStackTrace stacktrace = null;
        try {
            stacktrace = event.getStackTrace();
            List<RecordedFrame> frames = stacktrace.getFrames();
            int expectedStackDepth = Math.min(MAX_DEPTH, expectedDepth + (virtual ? 1 : 0));
            Asserts.assertEquals(expectedStackDepth, frames.size(), "Wrong stacktrace depth. Expected:" + expectedStackDepth);
            List<String> expectedMethods = getExpectedMethods(expectedDepth);
            Asserts.assertEquals(expectedMethods.size(), frames.size(), "Wrong expectedMethods depth. Test error.");

            for (int i = 0; i < frames.size(); ++i) {
                String name = frames.get(i).getMethod().getName();
                String expectedName = expectedMethods.get(i);
                System.out.printf("method[%d]=%s, expected=%s%n", i, name, expectedName);
                Asserts.assertEquals(name, expectedName, "Wrong method name");
            }

            boolean isTruncated = stacktrace.isTruncated();
            // Thread.runWith(Object, Runnable) invokes a virtual-thread task below
            // RecurseThread.run(). With MAX_DEPTH - 1 recursive frames, it fills the
            // final stack-depth slot, but continuation frames still remain below it.
            boolean isTruncateExpected = expectedDepth > MAX_DEPTH ||
                    (virtual && expectedDepth >= MAX_DEPTH - 1);
            Asserts.assertEquals(isTruncated, isTruncateExpected, "Wrong value for isTruncated. Expected:" + isTruncateExpected);

            String firstMethod = frames.getLast().getMethod().getName();
            if (!virtual) {
                boolean isFullTrace = "run".equals(firstMethod);
                String msg = String.format("Wrong values for isTruncated=%b, isFullTrace=%b", isTruncated, isFullTrace);
                Asserts.assertTrue(isTruncated != isFullTrace, msg);
            } else if (!isTruncated) {
                Asserts.assertEquals(firstMethod, "run", "A full virtual-thread trace should end at RecurseThread.run");
            }
        } catch (Throwable t) {
            System.out.println(String.format("stacktrace:%n%s", stacktrace));
            throw t;
        }
    }

    private List<String> getExpectedMethods(int depth) {
        List<String> methods = new ArrayList<>();
        methods.add("recurseEnd");
        for (int i = 0; i < depth - 2; ++i) {
            methods.add((i % 2) == 0 ? "recurseA" : "recurseB");
        }
        // A platform thread enters the recursive workload through RecurseThread.run().
        methods.add("run");
        if (virtual) {
            // A virtual thread invokes that Runnable through Thread.runWith(Object, Runnable).
            methods.add("runWith");
        }
        if (methods.size() > MAX_DEPTH) {
            methods = methods.subList(0, MAX_DEPTH);
        }
        return methods;
    }
}
