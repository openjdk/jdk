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
 */
package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import jdk.test.whitebox.gc.GC;

import java.lang.ref.*;

/*
 * @test
 * @bug 8329597
 * @summary Test that Reference.clear intrinsics are properly handled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @requires vm.compiler2.enabled
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.ReferenceClearTests

 */
public class ReferenceClearTests {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();

        // Use PerMethodTrapLimit=0 to compile all branches in the intrinsics.

        int idx = 0;
        if (GC.Serial.isSupported()) {
            // Serial:
            //   a) does not have SATB/keep-alive barriers at all
            //   b) has inter-generational barriers on stores, but folded away for null stores
            framework.addScenarios(new Scenario(idx++,
                "-XX:PerMethodTrapLimit=0",
                "-XX:+UseSerialGC"
            ));
        }
        if (GC.Parallel.isSupported()) {
            // Parallel:
            //   a) does not have SATB/keep-alive barriers at all
            //   b) has inter-generational barriers on stores, but folded away for null stores
            framework.addScenarios(new Scenario(idx++,
                "-XX:PerMethodTrapLimit=0",
                "-XX:+UseParallelGC")
            );
        }
        if (GC.G1.isSupported()) {
            // G1:
            //   a) has SATB/keep-alive barriers, should not be present for Reference.clear-s
            //   b) has inter-generational barriers on stores, but folded away for null stores
            framework.addScenarios(new Scenario(idx++,
                "-XX:PerMethodTrapLimit=0",
                "-XX:+UseG1GC"
            ));
        }
        if (GC.Shenandoah.isSupported()) {
            // Shenandoah:
            //   a) has SATB/keep-alive barriers, should not be present for Reference.clear-s
            //   b) has load-reference barriers, which would confuse the tests, we enable only SATB barriers
            framework.addScenarios(new Scenario(idx++,
                "-XX:PerMethodTrapLimit=0",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:ShenandoahGCMode=passive",
                "-XX:+ShenandoahSATBBarrier",
                "-XX:+UseShenandoahGC"
            ));
        }
        if (GC.Z.isSupported()) {
            // Z:
            //   a) does not have SATB/keep-alive barriers
            //   b) has inter-generational barriers on stores, but folded away for null stores
            framework.addScenarios(new Scenario(idx++,
                "-XX:PerMethodTrapLimit=0",
                "-XX:+UseZGC"
            ));
        }
        if (GC.Epsilon.isSupported()) {
            // Epsilon: does not emit barriers at all.
            framework.addScenarios(new Scenario(idx++,
                "-XX:PerMethodTrapLimit=0",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseEpsilonGC")
            );
        }
        framework.start();
    }

    static final Object REF = new Object();

    static final SoftReference<Object> SR = new SoftReference<>(REF);
    static final WeakReference<Object> WR = new WeakReference<>(REF);
    static final PhantomReference<Object> PR = new PhantomReference<>(REF, null);

    @Test
    @IR(failOn = { IRNode.STORE_PRIMITIVE, IRNode.LOAD_PRIMITIVE })
    public void soft() {
        SR.clear();
    }

    @Test
    @IR(failOn = { IRNode.STORE_PRIMITIVE, IRNode.LOAD_PRIMITIVE })
    public void weak() {
        WR.clear();
    }

    @Test
    @IR(failOn = { IRNode.STORE_PRIMITIVE, IRNode.LOAD_PRIMITIVE })
    public void phantom() {
        PR.clear();
    }

}
