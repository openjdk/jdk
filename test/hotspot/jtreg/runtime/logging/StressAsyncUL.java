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
 * @summary Stress test async UL in dropping and stalling mode
 * @requires vm.flagless
 * @requires vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver StressAsyncUL
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class StressAsyncUL {
    static void analyze_output(boolean stalling_mode, String... args) throws Exception {
        ProcessBuilder pb =
            ProcessTools.createLimitedTestJavaProcessBuilder(args);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        if (stalling_mode) {
            output.shouldNotContain("messages dropped due to async logging");
        }
    }
    public static void main(String[] args) throws Exception {
        analyze_output(false, "-Xlog:async:drop", "-Xlog:all=trace", InnerClass.class.getName());
        analyze_output(true, "-Xlog:async:stall", "-Xlog:all=trace", InnerClass.class.getName());
        // Stress test with a very small buffer. Note: Any valid buffer size must be able to hold a flush token.
        // Therefore the size of the buffer cannot be zero.
        analyze_output(false, "-Xlog:async:drop", "-Xlog:all=trace", "-XX:AsyncLogBufferSize=192", InnerClass.class.getName());
        analyze_output(true, "-Xlog:async:stall", "-Xlog:all=trace", "-XX:AsyncLogBufferSize=192", InnerClass.class.getName());
    }

    public static class InnerClass {
        public static void main(String[] args) {
        }
    }
}
