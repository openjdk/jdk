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
 * @summary Test that Reference.refersTo intrinsics are properly handled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @requires vm.compiler2.enabled
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.ReferenceRefersToTests

 */
public class ReferenceRefersToTests {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();

        // Use PerMethodTrapLimit=0 to compile all branches in the intrinsics.

        int idx = 0;
        if (GC.Serial.isSupported()) {
            framework.addScenarios(new Scenario(idx++, "-XX:PerMethodTrapLimit=0", "-XX:+UseSerialGC"));
        }
        if (GC.Parallel.isSupported()) {
            framework.addScenarios(new Scenario(idx++, "-XX:PerMethodTrapLimit=0", "-XX:+UseParallelGC"));
        }
        if (GC.G1.isSupported()) {
            framework.addScenarios(new Scenario(idx++, "-XX:PerMethodTrapLimit=0", "-XX:+UseG1GC"));
        }
        if (GC.Shenandoah.isSupported()) {
            framework.addScenarios(new Scenario(idx++, "-XX:PerMethodTrapLimit=0", "-XX:+UseShenandoahGC"));
        }
        if (GC.Z.isSupported()) {
            framework.addScenarios(new Scenario(idx++, "-XX:PerMethodTrapLimit=0", "-XX:+UseZGC"));
        }
        if (GC.Epsilon.isSupported()) {
            framework.addScenarios(new Scenario(idx++, "-XX:PerMethodTrapLimit=0", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC"));
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
        // Sanity check the test
        SR.refersTo(null);
    }

    @Test
    @IR(failOn = { IRNode.STORE_PRIMITIVE, IRNode.LOAD_PRIMITIVE })
    public void weak() {
        // Sanity check the test
        WR.refersTo(null);
    }

    @Test
    @IR(failOn = { IRNode.STORE_PRIMITIVE, IRNode.LOAD_PRIMITIVE })
    public void phantom() {
        // Sanity check the test
        PR.refersTo(null);
    }

}
