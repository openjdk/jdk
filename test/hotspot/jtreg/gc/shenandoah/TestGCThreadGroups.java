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

/**
 * @test TestGCThreadGroups
 * @summary Test Shenandoah GC uses concurrent/parallel threads correctly
 * @key gc
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=2 -XX:ParallelGCThreads=4 -Xmx16m                                         -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:-UseDynamicNumberOfGCThreads            -Xmx16m                                         -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+UnlockDiagnosticVMOptions -XX:+ForceDynamicNumberOfGCThreads -Xmx16m                   -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=2 -XX:ParallelGCThreads=4 -Xmx16m -XX:ShenandoahGCHeuristics=passive      -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=2 -XX:ParallelGCThreads=4 -Xmx16m -XX:ShenandoahGCHeuristics=adaptive     -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=2 -XX:ParallelGCThreads=4 -Xmx16m -XX:ShenandoahGCHeuristics=static       -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=2 -XX:ParallelGCThreads=4 -Xmx16m -XX:ShenandoahGCHeuristics=compact      -Dtarget=100  TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=2 -XX:ParallelGCThreads=4 -Xmx16m -XX:ShenandoahGCHeuristics=aggressive   -Dtarget=100  TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=2 -XX:ParallelGCThreads=4 -Xmx16m -XX:ShenandoahGCHeuristics=traversal    -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=4 -XX:ParallelGCThreads=2 -Xmx16m -XX:ShenandoahGCHeuristics=passive      -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=4 -XX:ParallelGCThreads=2 -Xmx16m -XX:ShenandoahGCHeuristics=adaptive     -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=4 -XX:ParallelGCThreads=2 -Xmx16m -XX:ShenandoahGCHeuristics=static       -Dtarget=1000 TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=4 -XX:ParallelGCThreads=2 -Xmx16m -XX:ShenandoahGCHeuristics=compact      -Dtarget=100  TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=4 -XX:ParallelGCThreads=2 -Xmx16m -XX:ShenandoahGCHeuristics=aggressive   -Dtarget=100  TestGCThreadGroups
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ConcGCThreads=4 -XX:ParallelGCThreads=2 -Xmx16m -XX:ShenandoahGCHeuristics=traversal    -Dtarget=1000 TestGCThreadGroups
*/

public class TestGCThreadGroups {

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
