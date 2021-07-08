/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

/* @test TestStringCriticalWithDedup
 * @summary Test string deduplication should not cause string critical to crash VM
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive -XX:+UseStringDeduplication
 *      -XX:+ShenandoahVerify -XX:+ShenandoahDegeneratedGC
 *      TestStringCriticalWithDedup
 *
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive -XX:+UseStringDeduplication
 *      -XX:+ShenandoahVerify -XX:-ShenandoahDegeneratedGC
 *      TestStringCriticalWithDedup
 */

/* @test TestPinnedGarbage
 * @summary Test string deduplication should not cause string critical to crash VM
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive -XX:+UseStringDeduplication
 *      TestStringCriticalWithDedup
 *
 * @run main/othervm/native -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx256m
 *      -XX:+UseShenandoahGC -XX:+UseStringDeduplication
 *      -XX:+ShenandoahVerify
 *      TestStringCriticalWithDedup
 */

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

public class TestStringCriticalWithDedup {
    static {
        System.loadLibrary("TestStringCriticalWithDedup");
    }

    private static final int NUM_RUNS      = 1_000;
    private static final int OBJS_COUNT    = 1 << 10;
    private static final int LITTLE_GARBAGE_COUNT = 1 << 5;
    private static final int GARBAGE_COUNT = 1 << 18;
    private static final int PINNED_STRING_COUNT = 1 << 4;

    private static native long pin(String s);
    private static native void unpin(String s, long p);

    private static volatile MyClass sink;
    public static void main(String[] args) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < NUM_RUNS; i++) {
            test(rng);
        }
    }

    private static void pissiblePinString(ThreadLocalRandom rng, List<Pair> pinnedList, String s) {
        int oneInCounter = OBJS_COUNT / PINNED_STRING_COUNT;
        if (rng.nextInt(oneInCounter) == 1) {
            long v = pin(s);
            pinnedList.add(new Pair(s, v));
        }
    }

    private static void test(ThreadLocalRandom rng) {
        String[] strArray = new String[OBJS_COUNT];
        List<Pair> pinnedStrings = new ArrayList<>(PINNED_STRING_COUNT);
        for (int i = 0; i < OBJS_COUNT; i++) {
            // Create some garbage inbetween, so strings can be scattered in
            // different regions
            createLittleGarbage(rng);

            strArray[i] = new String("Hello" + (i % 10));
            pissiblePinString(rng, pinnedStrings, strArray[i]);
        }

        for (int i = 0; i < GARBAGE_COUNT; i++) {
           sink = new MyClass();
        }

        // Let deduplication thread to run a bit
        try {
            Thread.sleep(10);
        } catch(Exception e) {
        }

        for (int i = 0; i < pinnedStrings.size(); i ++) {
            Pair p = pinnedStrings.get(i);
            unpin(p.getString(), p.getValue());
        }
    }

    private static void createLittleGarbage(ThreadLocalRandom rng) {
        int count = rng.nextInt(LITTLE_GARBAGE_COUNT);
        for (int index = 0; index < count; index ++) {
            sink = new MyClass();
        }
    }

    private static class Pair {
        String s;
        long   v;
        public Pair(String s, long v) {
            this.s = s;
            this.v = v;
        }

        public String getString() {
            return s;
        }

        public long getValue() {
            return v;
        }
    }

    private static class MyClass {
        public long[] payload = new long[100];
    }
}
