/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.test.LauncherAsServiceVerifier;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.PackageType;

/**
 * Launcher as service packaging test. Output of the test should be
 * servicetest*.* updateservicetest*.* and package bundles.
 */

/*
 * @test
 * @summary Launcher as service packaging test
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile ServiceTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ServiceTest
 */
public class ServiceTest {

    @Test
    public static void test() {
        var test = new PackageTest().addHelloAppInitializer(null);
        new LauncherAsServiceVerifier("A1").applyTo(test);
        test.run();
    }

    @Test
    public static void testUpdate() {
        final String upgradeCode = "4050AD4D-D6CC-452A-9CB0-58E5FA8C410F";

        var pkg = new PackageTest()
                .addHelloAppInitializer(null)
                .disablePackageUninstaller()
                .forTypes(PackageType.WINDOWS)
                .addInitializer(cmd -> {
                    cmd.addArguments("--win-upgrade-uuid", upgradeCode);
                });
        new LauncherAsServiceVerifier("Default").applyTo(pkg);

        var pkg2 = new PackageTest()
                .addHelloAppInitializer(null)
                .addInitializer(cmd -> {
                    cmd.addArguments("--app-version", "2.0");
                })
                .forTypes(PackageType.WINDOWS)
                .addInitializer(cmd -> {
                    cmd.addArguments("--win-upgrade-uuid", upgradeCode);
                });

        new LauncherAsServiceVerifier("foo", "foo-launcher-as-service.txt",
                "Foo").applyTo(pkg);
        new LauncherAsServiceVerifier("foo", "foo-launcher-as-service.txt",
                "Foo2").applyTo(pkg2);
        new LauncherAsServiceVerifier("bar", "bar-launcher-as-service.txt",
                "Bar").applyTo(pkg2);

        new PackageTest.Group(pkg, pkg2).run();
    }
}
