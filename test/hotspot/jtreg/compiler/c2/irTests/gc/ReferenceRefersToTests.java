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
package compiler.c2.irTests.gc;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import jdk.test.whitebox.gc.GC;

import java.lang.ref.*;
import java.util.*;

/*
 * @test
 * @bug 8256999
 * @summary Test that Reference.refersTo intrinsics are properly handled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @requires vm.compiler2.enabled
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.gc.ReferenceRefersToTests
 */
public class ReferenceRefersToTests {

    private static String[] args(String... add) {
        List<String> args = new ArrayList<>();

         // Use PerMethodTrapLimit=0 to compile all branches in the intrinsics.
        args.add("-XX:PerMethodTrapLimit=0");

        // Forcefully inline all methods to reach the intrinsic code.
        args.add("-XX:CompileCommand=inline,compiler.c2.irTests.gc.ReferenceRefersToTests::*");
        args.add("-XX:CompileCommand=inline,java.lang.ref.Reference::*");
        args.add("-XX:CompileCommand=inline,java.lang.ref.PhantomReference::*");

        // Mix in test config code.
        args.addAll(Arrays.asList(add));

        return args.toArray(new String[0]);
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();

        int idx = 0;
        if (GC.isSelectedErgonomically() && GC.Serial.isSupported()) {
            // Serial does not have any barriers in refersTo.
            framework.addScenarios(new Scenario(idx++, args(
                "-XX:+UseSerialGC"
            )));
        }
        if (GC.isSelectedErgonomically() && GC.Parallel.isSupported()) {
            // Parallel does not have any barriers in refersTo.
            framework.addScenarios(new Scenario(idx++, args(
                "-XX:+UseParallelGC"
            )));
        }
        if (GC.isSelectedErgonomically() && GC.G1.isSupported()) {
            // G1 nominally needs keep-alive barriers for Reference loads,
            // but should not have them for refersTo.
            framework.addScenarios(new Scenario(idx++, args(
                "-XX:+UseG1GC"
            )));
        }
        if (GC.isSelectedErgonomically() && GC.Shenandoah.isSupported()) {
            // Shenandoah nominally needs keep-alive barriers for Reference loads,
            // but should not have them for refersTo. We only care to check that
            // SATB barrier is not emitted. Shenandoah would also emit LRB barrier,
            // which would false-negative the test.
            framework.addScenarios(new Scenario(idx++, args(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:ShenandoahGCMode=passive",
                "-XX:+ShenandoahSATBBarrier",
                "-XX:+UseShenandoahGC"
            )));
        }
        if (GC.isSelectedErgonomically() && GC.Z.isSupported()) {
            // ZGC does not emit barriers in IR.
            framework.addScenarios(new Scenario(idx++, args(
                "-XX:+UseZGC"
            )));
        }
        framework.start();
    }

    static final Object REF = new Object();

    static final SoftReference<Object> SR = new SoftReference<>(REF);
    static final WeakReference<Object> WR = new WeakReference<>(REF);
    static final PhantomReference<Object> PR = new PhantomReference<>(REF, null);

    // Verify that we are left with a single load of Reference.referent and no stores.
    // This serves as a signal that no GC barriers are emitted in IR.

    @Test
    @IR(counts = { IRNode.LOAD, "1" })
    @IR(failOn = { IRNode.STORE })
    public boolean soft_null() {
        return SR.refersTo(null);
    }

    @Test
    @IR(counts = { IRNode.LOAD, "1" })
    @IR(failOn = { IRNode.STORE })
    public boolean soft_ref() {
        return SR.refersTo(REF);
    }

    @Test
    @IR(counts = { IRNode.LOAD, "1" })
    @IR(failOn = { IRNode.STORE })
    public boolean weak_null() {
        return WR.refersTo(null);
    }

    @Test
    @IR(counts = { IRNode.LOAD, "1" })
    @IR(failOn = { IRNode.STORE })
    public boolean weak_ref() {
        return WR.refersTo(REF);
    }

    @Test
    @IR(counts = { IRNode.LOAD, "1" })
    @IR(failOn = { IRNode.STORE })
    public boolean phantom_null() {
        return PR.refersTo(null);
    }

    @Test
    @IR(counts = { IRNode.LOAD, "1" })
    @IR(failOn = { IRNode.STORE })
    public boolean phantom_ref() {
        return PR.refersTo(REF);
    }

}
