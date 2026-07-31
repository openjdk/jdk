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


import static jdk.jpackage.internal.util.MemoizingSupplier.runOnce;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.xpath.XPathExpressionException;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.MacBundle;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.internal.util.TokenReplace;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;


/*
 * @test
 * @summary jpackage bundling modular app
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror ModularAppTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ModularAppTest
 */

public final class ModularAppTest {

    @Test
    @ParameterSupplier
    public void test(TestSpec spec) {
        spec.run();
    }

    /**
     * Test case for JDK-8233265. Adding modules in .jmod files for non-modular app
     * results in unexpected jpackage failure.
     */
    @Test
    @Parameter("Hello!")
    @Parameter("com.foo/com.foo.ModuleApp")
    public void test8233265(String mainAppDesc) throws IOException {
        JPackageCommand cmd = JPackageCommand.helloAppImage(mainAppDesc);

        // The test should make jpackage invoke jlink.
        cmd.ignoreDefaultRuntime(true);

        var modulePath = Optional.ofNullable(cmd.getArgumentValue("--module-path")).map(Path::of).orElseGet(() -> {
            var newModulePath = TKit.createTempDirectory("input-modules");
            cmd.addArguments("--module-path", newModulePath);
            return newModulePath;
        });

        JavaAppDesc extraModule = JavaAppDesc.parse("x.jmod:com.x/com.x.Y");
        HelloApp.createBundle(extraModule, modulePath);
        cmd.addArguments("--add-modules", extraModule.moduleName());

        cmd.executeAndAssertHelloAppImageCreated();
    }

    /**
     * Test case for JDK-8248254. App's module in the predefined runtime directory;
     * no "--module-path" option on the command line. jpackage should find the app's
     * module in the predefined runtime.
     */
    @Test
    @Parameter("IMAGE")
    @Parameter(value = "MAC_BUNDLE", ifOS = OperatingSystem.MACOS)
    public void test8248254(RuntimeType runtimeType) throws XPathExpressionException, IOException {

        final var appDesc = JavaAppDesc.parse("me.mymodule/me.mymodule.Main");

        new JPackageCommand()
                .setDefaultAppName()
                .setPackageType(PackageType.IMAGE)
                .setDefaultInputOutput()
                .removeArgumentWithValue("--input")
                .addArguments("--module", appDesc.moduleName() + "/" + appDesc.className())
                .setArgumentValue("--runtime-image", bakeModuleInRuntime(appDesc, runtimeType))
                .executeAndAssertHelloAppImageCreated();
    }

    /**
     * Test case for JDK-8261518. App's module is baked into the predefined runtime
     * image with jlink; no "--module-path" option on the command line. If there is
     * a non-modular jar in the current directory, jpackage used to throw.
     */
    @Test
    @Parameter("IMAGE")
    @Parameter(value = "MAC_BUNDLE", ifOS = OperatingSystem.MACOS)
    public void test8261518(RuntimeType runtimeType) throws XPathExpressionException, IOException {

        final var appDesc = JavaAppDesc.parse("com.foo/com.foo.main.Aloha");

        final var fooJarDir = TKit.createTempDirectory("foo");

        // Create "foo.jar" in dedicated directory.
        HelloApp.createBundle(JavaAppDesc.parse("foo.jar:"), fooJarDir);

        new JPackageCommand()
                .setDefaultAppName()
                .setPackageType(PackageType.IMAGE)
                .setDefaultInputOutput()
                .removeArgumentWithValue("--input")
                .addArguments("--module", appDesc.moduleName() + "/" + appDesc.className())
                .setArgumentValue("--runtime-image", bakeModuleInRuntime(appDesc, runtimeType).toAbsolutePath())
                // Run jpackage in the directory with "foo.jar"
                .setDirectory(fooJarDir).useToolProvider(false)
                .executeAndAssertHelloAppImageCreated();
    }

