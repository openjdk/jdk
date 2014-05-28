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
 * @summary Verify UseRTMDeopt option processing on CPUs without rtm support
 *          or on VMs without rtm locking support.
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestUseRTMDeoptOptionOnUnsupportedConfig
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestUseRTMDeoptOptionOnUnsupportedConfig
 */

import com.oracle.java.testlibrary.cli.CommandLineOptionTest;

import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import com.oracle.java.testlibrary.cli.predicate.NotPredicate;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

public class TestUseRTMDeoptOptionOnUnsupportedConfig
        extends RTMGenericCommandLineOptionTest {
    private static final String DEFAULT_VALUE = "false";

    private TestUseRTMDeoptOptionOnUnsupportedConfig() {
        super(new NotPredicate(new AndPredicate(new SupportedCPU(),
                        new SupportedVM())),
                "UseRTMDeopt", true, false,
                TestUseRTMDeoptOptionOnUnsupportedConfig.DEFAULT_VALUE, "true");
    }

    @Override
    protected void runX86SupportedVMTestCases() throws Throwable {
        super.verifyJVMStartup();
        // verify default value
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                defaultValue);
        // verify that until RTMLocking is not used, value
        // will be set to default false.
        CommandLineOptionTest.verifyOptionValueForSameVM(optionName,
                defaultValue, "-XX:+UseRTMDeopt");
    }

    public static void main(String args[]) throws Throwable {
        new TestUseRTMDeoptOptionOnUnsupportedConfig().test();
    }
}
