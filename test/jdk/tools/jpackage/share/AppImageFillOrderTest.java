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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.ApplicationLayout;
import jdk.jpackage.test.ConfigurationTarget;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary test order in which jpackage fills app image
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror AppImageFillOrderTest.java
 * @run main/othervm/timeout=1440 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=AppImageFillOrderTest
 */

/**
 * Test order in which overlapping items are added to the app image. jpackage
 * defaults should go first to let user-provided content override them.
 *
 * <p>
 * Custom content comes from:
 * <ul>
 * <li>input directory (--input)
 * <li>app resources (--app-resources)
 * <li>app content (--app-content)
 * <ul>
 */
public class AppImageFillOrderTest {

    @Test
    @ParameterSupplier
    public void test(AppImageOverlay overlay) {
        test(initJPackage().andThen(JPackageCommand::setFakeRuntime), false, overlay);
    }

    @Test
    @ParameterSupplier("test")
    public void testAppImage(AppImageOverlay overlay) {
        test(initJPackage().andThen(JPackageCommand::setFakeRuntime), true, overlay);
    }

    /**
     * Test they can override a file in the runtime.
     * @param jlink
     */
    @Test
    @Parameter({"true", "true"})
    @Parameter({"true", "false"})
    @Parameter({"false", "true"})
    @Parameter({"false", "false"})
    public void testRuntime(boolean appImage, boolean jlink) {

        Consumer<JPackageCommand> initializer = cmd -> {
            if (jlink) {
                cmd.ignoreDefaultRuntime(true);
            } else {
                // Configure fake runtime and create it.
                cmd.setFakeRuntime().executePrerequisiteActions();

                var runtimeDir = Path.of(cmd.getArgumentValue("--runtime-image"));
                if (!runtimeDir.toAbsolutePath().normalize().startsWith(TKit.workDir().toAbsolutePath().normalize())) {
                    throw new IllegalStateException(String.format(
                            "Fake runtime [%s] created outside of the test work directory [%s]",
                            runtimeDir, TKit.workDir()));
                }

                TKit.createTextFile(runtimeDir.resolve(RUNTIME_RELEASE_FILE), List.of("Foo release"));
            }
        };

        test(initJPackage().andThen(initializer), appImage, StandardAppImageOverlay.APP_CONTENT_RUNTIME_RELEASE_FILE);
    }

    /**
     * Test they can not override .jpackage.xml file.
     * @throws IOException
     */
    @Test
    public void testAppImageFile() throws IOException {

        var cmd = createJPackage().setFakeRuntime();

        var outputBundle = cmd.outputBundle();

        buildOverlay(cmd, TKit.createTempDirectory("app-content"), AppImageFile.getPathInAppImage(outputBundle))
                .textContent("This is not a valid XML content")
                .addAppContentOption().createOverlayFile();

        // Run jpackage and verify it created valid .jpackage.xml file ignoring the overlay.
        cmd.executeAndAssertImageCreated();

        TKit.trace(String.format("Parse [%s] file...", AppImageFile.getPathInAppImage(outputBundle)));
        AppImageFile.load(outputBundle);
    }

