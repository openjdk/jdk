/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     7194254
 * @summary Creates several threads with different java priorities and checks
 *      whether jstack reports correct priorities for them.
 *
 * @run main Test7194254
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test7194254 {

    public static void main(String[] args) throws Exception {
        final int NUMBER_OF_JAVA_PRIORITIES =
                Thread.MAX_PRIORITY - Thread.MIN_PRIORITY + 1;
        final CyclicBarrier barrier =
                new CyclicBarrier(NUMBER_OF_JAVA_PRIORITIES + 1);

        for (int p = Thread.MIN_PRIORITY; p <= Thread.MAX_PRIORITY; ++p) {
            final int priority = p;
            new Thread("Priority=" + p) {
                {
                    setPriority(priority);
                }
                public void run() {
                    try {
                        barrier.await(); // 1st
                        barrier.await(); // 2nd
                    } catch (Exception exc) {
                        // ignore
                    }
                }
            }.start();
        }
        barrier.await(); // 1st

        int matches = 0;
        List<String> failed = new ArrayList<>();
        try {
            String pid = getPid();
            String jstack = System.getProperty("java.home") + "/../bin/jstack";
            Process process = new ProcessBuilder(jstack, pid)
                    .redirectErrorStream(true).start();
            Pattern pattern = Pattern.compile(
                    "\\\"Priority=(\\d+)\\\".* prio=(\\d+).*");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        matches += 1;
                        String expected = matcher.group(1);
                        String actual = matcher.group(2);
                        if (!expected.equals(actual)) {
                            failed.add(line);
                        }
                    }
                }
            }
            barrier.await(); // 2nd
        } finally {
            barrier.reset();
        }

        if (matches != NUMBER_OF_JAVA_PRIORITIES) {
            throw new AssertionError("matches: expected " +
                    NUMBER_OF_JAVA_PRIORITIES + ", but was " + matches);
        }
        if (!failed.isEmpty()) {
            throw new AssertionError(failed.size() + ":" + failed);
        }
        System.out.println("Test passes.");
    }

    static String getPid() {
        RuntimeMXBean runtimebean = ManagementFactory.getRuntimeMXBean();
        String vmname = runtimebean.getName();
        int i = vmname.indexOf('@');
        if (i != -1) {
            vmname = vmname.substring(0, i);
        }
        return vmname;
    }

}

