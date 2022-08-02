/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8255495
 * @summary Test CDS with UseCompressedOops disable with various heap sizes.
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.gc.G1
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver DumpingWithNoCoops
 */

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;

public class DumpingWithNoCoops {
    static class HeapArgs {
        int initialSize, minSize, maxSize;
        HeapArgs(int initial, int min, int max) {
            initialSize = initial;
            minSize = min;
            maxSize = max;
        }
        String heapArgsString(HeapArgs ha) {
            String heapArgs = "";
            if (ha.initialSize > 0) {
                heapArgs += "-XX:InitialHeapSize=" + ha.initialSize + "g";
            }
            if (ha.minSize > 0) {
                if (heapArgs.length() > 0) {
                    heapArgs += " ";
                }
                heapArgs += "-XX:MinHeapSize=" + ha.minSize + "g";
            }
            if (ha.maxSize > 0) {
                if (heapArgs.length() > 0) {
                    heapArgs += " ";
                }
                heapArgs += "-XX:MaxHeapSize=" + ha.maxSize + "g";
            }
            return heapArgs;
        }
    }

    static HeapArgs[] heapArgsCases = {
        // InitialHeapSize, MinHeapSize, MaxHeapSize
        // all sizes are in the unit of GB
        // size of 0 means don't set the heap size
        new HeapArgs( 0, 0, 0),
        new HeapArgs( 5, 3, 5),
        new HeapArgs( 3, 3, 5),
        new HeapArgs( 5, 5, 5),
        new HeapArgs( 2, 1, 33),
    };

    static void checkExpectedMessages(HeapArgs ha, OutputAnalyzer output) throws Exception {
        final int DUMPTIME_MAX_HEAP = 4; // 4 GB
        if (ha.minSize > DUMPTIME_MAX_HEAP) {
            output.shouldContain("Setting MinHeapSize to 4G for CDS dumping");
        }
        if (ha.initialSize > DUMPTIME_MAX_HEAP) {
            output.shouldContain("Setting InitialHeapSize to 4G for CDS dumping");
        }
        if (ha.maxSize > DUMPTIME_MAX_HEAP) {
            output.shouldContain("Setting MaxHeapSize to 4G for CDS dumping");
        }
    }

    public static void main(String[] args) throws Exception {
        final String noCoops = "-XX:-UseCompressedOops";
        final String logArg = "-Xlog:gc+heap=trace,cds=debug";
        JarBuilder.getOrCreateHelloJar();
        String appJar = TestCommon.getTestJar("hello.jar");
        String appClasses[] = TestCommon.list("Hello");

        for (HeapArgs ha : heapArgsCases) {
            String heapArg = ha.heapArgsString(ha);
            List<String> dumptimeArgs = new ArrayList<String>();
            // UseCompressedOops is ergonomically disabled for MaxHeapSize > 32g.
            if (ha.maxSize < 32) {
                dumptimeArgs.add(noCoops);
            }
            dumptimeArgs.add(logArg);
            OutputAnalyzer output;
            if (heapArg.length() == 0) {
                System.out.println("\n    Test without heap args\n");
                output = TestCommon.dump(appJar, appClasses, dumptimeArgs.toArray(new String[0]));
            } else {
                System.out.println("\n    Test with heap args: " + heapArg + "\n");
                String[] heapSizes = heapArg.split(" ");
                for (String heapSize : heapSizes) {
                    dumptimeArgs.add(heapSize);
                }
                output = TestCommon.dump(appJar, appClasses, dumptimeArgs.toArray(new String[0]));
                checkExpectedMessages(ha, output);
            }

            TestCommon.checkDump(output);
            TestCommon.run("-cp", appJar,
                        logArg, "-Xlog:class+load", noCoops, "Hello")
                .assertNormalExit("Hello source: shared objects file");
        }
    }
}
