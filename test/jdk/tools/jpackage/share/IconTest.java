/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import jdk.incubator.jpackage.internal.IOUtils;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Functional;
import jdk.jpackage.test.Annotations.*;
import jdk.jpackage.test.JPackageCommand;

/*
 * @test
 * @summary jpackage create image with custom icon
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.incubator.jpackage/jdk.incubator.jpackage.internal
 * @compile IconTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=IconTest
 */

public class IconTest {
    @Test
    public static void testResourceDir() throws IOException {
        TKit.withTempDirectory("resources", tempDir -> {
            JPackageCommand cmd = JPackageCommand.helloAppImage()
                    .addArguments("--resource-dir", tempDir);

            Files.copy(GOLDEN_ICON, tempDir.resolve(appIconFileName(cmd)),
                    StandardCopyOption.REPLACE_EXISTING);

            testIt(cmd);
        });
    }

    @Test
    @Parameter("true")
    @Parameter("false")
    public static void testParameter(boolean relativePath) throws IOException {
        final Path iconPath;
        if (relativePath) {
            iconPath = TKit.createRelativePathCopy(GOLDEN_ICON);
        } else {
            iconPath = GOLDEN_ICON;
        }

        testIt(JPackageCommand.helloAppImage().addArguments("--icon", iconPath));
    }

    private static String appIconFileName(JPackageCommand cmd) {
        return IOUtils.replaceSuffix(cmd.appLauncherPath().getFileName(),
                TKit.ICON_SUFFIX).toString();
    }

    private static void testIt(JPackageCommand cmd) throws IOException {
        cmd.executeAndAssertHelloAppImageCreated();

        Path iconPath = cmd.appLayout().destktopIntegrationDirectory().resolve(
                appIconFileName(cmd));

        TKit.assertFileExists(iconPath);
        TKit.assertTrue(-1 == Files.mismatch(GOLDEN_ICON, iconPath),
                String.format(
                        "Check application icon file [%s] is a copy of source icon file [%s]",
                        iconPath, GOLDEN_ICON));
    }

    private final static Path GOLDEN_ICON = TKit.TEST_SRC_ROOT.resolve(Path.of(
            "resources", "icon" + TKit.ICON_SUFFIX));
}
