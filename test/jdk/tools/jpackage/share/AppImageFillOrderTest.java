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
import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingBiFunction.toBiFunction;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingBiFunction;
import jdk.jpackage.internal.util.function.ThrowingFunction;
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

        var appContentRoot = TKit.createTempDirectory("app-content");

        var file = AppImageFile.getPathInAppImage(appContentRoot);

        Files.createDirectories(file.getParent());
        TKit.createTextFile(file, List.of("This is not a valid XML content"));

        var cmd = createJPackage().setFakeRuntime();
        addAppContentPath(cmd, appContentRoot, ApplicationLayout::appDirectory);

        // Run jpackage and verify it created valid .jpackage.xml file ignoring the overlay.
        cmd.executeAndAssertImageCreated();

        TKit.trace(String.format("Parse [%s] file...", AppImageFile.getPathInAppImage(cmd.outputBundle())));
        AppImageFile.load(cmd.outputBundle());
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

            var overlayDigest = digest(fc.out());
            var noOverlayDigest = digest(noOverlayPath);
            var inputDigest = digest(fc.in());

            TKit.trace(String.format("Check [%s] file:", fc.out()));
            TKit.assertEquals(inputDigest, overlayDigest, String.format("Check contents equals to [%s] file", fc.in()));
            TKit.assertNotEquals(noOverlayDigest, overlayDigest, String.format("Check contents differ from [%s] file", noOverlayPath));
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

        AppImageDefaultOverlay(ThrowingFunction<JPackageCommand, Collection<FileCopy>> func) {
            this.func = toFunction(func);
        }

        Collection<FileCopy> addOverlay(JPackageCommand cmd) {
            return func.apply(cmd);
        }

        private final Function<JPackageCommand, Collection<FileCopy>> func;
    }


    private enum AppImageAppContentOverlay implements AppImageOverlay {
        APP_CONTENT_MAIN_LAUNCHER_CFG(AppImageFillOrderTest::replaceMainLauncherCfgFile),
        APP_CONTENT_MAIN_JAR(AppImageFillOrderTest::replaceMainJar),
        APP_CONTENT_RUNTIME_RELEASE_FILE((cmd, appContentRoot) -> {
            return addRuntimeFile(cmd, appContentRoot, RUNTIME_RELEASE_FILE);
        }),
        ;

        AppImageAppContentOverlay(ThrowingBiFunction<JPackageCommand, Path, Collection<FileCopy>> func) {
            this.func = toBiFunction(func);
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


    private static Collection<FileCopy> replaceMainLauncherCfgFile(JPackageCommand cmd) {
        var inputDir = Path.of(cmd.getArgumentValue("--input"));

        // Replace the standard main launcher .cfg file with the custom one from the input dir.
        final var inputMainLauncherCfg = inputDir.resolve(cmd.appLauncherCfgPath(null).getFileName());

        TKit.createTextFile(inputMainLauncherCfg, List.of("Hello!"));

        return List.of(new FileCopy(inputMainLauncherCfg, cmd.appLauncherCfgPath(null)));
    }

    private static Collection<FileCopy> replaceMainLauncherCfgFile(JPackageCommand cmd, Path appContentRoot) throws IOException {
        var appDirOverlay = APP_IMAGE_LAYOUT.resolveAt(appContentRoot).appDirectory();

        // Replace the standard main launcher .cfg file with the custom one from the app content.
        final var inputMainLauncherCfg = appDirOverlay.resolve(cmd.appLauncherCfgPath(null).getFileName());

        Files.createDirectories(inputMainLauncherCfg.getParent());
        TKit.createTextFile(inputMainLauncherCfg, List.of("!Olleh"));

        addAppContentPath(cmd, appContentRoot, ApplicationLayout::appDirectory);

        return List.of(new FileCopy(inputMainLauncherCfg, cmd.appLauncherCfgPath(null)));
    }

    private static Collection<FileCopy> replaceMainJar(JPackageCommand cmd, Path appContentRoot) throws IOException {
        var appDirOverlay = APP_IMAGE_LAYOUT.resolveAt(appContentRoot).appDirectory();

        // Replace the jar file that jpackage will pick up from the input directory with the custom one.
        var mainJar = appDirOverlay.resolve(cmd.getArgumentValue("--main-jar"));

        Files.createDirectories(mainJar.getParent());
        TKit.createTextFile(mainJar, List.of("Surprise!"));

        addAppContentPath(cmd, appContentRoot, ApplicationLayout::appDirectory);

        return List.of(new FileCopy(mainJar, cmd.appLayout().appDirectory().resolve(mainJar.getFileName())));
    }

    private static Collection<FileCopy> addRuntimeFile(JPackageCommand cmd, Path appContentRoot, Path pathInRuntime) throws IOException {
        var runtimeOverlay = APP_IMAGE_LAYOUT.resolveAt(appContentRoot).runtimeHomeDirectory();

        // Add a file to the runtime.
        var file = runtimeOverlay.resolve(pathInRuntime);

        Files.createDirectories(file.getParent());
        TKit.createTextFile(file, List.of("blob"));

        addAppContentPath(cmd, appContentRoot, ApplicationLayout::runtimeDirectory);

        return List.of(new FileCopy(file, cmd.appLayout().runtimeHomeDirectory().resolve(file.getFileName())));
    }

    private static void addAppContentPath(JPackageCommand cmd, Path appContentRoot, Function<ApplicationLayout, Path> appImageComponentGetter) {
        var pathInAppImage = appImageComponentGetter.apply(APP_IMAGE_LAYOUT);
        if (TKit.isOSX()) {
            pathInAppImage = ApplicationLayout.macAppImage().contentDirectory().relativize(pathInAppImage);
        }

        cmd.addArguments("--app-content", appContentRoot.resolve(pathInAppImage.getName(0)));
    }

    private static String digest(Path file) {
        try {
            var md = MessageDigest.getInstance("md5");
            try (var is = Files.newInputStream(file); var dis = new DigestInputStream(is, md)) {
                dis.readAllBytes();
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException|IOException ex) {
            throw rethrowUnchecked(ex);
        }
    }

    private static JPackageCommand createJPackage() {
        // With short name.
        return JPackageCommand.helloAppImage().setArgumentValue("--name", "Foo");
    }

    private static final ApplicationLayout APP_IMAGE_LAYOUT = ApplicationLayout.platformAppImage();
    private static final Path RUNTIME_RELEASE_FILE = Path.of("release");
}
