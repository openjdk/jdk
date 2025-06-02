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

import static jdk.internal.util.OperatingSystem.LINUX;
import static jdk.internal.util.OperatingSystem.MACOS;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.ApplicationLayout;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageUserScript;
import jdk.jpackage.test.JPackageUserScript.WinGlobals;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
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

    public record TestSpec(Mode mode, boolean verifyAppImageContents) {

        public TestSpec {
            Objects.requireNonNull(mode);
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(mode);
            if (verifyAppImageContents) {
                sb.append("; verifyAppImageContents");
            }
            return sb.toString();
        }

        static PackageTest createTest(Mode mode, PackageType... types) {
            if (types.length > 0 && Stream.of(types).allMatch(Predicate.not(PackageType::isEnabled))) {
                throw TKit.throwSkippedException(String.format("All native packagers from %s list are disabled", List.of(types)));
            }

            final var test = new PackageTest().forTypes(types);

            final var appImageCmd = JPackageCommand.helloAppImage()
                    .setFakeRuntime().setArgumentValue("--dest", TKit.createTempDirectory("appimage"));

            appImageCmd.execute();

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

            test.addInitializer(cmd -> {
                cmd.setArgumentValue("--resource-dir", TKit.createTempDirectory("resources"));
            });

            return test;
        }

        PackageTest createTest() {
            return createTest(mode);
        }

        PackageTest initTest() {
            return initTest(createTest());
        }

        PackageTest initTest(PackageTest test) {
            if (verifyAppImageContents) {
                test.addInitializer(cmd -> {
                    final Path runtimeDir;
                    if (TKit.isLinux()) {
                        runtimeDir = Path.of("/").relativize(cmd.appRuntimeDirectory());
                    } else if (!cmd.isRuntime()) {
                        runtimeDir = ApplicationLayout.platformAppImage().runtimeHomeDirectory();
                    } else if (TKit.isOSX()) {
                        runtimeDir = Path.of("Contents/Home");
                    } else {
                        runtimeDir = Path.of("");
                    }

                    final Path runtimeBinDir = runtimeDir.resolve("bin");

                    if (TKit.isWindows()) {
                        final List<String> script = new ArrayList<>();
                        script.addAll(WinGlobals.JS_SHELL.expr());
                        script.addAll(WinGlobals.JS_FS.expr());
                        script.addAll(List.of(
                                "WScript.Echo('PWD: ' + fs.GetFolder(shell.CurrentDirectory).Path)",
                                String.format("WScript.Echo('Probe directory: %s')", runtimeBinDir),
                                String.format("fs.GetFolder('%s')", runtimeBinDir.toString().replace('\\', '/'))
                        ));
                        JPackageUserScript.POST_IMAGE.create(cmd, script);
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
                JPackageUserScript.verifyPackagingDirectories(test);
            }

            return test;
        }
    }

    @Test
    @ParameterSupplier(value="createVerifyAppImageContentsTestSpecs")
    @ParameterSupplier(value="createVerifyNoNewFilesInDirectoriesTestSpecs")
    public static void test(TestSpec spec) {
        spec.initTest().run(Action.CREATE);
    }

    public static Collection<Object[]> createVerifyAppImageContentsTestSpecs() {
        return createModeTestSpecs(true);
    }

    public static Collection<Object[]> createVerifyNoNewFilesInDirectoriesTestSpecs() {
        return createModeTestSpecs(false);
    }

    @Test(ifOS = LINUX)
    @ParameterSupplier(value="createVerifyAppImageContentsTestSpecs")
    public static void testWithInstallDir(TestSpec spec) {
        spec.initTest(spec.createTest().addInitializer(cmd -> {
            cmd.addArguments("--install-dir", "/usr");
        })).run(Action.CREATE);
    }

    @Test(ifOS = MACOS)
    @Parameter("APP")
    public static void testWithServices(Mode mode) {
        final var test = TestSpec.createTest(mode, PackageType.MAC_PKG).addInitializer(cmd -> {
            cmd.addArgument("--launcher-as-service");
        });

        JPackageUserScript.verifyPackagingDirectories()
                .withUnchangedDirectory("../services")
                .withUnchangedDirectory("../support")
                .withNonexistantPath("../packages")
                .apply(test).run(Action.CREATE);
    }

    @Test
    public static void testEnvVars() {
        final Map<PackageType, JPackageUserScript.EnvVarVerifier> verifiers = new HashMap<>();

        final var imageDirOutputPrefix = "image-dir=";

        TestSpec.createTest(Mode.APP).addInitializer(cmd -> {
            final var verifier = JPackageUserScript.verifyEnvVariables().envVar("JpAppImageDir").create();
            verifiers.put(cmd.packageType(), verifier);

            final List<String> script = new ArrayList<>();
            script.addAll(verifier.createScript());
            if (TKit.isWindows()) {
                script.add("WScript.Echo('" + imageDirOutputPrefix + "' + fs.GetFolder(shell.CurrentDirectory).Path)");
            } else {
                script.add("printf '" + imageDirOutputPrefix + "%s\\n' \"$PWD\"");
            }

            JPackageUserScript.POST_IMAGE.create(cmd, script);

            cmd.saveConsoleOutput(true);

        }).addBundleVerifier((cmd, result) -> {
            final var imageDir = result.stdout().getOutput().stream().map(String::stripLeading).filter(str -> {
                return str.startsWith(imageDirOutputPrefix);
            }).map(str -> {
                return str.substring(imageDirOutputPrefix.length());
            }).findFirst().orElseThrow();
            final var verifier = verifiers.get(cmd.packageType());
            // On macOS, the path to app image set from jpackage starts with "/var"
            // and the value of `PWD` variable in the "post-image" script is a path
            // starting with "/private/var", which is a target of "/var" symlink.
            //
            // Can't use Path.toRealPath() to resolve symlinks because the app image directory is gone.
            //
            // Instead, the workaround is to strip all leading path components
            // before the path component starting with "jdk.jpackage" substring.
            verifier.verify(Map.of("JpAppImageDir", JPackageUserScript.ExpectedEnvVarValue.create(
                    stripLeadingNonJPackagePathComponents(imageDir),
                    PostImageScriptTest::stripLeadingNonJPackagePathComponents)));
        }).run(Action.CREATE);
    }

    private static Collection<Object[]> createModeTestSpecs(boolean verifyAppImageContents) {
        return Stream.of(Mode.values()).map(mode -> {
            return new TestSpec(mode, verifyAppImageContents);
        }).map(spec -> {
            return new Object[] {spec};
        }).toList();
    }

    private static Path stripLeadingNonJPackagePathComponents(String path) {
        if (!Path.of(path).isAbsolute()) {
            throw new IllegalArgumentException();
        }

        final var m = JPACKAGE_TEMP_DIR_REGEXP.matcher(path);
        if (!m.find()) {
            TKit.assertUnexpected(String.format("jpackage temp directory not foind in [%s] path", path));
        }

        return Path.of(m.group());
    }

    private static final Pattern JPACKAGE_TEMP_DIR_REGEXP = Pattern.compile("[\\\\/]jdk\\.jpackage.+$",
            TKit.isWindows() ? 0 : Pattern.CASE_INSENSITIVE);
}
