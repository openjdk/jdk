/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8031320
 * @summary Verify UseRTMLocking option processing on CPU without
 *          rtm support.
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestUseRTMLockingOptionOnUnsupportedCPU
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestUseRTMLockingOptionOnUnsupportedCPU
 */

import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.*;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import com.oracle.java.testlibrary.cli.predicate.NotPredicate;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

public class TestUseRTMLockingOptionOnUnsupportedCPU
        extends CommandLineOptionTest {
    private static final String DEFAULT_VALUE = "false";

    private TestUseRTMLockingOptionOnUnsupportedCPU() {
        super(new AndPredicate(new NotPredicate(new SupportedCPU()),
                new SupportedVM()));
    }

    @Override
    public void runTestCases() throws Throwable {
        String unrecongnizedOption
                = CommandLineOptionTest.getUnrecognizedOptionErrorMessage(
                "UseRTMLocking");
        String errorMessage = RTMGenericCommandLineOptionTest.RTM_INSTR_ERROR;

        if (Platform.isX86() || Platform.isX64()) {
            // verify that we get an error when use +UseRTMLocking
            // on unsupported CPU
            CommandLineOptionTest.verifySameJVMStartup(
                    new String[] { errorMessage },
                    new String[] { unrecongnizedOption },
                    ExitCode.FAIL, "-XX:+UseRTMLocking");
            // verify that we can pass -UseRTMLocking without
            // getting any error messages
            CommandLineOptionTest.verifySameJVMStartup(
                    null,
                    new String[]{
                            errorMessage,
                            unrecongnizedOption
                    }, ExitCode.OK, "-XX:-UseRTMLocking");

            // verify that UseRTMLocking is false by default
            CommandLineOptionTest.verifyOptionValueForSameVM("UseRTMLocking",
                    TestUseRTMLockingOptionOnUnsupportedCPU.DEFAULT_VALUE);
        } else {
            // verify that on non-x86 CPUs RTMLocking could not be used
            CommandLineOptionTest.verifySameJVMStartup(
                    new String[] { unrecongnizedOption },
                    null, ExitCode.FAIL, "-XX:+UseRTMLocking");

            CommandLineOptionTest.verifySameJVMStartup(
                    new String[] { unrecongnizedOption },
                    null, ExitCode.FAIL, "-XX:-UseRTMLocking");
        }
    }

    public static void main(String args[]) throws Throwable {
        new TestUseRTMLockingOptionOnUnsupportedCPU().test();
    }
}
