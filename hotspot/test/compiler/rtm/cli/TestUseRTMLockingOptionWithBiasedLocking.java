/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify processing of UseRTMLocking and UseBiasedLocking
 *          options combination on CPU and VM with rtm support.
 * @library /testlibrary /test/lib /compiler/testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build TestUseRTMLockingOptionWithBiasedLocking
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestUseRTMLockingOptionWithBiasedLocking
 */

import jdk.test.lib.*;
import jdk.test.lib.cli.*;
import jdk.test.lib.cli.predicate.AndPredicate;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

public class TestUseRTMLockingOptionWithBiasedLocking
        extends CommandLineOptionTest {
    private TestUseRTMLockingOptionWithBiasedLocking() {
        super(new AndPredicate(new SupportedCPU(), new SupportedVM()));
    }

    @Override
    public void runTestCases() throws Throwable {
        String warningMessage
                = RTMGenericCommandLineOptionTest.RTM_BIASED_LOCKING_WARNING;
        String shouldPassMessage = "JVM startup should pass with both "
                + "-XX:+UseRTMLocking and "
                + "-XX:-UseBiasedLocking flags set without any warnings";
        // verify that we will not get a warning
        CommandLineOptionTest.verifySameJVMStartup(null,
                new String[] { warningMessage }, shouldPassMessage,
                shouldPassMessage, ExitCode.OK,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMLocking", "-XX:-UseBiasedLocking");

        // verify that we will get a warning
        CommandLineOptionTest.verifySameJVMStartup(
                new String[] { warningMessage }, null,
                "JVM startup should pass when both -XX:+UseRTMLocking and "
                        + "-XX:+UseBiasedLocking flags set",
                "Flags -XX:+UseRTMLocking"
                        + " and -XX:+UseBiasedLocking conflicts. "
                        + "Warning should be shown.", ExitCode.OK,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMLocking", "-XX:+UseBiasedLocking");
        // verify that UseBiasedLocking is false when we use rtm locking
        CommandLineOptionTest.verifyOptionValueForSameVM("UseBiasedLocking",
                "false",
                "Value of option 'UseBiasedLocking' should be false if"
                        + "-XX:+UseRTMLocking flag set.",
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMLocking");
        // verify that we can't turn on biased locking when
        // using rtm locking
        CommandLineOptionTest
                .verifyOptionValueForSameVM(
                        "UseBiasedLocking",
                        "false",
                        "Value of option 'UseBiasedLocking' should be false if"
                                + "both -XX:+UseRTMLocking and "
                                + "-XX:+UseBiasedLocking flags set.",
                        CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                        "-XX:+UseRTMLocking", "-XX:+UseBiasedLocking");
    }

    public static void main(String args[]) throws Throwable {
        new TestUseRTMLockingOptionWithBiasedLocking().test();
    }
}
