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

import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.*;

/**
 * Test on bit manipulation related command line options,
 * that should be executed on CPU that supports all required
 * features.
 */
public class BMISupportedCPUTest extends BMICommandLineOptionTestBase {

    /**
     * Construct new test on {@code optionName} option.
     *
     * @param optionName Name of the option to be tested
     *                   without -XX:[+-] prefix.
     * @param warningMessage Message that can occur in VM output
     *                       if CPU on test box does not support
     *                       features required by the option.
     * @param cpuFeatures CPU features requires by the option.
     */
    public BMISupportedCPUTest(String optionName,
                               String warningMessage,
                               String... cpuFeatures) {
        super(optionName, warningMessage, cpuFeatures, null);
    }

    @Override
    public void runTestCases() throws Throwable {
        // verify that VM will succesfully start up whithout warnings
        CommandLineOptionTest.
            verifyJVMStartup("-XX:+" + optionName,
                             null, new String[] { warningMessage },
                             ExitCode.OK);

        // verify that VM will succesfully start up whithout warnings
        CommandLineOptionTest.
            verifyJVMStartup("-XX:-" + optionName,
                             null, new String[] { warningMessage },
                             ExitCode.OK);

        // verify that on appropriate CPU option in on by default
        CommandLineOptionTest.verifyOptionValue(optionName, "true");

        // verify that option could be explicitly turned off
        CommandLineOptionTest.verifyOptionValue(optionName, "false",
                                                "-XX:-" + optionName);
    }
}

