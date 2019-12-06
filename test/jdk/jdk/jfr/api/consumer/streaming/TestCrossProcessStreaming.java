/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.consumer.streaming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.tools.attach.VirtualMachine;
import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.EventStream;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @summary Test scenario where JFR event producer is in a different process
 *          with respect to the JFR event stream consumer.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.attach
 *          jdk.jfr
 * @run main jdk.jfr.api.consumer.streaming.TestCrossProcessStreaming
 */

public class TestCrossProcessStreaming {
    static String MAIN_STARTED_TOKEN = "MAIN_STARTED";

    public static class TestEvent extends Event {
    }

    public static class ResultEvent extends Event {
        int nrOfEventsProduced;
    }

    public static class EventProducer {
        public static void main(String... args) throws Exception {
            CrossProcessSynchronizer sync = new CrossProcessSynchronizer();
            log(MAIN_STARTED_TOKEN);

            long pid = ProcessHandle.current().pid();
            int nrOfEvents = 0;
            boolean exitRequested = false;
            while (!exitRequested) {
                TestEvent e = new TestEvent();
                e.commit();
                nrOfEvents++;
                if (nrOfEvents % 1000 == 0) {
                    Thread.sleep(100);
                    exitRequested = CrossProcessSynchronizer.exitRequested(pid);
                }
            }

            ResultEvent re = new ResultEvent();
            re.nrOfEventsProduced = nrOfEvents;
            re.commit();

            log("Number of TestEvents generated: " + nrOfEvents);
        }
    }


    static class CrossProcessSynchronizer {
        static void requestExit(long pid) throws Exception {
            Files.createFile(file(pid));
       }

        static boolean exitRequested(long pid) throws Exception {
            return Files.exists(file(pid));
        }

        static Path file(long pid) {
            return Paths.get(".", "exit-requested-" + pid);
        }
    }


    static class ConsumedEvents {
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger whileProducerAlive = new AtomicInteger(0);
        AtomicInteger produced = new AtomicInteger(-1);
    }


    public static void main(String... args) throws Exception {
        Process p = startProducerProcess("normal");
        String repo = getJfrRepository(p);

        ConsumedEvents ce = consumeEvents(p, repo);

        p.waitFor();
        Asserts.assertEquals(p.exitValue(), 0,
                             "Process exited abnormally, exitValue = " + p.exitValue());

        Asserts.assertEquals(ce.total.get(), ce.produced.get(), "Some events were lost");

        // Expected that some portion of events emitted by the producer are delivered
        // to the consumer while producer is still alive, at least one event for certain.
        // Assertion below is disabled due to: JDK-8235206
        // Asserts.assertLTE(1, ce.whileProducerAlive.get(),
        //                   "Too few events are delivered while producer is alive");
    }

    static Process startProducerProcess(String extraParam) throws Exception {
        ProcessBuilder pb =
            ProcessTools.createJavaProcessBuilder(false,
                                                  "-XX:StartFlightRecording",
                                                  EventProducer.class.getName(),
                                                  extraParam);
        Process p = ProcessTools.startProcess("Event-Producer", pb,
                                              line -> line.equals(MAIN_STARTED_TOKEN),
                                              0, TimeUnit.SECONDS);
        return p;
    }

    static String getJfrRepository(Process p) throws Exception {
        String repo = null;

        // It may take little bit of time for the observed process to set the property after
        // the process starts, therefore read the property in a loop.
        while (repo == null) {
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(p.pid()));
            repo = vm.getSystemProperties().getProperty("jdk.jfr.repository");
            vm.detach();
        }

        log("JFR repository = " + repo);
        return repo;
    }

    static ConsumedEvents consumeEvents(Process p, String repo) throws Exception {
        ConsumedEvents result = new ConsumedEvents();

        // wait for couple of JFR stream flushes before concluding the test
        CountDownLatch flushed = new CountDownLatch(2);

        // consume events produced by another process via event stream
        try (EventStream es = EventStream.openRepository(Paths.get(repo))) {
                es.onEvent(TestEvent.class.getName(),
                           e -> {
                               result.total.incrementAndGet();
                               if (p.isAlive()) {
                                   result.whileProducerAlive.incrementAndGet();
                               }
                           });

                es.onEvent(ResultEvent.class.getName(),
                           e -> result.produced.set(e.getInt("nrOfEventsProduced")));

                es.onFlush( () -> flushed.countDown() );

                // Setting start time to the beginning of the Epoch is a good way to start
                // reading the stream from the very beginning.
                es.setStartTime(Instant.EPOCH);
                es.startAsync();

                // await for certain number of flush events before concluding the test case
                flushed.await();
                CrossProcessSynchronizer.requestExit(p.pid());

                es.awaitTermination();
            }

        return result;
    }

    private static final void log(String msg) {
        System.out.println(msg);
    }
}
