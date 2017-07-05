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
 * @summary Verify UseRTMForStackLocks option processing on CPU with
 *          rtm support when VM supports rtm locking.
 * @library /testlibrary /test/lib /compiler/testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build TestUseRTMForStackLocksOptionOnSupportedConfig
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   TestUseRTMForStackLocksOptionOnSupportedConfig
 */

import jdk.test.lib.*;
import jdk.test.lib.cli.*;
import jdk.test.lib.cli.predicate.AndPredicate;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

public class TestUseRTMForStackLocksOptionOnSupportedConfig
        extends CommandLineOptionTest {
    private static final String DEFAULT_VALUE = "false";

    private TestUseRTMForStackLocksOptionOnSupportedConfig() {
        super(new AndPredicate(new SupportedVM(), new SupportedCPU()));
    }

    @Override
    public void runTestCases() throws Throwable {
        String errorMessage
                = CommandLineOptionTest.getExperimentalOptionErrorMessage(
                "UseRTMForStackLocks");
        String warningMessage
                = RTMGenericCommandLineOptionTest.RTM_FOR_STACK_LOCKS_WARNING;

        String shouldFailMessage = " VM option 'UseRTMForStackLocks' is "
                + "experimental%nJVM startup should fail without "
                + "-XX:+UnlockExperimentalVMOptions flag";

        CommandLineOptionTest.verifySameJVMStartup(
                new String[] { errorMessage }, null, shouldFailMessage,
                shouldFailMessage + "%nError message expected", ExitCode.FAIL,
                "-XX:+UseRTMForStackLocks");
        String shouldPassMessage = " VM option 'UseRTMForStackLocks'"
                + " is experimental%nJVM startup should pass with "
                + "-XX:+UnlockExperimentalVMOptions flag";
        // verify that we get a warning when trying to use rtm for stack
        // lock, but not using rtm locking.
        CommandLineOptionTest.verifySameJVMStartup(
                new String[] { warningMessage }, null, shouldPassMessage,
                "There should be warning when trying to use rtm for stack "
                        + "lock, but not using rtm locking", ExitCode.OK,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMForStackLocks",
                "-XX:-UseRTMLocking");
        // verify that we don't get a warning when no using rtm for stack
        // lock and not using rtm locking.
        CommandLineOptionTest.verifySameJVMStartup(null,
                new String[] { warningMessage }, shouldPassMessage,
                "There should not be any warning when use both "
                        + "-XX:-UseRTMForStackLocks and -XX:-UseRTMLocking "
                        + "flags",
                ExitCode.OK,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:-UseRTMForStackLocks",
                "-XX:-UseRTMLocking");
        // verify that we don't get a warning when using rtm for stack
        // lock and using rtm locking.
        CommandLineOptionTest.verifySameJVMStartup(null,
                new String[] { warningMessage }, shouldPassMessage,
                "There should not be any warning when use both "
                        + "-XX:+UseRTMForStackLocks and -XX:+UseRTMLocking"
                        + " flags",
                ExitCode.OK,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMForStackLocks",
                "-XX:+UseRTMLocking");
        // verify that default value if false
        CommandLineOptionTest.verifyOptionValueForSameVM("UseRTMForStackLocks",
                TestUseRTMForStackLocksOptionOnSupportedConfig.DEFAULT_VALUE,
                "Default value of option 'UseRTMForStackLocks' should be false",
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS);
        // verify that default value is false even with +UseRTMLocking
        CommandLineOptionTest.verifyOptionValueForSameVM("UseRTMForStackLocks",
                TestUseRTMForStackLocksOptionOnSupportedConfig.DEFAULT_VALUE,
                "Default value of option 'UseRTMForStackLocks' should be false",
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                "-XX:+UseRTMLocking");
        // verify that we can turn the option on
        CommandLineOptionTest.verifyOptionValueForSameVM("UseRTMForStackLocks",
                        "true", "Value of option 'UseRTMForStackLocks' should "
                                + "be able to be set as 'true' when both "
                                + "-XX:+UseRTMForStackLocks and "
                                + "-XX:+UseRTMLocking flags used",
                        CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                        "-XX:+UseRTMLocking", "-XX:+UseRTMForStackLocks");
    }

    public static void main(String args[]) throws Throwable {
        new TestUseRTMForStackLocksOptionOnSupportedConfig().test();
    }
}
