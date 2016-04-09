/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build jdk.test.lib.*
 * @run driver CreateCoredumpOnCrash
 */

import jdk.test.lib.*;
import sun.misc.Unsafe;

public class CreateCoredumpOnCrash {
    private static class Crasher {
        public static void main(String[] args) {
            Utils.getUnsafe().putInt(0L, 0);
        }
    }

    public static void main(String[] args) throws Exception {
        runTest("-XX:-CreateCoredumpOnCrash").shouldContain("CreateCoredumpOnCrash turned off, no core file dumped");

        if (Platform.isWindows()) {
            // The old CreateMinidumpOnCrash option should still work
            runTest("-XX:-CreateMinidumpOnCrash").shouldContain("CreateCoredumpOnCrash turned off, no core file dumped");
        } else {
            runTest("-XX:+CreateCoredumpOnCrash").shouldNotContain("CreateCoredumpOnCrash turned off, no core file dumped");
        }

    }
    public static OutputAnalyzer runTest(String option) throws Exception {
        return new OutputAnalyzer(
            ProcessTools.createJavaProcessBuilder(
            "-Xmx64m", "-XX:-TransmitErrorReport", option, Crasher.class.getName())
            .start());
    }
}
