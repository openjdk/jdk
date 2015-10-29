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
 */
package codeheapsize;

import jdk.test.lib.Platform;
import common.CodeCacheCLITestBase;
import common.CodeCacheCLITestCase;
import sun.hotspot.code.BlobType;
import java.util.EnumSet;
/**
 * @test
 * @bug 8015774
 * @summary Verify processing of options related to code heaps sizing.
 * @library /testlibrary .. /test/lib
 * @modules java.base/sun.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build TestCodeHeapSizeOptions jdk.test.lib.* codeheapsize.*
 *        common.*
 * @run main/timeout=240 codeheapsize.TestCodeHeapSizeOptions
 */
public class TestCodeHeapSizeOptions extends CodeCacheCLITestBase {
    private static final CodeCacheCLITestCase JVM_STARTUP
            = new CodeCacheCLITestCase(new CodeCacheCLITestCase.Description(
                            options -> options.segmented,
                            EnumSet.noneOf(BlobType.class)),
                    new JVMStartupRunner());

    private static final CodeCacheCLITestCase CODE_CACHE_FREE_SPACE
            = new CodeCacheCLITestCase(new CodeCacheCLITestCase.Description(
                            options -> options.segmented
                                    && Platform.isDebugBuild(),
                            EnumSet.noneOf(BlobType.class)),
                    new CodeCacheFreeSpaceRunner());

    private static final GenericCodeHeapSizeRunner GENERIC_RUNNER
            = new GenericCodeHeapSizeRunner();

    private TestCodeHeapSizeOptions() {
        super(CodeCacheCLITestBase.OPTIONS_SET,
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.INT_MODE.description,
                        GENERIC_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.NON_TIERED.description,
                        GENERIC_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.TIERED_LEVEL_0.description,
                        GENERIC_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.TIERED_LEVEL_1.description,
                        GENERIC_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.TIERED_LEVEL_4.description,
                        GENERIC_RUNNER),
                JVM_STARTUP,
                CODE_CACHE_FREE_SPACE);
    }

    public static void main(String args[]) throws Throwable {
        new TestCodeHeapSizeOptions().runTestCases();
    }
}
