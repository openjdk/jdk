/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jfr.RecordingContext;
import jdk.jfr.RecordingContextKey;
import jdk.jfr.consumer.RecordedContext;
import jdk.jfr.consumer.RecordedContextEntry;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.RecurseThread;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.profiling.TestFullContext
 */
public class TestFullContext {
    private final static String EVENT_NAME = EventNames.ExecutionSample;
    private final static int MAX_DEPTH = 64; // currently hardcoded in jvm

    private final static RecordingContextKey contextKey =
        RecordingContextKey.inheritableForName("contextKey");

    public static void main(String[] args) throws Throwable {

        try (RecordingContext context = RecordingContext.builder().where(contextKey, "contextValue").build()) {
            RecurseThread[] threads = new RecurseThread[3];
            for (int i = 0; i < threads.length; ++i) {
                int depth = MAX_DEPTH - 1 + i;
                threads[i] = new RecurseThread(depth);
                threads[i].setName("recursethread-" + depth);
                threads[i].start();
            }

            for (RecurseThread thread : threads) {
                while (!thread.isInRunLoop()) {
                    Thread.sleep(20);
                }
            }

            assertContexts(threads);

            for (RecurseThread thread : threads) {
                thread.quit();
                thread.join();
            }
        }
    }

    private static void assertContexts( RecurseThread[] threads) throws Throwable {
        Recording recording= null;
        do {
            recording = new Recording();
            recording.enable(EVENT_NAME).withPeriod(Duration.ofMillis(50));
            recording.start();
            Thread.sleep(500);
            recording.stop();
        } while (!hasValidContexts(recording, threads));
    }

    private static boolean hasValidContexts(Recording recording, RecurseThread[] threads) throws Throwable {
        boolean[] isEventFound = new boolean[threads.length];

        for (RecordedEvent event : Events.fromRecording(recording)) {
            //System.out.println("Event: " + event);
            String threadName = Events.assertField(event, "sampledThread.javaName").getValue();
            long threadId = Events.assertField(event, "sampledThread.javaThreadId").getValue();

            for (int threadIndex = 0; threadIndex < threads.length; ++threadIndex) {
                RecurseThread currThread = threads[threadIndex];
                if (threadId == currThread.getId()) {
                    System.out.println("ThreadName=" + currThread.getName() + ", depth=" + currThread.totalDepth);
                    Asserts.assertEquals(threadName, currThread.getName(), "Wrong thread name");
                    RecordedContextEntry first = getFirstContextEntry(event);
                    if ("contextValue".equals(first.getValue()) && "contextKey".equals(first.getKey())) {
                        isEventFound[threadIndex] = true;
                        // checkEvent(event, currThread.totalDepth);
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
               System.out.println("Falied to validate all threads, will retry.");
               return false;
            }
        }
        return true;
    }

    public static RecordedContextEntry getFirstContextEntry(RecordedEvent event) {
        List<RecordedContextEntry> entries = event.getContext().getEntries();
        Asserts.assertFalse(entries.isEmpty(), "Context Entries was empty");
        return entries.get(0);
    }

    // private static void checkEvent(RecordedEvent event, int expectedDepth) throws Throwable {
    //     RecordedContext context = null;
    //     try {
    //         context = event.getContext();
    //         List<RecordedContextEntry> frames = context.getEntries();
    //         Asserts.assertEquals(Math.min(MAX_DEPTH, expectedDepth), frames.size(), "Wrong context depth. Expected:" + expectedDepth);
    //         List<String> expectedMethods = getExpectedMethods(expectedDepth);
    //         Asserts.assertEquals(expectedMethods.size(), frames.size(), "Wrong expectedMethods depth. Test error.");

    //         for (int i = 0; i < frames.size(); ++i) {
    //             String name = frames.get(i).getMethod().getName();
    //             String expectedName = expectedMethods.get(i);
    //             System.out.printf("method[%d]=%s, expected=%s%n", i, name, expectedName);
    //             Asserts.assertEquals(name, expectedName, "Wrong method name");
    //         }

    //         boolean isTruncated = context.isTruncated();
    //         boolean isTruncateExpected = expectedDepth > MAX_DEPTH;
    //         Asserts.assertEquals(isTruncated, isTruncateExpected, "Wrong value for isTruncated. Expected:" + isTruncateExpected);

    //         String firstMethod = frames.get(frames.size() - 1).getMethod().getName();
    //         boolean isFullTrace = "run".equals(firstMethod);
    //         String msg = String.format("Wrong values for isTruncated=%b, isFullTrace=%b", isTruncated, isFullTrace);
    //         Asserts.assertTrue(isTruncated != isFullTrace, msg);
    //     } catch (Throwable t) {
    //         System.out.println(String.format("context:%n%s", context));
    //         throw t;
    //     }
    // }

    // private static List<String> getExpectedMethods(int depth) {
    //     List<String> methods = new ArrayList<>();
    //     methods.add("recurseEnd");
    //     for (int i = 0; i < depth - 2; ++i) {
    //         methods.add((i % 2) == 0 ? "recurseA" : "recurseB");
    //     }
    //     methods.add("run");
    //     if (depth > MAX_DEPTH) {
    //         methods = methods.subList(0, MAX_DEPTH);
    //     }
    //     return methods;
    // }
}
