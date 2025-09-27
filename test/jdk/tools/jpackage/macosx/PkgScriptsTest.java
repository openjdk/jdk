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
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=PkgScriptsTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;

public class PkgScriptsTest {

    @Test
    @Parameter({"TRUE", "TRUE"})
    @Parameter({"TRUE", "FALSE"})
    @Parameter({"FALSE", "TRUE"})
    @Parameter({"FALSE", "FALSE"})
    public void test(boolean preinstall, boolean postinstall) {
        final Path resources;
        if (preinstall || postinstall) {
            resources = TKit.createTempDirectory("resources");
            if (preinstall) {
                createScript(resources, "preinstall");
            }
            if (postinstall) {
                createScript(resources, "postinstall");
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

    private static void createScript(Path resourcesDir, String name) {
        List<String> script = Stream.of("#!/usr/bin/env sh",
                                        String.format("echo \"%s\"", name),
                                        "exit 0").toList();
        try {
            Files.write(resourcesDir.resolve(name), script);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static boolean isScriptExistsInResourceDir(JPackageCommand cmd, String name) {
        return Optional.ofNullable(cmd.getArgumentValue("--resource-dir"))
                .map(resourceDir -> {
                    return Files.exists(Path.of(resourceDir).resolve(name));
                }).orElseGet(() -> {
                    return false;
                });
    }

    private static void validateScript(boolean exists, Path scriptPath) {
        if (exists) {
            TKit.assertFileExists(scriptPath);
            String output = Executor.of(scriptPath.toString())
                    .executeAndGetFirstLineOfOutput();
            TKit.assertNotEquals(scriptPath.getFileName(), output,
                    "Check script output");
        } else {
            TKit.assertPathExists(scriptPath, false);
        }
    }

    private static void verifyPKG(JPackageCommand cmd) {
        if (cmd.isPackageUnpacked()) {
            final var preinstall = isScriptExistsInResourceDir(cmd, "preinstall");
            final var postinstall = isScriptExistsInResourceDir(cmd, "postinstall");

            Path dataDir = cmd.pathToUnpackedPackageFile(Path.of("/"))
                    .toAbsolutePath()
                    .getParent()
                    .resolve("data");
            try (var dataListing = Files.list(dataDir)) {
                dataListing.filter(file -> {
                    return ".pkg".equals(PathUtils.getSuffix(file.getFileName()));
                }).forEach(ThrowingConsumer.toConsumer(pkgDir -> {
                    Path preinstallPath =
                            pkgDir.resolve("Scripts").resolve("preinstall");
                    Path postinstallPath =
                            pkgDir.resolve("Scripts").resolve("postinstall");
                    validateScript(preinstall, preinstallPath);
                    validateScript(postinstall, postinstallPath);
                }));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
