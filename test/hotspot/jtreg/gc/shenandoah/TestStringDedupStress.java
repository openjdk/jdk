/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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
 * @test TestStringDedupStress
 * @summary Test Shenandoah string deduplication implementation
 * @key gc
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @modules java.base/jdk.internal.misc:open
 * @modules java.base/java.lang:open
 *          java.management
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -DtargetStrings=3000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=aggressive -DtargetStrings=2000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=aggressive -XX:+ShenandoahOOMDuringEvacALot -DtargetStrings=2000000
 *                    TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=static -DtargetStrings=4000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=compact
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=passive -XX:+ShenandoahDegeneratedGC -DtargetOverwrites=40000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=passive -XX:-ShenandoahDegeneratedGC -DtargetOverwrites=40000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=traversal
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahUpdateRefsEarly=off -DtargetStrings=3000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=compact -XX:ShenandoahUpdateRefsEarly=off -DtargetStrings=2000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=aggressive -XX:ShenandoahUpdateRefsEarly=off -DtargetStrings=2000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=static -XX:ShenandoahUpdateRefsEarly=off -DtargetOverwrites=4000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=aggressive -XX:ShenandoahUpdateRefsEarly=off -XX:+ShenandoahOOMDuringEvacALot -DtargetStrings=2000000
 *                   TestStringDedupStress
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UseStringDeduplication -Xmx512M -Xlog:gc+stats
 *                   -XX:ShenandoahGCHeuristics=traversal -XX:+ShenandoahOOMDuringEvacALot -DtargetStrings=2000000
 *                   TestStringDedupStress
 */

import java.lang.management.*;
import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

public class TestStringDedupStress {
    private static Field valueField;
    private static Unsafe unsafe;

    private static long TARGET_STRINGS = Long.getLong("targetStrings", 2_500_000);
    private static long TARGET_OVERWRITES = Long.getLong("targetOverwrites", 600_000);
    private static final long MAX_REWRITE_GC_CYCLES = 6;

    private static final int UNIQUE_STRINGS = 20;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);

            valueField = String.class.getDeclaredField("value");
            valueField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getValue(String string) {
        try {
            return valueField.get(string);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class StringAndId {
        private String str;
        private int id;

        public StringAndId(String str, int id) {
            this.str = str;
            this.id = id;
        }

        public String str() {
            return str;
        }

        public int id() {
            return id;
        }
    }

    // Generate uniqueStrings number of strings
    private static void generateStrings(ArrayList<StringAndId> strs, int uniqueStrings) {
        Random rn = new Random();
        for (int u = 0; u < uniqueStrings; u++) {
            int n = rn.nextInt(uniqueStrings);
            strs.add(new StringAndId("Unique String " + n, n));
        }
    }

    private static int verifyDedepString(ArrayList<StringAndId> strs) {
        HashMap<Object, StringAndId> seen = new HashMap<>();
        int total = 0;
        int dedup = 0;

        for (StringAndId item : strs) {
            total++;
            StringAndId existingItem = seen.get(getValue(item.str()));
            if (existingItem == null) {
                seen.put(getValue(item.str()), item);
            } else {
                if (item.id() != existingItem.id() ||
                        !item.str().equals(existingItem.str())) {
                    System.out.println("StringDedup error:");
                    System.out.println("id: " + item.id() + " != " + existingItem.id());
                    System.out.println("or String: " + item.str() + " != " + existingItem.str());
                    throw new RuntimeException("StringDedup Test failed");
                } else {
                    dedup++;
                }
            }
        }
        System.out.println("Dedup: " + dedup + "/" + total + " unique: " + (total - dedup));
        return (total - dedup);
    }

    static volatile ArrayList<StringAndId> astrs = new ArrayList<>();
    static GarbageCollectorMXBean gcCycleMBean;

    public static void main(String[] args) {
        Random rn = new Random();

        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if ("Shenandoah Cycles".equals(bean.getName())) {
                gcCycleMBean = bean;
                break;
            }
        }

        if (gcCycleMBean == null) {
            throw new RuntimeException("Can not find Shenandoah GC cycle mbean");
        }

        // Generate roughly TARGET_STRINGS strings, only UNIQUE_STRINGS are unique
        long genIters = TARGET_STRINGS / UNIQUE_STRINGS;
        for (long index = 0; index < genIters; index++) {
            generateStrings(astrs, UNIQUE_STRINGS);
        }

        long cycleBeforeRewrite = gcCycleMBean.getCollectionCount();

        for (long loop = 1; loop < TARGET_OVERWRITES; loop++) {
            int arrSize = astrs.size();
            int index = rn.nextInt(arrSize);
            StringAndId item = astrs.get(index);
            int n = rn.nextInt(UNIQUE_STRINGS);
            item.str = "Unique String " + n;
            item.id = n;

            if (loop % 1000 == 0) {
                // enough GC cycles for rewritten strings to be deduplicated
                if (gcCycleMBean.getCollectionCount() - cycleBeforeRewrite >= MAX_REWRITE_GC_CYCLES) {
                    break;
                }
            }
        }
        verifyDedepString(astrs);
    }
}
