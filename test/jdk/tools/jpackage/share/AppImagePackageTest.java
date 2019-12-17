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

import java.nio.file.Path;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;

/**
 * Test --app-image parameter. The output installer should provide the same
 * functionality as the default installer (see description of the default
 * installer in SimplePackageTest.java)
 */

/*
 * @test
 * @summary jpackage with --app-image
 * @key jpackagePlatformPackage
 * @library ../helpers
 * @requires (jpackage.test.SQETest == null)
 * @build jdk.jpackage.test.*
 * @modules jdk.incubator.jpackage/jdk.incubator.jpackage.internal
 * @compile AppImagePackageTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppImagePackageTest
 */
public class AppImagePackageTest {

    @Test
    public static void test() {
        Path appimageOutput = TKit.workDir().resolve("appimage");

        JPackageCommand appImageCmd = JPackageCommand.helloAppImage()
                .setArgumentValue("--dest", appimageOutput);

        new PackageTest()
        .addRunOnceInitializer(() -> appImageCmd.execute())
        .addInitializer(cmd -> {
            cmd.addArguments("--app-image", appImageCmd.outputBundle());
            cmd.removeArgumentWithValue("--input");
        }).addBundleDesktopIntegrationVerifier(false).run();
    }
}
