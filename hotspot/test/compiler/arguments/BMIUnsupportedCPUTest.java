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
 * that should be executed on CPU that does not support
 * required features.
 */
public class BMIUnsupportedCPUTest extends BMICommandLineOptionTestBase {

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
    public BMIUnsupportedCPUTest(String optionName,
                                 String warningMessage,
                                 String... cpuFeatures) {
        super(optionName, warningMessage, null, cpuFeatures);
    }

    @Override
    public void runTestCases() throws Throwable {
        if (Platform.isX86() || Platform.isX64()) {
            unsupportedX86CPUTestCases();
        } else {
            unsupportedNonX86CPUTestCases();
        }
    }

    /**
     * Run test cases common for all bit manipulation related VM options
     * targeted to X86 CPU that does not support required features.
     *
     * @throws Throwable if test failed.
     */
    public void unsupportedX86CPUTestCases() throws Throwable {

        // verify that VM will succesfully start up, but output will
        // contain a warning
        CommandLineOptionTest.
            verifyJVMStartup("-XX:+" + optionName,
                             new String[] { warningMessage },
                             new String[] { errorMessage },
                             ExitCode.OK);

        // verify that VM will succesfully startup without any warnings
        CommandLineOptionTest.
            verifyJVMStartup("-XX:-" + optionName,
                             null,
                             new String[] { warningMessage, errorMessage },
                             ExitCode.OK);

        // verify that on unsupported CPUs option is off by default
        CommandLineOptionTest.verifyOptionValue(optionName, "false");

        // verify that on unsupported CPUs option will be off even if
        // it was explicitly turned on by uset
        CommandLineOptionTest.verifyOptionValue(optionName, "false",
                                                     "-XX:+" + optionName);

    }

    /**
     * Run test cases common for all bit manipulation related VM options
     * targeted to non-X86 CPU that does not support required features.
     *
     * @throws Throwable if test failed.
     */
    public void unsupportedNonX86CPUTestCases() throws Throwable {

        // verify that VM known nothing about tested option
        CommandLineOptionTest.
            verifyJVMStartup("-XX:+" + optionName,
                             new String[] { errorMessage },
                             null,
                             ExitCode.FAIL);

        CommandLineOptionTest.
            verifyJVMStartup("-XX:-" + optionName,
                             new String[] { errorMessage },
                             null,
                             ExitCode.FAIL);
    }
}

