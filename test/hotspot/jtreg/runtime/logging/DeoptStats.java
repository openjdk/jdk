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
 * @test
 * @bug 8275865
 * @requires vm.compiler2.enabled
 * @summary Verify that the Deoptimization statistics are printed to the VM/Compiler log file
 * @library /test/lib
 * @run main/othervm -Xbatch -XX:-UseOnStackReplacement -XX:-OmitStackTraceInFastThrow
 *                   -XX:-OmitStackTraceInFastThrow
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation
 *                   -XX:-LogVMOutput -XX:LogFile=compilation.log DeoptStats
 * @run main/othervm -Xbatch -XX:-UseOnStackReplacement -XX:-OmitStackTraceInFastThrow
 *                   -XX:-OmitStackTraceInFastThrow
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation
 *                   -XX:+LogVMOutput -XX:LogFile=vmOutput.log DeoptStats
 * @run main/othervm DeoptStats compilation.log vmOutput.log
 */

import java.nio.file.Paths;
import jdk.test.lib.process.OutputAnalyzer;

public class DeoptStats {

    static class Value {
        int i;
    }

    static int f(Value v) {
        return v.i;
    }

    public static void verify(String[] logFiles) throws Exception {
        for (String logFile : logFiles) {
            OutputAnalyzer oa = new OutputAnalyzer(Paths.get(logFile));
            oa.shouldMatchByLine("<statistics type='deoptimization'>", // Start from this line
                                 "</statistics>",                      // Match until this line
                                 "(Deoptimization traps recorded:)|( .+)");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            verify(args);
        } else {
            for (int i = 0; i < 20_000; i++) {
                try {
                    f(null);
                }
                catch (NullPointerException npe) { }
            }
        }
    }
}
