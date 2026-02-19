/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageOutputValidator;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

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
public class PkgScriptsTest {

    public static Collection<Object[]> input() {
        List<Object[]> data = new ArrayList<>();
        for (var appStore : List.of(true, false)) {
            for (var scriptRoles : List.of(
                    List.of(PkgInstallScript.PREINSTALL, PkgInstallScript.POSTINSTALL),
                    List.of(PkgInstallScript.PREINSTALL),
                    List.of(PkgInstallScript.POSTINSTALL)
            )) {
                data.add(new Object[] {scriptRoles.toArray(PkgInstallScript[]::new), appStore});
            }
        }
        return data;
    }

    @Test
    @ParameterSupplier("input")
    public void test(PkgInstallScript[] customScriptRoles, boolean appStore) {
        var responseDir = TKit.createTempDirectory("response");

        var customScripts = Stream.of(customScriptRoles).map(role -> {
            return new CustomInstallScript(responseDir, role);
        }).toList();

        var noScriptRoles = noScriptRoles(customScripts);

        new PackageTest()
            .forTypes(PackageType.MAC_PKG)
            .configureHelloApp()
            .addInitializer(cmd -> {
                if (appStore) {
                    cmd.addArgument("--mac-app-store");
                }
                cmd.addArguments("--resource-dir", TKit.createTempDirectory("resources"));
                customScripts.forEach(customScript -> {
                    customScript.createFor(cmd);
                });
                // Verify jpackage logs script resources user can customize.
                noScriptRoles.stream().map(role -> {
                    return JPackageStringBundle.MAIN.cannedFormattedString(
                            "message.no-default-resource",
                            String.format("[%s]", role.resourceCategory()),
                            role.scriptName());
                }).forEach(str -> {
                    new JPackageOutputValidator()
                            .expectMatchingStrings(str)
                            .matchTimestamps()
                            .stripTimestamps()
                            .applyTo(cmd);
                });
            }).addInstallVerifier(cmd -> {
                customScripts.forEach(customScript -> {
                    customScript.verify(cmd);
                });
                if (cmd.isPackageUnpacked()) {
                    noScriptRoles.forEach(role -> {
                        role.verifyExists(cmd, false);
                    });
                }
            }).run();
    }

    enum PkgInstallScript {
        POSTINSTALL,
        PREINSTALL,
        ;

        String scriptName() {
            return name().toLowerCase();
        }

        String resourceCategory() {
            return JPackageStringBundle.MAIN.cannedFormattedString(
                    String.format("resource.pkg-%s-script", scriptName())).getValue();
        }

        Path pathInUnpackedPackage(JPackageCommand cmd) {
            cmd.verifyIsOfType(PackageType.MAC_PKG);
            if (!cmd.isPackageUnpacked()) {
                throw new UnsupportedOperationException();
            }
            // Internal unpacked pkg name will be "PkgScriptsTest-app.pkg" and not a
            // "PkgScriptsTest-1.0.pkg"
            return cmd.pathToUnpackedPackageFile(Path.of("/")).getParent()
                    .resolve("data").resolve(cmd.name() + "-app.pkg")
                    .resolve("Scripts");
        }

        void verifyExists(JPackageCommand cmd, boolean exists) {
            var scriptPath = pathInUnpackedPackage(cmd).resolve(scriptName());
            if (exists) {
                TKit.assertExecutableFileExists(scriptPath);
            } else {
                TKit.assertPathExists(scriptPath, false);
            }
        }
    }

    record CustomInstallScript(Path responseDir, PkgInstallScript role) {
        CustomInstallScript {
            Objects.requireNonNull(responseDir);
            Objects.requireNonNull(role);
        }

        void createFor(JPackageCommand cmd) {
            var responseFile = responseFilePath(cmd);
            TKit.assertPathExists(responseFile, false);
            TKit.createTextFile(Path.of(cmd.getArgumentValue("--resource-dir")).resolve(role.scriptName()), Stream.of(
                    "#!/usr/bin/env sh",
                    String.format("touch \"%s\"", responseFile.toAbsolutePath()),
                    "exit 0"
            ));
        }

        void verify(JPackageCommand cmd) {
            var scriptsEnabled = !MacHelper.isForAppStore(cmd);
            if (cmd.isPackageUnpacked()) {
                role.verifyExists(cmd, scriptsEnabled);
            } else if (scriptsEnabled) {
                TKit.assertFileExists(responseFilePath(cmd));
            } else {
                TKit.assertPathExists(responseFilePath(cmd), false);
            }
        }

        private Path responseFilePath(JPackageCommand cmd) {
            return responseDir.resolve(role.scriptName());
        }
    }

    private static Set<PkgInstallScript> noScriptRoles(Collection<CustomInstallScript> customScripts) {
        var noScriptRoles = new HashSet<>(Set.of(PkgInstallScript.values()));
        customScripts.stream().map(CustomInstallScript::role).forEach(noScriptRoles::remove);
        return noScriptRoles;
    }
}
