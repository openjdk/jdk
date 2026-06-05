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

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.ApplicationLayout;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary test order in which jpackage fills app image
 * @library /test/jdk/tools/jpackage/helpers
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
 * <li>app content (--app-content)
 * <ul>
 */
public class AppImageFillOrderTest {

    @Test
    @ParameterSupplier
    public void test(AppImageOverlay overlays[]) {
        test(createJPackage().setFakeRuntime(), overlays);
    }

    /**
     * Test they can override a file in the runtime.
     * @param jlink
     */
    @Test
    @Parameter("true")
    @Parameter("false")
    public void testRuntime(boolean jlink) {
        var cmd = createJPackage();
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

        test(cmd, AppImageAppContentOverlay.APP_CONTENT_RUNTIME_RELEASE_FILE);
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
                .configureCmdOptions().createOverlayFile();

        // Run jpackage and verify it created valid .jpackage.xml file ignoring the overlay.
        cmd.executeAndAssertImageCreated();

        TKit.trace(String.format("Parse [%s] file...", AppImageFile.getPathInAppImage(outputBundle)));
        AppImageFile.load(outputBundle);
    }

    private static void test(JPackageCommand cmd, AppImageOverlay... overlays) {
        if (overlays.length == 0) {
            throw new IllegalArgumentException();
        }

        final var outputDir = Path.of(cmd.getArgumentValue("--dest"));
        final var noOverlaysOutputDir = Path.of(outputDir.toString() + "-no-overlay");
        cmd.setArgumentValue("--dest", noOverlaysOutputDir);

        // Run the command without overlays with redirected output directory.
        cmd.execute();

        final Optional<Path> appContentRoot;
        if (Stream.of(overlays).anyMatch(AppImageAppContentOverlay.class::isInstance)) {
            appContentRoot = Optional.of(TKit.createTempDirectory("app-content"));
        } else {
            appContentRoot = Optional.empty();
        }

        // Apply overlays to the command.
        var fileCopies = Stream.of(overlays).map(overlay -> {
            switch (overlay) {
                case AppImageDefaultOverlay v -> {
                    return v.addOverlay(cmd);
                }
                case AppImageAppContentOverlay v -> {
                    return v.addOverlay(cmd, appContentRoot.orElseThrow());
                }
            }
        }).flatMap(Collection::stream).collect(toMap(FileCopy::out, x -> x, (a, b) -> {
            return b;
        }, TreeMap::new)).values().stream().toList();

        // Collect paths in the app image that will be affected by overlays.
        var noOverlayOutputPaths = fileCopies.stream().map(FileCopy::out).toList();

        fileCopies = fileCopies.stream().map(v -> {
            return new FileCopy(v.in(), outputDir.resolve(noOverlaysOutputDir.relativize(v.out())));
        }).toList();

        // Restore the original output directory for the command and execute it.
        cmd.setArgumentValue("--dest", outputDir).execute();

        for (var i = 0; i != fileCopies.size(); i++) {
            var noOverlayPath = noOverlayOutputPaths.get(i);
            var fc = fileCopies.get(i);
            TKit.assertSameFileContent(fc.in(), fc.out());
            TKit.assertMismatchFileContent(noOverlayPath, fc.out());
        }
    }

    public static Collection<Object[]> test() {
        return Stream.of(

                // Overwrite main launcher .cfg file from the input dir.
                List.of(AppImageDefaultOverlay.INPUT_MAIN_LAUNCHER_CFG),

                // Overwrite main launcher .cfg file from the app content dir.
                List.of(AppImageAppContentOverlay.APP_CONTENT_MAIN_LAUNCHER_CFG),

                // Overwrite main launcher .cfg file from the input dir and from the app content dir.
                // The one from app content should win.
                List.<AppImageOverlay>of(
                        AppImageDefaultOverlay.INPUT_MAIN_LAUNCHER_CFG,
                        AppImageAppContentOverlay.APP_CONTENT_MAIN_LAUNCHER_CFG
                ),

                // Overwrite main jar from the app content dir.
                List.of(AppImageAppContentOverlay.APP_CONTENT_MAIN_JAR)
        ).map(args -> {
            return args.toArray(AppImageOverlay[]::new);
        }).map(args -> {
            return new Object[] {args};
        }).toList();
    }


    public sealed interface AppImageOverlay {
    }


    private enum AppImageDefaultOverlay implements AppImageOverlay {
        INPUT_MAIN_LAUNCHER_CFG(AppImageFillOrderTest::replaceMainLauncherCfgFile),
        ;

        AppImageDefaultOverlay(Function<JPackageCommand, FileCopy> func) {
            Objects.requireNonNull(func);
            this.func = cmd -> {
                return List.of(func.apply(cmd));
            };
        }

        Collection<FileCopy> addOverlay(JPackageCommand cmd) {
            return func.apply(cmd);
        }

