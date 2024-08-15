/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @summary Disable CDS when incompatible options related to AOTClassLinking are used
 * @library /test/jdk/lib/testlibrary
 *          /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar Hello
 * @run driver AOTClassLinkingVMOptions
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class AOTClassLinkingVMOptions {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");

    static int testCaseNum = 0;
    static void testCase(String s) {
        testCaseNum++;
        System.out.println("Test case " + testCaseNum + ": " + s);
    }

    public static void main(String[] args) throws Exception {
        TestCommon.testDump(appJar, TestCommon.list("Hello"),
                            "-XX:+AOTClassLinking");

        testCase("Archived full module graph must be enabled at runtime");
        TestCommon.run("-cp", appJar, "-Djdk.module.validation=1", "Hello")
            .assertAbnormalExit("CDS archive has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used");

        // NOTE: tests for ClassFileLoadHook + AOTClassLinking is in
        // ../jvmti/ClassFileLoadHookTest.java
    }
}