    private static void test(Consumer<JPackageCommand> initializer, boolean appImage, AppImageOverlay overlay) {
        Objects.requireNonNull(overlay);

        final ConfigurationTarget targetWithoutOverlays;
        if (appImage) {
            targetWithoutOverlays = new ConfigurationTarget(JPackageCommand.helloAppImage());
        } else {
            targetWithoutOverlays = new ConfigurationTarget(new PackageTest().configureHelloApp());
        }

        targetWithoutOverlays
        .addInitializer(initializer)
        .addInitializer(cmdWithoutOverlays -> {
            cmdWithoutOverlays.setArgumentValue("--dest", cmdWithoutOverlays.getArgumentValue("--dest") + "-no-overlay");
        })
        .apply(JPackageCommand::execute, _ -> {})
        .addInstallVerifier(cmdWithoutOverlays -> {
            final ConfigurationTarget target;
            if (appImage) {
                target = new ConfigurationTarget(new JPackageCommand());
            } else {
                target = new ConfigurationTarget(new PackageTest().forTypes(cmdWithoutOverlays.packageType()));
            }

            Slot<List<FileCopy>> fileCopies = Slot.createEmpty();

            target.addInitializer(cmd -> {
                cmd.clearArguments()
                        .addArguments(cmdWithoutOverlays.getAllArguments())
                        .setDefaultInputOutput()
                        .setArgumentValue("--input", cmdWithoutOverlays.inputDir());

                // Apply overlays to the command.
                fileCopies.set(overlay.addOverlay(cmd).stream()
                        .sorted(Comparator.comparing(FileCopy::out).thenComparing(Comparator.comparing(FileCopy::in)))
                        .toList());
            })
            .apply(JPackageCommand::execute, _ -> {})
            .addInstallVerifier(cmd -> {

                Function<JPackageCommand, Path> unpackRoot = c -> {
                    return c.isImagePackageType() ? c.outputBundle() : c.pathToUnpackedPackageFile(c.appInstallationDirectory());
                };

                for (var fc : fileCopies.get()) {
                    var noOverlayPath = unpackRoot.apply(cmdWithoutOverlays).resolve(fc.out());
                    var overlayPath = unpackRoot.apply(cmd).resolve(fc.out());
                    TKit.assertSameFileContent(fc.in(), overlayPath);
                    if (Files.exists(noOverlayPath)) {
                        TKit.assertMismatchFileContent(noOverlayPath, overlayPath);
                    }
                }
            }).test().ifPresent(test -> {
                test.run(Action.CREATE_AND_UNPACK);
            });
        }).test().ifPresent(test -> {
            test.run(Action.CREATE_AND_UNPACK);
        });
    }

    public static Collection<Object[]> test() {

        var testCases = new ArrayList<AppImageOverlay>();

        Stream.<AppImageOverlay>of(

                // Overwrite main launcher .cfg file from the input dir.
                StandardAppImageOverlay.INPUT_MAIN_LAUNCHER_CFG,

                // Overwrite main launcher .cfg file from the app content dir.
                StandardAppImageOverlay.APP_CONTENT_MAIN_LAUNCHER_CFG,

                // Overwrite main launcher .cfg file from the input dir and from the app content dir.
                // The one from app content should win.
                AppImageOverlay.group().overlays(
                        StandardAppImageOverlay.INPUT_MAIN_LAUNCHER_CFG,
                        StandardAppImageOverlay.APP_CONTENT_MAIN_LAUNCHER_CFG
                ).last().create(),

                // Overwrite main jar from the app content dir.
                StandardAppImageOverlay.APP_CONTENT_MAIN_JAR,

                // The same file is copied from the --app-resources and --app-content options.
                // The one from the - app-content should win regardless of the order of the options on the command line.
                AppImageOverlay.group().overlays(
                        StandardAppImageOverlay.APP_RESOURCES_USER_FILE,
                        StandardAppImageOverlay.APP_CONTENT_USER_FILE).last().create(),
                AppImageOverlay.group().overlays(
                        StandardAppImageOverlay.APP_CONTENT_USER_FILE,
                        StandardAppImageOverlay.APP_RESOURCES_USER_FILE).first().create()

        ).forEach(testCases::add);

        return testCases.stream().map(args -> {
            return new Object[] {args};
        }).toList();
    }


    @FunctionalInterface
    public interface AppImageOverlay {

        Collection<FileCopy> addOverlay(JPackageCommand cmd);

        static AppImageOverlay fileOverlay(BiFunction<JPackageCommand, Path, OverlayFileBuilder> initializer) {
            Objects.requireNonNull(initializer);
            return cmd -> {
                return List.of(initializer.apply(cmd, TKit.createTempDirectory("content")).createOverlayFile());
            };
        }

        static GroupAppImageOverlay.Builder group() {
            return new GroupAppImageOverlay.Builder();
        }
    }


    private enum StandardAppImageOverlay implements AppImageOverlay {

