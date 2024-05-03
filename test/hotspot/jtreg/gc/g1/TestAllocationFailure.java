/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/*
 * @test TestAllocationFailure
 * @summary Ensure the output for a minor GC with G1 that has allocation failure contains the correct strings.
 * @requires vm.gc.G1
 * @requires vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   gc.g1.TestAllocationFailure
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestAllocationFailure {

    public static void main(String[] args) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx32M",
                                                                    "-Xmn16M",
                                                                    "-XX:+G1GCAllocationFailureALot",
                                                                    "-XX:G1GCAllocationFailureALotCount=100",
                                                                    "-XX:G1GCAllocationFailureALotInterval=1",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-Xlog:gc",
                                                                    GCTestWithAllocationFailure.class.getName());

        System.out.println(output.getStdout());
        output.shouldContain("(Evacuation Failure:");
        output.shouldHaveExitValue(0);
    }

    static class GCTestWithAllocationFailure {
        private static byte[] garbage;
        private static byte[] largeObject;
        private static Object[] holder = new Object[200]; // Must be larger than G1GCAllocationFailureALotCount

        public static void main(String [] args) {
            largeObject = new byte[16 * 1024 * 1024];
            System.out.println("Creating garbage");
            // Create 16 MB of garbage. This should result in at least one GC,
            // (Heap size is 32M, we use 17MB for the large object above)
            // which is larger than G1GCAllocationFailureALotInterval.
            for (int i = 0; i < 16 * 1024; i++) {
                holder[i % holder.length] = new byte[1024];
            }
            System.out.println("Done");
        }
    }
}
