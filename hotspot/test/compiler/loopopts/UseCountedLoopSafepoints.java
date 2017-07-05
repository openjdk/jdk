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
 *
 */

/**
 * @test
 * @bug 6869327
 * @summary Test that C2 flag UseCountedLoopSafepoints ensures a safepoint is kept in a CountedLoop
 * @library /testlibrary
 * @modules java.base
 * @run main UseCountedLoopSafepoints
 */

import java.util.concurrent.atomic.AtomicLong;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.OutputAnalyzer;

public class UseCountedLoopSafepoints {
    private static final AtomicLong _num = new AtomicLong(0);

    // Uses the fact that an EnableBiasedLocking vmop will be started
    // after 500ms, while we are still in the loop. If there is a
    // safepoint in the counted loop, then we will reach safepoint
    // very quickly. Otherwise SafepointTimeout will be hit.
    public static void main (String args[]) throws Exception {
        if (args.length == 1) {
            final int loops = Integer.parseInt(args[0]);
            for (int i = 0; i < loops; i++) {
                _num.addAndGet(1);
            }
        } else {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:+IgnoreUnrecognizedVMOptions",
                    "-XX:-TieredCompilation",
                    "-XX:+UseBiasedLocking",
                    "-XX:BiasedLockingStartupDelay=500",
                    "-XX:+SafepointTimeout",
                    "-XX:SafepointTimeoutDelay=2000",
                    "-XX:+UseCountedLoopSafepoints",
                    "UseCountedLoopSafepoints",
                    "2000000000"
                    );
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldNotContain("Timeout detected");
            output.shouldHaveExitValue(0);
        }
    }
}
