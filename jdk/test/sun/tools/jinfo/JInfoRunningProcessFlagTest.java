/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import sun.management.ManagementFactoryHelper;

import com.sun.management.HotSpotDiagnosticMXBean;

import jdk.testlibrary.OutputAnalyzer;
import static jdk.testlibrary.Platform.isSolaris;
import static jdk.testlibrary.Asserts.assertEquals;
import static jdk.testlibrary.Asserts.assertNotEquals;
import static jdk.testlibrary.Asserts.assertTrue;

/**
 * @test
 * @summary The test sanity checks 'jinfo -flag' option.
 * @library /lib/testlibrary
 * @build jdk.testlibrary.* JInfoHelper
 * @run main/othervm -XX:+HeapDumpOnOutOfMemoryError JInfoRunningProcessFlagTest
 */
public class JInfoRunningProcessFlagTest {

    public static void main(String[] args) throws Exception {
        testFlag();
        testFlagPlus();
        testFlagMinus();
        testFlagEqual();

        testInvalidFlag();

        testSolarisSpecificFlag();
    }

    private static void testFlag() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo("-flag", "HeapDumpOnOutOfMemoryError");
        output.shouldHaveExitValue(0);
        assertTrue(output.getStderr().isEmpty(), "'jinfo -flag HeapDumpOnOutOfMemoryError' stderr should be empty");
        output.shouldContain("+HeapDumpOnOutOfMemoryError");
    }

    private static void testFlagPlus() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo("-flag", "+PrintGC");
        output.shouldHaveExitValue(0);
        output = JInfoHelper.jinfo("-flag", "PrintGC");
        output.shouldHaveExitValue(0);
        output.shouldContain("+PrintGC");
        verifyIsEnabled("PrintGC");
    }

    private static void testFlagMinus() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo("-flag", "-PrintGC");
        output.shouldHaveExitValue(0);
        output = JInfoHelper.jinfo("-flag", "PrintGC");
        output.shouldHaveExitValue(0);
        output.shouldContain("-PrintGC");
        verifyIsDisabled("PrintGC");
    }

    private static void testFlagEqual() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo("-flag", "PrintGC=1");
        output.shouldHaveExitValue(0);
        output = JInfoHelper.jinfo("-flag", "PrintGC");
        output.shouldHaveExitValue(0);
        output.shouldContain("+PrintGC");
        verifyIsEnabled("PrintGC");
    }

    private static void testInvalidFlag() throws Exception {
        OutputAnalyzer output = JInfoHelper.jinfo("-flag", "monkey");
        assertNotEquals(output.getExitValue(), 0, "A non-zero exit code should be returned for invalid flag");
    }

    private static void testSolarisSpecificFlag() throws Exception {
        if (!isSolaris())
            return;

        OutputAnalyzer output = JInfoHelper.jinfo("-flag", "+ExtendedDTraceProbes");
        output.shouldHaveExitValue(0);
        output = JInfoHelper.jinfo();
        output.shouldContain("+ExtendedDTraceProbes");
        verifyIsEnabled("ExtendedDTraceProbes");

        output = JInfoHelper.jinfo("-flag", "-ExtendedDTraceProbes");
        output.shouldHaveExitValue(0);
        output = JInfoHelper.jinfo();
        output.shouldContain("-ExtendedDTraceProbes");
        verifyIsDisabled("ExtendedDTraceProbes");

        output = JInfoHelper.jinfo("-flag", "ExtendedDTraceProbes");
        output.shouldContain("-ExtendedDTraceProbes");
        output.shouldHaveExitValue(0);
    }

    private static void verifyIsEnabled(String flag) {
        HotSpotDiagnosticMXBean hotspotDiagnostic = ManagementFactoryHelper.getDiagnosticMXBean();
        String flagValue = hotspotDiagnostic.getVMOption(flag).getValue();
        assertEquals(flagValue, "true", "Expected '" + flag + "' flag be enabled");
    }

    private static void verifyIsDisabled(String flag) {
        HotSpotDiagnosticMXBean hotspotDiagnostic = ManagementFactoryHelper.getDiagnosticMXBean();
        String flagValue = hotspotDiagnostic.getVMOption(flag).getValue();
        assertEquals(flagValue, "false", "Expected '" + flag + "' flag be disabled");
    }

}
