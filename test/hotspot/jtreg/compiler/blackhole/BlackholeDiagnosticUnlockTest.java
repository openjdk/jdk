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
 * @run driver compiler.blackhole.BlackholeDiagnosticUnlockTest
 */

package compiler.blackhole;

import java.io.IOException;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class BlackholeDiagnosticUnlockTest {

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
       final String msg = "Blackhole compile option is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions";

       if (!Platform.isDebugBuild()) { // UnlockDiagnosticVMOptions is true in debug
           ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-Xmx128m",
               "-Xbatch",
               "-XX:CompileCommand=quiet",
               "-XX:CompileCommand=option,compiler/blackhole/BlackholeTarget.bh_*,Blackhole",
               "compiler.blackhole.BlackholeDiagnosticUnlockTest",
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
               "-XX:CompileCommand=quiet",
               "-XX:CompileCommand=option,compiler/blackhole/BlackholeTarget.bh_*,Blackhole",
               "compiler.blackhole.BlackholeDiagnosticUnlockTest",
               "run"
           );
           OutputAnalyzer output = new OutputAnalyzer(pb.start());
           output.shouldHaveExitValue(0);
           output.shouldNotContain(msg);
       }

       {
           ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-Xmx128m",
               "-XX:+UnlockDiagnosticVMOptions",
               "-XX:CompileCommand=quiet",
               "-XX:CompileCommand=option,compiler/blackhole/BlackholeTarget.bh_*,Blackhole",
               "compiler.blackhole.BlackholeDiagnosticUnlockTest",
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
            BlackholeTarget.bh_s_int_1(c);
        }
    }

}
