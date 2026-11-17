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

package gc.g1;

/*
 * @test
 * @bug 8396872
 * @requires vm.gc.G1
 * @requires vm.bits != "32"
 * @summary Test that the VM does not start with heaps exceeding maximum region indexes.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver gc.g1.TestRegionIdxExceedsRepresentable
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestRegionIdxExceedsRepresentable {

    public static void runTest(long heapsize) throws Exception {
        final String[] arguments = {
            "-XX:+UseG1GC",
            "-XX:-UseCompactObjectHeaders",
            "-XX:G1HeapRegionSize=1m",
            "-Xms16m",
            "-Xmx" + heapsize + "M",
            "-version"
            };

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(arguments);

        System.out.println(output.getStdout());

        output.shouldNotHaveExitValue(0);

        String pattern = "would exceed maximum region count 2147483648";
        output.shouldContain(pattern);
    }

    public static void main(String[] args) throws Exception {
        // Just above maximum.
        runTest(2_147_483_649L);
        // Original uint overflow bugs.
        runTest(4_294_967_296L);
        runTest(4_294_967_297L);
    }
}
