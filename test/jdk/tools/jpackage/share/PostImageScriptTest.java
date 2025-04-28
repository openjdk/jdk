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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.ApplicationLayout;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageUserScript;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage with user-supplied post app image script
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror PostImageScriptTest.java
 * @run main/othervm/timeout=720 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=PostImageScriptTest
 */

public class PostImageScriptTest {

    public enum Mode {
        APP,
        RUNTIME,
        EXTERNAL_APP_IMAGE
    }

    public record TestSpec(Mode mode, Optional<Path> installDir, boolean verifyAppImageContents) {

        public TestSpec {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(installDir);
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(mode);
            installDir.ifPresent(dir -> {
                sb.append("; installdir=").append(dir);
            });
            if (verifyAppImageContents) {
                sb.append("; verifyAppImageContents");
            }
            return sb.toString();
        }

        void test() {
            final var appImageCmd = JPackageCommand.helloAppImage()
                    .setFakeRuntime().setArgumentValue("--dest", TKit.createTempDirectory("appimage"));

            appImageCmd.execute();

            final var test = new PackageTest();

            installDir.ifPresent(dir -> {
                test.addInitializer(cmd -> {
                    cmd.addArguments("--install-dir", dir);
                });
            });

            switch (mode) {
                case APP -> {
                    test.configureHelloApp();
                    test.addInitializer(cmd -> {
                        cmd.addArguments("--runtime-image", appImageCmd.appRuntimeDirectory());
                    });
                }
                case RUNTIME -> {
                    test.addInitializer(cmd -> {
                        cmd.removeArgumentWithValue("--input");
                        cmd.addArguments("--runtime-image", appImageCmd.appRuntimeDirectory());
                    });
                }
                case EXTERNAL_APP_IMAGE -> {
                    test.addInitializer(cmd -> {
                        cmd.removeArgumentWithValue("--input");
                        cmd.addArguments("--app-image", appImageCmd.outputBundle());
                    });
                }
            }

            if (verifyAppImageContents) {
                test.addInitializer(cmd -> {
                    cmd.setArgumentValue("--resource-dir", TKit.createTempDirectory("resources"));

                    final Path runtimeDir;
                    if (TKit.isLinux()) {
                        runtimeDir = Path.of("/").relativize(cmd.appRuntimeDirectory());
                    } else if (cmd.isRuntime()) {
                        runtimeDir = ApplicationLayout.javaRuntime().runtimeDirectory();
                    } else {
                        runtimeDir = ApplicationLayout.platformAppImage().runtimeDirectory();
                    }

                    final Path runtimeBinDir = runtimeDir.resolve("bin");

                    if (TKit.isWindows()) {
                        JPackageUserScript.POST_IMAGE.create(cmd, List.of(
                                "var fs = new ActiveXObject('Scripting.FileSystemObject')",
                                "var shell = new ActiveXObject('WScript.Shell')",
                                "WScript.Echo('PWD: ' + fs.GetFolder(shell.CurrentDirectory).Path)",
                                String.format("WScript.Echo('Probe directory: %s')", runtimeBinDir),
                                String.format("fs.GetFolder('%s')", runtimeBinDir.toString().replace('\\', '/'))
                        ));
                    } else {
                        JPackageUserScript.POST_IMAGE.create(cmd, List.of(
                                "set -e",
                                "printf 'PWD: %s\\n' \"$PWD\"",
                                String.format("printf 'Probe directory: %%s\\n' '%s'", runtimeBinDir),
                                String.format("[ -d '%s' ]", runtimeBinDir)
                        ));
                    }
                });
            } else {
                JPackageUserScript.verifyDirectories(test);
            }

            test.run(Action.CREATE);
        }
    }

    public static Collection<Object[]> test() {
        final List<TestSpec> data = new ArrayList<>();
        for (final var mode : Mode.values()) {
            for (final var verifyAppImageContents : List.of(true, false)) {
                data.add(new TestSpec(mode, Optional.empty(), verifyAppImageContents));
            }
        }

        if (TKit.isLinux()) {
            for (final var mode : Mode.values()) {
                data.add(new TestSpec(mode, Optional.of(Path.of("/usr")), true));
            }
        }

        return data.stream().map(spec -> {
            return new Object[] {spec};
        }).toList();
    }

    @Test
    @ParameterSupplier
    public static void test(TestSpec spec) {
        spec.test();
    }
}
