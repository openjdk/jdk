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
 * @summary Verify UseRTMForStackLocks option processing on CPUs without
 *          rtm support and/or on VMs without rtm locking support.
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestUseRTMForStackLocksOptionOnUnsupportedConfig
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                    TestUseRTMForStackLocksOptionOnUnsupportedConfig
 */

import com.oracle.java.testlibrary.ExitCode;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import com.oracle.java.testlibrary.cli.predicate.NotPredicate;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

public class TestUseRTMForStackLocksOptionOnUnsupportedConfig
        extends RTMGenericCommandLineOptionTest {
    private static final String DEFAULT_VALUE = "false";

    private TestUseRTMForStackLocksOptionOnUnsupportedConfig() {
        super(new NotPredicate(new AndPredicate(new SupportedCPU(),
                        new SupportedVM())),
                "UseRTMForStackLocks", true, true,
                TestUseRTMForStackLocksOptionOnUnsupportedConfig.DEFAULT_VALUE,
                "true");
    }

    @Override
    protected void runX86SupportedVMTestCases() throws Throwable {
        // verify that option is experimental
        CommandLineOptionTest.verifySameJVMStartup(
                new String[]{ experimentalOptionError },
                null, ExitCode.FAIL, prepareOptionValue("true"));

        CommandLineOptionTest.verifySameJVMStartup(
                new String[]{ experimentalOptionError },
                null, ExitCode.FAIL, prepareOptionValue("false"));

        // verify that if we turn it on, then VM output will contain
        // warning saying that this option could be turned on only
        // when we use rtm locking
        CommandLineOptionTest.verifySameJVMStartup(
                new String[]{
                    RTMGenericCommandLineOptionTest.RTM_FOR_STACK_LOCKS_WARNING
                },
                null, ExitCode.OK,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                prepareOptionValue("true")
        );
        // verify that options is turned off by default
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                TestUseRTMForStackLocksOptionOnUnsupportedConfig.DEFAULT_VALUE,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS);
        // verify that it could not be turned on without rtm locking
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                TestUseRTMForStackLocksOptionOnUnsupportedConfig.DEFAULT_VALUE,
                CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                prepareOptionValue("true"));
    }

    public static void main(String args[]) throws Throwable {
        new TestUseRTMForStackLocksOptionOnUnsupportedConfig().test();
    }
}
