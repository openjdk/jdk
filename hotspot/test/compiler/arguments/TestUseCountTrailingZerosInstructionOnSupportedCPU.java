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

/**
 * @test
 * @bug 8031321
 * @summary Verify processing of UseCountTrailingZerosInstruction option
 *          on CPU with TZCNT (BMI1 feature) support.
 * @library /testlibrary /testlibrary/whitebox
 * @build TestUseCountTrailingZerosInstructionOnSupportedCPU
 *        BMISupportedCPUTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   TestUseCountTrailingZerosInstructionOnSupportedCPU
 */

import sun.hotspot.cpuinfo.CPUInfo;
import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.*;

public class TestUseCountTrailingZerosInstructionOnSupportedCPU
     extends BMISupportedCPUTest {

    public TestUseCountTrailingZerosInstructionOnSupportedCPU() {
        super("UseCountTrailingZerosInstruction", TZCNT_WARNING, "bmi1");
    }

    @Override
    public void runTestCases() throws Throwable {

        super.runTestCases();

        // verify that option will be disabled if all BMI1 instuctions
        // are explicitly disabled
        CommandLineOptionTest.
            verifyOptionValue("UseCountTrailingZerosInstruction", "false",
                              "-XX:-UseBMI1Instructions");

        // verify that option could be turned on even if other BMI1
        // instructions were turned off
        CommandLineOptionTest.
            verifyOptionValue("UseCountTrailingZerosInstruction", "true",
                              "-XX:-UseBMI1Instructions",
                              "-XX:+UseCountTrailingZerosInstruction");
    }

    public static void main(String args[]) throws Throwable {
        new TestUseCountTrailingZerosInstructionOnSupportedCPU().test();
    }
}

