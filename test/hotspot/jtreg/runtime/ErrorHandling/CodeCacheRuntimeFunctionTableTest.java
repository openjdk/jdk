/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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
 * @bug 8387032
 * @summary Verifies that Windows/AArch64 can dispatch an exception from the
 *          code cache through the OS runtime function table when HotSpot's
 *          vectored exception handler declines it.
 * @requires os.family == "windows" & os.arch == "aarch64" & vm.debug
 * @library /test/lib
 * @run driver ${test.main.class}
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CodeCacheRuntimeFunctionTableTest {
    static class Test {
        static class Box { int value; }
        static int get(Box box) { return box.value; }

        public static void main(String[] args) {
            System.out.println(get(null));
            System.out.println("unreachable");
        }
    }

    public static void main(String[] args) throws Exception {
        // Set `InterceptOSException` so that VEH declines handling the
        // exception, thus diverting the exception to the Windows table-driven
        // dispatch.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+InterceptOSException",
                "-XX:-CreateCoredumpOnCrash",
                "-XX:CompileCommand=compileonly,${test.main.class}$Test::get",
                Test.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
        output.shouldMatch("# +EXCEPTION_ACCESS_VIOLATION.*");
    }
}
