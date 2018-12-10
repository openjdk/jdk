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
 * @test TestLotsOfCycles
 * @key gc
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=passive      -Dtarget=10000 -XX:+ShenandoahDegeneratedGC     TestLotsOfCycles
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=passive      -Dtarget=10000 -XX:-ShenandoahDegeneratedGC     TestLotsOfCycles
 *
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=aggressive   -Dtarget=1000  -XX:+ShenandoahOOMDuringEvacALot TestLotsOfCycles
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=aggressive   -Dtarget=1000  -XX:+ShenandoahAllocFailureALot  TestLotsOfCycles
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=aggressive   -Dtarget=1000                                   TestLotsOfCycles
 *
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=adaptive     -Dtarget=10000 TestLotsOfCycles
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=traversal    -Dtarget=10000 TestLotsOfCycles
 *
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=static       -Dtarget=10000 TestLotsOfCycles
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -Xmx16m -XX:ShenandoahGCHeuristics=compact      -Dtarget=1000  TestLotsOfCycles
 */

public class TestLotsOfCycles {

    static final long TARGET_MB = Long.getLong("target", 10_000); // 10 Gb allocation, around 1K cycles to handle
    static final long STRIDE = 100_000;

    static volatile Object sink;

    public static void main(String[] args) throws Exception {
        long count = TARGET_MB * 1024 * 1024 / 16;
        for (long c = 0; c < count; c += STRIDE) {
            for (long s = 0; s < STRIDE; s++) {
                sink = new Object();
            }
            Thread.sleep(1);
        }
    }

}
