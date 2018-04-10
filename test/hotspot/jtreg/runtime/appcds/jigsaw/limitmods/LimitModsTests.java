/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires vm.cds & !vm.graal.enabled
 * @library ../..
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules jdk.jartool/sun.tools.jar
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @compile LimitModsHelper.java
 * @compile ../../test-classes/java/net/HttpCookie.jasm
 * @compile ../../test-classes/jdk/dynalink/DynamicLinker.jasm
 * @compile ../../test-classes/com/sun/tools/javac/Main.jasm
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main LimitModsTests
 * @summary AppCDS tests for excluding class in module by using --limit-modules.
 */

/**
 * This is for testing the --limit-modules option with AppCDS.
 * This test assumes the following defining class loader, module, class relations:
 * class loader    module            class
 * -----------------------------------------------------
 * boot            java.base         java/net/HttpCookie
 * platform        jdk.dynalink      jdk/dynalink/DynamicLinker
 * app             jdk.compiler      com/sun/tools/javac/Main
 *
 * This test dumps the above 3 classes into a shared archive.
 * Then it will run the following 4 -limit-modules scenarios:
 * 1. without --limit-modules
 *    All 3 classes should be loaded successfully.
 *    All 3 classes should be loaded by the appropriate class loader.
 *    All 3 classes should be found in the shared archive.
 * 2. --limit-modules java.base,jdk.dynalink
 *    The loading of the com/sun/tools/javac/Main class should fail.
 *    The other 2 classes should be loaded successfully and by the appropriate class loader.
 *    The other 2 classes should be found in the shared archive.
 * 3. --limit-modules java.base,jdk.compiler
 *    The loading of the jdk/nio/dynalink/DynamicLinker class should fail.
 *    The other 2 classes should be loaded successfully and by the appropriate class loader.
 *    The other 2 classes should be found in the shared archive.
 * 4. --limit-modules jdk.dynalink,jdk.compiler
 *    The java.base module can't be excluded.
 *    The results for this case is the same as for case #1.
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;


public class LimitModsTests {

    // the module that is limited
    private static final String[] LIMIT_MODULES = {"java.base", "jdk.dynalink", "jdk.compiler"};

    // test classes to archive.
    private static final String BOOT_ARCHIVE_CLASS = "java/net/HttpCookie";
    private static final String PLATFORM_ARCHIVE_CLASS = "jdk/dynalink/DynamicLinker";
    private static final String APP_ARCHIVE_CLASS = "com/sun/tools/javac/Main";
    private static final String[] ARCHIVE_CLASSES = {
        BOOT_ARCHIVE_CLASS, PLATFORM_ARCHIVE_CLASS, APP_ARCHIVE_CLASS};
    private String bootClassPath = null;
    private String whiteBoxJar = null;
    private String helperJar = null;
    private String appJar = null;
    private OutputAnalyzer output = null;

    public static void main(String[] args) throws Exception {
        LimitModsTests tests = new LimitModsTests();
        tests.dumpArchive();
        tests.runTestNoLimitMods();
        tests.runTestLimitMods();
    }

    void dumpArchive() throws Exception {
        JarBuilder.build("limitModsTest", BOOT_ARCHIVE_CLASS, PLATFORM_ARCHIVE_CLASS, APP_ARCHIVE_CLASS);
        JarBuilder.build(true, "WhiteBox", "sun/hotspot/WhiteBox");
        JarBuilder.build("limitModsHelper", "LimitModsHelper");

        appJar = TestCommon.getTestJar("limitModsTest.jar");
        whiteBoxJar = TestCommon.getTestJar("WhiteBox.jar");
        helperJar = TestCommon.getTestJar("limitModsHelper.jar");
        bootClassPath = "-Xbootclasspath/a:" + whiteBoxJar;
        // Dump the test classes into the archive
        OutputAnalyzer output1  = TestCommon.dump(appJar, TestCommon.list(ARCHIVE_CLASSES), bootClassPath);
        TestCommon.checkDump(output1);
        // Make sure all the classes where successfully archived.
        for (String archiveClass : ARCHIVE_CLASSES) {
            output1.shouldNotContain("Preload Warning: Cannot find " + archiveClass);
        }
    }

    // run the test without --limit-modules
    public void runTestNoLimitMods() throws Exception {
        output = TestCommon.exec(
            appJar + File.pathSeparator + helperJar,
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI", bootClassPath,
            "LimitModsHelper",
            BOOT_ARCHIVE_CLASS, PLATFORM_ARCHIVE_CLASS, APP_ARCHIVE_CLASS, "-1"); // last 4 args passed to test
        TestCommon.checkExec(output);
    }

    // run the test with --limit-modules
    //
    // --limit-modules jdk.dynalink,jdk.compiler
    // It seems we can't exclude the java.base module. For this case,
    // although the java.base module isn't in --limit-modules, the class
    // in the java.base module (java.net.HttpCookie) can also be found.
    //
    // --limit-modules java.base,jdk.dynalink
    // --limit-modules java.base,jdk.compiler
    public void runTestLimitMods() throws Exception {
        String limitMods = null;
        for (int excludeModIdx = 0; excludeModIdx < 3; excludeModIdx++) {
            for (int includeModIdx = 0; includeModIdx < 3; includeModIdx++) {
                if (includeModIdx != excludeModIdx) {
                    if (limitMods != null) {
                        limitMods += ",";
                        limitMods += LIMIT_MODULES[includeModIdx];
                    } else {
                        limitMods = LIMIT_MODULES[includeModIdx];
                    }
                }
            }
            TestCommon.run(
                "-cp", appJar + File.pathSeparator + helperJar,
                "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI", bootClassPath,
                "--limit-modules", limitMods,
                "LimitModsHelper",
                BOOT_ARCHIVE_CLASS, PLATFORM_ARCHIVE_CLASS, APP_ARCHIVE_CLASS,
                Integer.toString(excludeModIdx)) // last 4 args passed to test
                .assertSilentlyDisabledCDS(0);
            limitMods = null;
        }
    }
}
