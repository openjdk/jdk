/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jcmd;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.process.OutputAnalyzer;
/**
 * @test
 * @summary The test verifies JFR.view command
 * @key jfr
 * @requires vm.hasJFR
 * @requires (vm.gc == "G1" | vm.gc == null)
 *           & vm.opt.ExplicitGCInvokesConcurrent != false
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:-ExplicitGCInvokesConcurrent -XX:-DisableExplicitGC
 *                   -XX:+UseG1GC -Xlog:jfr+dcmd=debug -Xlog:jfr+system+parser=info jdk.jfr.jcmd.TestJcmdView
 */
public class TestJcmdView {
    private static volatile Instant lastTimestamp;

    public static void main(String... args) throws Throwable {
        CountDownLatch jvmInformation = new CountDownLatch(1);
        CountDownLatch systemGC = new CountDownLatch(1);
        CountDownLatch threadSleep = new CountDownLatch(1);
        CountDownLatch gcHeapSummary = new CountDownLatch(1);
        CountDownLatch oldCollection = new CountDownLatch(1);
        CountDownLatch garbageCollection = new CountDownLatch(1);

        try (RecordingStream rs = new RecordingStream()) {
            // Make sure chunks are not released after consumption
            rs.setMaxSize(Long.MAX_VALUE);
            rs.enable("jdk.JVMInformation").with("period", "beginChunk");
            rs.enable("jdk.SystemGC");
            rs.enable("jdk.ThreadSleep").withoutThreshold().withStackTrace();
            rs.enable("jdk.GCHeapSummary");
            rs.enable("jdk.GarbageCollection");
            rs.enable("jdk.OldGarbageCollection");
            rs.enable("jdk.YoungGarbageCollection");
            rs.onEvent("jdk.JVMInformation", e -> {
                jvmInformation.countDown();
                System.out.println(e);
                storeLastTimestamp(e);
            });
            rs.onEvent("jdk.SystemGC", e -> {
                systemGC.countDown();
                System.out.println(e);
                storeLastTimestamp(e);
            });
            rs.onEvent("jdk.ThreadSleep", e -> {
                threadSleep.countDown();
                System.out.println(e);
                storeLastTimestamp(e);
            });
            rs.onEvent("jdk.GCHeapSummary", e -> {
                gcHeapSummary.countDown();
                System.out.println(e);
                storeLastTimestamp(e);
            });
            rs.onEvent("jdk.OldGarbageCollection", e -> {
                oldCollection.countDown();
                System.out.println(e);
                storeLastTimestamp(e);
            });
            rs.onEvent("jdk.GarbageCollection", e-> {
                garbageCollection.countDown();
                System.out.println(e);
                storeLastTimestamp(e);
            });
            rs.startAsync();
            // Emit some GC events
            System.gc();
            System.gc();
            System.gc();
            // Emit thread sleep event
            Thread.sleep(1);
            // Wait for them being in the repository
            jvmInformation.await();
            systemGC.await();
            threadSleep.await();
            gcHeapSummary.await();
            oldCollection.await();
            // Wait for Instant.now() to advance 1 s past the last event timestamp.
            // The rationale for this is twofold:
            // - DcmdView starts one second before Instant.now() (to make the command
            //   responsive for the user).
            // - Instant.now() and the event timestamp use different time sources
            //   and they need to synchronize.
            Instant end = lastTimestamp.plusSeconds(1);
            while (Instant.now().isBefore(end)) {
                Thread.sleep(10);
            }
            System.out.println("Time before testEventType() " + Instant.now());
            // Test events that are in the current chunk
            testEventType();
            testFormView();
            testTableView();
            rs.disable("jdk.JVMInformation");
            // Force chunk rotation
            System.out.println("About to rotate chunk");
            rotate();
            // Test events that are NOT in current chunk
            testEventType();
            testFormView();
            testTableView();
        }
    }

    private static void storeLastTimestamp(RecordedEvent e) {
        Instant time = e.getEndTime();
        if (lastTimestamp == null || time.isAfter(lastTimestamp)) {
            lastTimestamp = time;
        }
    }

    private static void rotate() {
       try (Recording r = new Recording()) {
           r.start();
       }
    }

    private static void testFormView() throws Throwable {
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.view", "jvm-information");
        // Verify title
        output.shouldContain("JVM Information");
        // Verify field label
        output.shouldContain("VM Arguments:");
        // Verify field value
        long pid = ProcessHandle.current().pid();
        String lastThreeDigits = String.valueOf(pid % 1000);
        output.shouldContain(lastThreeDigits);
    }

    private static void testTableView() throws Throwable {
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.view", "verbose=true", "gc");
        // Verify heading
        output.shouldContain("Longest Pause");
        // Verify verbose heading
        output.shouldContain("(longestPause)");
        // Verify row contents
        output.shouldContain("Old Garbage Collection");
        // Verify verbose query
        output.shouldContain("SELECT");
    }

    private static void testEventType() throws Throwable {
        OutputAnalyzer output = JcmdHelper.jcmd(
             "JFR.view", "verbose=true", "width=300", "cell-height=100", "ThreadSleep");
        // Verify title
        output.shouldContain("Thread Sleep");
        // Verify headings
        output.shouldContain("Sleep Time");
        // Verify verbose headings
        output.shouldContain("time");
        // Verify thread value
        output.shouldContain(Thread.currentThread().getName());
        // Verify stack frame
        output.shouldContain("TestJcmdView.main");
    }
}
