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
package printcodecache;

import common.CodeCacheCLITestBase;
import common.CodeCacheCLITestCase;
import sun.hotspot.code.BlobType;
import java.util.EnumSet;
/**
 * @test
 * @bug 8015774
 * @summary Verify that PrintCodeCache option print correct information.
 * @library /testlibrary .. /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build TestPrintCodeCacheOption jdk.test.lib.*
 *        printcodecache.* common.*
 * @run main/timeout=240 printcodecache.TestPrintCodeCacheOption
 */
public class TestPrintCodeCacheOption extends CodeCacheCLITestBase {
    private static final CodeCacheCLITestCase DISABLED_PRINT_CODE_CACHE
            = new CodeCacheCLITestCase(new CodeCacheCLITestCase.Description(
                            options -> true, EnumSet.noneOf(BlobType.class)),
                    new PrintCodeCacheRunner(false));

    private static final CodeCacheCLITestCase.Runner DEFAULT_RUNNER
            = new PrintCodeCacheRunner();

    private TestPrintCodeCacheOption() {
        super(CodeCacheCLITestBase.OPTIONS_SET,
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.INT_MODE.description,
                        DEFAULT_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.NON_SEGMENTED.description,
                        DEFAULT_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.NON_TIERED.description,
                        DEFAULT_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.TIERED_LEVEL_0.description,
                        DEFAULT_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.TIERED_LEVEL_1.description,
                        DEFAULT_RUNNER),
                new CodeCacheCLITestCase(CodeCacheCLITestCase
                        .CommonDescriptions.TIERED_LEVEL_4.description,
                        DEFAULT_RUNNER),
                DISABLED_PRINT_CODE_CACHE);
    }

    public static void main(String args[]) throws Throwable {
        new TestPrintCodeCacheOption().runTestCases();
    }
}
