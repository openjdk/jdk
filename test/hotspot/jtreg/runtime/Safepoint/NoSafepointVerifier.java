/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8184732
 * @summary Ensure that special locks never safepoint check.
 * @requires vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver NoSafepointVerifier
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import sun.hotspot.WhiteBox;

public class NoSafepointVerifier {

    static void runTest(String test) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
              "-Xbootclasspath/a:.",
              "-XX:+UnlockDiagnosticVMOptions",
              "-XX:+WhiteBoxAPI",
              "-XX:-CreateCoredumpOnCrash",
              "NoSafepointVerifier",
              test);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain(test)
              .shouldNotHaveExitValue(0);
    }

    static String test1 = "Special locks or below should never safepoint";
    static String test2 = "Possible safepoint reached by thread that does not allow it";

    public static void main(String args[]) throws Exception {
        if (args.length > 0) {
            if (args[0].equals(test1)) {
                WhiteBox.getWhiteBox().assertSpecialLock(/*vm_block*/true, /*safepoint_check_always*/true);
            } else if (args[0].equals(test2)) {
                WhiteBox.getWhiteBox().assertSpecialLock(/*vm_block*/true, /*safepoint_check_always*/false);
            }
        } else {
            runTest(test1);
            runTest(test2);
        }
    }
}
