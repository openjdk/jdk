/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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
 * @test TestStringDedup
 * @summary Test Shenandoah string deduplication implementation
 * @key gc
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc:open
 * @modules java.base/java.lang:open
 *          java.management
 *
 * @run main/othervm -Xmx256m -Xlog:gc+stats -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive
 *      -XX:+ShenandoahDegeneratedGC
 *      TestStringDedup
 *
 * @run main/othervm -Xmx256m -Xlog:gc+stats -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive
 *      -XX:-ShenandoahDegeneratedGC
 *      TestStringDedup
 */

/*
 * @test TestStringDedup
 * @summary Test Shenandoah string deduplication implementation
 * @key gc
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc:open
 * @modules java.base/java.lang:open
 *          java.management
 *
 * @run main/othervm -Xmx256m -Xlog:gc+stats -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive
 *      TestStringDedup
 *
 * @run main/othervm -Xmx256m -Xlog:gc+stats -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication
 *      -XX:+UseShenandoahGC
 *      TestStringDedup
 *
 * @run main/othervm -Xmx256m -Xlog:gc+stats -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=compact
 *      TestStringDedup
 */

/*
 * @test TestStringDedup
 * @summary Test Shenandoah string deduplication implementation
 * @key gc
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc:open
 * @modules java.base/java.lang:open
 *          java.management
 *
 * @run main/othervm -Xmx256m -Xlog:gc+stats -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=iu
 *      TestStringDedup
 *
 * @run main/othervm -Xmx256m -Xlog:gc+stats -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=iu -XX:ShenandoahGCHeuristics=aggressive
 *      TestStringDedup
 */

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

public class TestStringDedup {
    private static Field valueField;
    private static Unsafe unsafe;

    private static final int UniqueStrings = 20;

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

    private static void generateStrings(ArrayList<StringAndId> strs, int unique_strs) {
        Random rn = new Random();
        for (int u = 0; u < unique_strs; u++) {
            int n = rn.nextInt() % 10;
            n = Math.max(n, 2);
            for (int index = 0; index < n; index++) {
                strs.add(new StringAndId("Unique String " + u, u));
            }
        }
    }

    private static int verifyDedepString(ArrayList<StringAndId> strs) {
        HashMap<Object, StringAndId> seen = new HashMap<>();
        int total = 0;
        int dedup = 0;

        for (StringAndId item : strs) {
            total++;
            StringAndId existing_item = seen.get(getValue(item.str()));
            if (existing_item == null) {
                seen.put(getValue(item.str()), item);
            } else {
                if (item.id() != existing_item.id() ||
                        !item.str().equals(existing_item.str())) {
                    System.out.println("StringDedup error:");
                    System.out.println("String: " + item.str() + " != " + existing_item.str());
                    throw new RuntimeException("StringDedup Test failed");
                } else {
                    dedup++;
                }
            }
        }
        System.out.println("Dedup: " + dedup + "/" + total + " unique: " + (total - dedup));
        return (total - dedup);
    }

    public static void main(String[] args) {
        ArrayList<StringAndId> astrs = new ArrayList<>();
        generateStrings(astrs, UniqueStrings);
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();

        if (verifyDedepString(astrs) != UniqueStrings) {
            // Can not guarantee all strings are deduplicated, there can
            // still have pending items in queues.
            System.out.println("Not all strings are deduplicated");
        }
    }
}
