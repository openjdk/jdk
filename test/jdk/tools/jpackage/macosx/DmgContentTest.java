/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.TKit;

import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;

import java.util.Collection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @summary jpackage with --type dmg --mac-dmg-content
 * @library ../helpers
 * @library /test/lib
 * @library base
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build DmgContentTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=DmgContentTest
 */
public class DmgContentTest {

    private static final String TEST_JAVA = TKit.TEST_SRC_ROOT.resolve(
            "apps/PrintEnv.java").toString();
    private static final String TEST_DUKE = TKit.TEST_SRC_ROOT.resolve(
            "apps/dukeplug.png").toString();
    private static final String TEST_DIR = TKit.TEST_SRC_ROOT.resolve(
            "apps").toString();
    private static final String TEST_BAD = TKit.TEST_SRC_ROOT.resolve(
            "non-existant").toString();

    @Parameters
    public static Collection input() {
        List<Object[]> data = new ArrayList<>();
        data.addAll(List.of(new Object[][] {
            {"0", PackageType.MAC_DMG, new String[] {TEST_JAVA, TEST_DUKE}},
            {"1", PackageType.MAC_PKG, new String[] {TEST_JAVA, TEST_DUKE}},
            {"1", PackageType.MAC_DMG, new String[] {TEST_JAVA, TEST_BAD}},
            {"0", PackageType.MAC_DMG,
                  new String[] {TEST_JAVA + "," + TEST_DUKE, TEST_DIR}}}));
       return data;
    }

    public DmgContentTest(String expected, PackageType type, String[] content) {
        this.expected = Integer.parseInt(expected);
        this.type = type;
        this.content = content;
    }

    @Test
    public void test() {
        new PackageTest()
                .forTypes(type)
                .configureHelloApp()
                .addInitializer(cmd -> {
                    for (String arg : content) {
                        cmd.addArguments("--mac-dmg-content", arg);
                    }
                })
                .addInstallVerifier(DmgContentTest::verifyDMG)
                .setExpectedExitCode(expected)
                .run(PackageTest.Action.CREATE_AND_UNPACK);
    }

    private static void verifyDMG(JPackageCommand cmd) {
        if (cmd.isPackageUnpacked()) {
            Path installDir = cmd.appInstallationDirectory();
            Path dmgRoot = cmd.pathToUnpackedPackageFile(installDir)
                      .toAbsolutePath().getParent();
            TKit.assertFileExists(dmgRoot.resolve("PrintEnv.java"));
        }
    }

    private final int expected;
    private final PackageType type;
    private final String[] content;

}
