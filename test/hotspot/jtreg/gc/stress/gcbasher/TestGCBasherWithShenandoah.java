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

package gc.stress.gcbasher;

import java.io.IOException;

/*
 * @test TestGCBasherWithShenandoah
 * @key gc
 * @key stress
 * @library /
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 * @requires vm.flavor == "server" & !vm.emulatedClient & !vm.graal.enabled
 * @summary Stress the Shenandoah GC by trying to make old objects more likely to be garbage than young objects.
 *
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=passive      -XX:+ShenandoahVerify -XX:+ShenandoahDegeneratedGC gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=passive      -XX:+ShenandoahVerify -XX:-ShenandoahDegeneratedGC gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 *
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive                         -XX:+ShenandoahOOMDuringEvacALot gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive                         -XX:+ShenandoahAllocFailureALot  gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive                                                          gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 *
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive     -XX:+ShenandoahVerify gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=traversal    -XX:+ShenandoahVerify gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 *
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=adaptive                           gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=traversal                          gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx1g -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=compact                            gc.stress.gcbasher.TestGCBasherWithShenandoah 120000
 */
public class TestGCBasherWithShenandoah {
    public static void main(String[] args) throws IOException {
        TestGCBasher.main(args);
    }
}
