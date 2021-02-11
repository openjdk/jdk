/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @library /test/lib
 * @build compiler.blackhole.BlackholeTarget
 * @run driver compiler.blackhole.BlackholeNonVoidWarningTest
 */

package compiler.blackhole;

import java.io.IOException;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class BlackholeNonVoidWarningTest {

    private static final int CYCLES = 1_000_000;
    private static final int TRIES = 10;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            driver();
        } else {
            runner();
        }
    }

    public static void driver() throws IOException {
       final String msg = "Blackhole compile option only works for methods with void type: compiler.blackhole.BlackholeTarget.bh_sr_int(I)I";

       {
           ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-Xmx128m",
               "-Xbatch",
               "-XX:+UnlockDiagnosticVMOptions",
               "-XX:CompileCommand=quiet",
               "-XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*",
               "compiler.blackhole.BlackholeNonVoidWarningTest",
               "run"
           );
           OutputAnalyzer output = new OutputAnalyzer(pb.start());
           output.shouldHaveExitValue(0);
           output.shouldContain(msg);
       }

       {
           ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-Xmx128m",
               "-XX:-PrintWarnings",
               "-XX:+UnlockDiagnosticVMOptions",
               "-XX:CompileCommand=quiet",
               "-XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*",
               "compiler.blackhole.BlackholeNonVoidWarningTest",
               "run"
           );
           OutputAnalyzer output = new OutputAnalyzer(pb.start());
           output.shouldHaveExitValue(0);
           output.shouldNotContain(msg);
       }
    }

    public static void runner() {
        for (int t = 0; t < TRIES; t++) {
            run();
        }
    }

    public static void run() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_int(c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

}
