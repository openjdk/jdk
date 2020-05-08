/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase jit/tiered.
 * VM Testbase keywords: [jit, quick]
 * VM Testbase readme:
 * Description
 *     The test verifies that JVM prints tiered events with -XX:+PrintTieredEvents
 *     for tiered compilation explicitly enabled with -XX:+TieredCompilation.
 *     If tiered compilation is explicitly disabled the test verifies that there are no
 *     output from PrintTieredEvents.
 *
 * @comment the test can't be run w/ jvmci compiler enabled as it enforces tiered compilation
 * @requires vm.opt.UseJVMCICompiler != true
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver vmTestbase.jit.tiered.Test
 */

package vmTestbase.jit.tiered;

import jtreg.SkippedException;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class Test {
    private static String UNSUPPORTED_OPTION_MESSAGE = "-XX:+TieredCompilation not supported in this VM";
    private static String REGEXP = "^[0-9.]+: \\[compile level=\\d";
    public static void main(String[] args) throws Exception {
        {
            System.out.println("TieredCompilation is enabled");
            var pb = ProcessTools.createTestJvm(
                    "-XX:+TieredCompilation",
                    "-XX:+PrintTieredEvents",
                    "-version");
            var output = new OutputAnalyzer(pb.start());
            if (output.getStderr().contains(UNSUPPORTED_OPTION_MESSAGE)) {
                throw new SkippedException(UNSUPPORTED_OPTION_MESSAGE);
            }
            output.shouldHaveExitValue(0)
                  .stdoutShouldMatch(REGEXP);
        }
        {
            System.out.println("TieredCompilation is disabled");
            var pb = ProcessTools.createTestJvm(
                    "-XX:-TieredCompilation",
                    "-XX:+PrintTieredEvents",
                    "-version");
            var output = new OutputAnalyzer(pb.start())
                    .shouldHaveExitValue(0)
                    .stdoutShouldNotMatch(REGEXP);
        }
    }
}
