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
 * @summary converted from VM Testbase vm/compiler/CodeCacheInfo.
 * VM Testbase readme:
 * DESCRIPTION
 *     Test calls java -version and checks enhanced output format of the
 *     -XX:+PrintCodeCache vm option.
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver vm.compiler.CodeCacheInfo.Test
 */

package vm.compiler.CodeCacheInfo;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class Test {
    private static String p1 = " size=\\d+Kb used=\\d+Kb max_used=\\d+Kb free=\\d+Kb\\n";
    private static String p2 = " bounds \\[0x[0-9a-f]+, 0x[0-9a-f]+, 0x[0-9a-f]+\\]\\n";
    private static String p3 = " total_blobs=\\d+ nmethods=\\d+ adapters=\\d+\\n";
    private static String p4 = " compilation: enabled\\n";

    private static String SEG_REGEXP = "^(CodeHeap '[^']+':" + p1 + p2 + ")+" + p3 + p4;
    private static String NOSEG_REGEXP = "^CodeCache:" + p1 + p2 + p3 + p4;

    public static void main(String[] args) throws Exception {
        {
            System.out.println("SegmentedCodeCache is enabled");
            var pb = ProcessTools.createJavaProcessBuilder(true,
                    "-XX:+SegmentedCodeCache",
                    "-XX:+PrintCodeCache",
                    "-version");
            var output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
            output.stdoutShouldMatch(SEG_REGEXP);
        }
        {
            System.out.println("SegmentedCodeCache is disabled");
            var pb = ProcessTools.createJavaProcessBuilder(true,
                    "-XX:-SegmentedCodeCache",
                    "-XX:+PrintCodeCache",
                    "-version");
            var output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
            output.stdoutShouldMatch(NOSEG_REGEXP);
        }
    }
}
