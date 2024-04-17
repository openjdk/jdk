/*
 * Copyright (c) 2014, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package gc.parallel;

/**
 * @test TestAlwaysPreTouchBehavior
 * @summary Tests AlwaysPreTouch Bahavior, pages of java heap should be pretouched with AlwaysPreTouch enabled. This test reads RSS of test process, which should be bigger than heap size(1g) with AlwaysPreTouch enabled.
 * @requires vm.gc.Parallel
 * @requires vm.debug != true
 * @requires os.family == "linux"
 * @requires os.maxMemory > 2G
 * @library /test/lib
 * @run main/othervm -Xmx1g -Xms1g -XX:+UseParallelGC -XX:+AlwaysPreTouch gc.parallel.TestAlwaysPreTouchBehavior
 */
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.*;
import javax.management.*;
import java.lang.management.*;
import jdk.test.lib.Utils;
import jdk.test.lib.Asserts;
import java.lang.management.*;
import java.util.stream.*;
import java.io.*;

public class TestAlwaysPreTouchBehavior {
    public static long getProcessRssInKb() throws IOException {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        // Read RSS from /proc/$pid/status. Only available on Linux.
        String processStatusFile = "/proc/" + pid + "/status";
        BufferedReader reader = new BufferedReader(new FileReader(processStatusFile));
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("VmRSS:")) {
                break;
            }
        }
        reader.close();
        return Long.valueOf(line.split("\\s+")[1].trim());
    }
    public static void main(String [] args) {
    long rss = 0;
    Runtime runtime = Runtime.getRuntime();
    long committedMemory = runtime.totalMemory() / 1024; // in kb
    try {
        rss = getProcessRssInKb();
    } catch (Exception e) {
        System.out.println("cannot get RSS, just skip");
        return; // Did not get avaiable RSS, just ignore this test
    }
    Asserts.assertGreaterThanOrEqual(rss, committedMemory, "RSS of this process(" + rss + "kb) should be bigger than or equal to committed heap mem(" + committedMemory + "kb)");
   }
}