        // Replace the standard main launcher .cfg file with the custom one from the input dir.
        INPUT_MAIN_LAUNCHER_CFG(cmd -> {

            final var outputFile = relativize(cmd, cmd.appLauncherCfgPath(null));

            final var inputDir = Path.of(cmd.getArgumentValue("--input"));

            final var file = inputDir.resolve(outputFile.getFileName());

            TKit.createTextFile(file, List.of("Hello!"));

            return List.of(new FileCopy(file, outputFile));
        }),

        // Replace the standard main launcher .cfg file with the custom one from the app content.
        APP_CONTENT_MAIN_LAUNCHER_CFG(AppImageOverlay.fileOverlay((cmd, contentRoot) -> {
            return buildOverlay(cmd, contentRoot, cmd.appLauncherCfgPath(null))
                    .textContent("!Olleh")
                    .addAppContentOption();
        })),

        // Replace the jar file that jpackage will pick up from the input directory with the custom one.
        APP_CONTENT_MAIN_JAR(AppImageOverlay.fileOverlay((cmd, contentRoot) -> {
            return buildOverlay(cmd, contentRoot, cmd.appLayout().appDirectory().resolve(cmd.getArgumentValue("--main-jar")))
                    .textContent("Surprise!")
                    .addAppContentOption();
        })),

        // Replace "release" file in the runtime directory.
        APP_CONTENT_RUNTIME_RELEASE_FILE(AppImageOverlay.fileOverlay((cmd, contentRoot) -> {
            return buildOverlay(cmd, contentRoot, cmd.appLayout().runtimeHomeDirectory().resolve("release"))
                    .textContent("blob")
                    .addAppContentOption();
        })),

        // "a/b/c.txt" file in the content directory.
        APP_CONTENT_USER_FILE(AppImageOverlay.fileOverlay((cmd, contentRoot) -> {
            var dstDir = TKit.isOSX() ? cmd.appLayout().resourcesDirectory() : cmd.appLayout().contentDirectory();
            return buildOverlay(cmd, contentRoot, dstDir.resolve("a/b/c.txt"))
                    .textContent("MACOS_APP_CONTENT_USER_FILE")
                    .addAppContentOption();
        })),

        // "a/b/c.txt" file in the resources directory.
        APP_RESOURCES_USER_FILE(AppImageOverlay.fileOverlay((cmd, contentRoot) -> {
            return buildOverlay(cmd, contentRoot, cmd.appLayout().resourcesDirectory().resolve("a/b/c.txt"))
                    .textContent("APP_RESOURCES_USER_FILE")
                    .addAppResourcesOption();
        })),

        ;

        StandardAppImageOverlay(AppImageOverlay impl) {
            this.impl = Objects.requireNonNull(impl);
        }

        @Override
        public Collection<FileCopy> addOverlay(JPackageCommand cmd) {
            return impl.addOverlay(cmd);
        }

        private final AppImageOverlay impl;
    }


    private record GroupAppImageOverlay(List<AppImageOverlay> group, Selector selector) implements AppImageOverlay {

        GroupAppImageOverlay {
            Objects.requireNonNull(selector);
            group.forEach(Objects::requireNonNull);
            if (group.size() < 2) {
                throw new IllegalArgumentException();
            }
        }

        enum Selector {
            LAST,
            FIRST,
            EACH,
            ;
        }

        @Override
        public Collection<FileCopy> addOverlay(JPackageCommand cmd) {
            var fileCopies = group.stream().flatMap(overlay -> {
                return overlay.addOverlay(cmd).stream();
            }).toList();

            return switch (selector) {
                case EACH -> fileCopies;
                case FIRST -> List.of(fileCopies.getFirst());
                case LAST -> List.of(fileCopies.getLast());
            };
        }

        @Override
        public String toString() {
            if (selector == Selector.EACH) {
                return String.format("%s", group);
            } else {
                return String.format("%s%s", selector, group);
            }
        }

        final static class Builder {

            Builder selector(Selector v) {
                selector = v;
                return this;
            }

            Builder first() {
                return selector(Selector.FIRST);
            }

