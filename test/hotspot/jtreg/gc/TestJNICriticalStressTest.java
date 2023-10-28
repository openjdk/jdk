/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test G1 Full GC execution when JNICritical is active
 * @summary Check that Full GC calls are not ignored if concurrent with an active GCLocker.
 * @bug 8057586
 * @requires vm.gc.G1
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -Xms1g -Xmx1g -Xlog:gc TestJNICriticalStressTest 30 4 4 G1
 */

 /*
 * @test Parallel Full GC execution when JNICritical is active
 * @bug 8057586
 * @requires vm.gc.Parallel
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseParallelGC -Xms1g -Xmx1g -Xlog:gc TestJNICriticalStressTest 30 4 4 Parallel
 */

/*
 * @test Serial Full GC execution when JNICritical is active
 * @bug 8057586
 * @requires vm.gc.Serial
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseSerialGC -Xms1g -Xmx1g -Xlog:gc TestJNICriticalStressTest 30 4 4 Serial
 */

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Test verifies that Full GC calls are not ignored if concurrent with an active GCLocker.
 *
 * The test checks that at least a full gc is executed in the duration of a WhiteBox.fullGC() call;
 * either by the calling thread or a concurrent thread.
 */
public class TestJNICriticalStressTest {
    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    static private final int LARGE_MAP_SIZE = 64 * 1024;

    static private final int MAP_ARRAY_LENGTH = 4;
    static private final int MAP_SIZE = 1024;

    static private final int BYTE_ARRAY_LENGTH = 16 * 1024;

    static private final long SYSTEM_GC_PERIOD_MS = 5 * 1000;
    static private long gcCountBefore = 0;
    static private GarbageCollectorMXBean collector = null;

    static private void println(String str) { System.out.println(str); }
    static private void exit(int code)      { System.exit(code);       }

    static Map<Integer,String> populateMap(int size) {
        Map<Integer,String> map = new HashMap<Integer,String>();
        for (int i = 0; i < size; i += 1) {
            Integer keyInt = Integer.valueOf(i);
            String valStr = "value is [" + i + "]";
            map.put(keyInt,valStr);
        }
        return map;
    }

    static private class AllocatingWorker implements Runnable {
        private final Object[] array = new Object[MAP_ARRAY_LENGTH];
        private int arrayIndex = 0;

        private void doStep() {
            Map<Integer,String> map = populateMap(MAP_SIZE);
            array[arrayIndex] = map;
            arrayIndex = (arrayIndex + 1) % MAP_ARRAY_LENGTH;
        }

        public void run() {
            while (true) {
                doStep();
            }
        }
    }

    static private class JNICriticalWorker implements Runnable {
        private int count;

        private void doStep() {
            byte[] inputArray = new byte[BYTE_ARRAY_LENGTH];
            for (int i = 0; i < inputArray.length; i += 1) {
                inputArray[i] = (byte) (count + i);
            }

            Deflater deflater = new Deflater();
            deflater.setInput(inputArray);
            deflater.finish();

            byte[] outputArray = new byte[2 * inputArray.length];
            deflater.deflate(outputArray);
            deflater.end();

            count += 1;
        }

        public void run() {
            while (true) {
                doStep();
            }
        }
    }

    static private class SystemGCWorker implements Runnable {
        public void run() {
            long fullGcCounts = 0;
            while (true) {
                try {
                    Thread.sleep(SYSTEM_GC_PERIOD_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }

                long gcCountBefore = collector.getCollectionCount();
                wb.fullGC();
                long gcCountAfter = collector.getCollectionCount();

                Asserts.assertLessThan(gcCountBefore, gcCountAfter, "Triggered more Full GCs than expected");
            }
        }
    }

    static public Map<Integer,String> largeMap;

    public static void main(String... args) throws Exception {
        if (args.length < 4) {
            println("usage: JNICriticalStressTest <duration sec> <alloc threads> <jni critical threads> <gc name>");
            exit(-1);
        }

        long durationSec = Long.parseLong(args[0]);
        int allocThreadNum = Integer.parseInt(args[1]);
        int jniCriticalThreadNum = Integer.parseInt(args[2]);

        StringBuilder OldGCName = new StringBuilder();
        switch (args[3]) {
            case "G1":
                OldGCName.append("G1 Old Generation");
                break;
            case "Parallel":
                OldGCName.append("PS MarkSweep");
                break;
            case "Serial":
                OldGCName.append("MarkSweepCompact");
                break;
            default:
                throw new RuntimeException("Unsupported GC selected");
        }

        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();

        for (int i = 0; i < collectors.size(); i++) {
            if (collectors.get(i).getName().contains(OldGCName.toString())) {
                collector = collectors.get(i);
                break;
            }
        }

        if (collector == null) {
            throw new RuntimeException(OldGCName.toString() + " not found");
        }

        println("Running for " + durationSec + " secs");

        largeMap = populateMap(LARGE_MAP_SIZE);

        // Start threads to allocate memory, this will trigger both GCLocker initiated
        // garbage collections (GCs) and regular GCs. Thus increasing the likelihood of
        // having different types of GCs happening concurrently with the System.gc call.
        println("Starting " + allocThreadNum + " allocating threads");
        for (int i = 0; i < allocThreadNum; i += 1) {
            new Thread(new AllocatingWorker()).start();
        }

        println("Starting " + jniCriticalThreadNum + " jni critical threads");
        for (int i = 0; i < jniCriticalThreadNum; i += 1) {
            new Thread(new JNICriticalWorker()).start();
        }

        new Thread(new SystemGCWorker()).start();

        long durationMS = (long) (1000 * durationSec);
        try {
            Thread.sleep(durationMS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            exit(-1);
        }
   }
}
