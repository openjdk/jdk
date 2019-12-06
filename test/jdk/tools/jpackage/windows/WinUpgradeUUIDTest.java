/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;

/**
 * Test both --win-upgrade-uuid and --app-version parameters. Output of the test
 * should be WinUpgradeUUIDTest-1.0.exe and WinUpgradeUUIDTest-2.0.exe
 * installers. Both output installers should provide the same functionality as
 * the default installer (see description of the default installer in
 * SimplePackageTest.java) but have the same product code and different
 * versions. Running WinUpgradeUUIDTest-2.0.exe installer should automatically
 * uninstall older version of the test application previously installed with
 * WinUpgradeUUIDTest-1.0.exe installer.
 */

/*
 * @test
 * @summary jpackage with --win-upgrade-uuid and --app-version
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @modules jdk.incubator.jpackage/jdk.incubator.jpackage.internal
 * @run main/othervm/timeout=360 -Xmx512m WinUpgradeUUIDTest
 */

public class WinUpgradeUUIDTest {
    public static void main(String[] args) {
        TKit.run(args, () -> {
            PackageTest test = init();
            if (test.getAction() != PackageTest.Action.VERIFY_INSTALL) {
                test.run();
            }

            test = init();
            test.addInitializer(cmd -> {
                cmd.setArgumentValue("--app-version", "2.0");
                cmd.setArgumentValue("--arguments", "bar");
            });
            test.run();
        });
    }

    private static PackageTest init() {
        return new PackageTest()
            .forTypes(PackageType.WINDOWS)
            .configureHelloApp()
            .addInitializer(cmd -> cmd.addArguments("--win-upgrade-uuid",
                    "F0B18E75-52AD-41A2-BC86-6BE4FCD50BEB"));
    }
}
