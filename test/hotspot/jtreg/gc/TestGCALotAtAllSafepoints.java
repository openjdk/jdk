/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc;

/**
 * @test id=Serial
 * @bug 8389096
 * @summary Verify that -XX:GCALotAtAllSafepoints and -XX:+ScavengeALot do not hang the VM.
 * @comment GCALotAtAllSafepoints and ScavengeALot cause garbage collections at many places in the VM. These
 *          garbage collection should not cause hangs.
 * @requires vm.flagless
 * @requires vm.debug
 * @requires vm.gc.Serial
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver/timeout=60 gc.TestGCALotAtAllSafepoints -XX:+UseSerialGC
 */

/**
 * @test id=Parallel
 * @bug 8389096
 * @summary Verify that -XX:GCALotAtAllSafepoints and -XX:+ScavengeALot do not hang the VM.
 * @comment GCALotAtAllSafepoints and ScavengeALot cause garbage collections at many places in the VM. These
 *          garbage collection should not cause hangs.
 * @requires vm.flagless
 * @requires vm.debug
 * @requires vm.gc.Parallel
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver/timeout=60 gc.TestGCALotAtAllSafepoints -XX:+UseParallelGC
 */

/**
 * @test id=G1
 * @bug 8389096
 * @summary Verify that -XX:GCALotAtAllSafepoints and -XX:+ScavengeALot do not hang the VM.
 * @comment GCALotAtAllSafepoints and ScavengeALot cause garbage collections at many places in the VM. These
 *          garbage collection should not cause hangs.
 * @requires vm.flagless
 * @requires vm.debug
 * @requires vm.gc.G1
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver/timeout=60 gc.TestGCALotAtAllSafepoints -XX:+UseG1GC
 */

/**
 * @test id=Z
 * @bug 8389096
 * @summary Verify that -XX:GCALotAtAllSafepoints and -XX:+ScavengeALot do not hang the VM.
 * @comment GCALotAtAllSafepoints and ScavengeALot cause garbage collections at many places in the VM. These
 *          garbage collection should not cause hangs.
 * @requires vm.flagless
 * @requires vm.debug
 * @requires vm.gc.Z
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver/timeout=60 gc.TestGCALotAtAllSafepoints -XX:+UseZGC
 */

/**
 * @test id=Shenandoah
 * @bug 8389096
 * @summary Verify that -XX:GCALotAtAllSafepoints and -XX:+ScavengeALot do not hang the VM.
 * @comment GCALotAtAllSafepoints and ScavengeALot cause garbage collections at many places in the VM. These
 *          garbage collection should not cause hangs.
 * @requires vm.flagless
 * @requires vm.debug
 * @requires vm.gc.Shenandoah
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver/timeout=60 gc.TestGCALotAtAllSafepoints -XX:+UseShenandoahGC
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestGCALotAtAllSafepoints {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args[0],
                                                                             "-Xmx16m",
                                                                             // Even this small test can generate thousands of GCs. Reduce them.
                                                                             "-XX:ScavengeALotInterval=13",
                                                                             "-XX:+GCALotAtAllSafepoints",
                                                                             "-XX:+ScavengeALot",
                                                                             "-Xlog:gc,gc+start,safepoint",
                                                                             "NoSuchClass");

        Process process = ProcessTools.startProcess("gcalot", pb);
        OutputAnalyzer output = new OutputAnalyzer(process);
        output.shouldMatch("Error: Could not find or load main class NoSuchClass");
        output.shouldHaveExitValue(1);
    }
}
