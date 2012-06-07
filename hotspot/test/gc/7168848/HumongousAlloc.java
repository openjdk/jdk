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
 * @test Humongous.java
 * @bug 7168848
 * @summary G1: humongous object allocations should initiate marking cycles when necessary
 * @run main/othervm -Xms100m -Xmx100m -XX:+PrintGC -XX:G1HeapRegionSize=1m -XX:+UseG1GC  HumongousAlloc
 *
 */
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class HumongousAlloc {

    public static byte[] dummy;
    private static int sleepFreq = 40;
    private static int sleepTime = 1000;
    private static double size = 0.75;
    private static int iterations = 50;
    private static int MB = 1024 * 1024;

    public static void allocate(int size, int sleepTime, int sleepFreq) throws InterruptedException {
        System.out.println("Will allocate objects of size: " + size
                + " bytes and sleep for " + sleepTime
                + " ms after every " + sleepFreq + "th allocation.");
        int count = 0;
        while (count < iterations) {
            for (int i = 0; i < sleepFreq; i++) {
                dummy = new byte[size - 16];
            }
            Thread.sleep(sleepTime);
            count++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        allocate((int) (size * MB), sleepTime, sleepFreq);
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean collector : collectors) {
            if (collector.getName().contains("G1 Old")) {
               long count = collector.getCollectionCount();
                if (count > 0) {
                    throw new RuntimeException("Failed: FullGCs should not have happened. The number of FullGC run is " + count);
                }
                else {
                    System.out.println("Passed.");
                }
            }
        }
    }
}

