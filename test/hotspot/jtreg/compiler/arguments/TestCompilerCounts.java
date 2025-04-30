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

/*
 * @test
 * @library /test/lib /
 * @bug 8356000
 * @requires vm.flagless
 * @requires vm.debug
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 *
 * @summary Test compiler counts selection, verified by internal assertions
 * @run driver compiler.arguments.TestCompilerCounts
 */

package compiler.arguments;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestCompilerCounts {

    public static void main(String[] args) throws IOException {
        testWith("-XX:TieredStopAtLevel=0");
        testWith("-XX:TieredStopAtLevel=1");
        testWith("-XX:TieredStopAtLevel=2");
        testWith("-XX:TieredStopAtLevel=3");
        testWith("-XX:TieredStopAtLevel=4");
        testWith("-XX:-TieredCompilation");
    }

    public static void testWith(String mode) throws IOException {
        for (int cpus = 1; cpus <= Runtime.getRuntime().availableProcessors(); cpus++) {
            String[] args = new String[] {
                mode,
                "-XX:ActiveProcessorCount=" + cpus,
                "-version"
            };
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
        }
    }

}
