/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.nio.file.Paths;
import java.nio.file.*;
import java.net.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a microbenchmark as a stress test. Since we do not use benchmarks to
 * actually measure performance, it is ok to have multiple benchmarks run
 * concurrently, therefore JMH is configured to ignore lock.
 * <pre>
 * Usage: [jmh flag]* <benchmark name>
 * </pre>
 */
 
public class MicroRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new Error("Usage: [jmh flag]* <benchmark name>");
        }
        String[] cmds = getCmd(args);
        ProcessBuilder pb = ProcessTools.createTestJvm(cmds);
        Process p = ProcessTools.startProcess("jmh", pb);
        int exitCode = p.waitFor();
        Asserts.assertEQ(0, exitCode);
    }

    private static String[] getCmd(String[] args) throws Exception {
        List<String> extraFlags = new ArrayList<>();

        // add jar with microbenchmarks and jmh to CP
        extraFlags.add("-cp");
        extraFlags.add(System.getProperty("java.class.path")
                + File.pathSeparator
                + Paths.get(System.getenv("TEST_IMAGE_MICROBENCHMARK_JAR")).toString());
        // jmh needs java.io to be open
        extraFlags.add("--add-opens");
        extraFlags.add("java.base/java.io=ALL-UNNAMED");
        // allow concurrent execution
        extraFlags.add("-Djmh.ignoreLock=true");
        // redirect tmp to jtreg-managed work dir
        extraFlags.add("-Djava.io.tmpdir=" + System.getProperty("user.dir"));
        // use jmh entry point
        extraFlags.add("org.openjdk.jmh.Main");
        String[] result = new String[extraFlags.size() + args.length];
        extraFlags.toArray(result);
        System.arraycopy(args, 0, result, extraFlags.size(), args.length);
        return result;
    }
}

