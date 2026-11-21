/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.WindowsHelper.getWixTypeFromVerboseJPackageOutput;

import jdk.internal.util.Architecture;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand.MessageCategory;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.WindowsHelper;
import jdk.jpackage.test.WindowsHelper.WixType;

/**
 * Test the platform of an MSI package matches the host architecture.
 * With WiX v3, jpackage builds x64 packages on all 64-bit hosts.
 */

/*
 * @test
 * @summary jpackage test to validate the platform of MSI packages
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @compile -Xlint:all -Werror WinMsiPlatformTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinMsiPlatformTest
 */
public class WinMsiPlatformTest {

    @Test
    public static void test() {
        new PackageTest()
        .forTypes(PackageType.WIN_MSI)
        .configureHelloApp()
        .addInitializer(cmd -> {
            // Need summary to pick WiX version.
            cmd.setFakeRuntime().saveConsoleOutput(true).enableMessageCategories(MessageCategory.SUMMARY);
        })
        .addBundleVerifier((cmd, result) -> {
            final String expectedPlatform;
            if (Architecture.isAARCH64() && getWixTypeFromVerboseJPackageOutput(result) == WixType.WIX4) {
                expectedPlatform = "Arm64";
            } else {
                expectedPlatform = "x64";
            }
            TKit.assertEquals(expectedPlatform, WindowsHelper.getMsiPlatform(cmd), "Check MSI platform");
        })
        .run(Action.CREATE);
    }
}
