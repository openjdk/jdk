/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm/timeout=900 TestOptionsWithRanges
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.test.lib.Asserts;
import optionsvalidation.JVMOption;
import optionsvalidation.JVMOptionsUtils;

public class TestOptionsWithRanges {

    private static Map<String, JVMOption> allOptionsAsMap;

    private static void excludeTestMaxRange(String optionName) {
        JVMOption option = allOptionsAsMap.get(optionName);

        if (option != null) {
            option.excludeTestMaxRange();
        }
    }

    private static void excludeTestMinRange(String optionName) {
        JVMOption option = allOptionsAsMap.get(optionName);

        if (option != null) {
            option.excludeTestMinRange();
        }
    }

    private static void excludeTestRange(String optionName) {
        allOptionsAsMap.remove(optionName);
    }

    private static void setAllowedExitCodes(String optionName, Integer... allowedExitCodes) {
        JVMOption option = allOptionsAsMap.get(optionName);

        if (option != null) {
            option.setAllowedExitCodes(allowedExitCodes);
        }
    }

    public static void main(String[] args) throws Exception {
        int failedTests;
        List<JVMOption> allOptions;

        allOptionsAsMap = JVMOptionsUtils.getOptionsWithRangeAsMap();

        /* Shared flags can cause JVM to exit with error code 2 */
        setAllowedExitCodes("SharedReadWriteSize", 2);
        setAllowedExitCodes("SharedReadOnlySize", 2);
        setAllowedExitCodes("SharedMiscDataSize", 2);
        setAllowedExitCodes("SharedMiscCodeSize", 2);

        /*
         * JDK-8145204
         * Temporarily remove testing of max range for ParGCArrayScanChunk because
         * JVM can hang when ParGCArrayScanChunk=4294967296 and ParallelGC is used
         */
        excludeTestMaxRange("ParGCArrayScanChunk");

        /*
         * Remove CICompilerCount from testing because currently it can hang system
         */
        excludeTestMaxRange("CICompilerCount");

        /*
         * JDK-8136766
         * Temporarily remove ThreadStackSize from testing because Windows can set it to 0
         * (for default OS size) but other platforms insist it must be greater than 0
        */
        excludeTestRange("ThreadStackSize");

        /*
         * JDK-8143958
         * Temporarily exclude testing of max range for Shared* flags
         */
        excludeTestMaxRange("SharedReadWriteSize");
        excludeTestMaxRange("SharedReadOnlySize");
        excludeTestMaxRange("SharedMiscDataSize");
        excludeTestMaxRange("SharedMiscCodeSize");

        /*
         * Remove the flag controlling the size of the stack because the
         * flag has direct influence on the physical memory usage of
         * the VM.
         */
        allOptionsAsMap.remove("CompilerThreadStackSize");

        /*
         * Exclude MallocMaxTestWords as it is expected to exit VM at small values (>=0)
         */
        excludeTestMinRange("MallocMaxTestWords");

        /*
         * Exclude below options as their maximum value would consume too much memory
         * and would affect other tests that run in parallel.
         */
        excludeTestMaxRange("ConcGCThreads");
        excludeTestMaxRange("G1ConcRefinementThreads");
        excludeTestMaxRange("G1RSetRegionEntries");
        excludeTestMaxRange("G1RSetSparseRegionEntries");
        excludeTestMaxRange("G1UpdateBufferSize");
        excludeTestMaxRange("InitialBootClassLoaderMetaspaceSize");
        excludeTestMaxRange("InitialHeapSize");
        excludeTestMaxRange("MaxHeapSize");
        excludeTestMaxRange("MaxRAM");
        excludeTestMaxRange("NewSize");
        excludeTestMaxRange("OldSize");
        excludeTestMaxRange("ParallelGCThreads");

        excludeTestMaxRange("VMThreadStackSize");

        /*
         * Remove parameters controlling the code cache. As these
         * parameters have implications on the physical memory
         * reserved by the VM, setting them to large values may hang
         * the system and/or may cause concurrently executed tests to
         * fail. These parameters are rigorously checked when the code
         * cache is initialized (see
         * hotspot/src/shared/vm/code/codeCache.cpp), therefore
         * omitting testing for them does not pose a problem.
         */
        excludeTestMaxRange("InitialCodeCacheSize");
        excludeTestMaxRange("CodeCacheMinimumUseSpace");
        excludeTestMaxRange("ReservedCodeCacheSize");
        excludeTestMaxRange("NonProfiledCodeHeapSize");
        excludeTestMaxRange("ProfiledCodeHeapSize");
        excludeTestMaxRange("NonNMethodCodeHeapSize");
        excludeTestMaxRange("CodeCacheExpansionSize");

        allOptions = new ArrayList<>(allOptionsAsMap.values());

        Asserts.assertGT(allOptions.size(), 0, "Options with ranges not found!");

        System.out.println("Parsed " + allOptions.size() + " options with ranges. Start test!");

        failedTests = JVMOptionsUtils.runCommandLineTests(allOptions);

        Asserts.assertEQ(failedTests, 0,
                String.format("%d tests failed! %s", failedTests, JVMOptionsUtils.getMessageWithFailures()));
    }
}
