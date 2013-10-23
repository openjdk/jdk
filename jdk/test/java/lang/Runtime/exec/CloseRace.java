/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8024521
 * @summary Closing ProcessPipeInputStream at the time the process exits is racy
 *          and leads to the data corruption.
 * @library /lib/testlibrary
 * @run main/othervm/timeout=80 CloseRace
 */

/**
 * This test has a little chance to catch the race during the given default
 * time gap of 20 seconds. To increase the time gap, set the system property
 * CloseRaceTimeGap=N to the number of seconds.
 * Jtreg's timeoutFactor should also be set appropriately.
 *
 * For example, to run the test for 10 minutes:
 * > jtreg \
 *       -testjdk:$(PATH_TO_TESTED_JDK) \
 *       -timeoutFactor:10 \
 *       -DCloseRaceTimeGap=600 \
 *       $(PATH_TO_TESTED_JDK_SOURCE)/test/java/lang/Runtime/exec/CloseRace.java
 */

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import jdk.testlibrary.OutputAnalyzer;
import static jdk.testlibrary.ProcessTools.*;

public class CloseRace {

    public static void main(String args[]) throws Exception {
        ProcessBuilder pb = createJavaProcessBuilder("-Xmx64M", "CloseRace$Child",
                System.getProperty("CloseRaceTimeGap", "20"));
        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.stderrShouldNotContain("java.lang.OutOfMemoryError");
    }

    public static class Child {
        private static final String BIG_FILE = "bigfile";
        private static final String SMALL_FILE = "smallfile";
        private static int timeGap = 20; // seconds

        public static void main(String args[]) throws Exception {
            if (args.length > 0) {
                try {
                    timeGap = Integer.parseUnsignedInt(args[0]);
                    timeGap = Integer.max(timeGap, 10);
                    timeGap = Integer.min(timeGap, 10 * 60 * 60); // no more than 10 hours
                } catch (NumberFormatException ignore) {}
            }
            try (RandomAccessFile f = new RandomAccessFile(BIG_FILE, "rw")) {
                f.setLength(1024 * 1024 * 1024); // 1 Gb, greater than max heap size
            }
            try (FileOutputStream fs = new FileOutputStream(SMALL_FILE);
                 PrintStream ps = new PrintStream(fs)) {
                for (int i = 0; i < 128; ++i)
                    ps.println("line of text");
            }

            List<Thread> threads = new LinkedList<>();
            for (int i = 0; i < 99; ++i) {
                Thread t = new Thread (new OpenLoop());
                t.start();
                threads.add(t);
            }
            Thread t2 = new Thread (new ExecLoop());
            t2.start();
            threads.add(t2);

            Thread.sleep(timeGap);

            for (Thread t : threads) {
                t.interrupt();
                t.join();
            }
        }

        private static class OpenLoop implements Runnable {
            public void run() {
                final Path bigFilePath = Paths.get(BIG_FILE);
                while (!Thread.interrupted()) {
                    try (InputStream in = Files.newInputStream(bigFilePath)) {
                        // Widen the race window by sleeping 1ms
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }
            }
        }

        private static class ExecLoop implements Runnable {
            public void run() {
                List<String> command = new ArrayList<>(
                        Arrays.asList("/bin/cat", SMALL_FILE));
                while (!Thread.interrupted()) {
                    try {
                        ProcessBuilder builder = new ProcessBuilder(command);
                        final Process process = builder.start();
                        InputStream is = process.getInputStream();
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader br = new BufferedReader(isr);
                        while (br.readLine() != null) {}
                        process.waitFor();
                        isr.close();
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }
            }
        }
    }
}
