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
 *
 */

/*
 * @test id=young
 * @bug 8394388
 * @summary Test that WhiteBox::youngGC is robust
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxYoungGC young
 */

/*
 * @test id=not-concurrent
 * @bug 8394388
 * @summary Test that WhiteBox::youngGC falls back to global with -XX:-ExplicitGCInvokesConcurrent
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxYoungGC not-concurrent
 */
package gc.shenandoah.generational;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import static gc.shenandoah.generational.TestHelpers.promote;

public class TestWhiteBoxYoungGC {
    public static void main(String[] argv) throws Exception {
        switch (argv[0]) {
            case "young"          -> Young.runTest();
            case "not-concurrent" -> NotConcurrent.runTest();
            default -> throw new IllegalArgumentException("Argument should be: old|mixed|cancel");
        }
    }

    public static class Young {
        private static void runTest() throws Exception {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                getCommandLineArguments(Young.class.getName())
            );

            output.shouldContain("WhiteBox Initiated Young GC");
            output.shouldHaveExitValue(0);
        }

        public static void main(String[] argv) {
            final WhiteBox WB = WhiteBox.getWhiteBox();
            WB.youngGC();
            WB.youngGC();
        }
    }

    public static class NotConcurrent {
        private static void runTest() throws Exception {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    getCommandLineArguments(
                            "-XX:-ExplicitGCInvokesConcurrent",
                            NotConcurrent.class.getName())
            );

            output.shouldNotContain("WhiteBox Initiated Global GC");
            output.shouldHaveExitValue(0);
        }

        public static void main(String[] argv) {
            final WhiteBox WB = WhiteBox.getWhiteBox();
            WB.youngGC();
            WB.youngGC();
        }
    }

    private static String[] getCommandLineArguments(String... additional) {
        var defaultArguments = new String[] {
            "-Xbootclasspath/a:.",
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational",
            "-Xlog:gc*", "-Xmx128M", "-ea",
            "-XX:ShenandoahGenerationalMinTenuringAge=1",
            "-XX:ShenandoahGenerationalMaxTenuringAge=1",
        };
        var arguments = new String[defaultArguments.length + additional.length];
        System.arraycopy(defaultArguments, 0, arguments, 0, defaultArguments.length);
        System.arraycopy(additional, 0, arguments, defaultArguments.length, additional.length);
        return arguments;
    }
}
