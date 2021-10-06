/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.nio.file.Files;
import jdk.jpackage.internal.ApplicationLayout;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Parameters;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


/**
 * Tests generation of packages with input folder containing empty folders.
 */

/*
 * @test
 * @summary jpackage with --app-content option
 * @library ../helpers
 * @library /test/lib
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build AppContentTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppContentTest
 */
public class AppContentTest {

    private static final String TEST_JAVA = TKit.TEST_SRC_ROOT.resolve(
            "apps/PrintEnv.java").toString();
    private static final String TEST_DUKE = TKit.TEST_SRC_ROOT.resolve(
            "apps/dukeplug.png").toString();
    private static final String TEST_DIR = TKit.TEST_SRC_ROOT.resolve(
            "apps").toString();
    private static final String TEST_BAD = TKit.TEST_SRC_ROOT.resolve(
            "non-existant").toString();

    private final List<String> testPathArgs;

    @Parameters
    public static Collection data() {
        return List.of(new String[][]{
            {TEST_JAVA, TEST_DUKE}, // include two files in two options
            {TEST_JAVA, TEST_BAD},  // try to include non-existant content
            {TEST_JAVA + "," + TEST_DUKE, TEST_DIR}, // two files in one option,
                                            // and a dir tree in another option.
        });
    }

    public AppContentTest(String... testPathArgs) {
        this.testPathArgs = List.of(testPathArgs);
    }

    @Test
    public void test() throws Exception {

        new PackageTest().configureHelloApp()
            .addInitializer(cmd -> {
                for (String arg : testPathArgs) {
                    cmd.addArguments("--app-content", arg);
                }
            })
            .addInstallVerifier(cmd -> {
                ApplicationLayout appLayout = cmd.appLayout();
                Path contentDir = appLayout.contentDirectory();
                for (String arg : testPathArgs) {
                    List<String> paths = Arrays.asList(arg.split(","));
                    for (String p : paths) {
                        Path name = Path.of(p).getFileName();
                        TKit.assertPathExists(contentDir.resolve(name), true);
                    }
                }

            })
            .setExpectedExitCode(testPathArgs.contains(TEST_BAD) ? 1 : 0)
            .run();
        }
}
