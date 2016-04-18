/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test MonitorMismatchTest
 * @bug 8150084
 * @library /testlibrary
 * @compile MonitorMismatchHelper.jasm
 * @build jdk.test.lib.OutputAnalyzer jdk.test.lib.ProcessTools jdk.test.lib.Platform
 * @run driver MonitorMismatchTest
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Platform;

public class MonitorMismatchTest {

    public static void main(String... args) throws Exception {
        if (!Platform.isEmbedded()){
            // monitormismatch should turn on.
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xcomp",
                                                                      "-XX:+TieredCompilation",
                                                                      "-Xlog:monitormismatch=info",
                                                                      "MonitorMismatchHelper");
            OutputAnalyzer o = new OutputAnalyzer(pb.start());
            o.shouldContain("[monitormismatch] Monitor mismatch in method");

            // monitormismatch should turn off.
            pb = ProcessTools.createJavaProcessBuilder("-Xcomp",
                                                       "-XX:+TieredCompilation",
                                                       "-Xlog:monitormismatch=off",
                                                       "MonitorMismatchHelper");
            o = new OutputAnalyzer(pb.start());
            o.shouldNotContain("[monitormismatch]");
        }
    };

}
