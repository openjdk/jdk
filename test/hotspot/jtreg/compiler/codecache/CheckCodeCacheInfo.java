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
 */

/*
 * @test CheckCodeCacheInfo
 * @bug 8005885
 * @summary Checks VM verbose information related to the code cache
 * @library /test/lib
 * @requires vm.debug
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   compiler.codecache.CheckCodeCacheInfo
 */

package compiler.codecache;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CheckCodeCacheInfo {
    private static final String VERBOSE_REGEXP;

    static {
        String entry = "\\d+K( \\(hdr \\d+K \\d+%, loc \\d+K \\d+%, code \\d+K \\d+%, stub \\d+K \\d+%, \\[oops \\d+K \\d+%, metadata \\d+K \\d+%, data \\d+K \\d+%, pcs \\d+K \\d+%\\]\\))?\\n";
        String pair = " #\\d+ live = " + entry
                    + " #\\d+ dead = " + entry;

        VERBOSE_REGEXP = "nmethod blobs per compilation level:\\n"
                       + "none:\\n"
                       + pair
                       + "simple:\\n"
                       + pair
                       + "limited profile:\\n"
                       + pair
                       + "full profile:\\n"
                       + pair
                       + "full optimization:\\n"
                       + pair
                       + "Non-nmethod blobs:\\n"
                       + " #\\d+ runtime = " + entry
                       + " #\\d+ uncommon trap = " + entry
                       + " #\\d+ deoptimization = " + entry
                       + " #\\d+ adapter = " + entry
                       + " #\\d+ buffer blob = " + entry
                       + " #\\d+ other = " + entry;
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;

        pb = ProcessTools.createJavaProcessBuilder("-XX:+PrintCodeCache",
                                                   "-XX:+Verbose",
                                                   "-version");
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
        out.stdoutShouldMatch(VERBOSE_REGEXP);
    }
}
