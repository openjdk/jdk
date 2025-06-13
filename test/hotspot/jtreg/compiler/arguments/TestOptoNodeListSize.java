/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

 /*
 * @test
 * @library /test/lib /
 * @bug 8359200
 * @key randomness
 * @requires vm.flagless & vm.compiler2.enabled & vm.debug == true
 * @summary Test that -XX:OptoNodeListSize does not crash the VM.
 * @run driver compiler.arguments.TestOptoNodeListSize
 */

package compiler.arguments;

import java.io.IOException;
import java.util.Random;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;

public class TestOptoNodeListSize {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            int size = RANDOM.nextInt(1000) + 1;
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:OptoNodeListSize=" + size,
                "-Xcomp", "-XX:-TieredCompilation", "compiler.arguments.TestOptoNodeListSize", "run");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
        } else {
            System.out.println("Test passed.");
        }
    }
}
