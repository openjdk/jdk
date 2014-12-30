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
 */

import com.oracle.java.testlibrary.ExitCode;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;

/**
 * @test
 * @bug 8031323
 * @summary Verify SurvivorAlignmentInBytes option processing.
 * @library /testlibrary
 * @requires vm.opt.SurvivorAlignmentInBytes == null
 *           & vm.opt.ObjectAlignmentInBytes == null
 *           & vm.opt.UnlockExperimentalVMOptions == null
 *           & (vm.opt.IgnoreUnrecognizedVMOptions == null
 *              | vm.opt.IgnoreUnrecognizedVMOptions == "false")
 * @run main TestSurvivorAlignmentInBytesOption
 */
public class TestSurvivorAlignmentInBytesOption {
    public static void main(String args[]) throws Throwable {
        String optionName = "SurvivorAlignmentInBytes";
        String unlockExperimentalVMOpts = "UnlockExperimentalVMOptions";
        String optionIsExperimental
                = CommandLineOptionTest.getExperimentalOptionErrorMessage(
                optionName);
        String valueIsTooSmall= ".*SurvivorAlignmentInBytes=.*must be greater"
                + " than ObjectAlignmentInBytes.*";
        String mustBePowerOf2 = ".*SurvivorAlignmentInBytes=.*must be "
                + "power of 2.*";

        // Verify that without -XX:+UnlockExperimentalVMOptions usage of
        // SurvivorAlignmentInBytes option will cause JVM startup failure
        // with the warning message saying that that option is experimental.
        CommandLineOptionTest.verifyJVMStartup(
                new String[]{optionIsExperimental}, null, ExitCode.FAIL, false,
                "-XX:-UnlockExperimentalVMOptions",
                CommandLineOptionTest.prepareBooleanFlag(
                        unlockExperimentalVMOpts, false),
                CommandLineOptionTest.prepareNumericFlag(optionName, 64));

        // Verify that with -XX:+UnlockExperimentalVMOptions passed to JVM
        // usage of SurvivorAlignmentInBytes option won't cause JVM startup
        // failure.
        CommandLineOptionTest.verifyJVMStartup(
                null, new String[]{optionIsExperimental}, ExitCode.OK, false,
                CommandLineOptionTest.prepareBooleanFlag(
                        unlockExperimentalVMOpts, true),
                CommandLineOptionTest.prepareNumericFlag(optionName, 64));

        // Verify that if specified SurvivorAlignmentInBytes is lower then
        // ObjectAlignmentInBytes, then the JVM startup will fail with
        // appropriate error message.
        CommandLineOptionTest.verifyJVMStartup(
                new String[]{valueIsTooSmall}, null, ExitCode.FAIL, false,
                CommandLineOptionTest.prepareBooleanFlag(
                        unlockExperimentalVMOpts, true),
                CommandLineOptionTest.prepareNumericFlag(optionName, 2));

        // Verify that if specified SurvivorAlignmentInBytes value is not
        // a power of 2 then the JVM startup will fail with appropriate error
        // message.
        CommandLineOptionTest.verifyJVMStartup(
                new String[]{mustBePowerOf2}, null, ExitCode.FAIL, false,
                CommandLineOptionTest.prepareBooleanFlag(
                        unlockExperimentalVMOpts, true),
                CommandLineOptionTest.prepareNumericFlag(optionName, 127));

        // Verify that if SurvivorAlignmentInBytes has correct value, then
        // the JVM will be started without errors.
        CommandLineOptionTest.verifyJVMStartup(
                null, new String[]{".*SurvivorAlignmentInBytes.*"},
                ExitCode.OK, false,
                CommandLineOptionTest.prepareBooleanFlag(
                        unlockExperimentalVMOpts, true),
                CommandLineOptionTest.prepareNumericFlag(optionName, 128));

        // Verify that we can setup different SurvivorAlignmentInBytes values.
        for (int alignment = 32; alignment <= 128; alignment *= 2) {
            CommandLineOptionTest.verifyOptionValue(optionName,
                    Integer.toString(alignment),
                    CommandLineOptionTest.prepareBooleanFlag(
                            unlockExperimentalVMOpts, true),
                    CommandLineOptionTest.prepareNumericFlag(
                            optionName, alignment));
        }
    }
}