    private static Path bakeModuleInRuntime(JavaAppDesc appDesc, RuntimeType runtimeType) throws IOException {

        final var moduleOutputDir = TKit.createTempDirectory("modules");
        HelloApp.createBundle(appDesc, moduleOutputDir);

        final var workDir = TKit.createTempDirectory("runtime").resolve("data");
        final Path jlinkOutputDir = switch (runtimeType) {
            case IMAGE -> {
                yield workDir;
            }
            case MAC_BUNDLE -> {
                var macBundle = new MacBundle(workDir);

                // Create macOS bundle structure sufficient to pass jpackage validation.
                Files.createDirectories(macBundle.homeDir().getParent());
                Files.createDirectories(macBundle.macOsDir());
                Files.createFile(macBundle.infoPlistFile());
                yield macBundle.homeDir();
            }
        };

        // List of modules required for the test app.
        final var modules = new String[] {
            "java.base",
            "java.desktop",
            appDesc.moduleName()
        };

        new Executor()
        .setToolProvider(JavaTool.JLINK)
        .dumpOutput()
        .addArguments(
                "--add-modules", String.join(",", modules),
                "--output", jlinkOutputDir.toString(),
                "--module-path", moduleOutputDir.resolve(appDesc.jarFileName()).toString(),
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--strip-native-commands")
        .execute();

        return workDir;
    }

    enum RuntimeType {
        IMAGE,
        MAC_BUNDLE,
        ;
    }

    record TestSpec(List<String> paths, String appDesc) {
        TestSpec {
            Objects.requireNonNull(paths);
            Objects.requireNonNull(appDesc);
            Objects.requireNonNull(JavaAppDesc.parse(appDesc).moduleName());
        }

        boolean isWithGoodPath() {
            var isWithGoodPath = Slot.<Boolean>createEmpty();
            paths.forEach(path -> {
                ALL_TOKENS.applyTo(path, token -> {
                    if (token.equals(Token.GOOD_DIR.token())) {
                        isWithGoodPath.set(true);
                    }
                    return token;
                });
            });
            return isWithGoodPath.find().isPresent();
        }

        void run() {
            var emptyDir = runOnce(() -> {
                return TKit.createTempDirectory("empty-dir");
            });

            var nonExistingDir = runOnce(() -> {
                return TKit.withTempDirectory("non-existing-dir", x -> {});
            });

            var goodDir = runOnce(() -> {
                return TKit.createTempDirectory("modules");
            });

            var theAppDesc = JavaAppDesc.parse(appDesc);

            HelloApp.createBundle(theAppDesc, goodDir.get());

            var cmd = new JPackageCommand()
                    .setArgumentValue("--dest", TKit.workDir().resolve("output"))
                    .setDefaultAppName()
                    .setPackageType(PackageType.IMAGE)
                    // Ignore runtime that can be set for all tests. Usually if default
                    // runtime is set, it is fake one to save time on running jlink and
                    // copying megabytes of data from Java home to application image.
                    // We need proper runtime for this test.
                    .ignoreDefaultRuntime(true)
                    .addArguments("--module", String.join("/", theAppDesc.moduleName(), theAppDesc.className()));

            if (TKit.isWindows()) {
                cmd.addArguments("--win-console");
            }

            paths.stream().map(path -> {
                return ALL_TOKENS.applyTo(path, token -> {
                    return (switch (Token.valueOf(token.substring(2, token.length() - 2))) {
                        case EMPTY_DIR -> {
                            yield emptyDir;
                        }
                        case GOOD_DIR -> {
                            yield goodDir;
                        }
                        case NON_EXISTING_DIR -> {
                            yield nonExistingDir;
                        }
                    }).get();
                });
            }).<String>mapMulti((path, acc) -> {
                acc.accept("--module-path");
                acc.accept(path);
            }).forEach(cmd::addArgument);

            if (isWithGoodPath()) {
                cmd.executeAndAssertHelloAppImageCreated();
            } else {
                final CannedFormattedString expectedErrorMessage;
                if (paths.isEmpty()) {
                    expectedErrorMessage = JPackageCommand.makeError(
                            "ERR_MissingArgument2", "--runtime-image", "--module-path");
                } else {
                    expectedErrorMessage = JPackageCommand.makeError(
                            "error.no-module-in-path", theAppDesc.moduleName());
                }

                cmd.validateErr(expectedErrorMessage).execute(1);
            }
        }

        private static final TokenReplace ALL_TOKENS =
                new TokenReplace(Stream.of(Token.values()).map(Token::token).toArray(String[]::new));
    }

    public static Collection<?> test() {

        var testCases = new ArrayList<TestSpec>();

        for (String appDesc : List.of(
                "benvenuto.jar:com.jar.foo/com.jar.foo.Hello",
                "benvenuto.jmod:com.jmod.foo/com.jmod.foo.JModHello"
        )) {
            Stream.<Stream<String>>of(
                    Stream.<Token>of(Token.GOOD_DIR, Token.EMPTY_DIR, Token.NON_EXISTING_DIR).map(Token::token),
                    Stream.<Token>of(Token.EMPTY_DIR, Token.NON_EXISTING_DIR, Token.GOOD_DIR).map(Token::token),
                    Stream.<String>of(Token.GOOD_DIR.token() + "/a/b/c/d", Token.GOOD_DIR.token()),
                    Stream.<String>of(),
                    Stream.<Token>of(Token.EMPTY_DIR).map(Token::token)
            ).map(Stream::toList).map(paths -> {
                return new TestSpec(paths, appDesc);
            }).forEach(testCases::add);
        }

        return testCases.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    enum Token {
        GOOD_DIR,
        EMPTY_DIR,
        NON_EXISTING_DIR,
        ;

        String token() {
            return makeToken(name());
        }

        private static String makeToken(String v) {
            Objects.requireNonNull(v);
            return String.format("@@%s@@", v);
        }
    }
}
