/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test GenerateOopMap
 * @bug 8379015
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver GenerateOopMapTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;

public class GenerateOopMapTest {

    static String infoPattern = "[generateoopmap]";
    static String debugPattern = "[generateoopmap] Basicblock#0 begins at:";
    static String tracePattern = "[trace][generateoopmap]        5  vars = 'r'      stack = 'v'   monitors = ''  	ifne";
    static String traceDetailPattern = "[generateoopmap]         0 vars     = ( r  |slot0)    invokestatic()V";

    static void match(String logFileName, String pattern, boolean shouldContain) throws Exception {
        // Does this use piles of memory?
        boolean contains = Files.readString(Path.of(logFileName)).contains(pattern);
        if (shouldContain && !contains) {
            throw new RuntimeException("Pattern " + pattern + " not found in " + logFileName);
        } else if (!shouldContain && contains) {
            throw new RuntimeException("Pattern " + pattern + " should not be found in " + logFileName);
        }
    }

    static void test() throws Exception {
        // Don't print much with info level.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:generateoopmap=info:info.log",
                                                                             "GenerateOopMapTest", "test");
        OutputAnalyzer o = new OutputAnalyzer(pb.start());
        o.shouldHaveExitValue(0);
        match("info.log", debugPattern, false);

        // Prints bytecodes and BasicBlock information in debug.
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:generateoopmap=debug:debug.log",
                                                                             "GenerateOopMapTest", "test");
        o = new OutputAnalyzer(pb.start());
        o.shouldHaveExitValue(0);
        match("debug.log", debugPattern, true);

        // Prints ref/val for each bytecode in trace.
        pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:generateoopmap=trace:trace.log",
                                                              "GenerateOopMapTest", "test");
        o = new OutputAnalyzer(pb.start());
        o.shouldHaveExitValue(0);
        match("trace.log", tracePattern, true);

        // Prints extra stuff with detailed. Not sure how useful this is but keep it for now.
        if (Platform.isDebugBuild()) {
            pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:generateoopmap=trace:detail.log",
                                                                  "-XX:+TraceNewOopMapGenerationDetailed",
                                                                  "GenerateOopMapTest", "test");
            o = new OutputAnalyzer(pb.start());
            o.shouldHaveExitValue(0);
            match("detail.log", traceDetailPattern, true);
        }
    };

    public static void main(String... args) throws Exception {
        System.gc();
        if (args.length == 0) {
            test();
        }
    }
}
