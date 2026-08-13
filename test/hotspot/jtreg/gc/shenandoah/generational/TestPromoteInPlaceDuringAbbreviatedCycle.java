/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package gc.shenandoah.generational;

import jdk.test.whitebox.WhiteBox;

/*
 * @test id=generational
 * @requires vm.gc.Shenandoah
 * @summary Aged regions must be promoted in place during an abbreviated cycle
 *          (one that skips the evacuation and update-refs phases).
 * @bug 8387539
 * @library /testlibrary /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *      -Xms512m -Xmx512m
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahRegionSize=1m
 *      -XX:ShenandoahImmediateThreshold=0
 *      -XX:ShenandoahGenerationalMinTenuringAge=1
 *      -XX:ShenandoahGenerationalMaxTenuringAge=1
 *      gc.shenandoah.generational.TestPromoteInPlaceDuringAbbreviatedCycle
 */
public class TestPromoteInPlaceDuringAbbreviatedCycle {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    // Make a humongous array (with 1MB regions, this will be humongous with and with out compressed oops).
    private static final int HUMONGOUS_REFS = 512 * 1024;

    // Used to create pure garbage regions to satisfy immediate garbage threshold
    private static final int GARBAGE_BYTES = 2 * 1024 * 1024;

    // Test will fail if our humongous object isn't promoted in this many cycles
    private static final int MAX_CYCLES = 5;

    // Strong reference so the array under test stays live and ages in young.
    private static Object[] humongous;

    // Strong reference used to publish, then drop, the per-cycle garbage.
    private static Object garbage;

    public static void main(String[] args) throws Exception {
        humongous = new Object[HUMONGOUS_REFS];

        if (WB.isObjectInOldGen(humongous)) {
            throw new IllegalStateException(
                    "Precondition failed: the humongous array should start in the young generation");
        }

        for (int cycle = 1; cycle <= MAX_CYCLES; cycle++) {
            // Produce one whole dead region so the upcoming cycle is abbreviated.
            garbage = new byte[GARBAGE_BYTES];
            garbage = null;

            // Runs a concurrent (global) cycle and blocks until it completes.
            WB.youngGC();

            if (WB.isObjectInOldGen(humongous)) {
                System.out.println("Humongous array promoted in place during an abbreviated cycle after "
                        + cycle + " cycle(s)");
                return;
            }
        }

        throw new RuntimeException("Humongous array was never promoted in place during an abbreviated cycle after "
                + MAX_CYCLES + " cycles; in-place promotion is not happening on the abbreviated path");
    }
}

