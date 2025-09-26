/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

 /*
 * @test
 * @summary jpackage with --type pkg --resource-dir Scripts
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build PkgScriptsTest
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=PkgScriptsTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;

public class PkgScriptsTest {

    @Test
    @Parameter("TRUE")
    @Parameter("FALSE")
    public void test(boolean provideScripts) {
        final Path resources;
        if (provideScripts) {
            resources = TKit.createTempDirectory("resources");
            try {
                Files.createFile(resources.resolve("preinstall"));
                Files.createFile(resources.resolve("postinstall"));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } else {
            resources = null;
        }

        new PackageTest()
                .forTypes(PackageType.MAC_PKG)
                .configureHelloApp()
                .addInitializer(cmd -> {
                    if (resources != null) {
                        cmd.addArguments("--resource-dir", resources.toString());
                    }
                })
                .addInstallVerifier(PkgScriptsTest::verifyPKG)
                .setExpectedExitCode(0)
                .run(PackageTest.Action.CREATE_AND_UNPACK);
    }

    private static void verifyPKG(JPackageCommand cmd) {
        if (cmd.isPackageUnpacked()) {
            final boolean provideScripts = cmd.hasArgument("--resource-dir");
            Path dataDir = cmd.pathToUnpackedPackageFile(Path.of("/"))
                    .toAbsolutePath()
                    .getParent()
                    .resolve("data");
            try (var dataListing = Files.list(dataDir)) {
                dataListing.filter(file -> {
                    return ".pkg".equals(PathUtils.getSuffix(file.getFileName()));
                }).forEach(ThrowingConsumer.toConsumer(pkgDir -> {
                    Path preinstall = pkgDir.resolve("Scripts").resolve("preinstall");
                    Path postinstall = pkgDir.resolve("Scripts").resolve("postinstall");
                    if (provideScripts) {
                        TKit.assertFileExists(preinstall);
                        TKit.assertFileExists(postinstall);
                    } else {
                        TKit.assertPathExists(preinstall, false);
                        TKit.assertPathExists(postinstall, false);
                    }
                }));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
