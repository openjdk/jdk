/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run driver CreateCoredumpOnCrash
 * @requires vm.flagless
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.internal.misc.Unsafe;

public class CreateCoredumpOnCrash {
    private static class Crasher {
        public static void main(String[] args) {
            Unsafe.getUnsafe().putInt(0L, 0);
        }
    }

    private static String ulimitString(int limit) {
        String string = "ulimit -c ";
        if (limit != Integer.MAX_VALUE) {
            string += limit;
        } else {
            string += "unlimited";
        }
        return string+";";
    }

    public static void main(String[] args) throws Exception {
        runTest("-XX:-CreateCoredumpOnCrash").shouldContain("CreateCoredumpOnCrash turned off, no core file dumped")
        .shouldNotHaveExitValue(0);

        if (Platform.isWindows()) {
            // The old CreateMinidumpOnCrash option should still work
            runTest("-XX:-CreateMinidumpOnCrash").shouldContain("CreateCoredumpOnCrash turned off, no core file dumped")
            .shouldNotHaveExitValue(0);
        } else {
            String exec_cmd[] = {"sh", "-c", "ulimit -c"};
            OutputAnalyzer oa = new OutputAnalyzer(Runtime.getRuntime().exec(exec_cmd));
            oa.shouldHaveExitValue(0);
            if (!oa.contains("0\n")) {
                oa = runTest("-XX:+CreateCoredumpOnCrash");
                oa.shouldContain("Core dump will be written.");
                oa.shouldNotHaveExitValue(0);

                oa = runTest("-XX:+CreateCoredumpOnCrash", ulimitString(1024));
                oa.shouldContain("warning: CreateCoredumpOnCrash specified, but");
                oa.shouldNotHaveExitValue(0);

                oa = runTest("-XX:+CreateCoredumpOnCrash", ulimitString(0));
                oa.shouldContain("warning: CreateCoredumpOnCrash specified, but");
                oa.shouldNotHaveExitValue(0);
            } else {
                throw new Exception("ulimit is not set correctly, try 'ulimit -c unlimited' and re-run.");
            }
        }
    }

    public static OutputAnalyzer runTest(String option) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xmx128m",
                                                                             "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                                                                             option, Crasher.class.getName());
        return new OutputAnalyzer(pb.start());
    }
    public static OutputAnalyzer runTest(String option, String limit) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xmx128m",
                                                                             "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                                                                             option, new String("'"+Crasher.class.getName()+"'"));
        String args = "";
        for (String s:pb.command()) {
            args += s+" ";
        }
        String exec_cmd[] = {"sh", "-c", limit+args};
        return new OutputAnalyzer(Runtime.getRuntime().exec(exec_cmd));
    }
}
