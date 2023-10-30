/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test TestPLABAdaptToMinTLABSize
 * @bug 8289137
 * @summary Make sure that Young/OldPLABSize adapt to MinTLABSize setting.
 * @requires vm.gc.Parallel | vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver gc.TestPLABAdaptToMinTLABSize
 */

import java.util.ArrayList;
import java.util.Collections;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestPLABAdaptToMinTLABSize {
    private static void runTest(boolean shouldSucceed, String... extraArgs) throws Exception {
        ArrayList<String> testArguments = new ArrayList<String>();
        testArguments.add("-Xmx12m");
        testArguments.add("-XX:+PrintFlagsFinal");
        Collections.addAll(testArguments, extraArgs);
        testArguments.add("-version");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(testArguments);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        System.out.println(output.getStderr());

        if (shouldSucceed) {
            output.shouldHaveExitValue(0);

            long oldPLABSize = Long.parseLong(output.firstMatch("OldPLABSize\\s+=\\s(\\d+)",1));
            long youngPLABSize = Long.parseLong(output.firstMatch("YoungPLABSize\\s+=\\s(\\d+)",1));
            long minTLABSize = Long.parseLong(output.firstMatch("MinTLABSize\\s+=\\s(\\d+)",1));

            System.out.println("OldPLABSize=" + oldPLABSize + " YoungPLABSize=" + youngPLABSize +
                               "MinTLABSize=" + minTLABSize);

        } else {
            output.shouldNotHaveExitValue(0);
        }
    }

    public static void main(String[] args) throws Exception {
        runTest(true, "-XX:MinTLABSize=100k");
        // Should not succeed when explicitly specifying invalid combination.
        runTest(false, "-XX:MinTLABSize=100k", "-XX:OldPLABSize=5k");
        runTest(false, "-XX:MinTLABSize=100k", "-XX:YoungPLABSize=5k");
    }
}
