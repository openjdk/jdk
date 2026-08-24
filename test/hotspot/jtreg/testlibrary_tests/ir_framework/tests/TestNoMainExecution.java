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
 * @bug 8381564
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler2.enabled & vm.flagless
 * @summary Test that different ways to avoid executing main() are reported as correctly as failure.
 * @library /test/lib /testlibrary_tests /
 * @run driver ${test.main.class}
 */

package testlibrary_tests.ir_framework.tests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class TestNoMainExecution {
    public static void main(String[] args) {
        // We do not handshake because this flag disables socket communication
        run("-DReproduce=true");

        // We do not reach main() because there is no source file involved.
        run("--version");

        // We do not reach main() because we crash already at start-up.
        runWithVmCrash();
    }

    private static void run(String... flags) {
        try {
            TestFramework.runWithFlags(flags);
            Asserts.fail("should throw");
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            // We expect a useful help message - match its header.
            Asserts.assertTrue(errorMessage.contains("Did any of the following happen?"), errorMessage);
        }
    }

    private static void runWithVmCrash() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;

        try (PrintStream ps = new PrintStream(baos)) {
            System.setErr(ps);

            try {
                TestFramework.runWithFlags("-Xcomp", "-XX:+CICountNative", "-XX:CICrashAt=1");
                Asserts.fail("should throw");
            } catch (RuntimeException e) {
                // With a VM crash, the message is found on the normal stderr instead.
                System.setErr(oldErr);
                String output = baos.toString();
                Asserts.assertTrue(output.contains("Did any of the following happen?"));
            }
        }
    }

    @Test
    public void test() {}
}
