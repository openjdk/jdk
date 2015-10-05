/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test VM Options with ranges
 * @library /testlibrary /runtime/CommandLine/OptionsValidation/common
 * @modules java.base/sun.misc
 *          java.management
 *          jdk.attach
 *          jdk.management/sun.tools.attach
 * @run main/othervm/timeout=600 TestOptionsWithRanges
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.test.lib.Asserts;
import optionsvalidation.JVMOption;
import optionsvalidation.JVMOptionsUtils;

public class TestOptionsWithRanges {

    public static void main(String[] args) throws Exception {
        int failedTests;
        Map<String, JVMOption> allOptionsAsMap = JVMOptionsUtils.getOptionsWithRangeAsMap();
        List<JVMOption> allOptions;

        /*
         * Remove CICompilerCount from testing because currently it can hang system
         */
        allOptionsAsMap.remove("CICompilerCount");

        /*
         * Exclude below options as their maximum value would consume too much memory
         * and would affect other tests that run in parallel.
         */
        allOptionsAsMap.remove("G1ConcRefinementThreads");
        allOptionsAsMap.remove("G1RSetRegionEntries");
        allOptionsAsMap.remove("G1RSetSparseRegionEntries");

        allOptions = new ArrayList<>(allOptionsAsMap.values());

        Asserts.assertGT(allOptions.size(), 0, "Options with ranges not found!");

        System.out.println("Parsed " + allOptions.size() + " options with ranges. Start test!");

        failedTests = JVMOptionsUtils.runCommandLineTests(allOptions);

        Asserts.assertEQ(failedTests, 0,
                String.format("%d tests failed! %s", failedTests, JVMOptionsUtils.getMessageWithFailures()));
    }
}
