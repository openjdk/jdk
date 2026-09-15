/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8392223
 * @summary Verify that concurrent time zone name lookups match
 *          sequential lookups
 * @library /test/lib
 * @run main TimeZoneNameConcurrencyTest
 */
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TimeZoneNameConcurrencyTest {
    static final int FORKS = 8;
    static final int THREADS = 32;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if (args[0].equals("serial")) {
                printNames(TimeZone.getTimeZone("Asia/Seoul"));
            } else {
                child();
            }
            return;
        }
        // Names after concurrent first lookups must match a single-threaded
        // run in a fresh JVM.
        String expected = run("serial");
        for (int i = 0; i < FORKS; i++) {
            String actual = run("child");
            if (!expected.equals(actual)) {
                throw new RuntimeException("Fork " + i
                        + ": serial names=" + expected
                        + ", concurrent names=" + actual);
            }
        }
        System.out.println("OK: " + FORKS + " forks match serial names");
    }

    private static String run(String mode) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-Xmx64m", "TimeZoneNameConcurrencyTest", mode);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);
        return output.getStdout().trim();
    }

    static void child() throws Exception {
        TimeZone tz = TimeZone.getTimeZone("Asia/Seoul");
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        var failures = new ConcurrentLinkedQueue<Throwable>();
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            final Locale l = (i % 2 == 1) ? Locale.ENGLISH : Locale.US;
            ts[i] = new Thread(() -> {
                try {
                    barrier.await();
                    tz.getDisplayName(false, TimeZone.SHORT, l);
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        if (!failures.isEmpty()) {
            RuntimeException failure = new RuntimeException("Worker failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
        printNames(tz);
    }

    private static void printNames(TimeZone tz) {
        String us = tz.getDisplayName(false, TimeZone.SHORT, Locale.US);
        String en = tz.getDisplayName(false, TimeZone.SHORT, Locale.ENGLISH);
        System.out.println(us);
        System.out.println(en);
    }
}