        private final Function<JPackageCommand, Collection<FileCopy>> func;
    }


    private enum AppImageAppContentOverlay implements AppImageOverlay {
        // Replace the standard main launcher .cfg file with the custom one from the app content.
        APP_CONTENT_MAIN_LAUNCHER_CFG((cmd, appContentRoot) -> {
            return buildOverlay(cmd, appContentRoot, cmd.appLauncherCfgPath(null))
                    .textContent("!Olleh")
                    .configureCmdOptions().createOverlayFile();
        }),

        // Replace the jar file that jpackage will pick up from the input directory with the custom one.
        APP_CONTENT_MAIN_JAR((cmd, appContentRoot) -> {
            return buildOverlay(cmd, appContentRoot, cmd.appLayout().appDirectory().resolve(cmd.getArgumentValue("--main-jar")))
                    .textContent("Surprise!")
                    .configureCmdOptions().createOverlayFile();
        }),

        // Replace "release" file in the runtime directory.
        APP_CONTENT_RUNTIME_RELEASE_FILE((cmd, appContentRoot) -> {
            return buildOverlay(cmd, appContentRoot, cmd.appLayout().runtimeHomeDirectory().resolve("release"))
                    .textContent("blob")
                    .configureCmdOptions().createOverlayFile();
        }),
        ;

        AppImageAppContentOverlay(BiFunction<JPackageCommand, Path, FileCopy> func) {
            Objects.requireNonNull(func);
            this.func = (cmd, appContentRoot) -> {
                return List.of(func.apply(cmd, appContentRoot));
            };
        }

        Collection<FileCopy> addOverlay(JPackageCommand cmd, Path appContentRoot) {
            return func.apply(cmd, appContentRoot);
        }

        private final BiFunction<JPackageCommand, Path, Collection<FileCopy>> func;
    }


    private record FileCopy(Path in, Path out) {
        FileCopy {
            Objects.requireNonNull(in);
            Objects.requireNonNull(out);
        }
    }


    private static FileCopy replaceMainLauncherCfgFile(JPackageCommand cmd) {
        // Replace the standard main launcher .cfg file with the custom one from the input dir.
        final var outputFile = cmd.appLauncherCfgPath(null);

        final var inputDir = Path.of(cmd.getArgumentValue("--input"));

        final var file = inputDir.resolve(outputFile.getFileName());

        TKit.createTextFile(file, List.of("Hello!"));

        return new FileCopy(file, outputFile);
    }

    private static AppContentOverlayFileBuilder buildOverlay(JPackageCommand cmd, Path appContentRoot, Path outputFile) {
        return new AppContentOverlayFileBuilder(cmd, appContentRoot, outputFile);
    }


    private static final class AppContentOverlayFileBuilder {

        AppContentOverlayFileBuilder(JPackageCommand cmd, Path appContentRoot, Path outputFile) {
            if (outputFile.isAbsolute()) {
                throw new IllegalArgumentException();
            }

            if (!outputFile.startsWith(cmd.outputBundle())) {
                throw new IllegalArgumentException();
            }

            this.cmd = Objects.requireNonNull(cmd);
            this.outputFile = Objects.requireNonNull(outputFile);
            this.appContentRoot = Objects.requireNonNull(appContentRoot);
        }

        FileCopy createOverlayFile() {
            final var file = appContentRoot.resolve(pathInAppContentDirectory());

            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            fileContentInitializer.accept(file);

            return new FileCopy(file, outputFile);
        }

        AppContentOverlayFileBuilder configureCmdOptions() {
            cmd.addArguments("--app-content", appContentRoot.resolve(pathInAppContentDirectory().getName(0)));
            return this;
        }

        AppContentOverlayFileBuilder content(Consumer<Path> v) {
            fileContentInitializer = v;
            return this;
        }

        AppContentOverlayFileBuilder textContent(String... lines) {
            return content(path -> {
                TKit.createTextFile(path, List.of(lines));
            });
        }

        private Path pathInAppContentDirectory() {
            return APP_IMAGE_LAYOUT.resolveAt(cmd.outputBundle()).contentDirectory().relativize(outputFile);
        }

        private Consumer<Path> fileContentInitializer;
        private final JPackageCommand cmd;
        private final Path outputFile;
        private final Path appContentRoot;
    }


    private static JPackageCommand createJPackage() {
        // With short name.
        var cmd = JPackageCommand.helloAppImage().setArgumentValue("--name", "Foo");

        // Clean leftovers in the input dir from the previous test run if any.
        TKit.deleteDirectoryContentsRecursive(cmd.inputDir());

        return cmd;
    }

    private static final ApplicationLayout APP_IMAGE_LAYOUT = ApplicationLayout.platformAppImage();
    private static final Path RUNTIME_RELEASE_FILE = Path.of("release");
}
