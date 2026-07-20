/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8335708
 * @summary Crash Compile::verify_graph_edges with dead code check when safepoints are reachable but not connected back to Root's inputs
 * @library /test/lib
 *
 * @run driver compiler.loopopts.VerifyGraphEdgesWithDeadCodeCheckFromSafepoints
 * @run main/othervm
 *       -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *       -XX:-TieredCompilation -XX:+VerifyGraphEdges
 *       -XX:+StressIGVN -Xcomp
 *       -XX:CompileCommand=compileonly,compiler.loopopts.VerifyGraphEdgesWithDeadCodeCheckFromSafepoints::mainTest
 *       compiler.loopopts.VerifyGraphEdgesWithDeadCodeCheckFromSafepoints
 *
 */

package compiler.loopopts;

import jdk.test.lib.Utils;

public class VerifyGraphEdgesWithDeadCodeCheckFromSafepoints {
    public static void main(String[] args) throws Exception {
        Thread thread = new Thread() {
            public void run() {
                VerifyGraphEdgesWithDeadCodeCheckFromSafepoints instance = new VerifyGraphEdgesWithDeadCodeCheckFromSafepoints();
                byte[] a = new byte[997];
                for (int i = 0; i < 100; ++i) {
                    instance.mainTest(a, a);
                }
            }
        };
        // Give thread some time to trigger compilation
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(500));
    }

    public void mainTest(byte[] a, byte[] b) {
        int i = 0;
        while (i < (a.length - 4)) {
            a[i] = b[i];
        }
        while (true) {
            a[i] = b[i];
        }
    }
}
