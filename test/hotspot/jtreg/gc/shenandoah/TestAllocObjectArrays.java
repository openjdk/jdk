/*
 * Copyright (c) 2016, 2018, Red Hat, Inc. All rights reserved.
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
 * @test TestAllocObjectArrays
 * @summary Acceptance tests: collector can withstand allocation
 * @key gc
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=passive      -XX:+ShenandoahDegeneratedGC     -XX:+ShenandoahVerify TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=passive      -XX:-ShenandoahDegeneratedGC     -XX:+ShenandoahVerify TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=passive      -XX:+ShenandoahDegeneratedGC                           TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=passive      -XX:-ShenandoahDegeneratedGC                           TestAllocObjectArrays
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=aggressive   -XX:+ShenandoahOOMDuringEvacALot -XX:+ShenandoahVerify TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=aggressive   -XX:+ShenandoahAllocFailureALot  -XX:+ShenandoahVerify TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=aggressive   -XX:+ShenandoahOOMDuringEvacALot                       TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=aggressive   -XX:+ShenandoahAllocFailureALot                        TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=aggressive                                                          TestAllocObjectArrays
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=adaptive     -XX:+ShenandoahVerify TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=traversal    -XX:+ShenandoahVerify TestAllocObjectArrays
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=adaptive     TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=static       TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=compact      TestAllocObjectArrays
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:ShenandoahGCHeuristics=traversal    TestAllocObjectArrays
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx1g -Xms1g -XX:-UseTLAB                            -XX:+ShenandoahVerify TestAllocObjectArrays
 */

import java.util.Random;

public class TestAllocObjectArrays {

    static final long TARGET_MB = Long.getLong("target", 10_000); // 10 Gb allocation

    static volatile Object sink;

    public static void main(String[] args) throws Exception {
        final int min = 0;
        final int max = 384 * 1024;
        long count = TARGET_MB * 1024 * 1024 / (16 + 4 * (min + (max - min) / 2));

        Random r = new Random();
        for (long c = 0; c < count; c++) {
            sink = new Object[min + r.nextInt(max - min)];
        }
    }

}
