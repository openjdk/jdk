/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388186
 * @summary Test for VM crash with -XX:+EnableX86ECoreOpts and UseAVX < 2.
 * @requires vm.flagless
 * @requires os.arch == "amd64" | os.arch == "x86_64"
 * @library /test/lib
 * @run driver ${test.main.class}
 */

package compiler.cpuflags;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestEnableX86ECoreOptsWithAVX2Disabled {
    static final String[] OPTIONS = {
        "-XX:UseSSE=2",
        "-XX:UseSSE=3",
        "-XX:UseAVX=0",
        "-XX:UseAVX=1"
    };

    public static void main(String[] args) throws Exception {
        for (String option : OPTIONS) {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                                                 "-XX:+UnlockDiagnosticVMOptions",
                                                 "-XX:+EnableX86ECoreOpts",
                                                 option,
                                                 "-version");
            output.shouldHaveExitValue(0);
        }
    }
}
