/*
 * Copyright (c) 2023, Red Hat Inc.
 *
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

import java.io.File;
import java.io.FileOutputStream;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import jdk.internal.platform.Metrics;


// Check dynamic limits updating. Metrics (java) side.
public class LimitUpdateChecker {

    private static final File UPDATE_FILE = new File("/tmp", "limitsUpdated");
    private static final File STARTED_FILE = new File("/tmp", "started");

    public static void main(String[] args) throws Exception {
        System.out.println("Running LimitUpdateChecker...");
        Metrics metrics = jdk.internal.platform.Container.metrics();
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        printMetrics(osBean, metrics); // initial limits
        createStartedFile();
        while (!UPDATE_FILE.exists()) {
            Thread.sleep(200);
        }
        System.out.println("'limitsUpdated' file appeared. Stopped loop.");
        printMetrics(osBean, metrics); // updated limits
        System.out.println("LimitUpdateChecker DONE.");
    }

    private static void printMetrics(OperatingSystemMXBean osBean, Metrics metrics) {
        System.out.println(String.format("Runtime.availableProcessors: %d", Runtime.getRuntime().availableProcessors()));
        System.out.println(String.format("OperatingSystemMXBean.getAvailableProcessors: %d", osBean.getAvailableProcessors()));
        System.out.println("Metrics.getMemoryLimit() == " + metrics.getMemoryLimit());
        System.out.println(String.format("OperatingSystemMXBean.getTotalMemorySize: %d", osBean.getTotalMemorySize()));
    }

    private static void createStartedFile() throws Exception {
        FileOutputStream fout = new FileOutputStream(STARTED_FILE);
        fout.close();
    }

}
