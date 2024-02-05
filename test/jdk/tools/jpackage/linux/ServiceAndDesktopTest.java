/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.LauncherAsServiceVerifier;
import jdk.jpackage.test.TKit;

/**
 * Test how services and desktop integration align together in the same package.
 * On Linux these features share common code in custom actions (common_utils.sh).
 * Test correctness of integration of this code.
 *
 * The test is not intended to be executed by SQE. It is for internal use only
 */

/*
 * @test
 * @summary jpackage with desktop integration and services on Linux
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @requires jpackage.test.SQETest == null
 * @build jdk.jpackage.test.*
 * @requires (os.family == "linux")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile ServiceAndDesktopTest.java
 * @run main/othervm/timeout=720 jdk.jpackage.test.Main
 *  --jpt-run=ServiceAndDesktopTest
 */

public class ServiceAndDesktopTest {

    @Test
    public static void test() {
        var pkg = new PackageTest()
                .configureHelloApp()
                .addBundleDesktopIntegrationVerifier(true)
                .addInitializer(cmd -> {
                    // Want a .desktop file for the main launcher
                    cmd.addArguments("--icon", GOLDEN_ICON.toString());
                });
        LauncherAsServiceVerifier.build().setLauncherName("foo").
                setExpectedValue("Fun").setAdditionalLauncherCallback(al -> {
                    // Don't want .desktop file for service launcher
                    al.setNoIcon();
                }).applyTo(pkg);
        pkg.run();
    }

    private final static Path GOLDEN_ICON = TKit.TEST_SRC_ROOT.resolve(Path.of(
            "resources", "icon" + TKit.ICON_SUFFIX));
}