            Builder last() {
                return selector(Selector.LAST);
            }

            Builder overlays(Collection<AppImageOverlay> v) {
                overlays.addAll(v);
                return this;
            }

            Builder overlays(AppImageOverlay... v) {
                return overlays(List.of(v));
            }

            AppImageOverlay create() {
                if (overlays.size() == 1) {
                    return overlays.getFirst();
                } else {
                    return new GroupAppImageOverlay(
                            List.copyOf(overlays), Optional.ofNullable(selector).orElse(Selector.EACH));
                }
            }

            private Selector selector;
            private List<AppImageOverlay> overlays = new ArrayList<>();
        }
    }


    private record FileCopy(Path in, Path out) {
        FileCopy {
            Objects.requireNonNull(in);
            Objects.requireNonNull(out);
        }
    }


    private static OverlayFileBuilder buildOverlay(JPackageCommand cmd, Path appContentRoot, Path outputFile) {
        return new OverlayFileBuilder(cmd, appContentRoot, outputFile);
    }


    private static final class OverlayFileBuilder {

        OverlayFileBuilder(JPackageCommand cmd, Path srcRoot, Path outputFile) {
            this.cmd = Objects.requireNonNull(cmd);
            this.outputFilePathInAppImage = relativize(cmd, outputFile);
            this.srcRoot = Objects.requireNonNull(srcRoot);
        }

        FileCopy createOverlayFile() {
            if (srcFile == null) {
                throw new IllegalStateException();
            }

            try {
                Files.createDirectories(srcFile.getParent());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            fileContentInitializer.accept(srcFile);

            return new FileCopy(srcFile, outputFilePathInAppImage);
        }

        OverlayFileBuilder addAppContentOption() {
            addJPackageOption("--app-content", APP_IMAGE_LAYOUT.contentDirectory());
            return this;
        }

        OverlayFileBuilder addAppResourcesOption() {
            addJPackageOption("--app-resources", APP_IMAGE_LAYOUT.resourcesDirectory());
            return this;
        }

        OverlayFileBuilder content(Consumer<Path> v) {
            fileContentInitializer = v;
            return this;
        }

        OverlayFileBuilder textContent(String... lines) {
            return content(path -> {
                TKit.createTextFile(path, List.of(lines));
            });
        }

        private void addJPackageOption(String optionName, Path outputDirectoryInAppImage) {
            Objects.requireNonNull(optionName);

            var relativeSrcFilePath = relativize(outputDirectoryInAppImage, outputFilePathInAppImage);

            cmd.addArguments(optionName, srcRoot.resolve(relativeSrcFilePath.getName(0)));

            srcFile = srcRoot.resolve(relativeSrcFilePath);
        }

        private Consumer<Path> fileContentInitializer;
        private final JPackageCommand cmd;
        private final Path outputFilePathInAppImage;
        private final Path srcRoot;
        private Path srcFile;
    }


    private static Path relativize(Path base, Path path) {
        if (base.isAbsolute() != path.isAbsolute()) {
            throw new IllegalArgumentException();
        }

        if (base.equals(Path.of(""))) {
            return path;
        }

        if (!path.startsWith(base)) {
            throw new IllegalArgumentException();
        }

        return base.relativize(path);
    }

    private static Path relativize(JPackageCommand cmd, Path path) {
        var base = cmd.isImagePackageType() ? cmd.outputBundle() : cmd.appInstallationDirectory();
        return relativize(base, path);
    }

    private static Consumer<JPackageCommand> initJPackage() {
        return cmd -> {
            // With short name.
            cmd.setArgumentValue("--name", "Foo");

            // Fresh input dir.
            cmd.setInputToEmptyDirectory();
        };
    }

    private static JPackageCommand createJPackage() {
        return JPackageCommand.helloAppImage().mutate(initJPackage());
    }

    private static final ApplicationLayout APP_IMAGE_LAYOUT = ApplicationLayout.platformAppImage();
    private static final Path RUNTIME_RELEASE_FILE = Path.of("release");
}
