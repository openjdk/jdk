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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.function.ExceptionBox.toUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jdk.jpackage.internal.PackagingPipeline.BuildApplicationTaskID;
import jdk.jpackage.internal.PackagingPipeline.CopyAppImageTaskID;
import jdk.jpackage.internal.PackagingPipeline.NoArgTaskAction;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskAction;
import jdk.jpackage.internal.PackagingPipeline.TaskContext;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeBuilder;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.function.ExceptionBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;


public class PackagingPipelineTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBuildApplication(boolean withRuntimeBuilder, @TempDir Path workDir) throws IOException {

        final var app = createApp(TEST_LAYOUT_1, withRuntimeBuilder ? Optional.of(TestRuntimeBuilder.INSTANCE) : Optional.empty());
        final var env = buildEnv(workDir.resolve("build")).appImageDirFor(app).create();

        // Build application image in `env.appImageDir()` directory.
        final var builder = buildPipeline();
        if (app.runtimeBuilder().isEmpty()) {
            builder.task(BuildApplicationTaskID.RUNTIME).noaction().add();
        }

        builder.create().execute(env, app);

        assertEquals(app.appImageDirName(), env.appImageDir().getFileName());

        var executedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(env, app);
        }));

        List<TaskID> expectedActions = new ArrayList<>();
        if (app.runtimeBuilder().isPresent()) {
            expectedActions.add(BuildApplicationTaskID.RUNTIME);
        }
        expectedActions.addAll(List.of(BuildApplicationTaskID.LAUNCHERS, BuildApplicationTaskID.CONTENT));

        assertEquals(expectedActions, executedTaskActions);

        final ExpectedAppImage expectedAppImage;
        if (withRuntimeBuilder) {
            expectedAppImage = ExpectedAppImage.build().dir("")
                    .file("launchers/my-launcher", TestLauncher.CONTENT)
                    .file("runtime/my-runtime", TestRuntimeBuilder.CONTENT);
        } else {
            expectedAppImage = ExpectedAppImage.build().dir("")
                    .file("launchers/my-launcher", TestLauncher.CONTENT);
        }

        assertEquals(expectedAppImage, ExpectedAppImage.load(env.appImageDir()));
    }

    @Test
    void testCopyApplication(@TempDir Path workDir) throws IOException {

        final var srcApp = createApp(TEST_LAYOUT_1, TestRuntimeBuilder.INSTANCE);

        final var srcEnv = buildEnv(workDir.resolve("build")).appImageDirFor(srcApp).create();

        // Build application image in `srcEnv.appImageDir()` directory.
        buildPipeline().create().execute(srcEnv, srcApp);

        final var dstApp = createApp(TEST_LAYOUT_2, TestRuntimeBuilder.INSTANCE);

        final var dstEnv = buildEnv(workDir.resolve("build-2"))
                .appImageLayout(dstApp.imageLayout().resolveAt(workDir.resolve("a/b/c")))
                .create();

        // Copy application image from `srcEnv.appImageDir()` into `dstEnv.appImageDir()`
        // with layout transformation.
        // This test exercises flexibility of the packaging pipeline.
        final var builder = buildPipeline()
                .task(PrimaryTaskID.BUILD_APPLICATION_IMAGE).applicationAction(cfg -> {
                    assertSame(dstApp, cfg.app());
                    assertEquals(dstEnv.appImageDir(), cfg.env().appImageLayout().rootDirectory());
                    assertFalse(Files.exists(dstEnv.appImageDir()));
                    PackagingPipeline.copyAppImage(srcEnv.appImageLayout(), cfg.env().appImageLayout(), false);
                }).add();

        // Disable the default "build application image" actions of the tasks which
        // are the dependencies of `PrimaryTaskID.BUILD_APPLICATION_IMAGE` task as
        // their output will be overwritten in the custom action of this task.
        builder.taskGraphSnapshot().getAllTailsOf(PrimaryTaskID.BUILD_APPLICATION_IMAGE).forEach(taskId -> {
            builder.task(taskId).noaction().add();
        });

        builder.create().execute(dstEnv, dstApp);

        AppImageLayout.toPathGroup(dstEnv.appImageLayout()).paths().forEach(path -> {
            assertTrue(Files.exists(path));
        });

        assertEquals(Path.of("c"), dstEnv.appImageDir().getFileName());

        var executedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(dstEnv, dstApp);
        }));

        assertEquals(List.of(PrimaryTaskID.BUILD_APPLICATION_IMAGE), executedTaskActions);

        final ExpectedAppImage expectedAppImage = ExpectedAppImage.build().dir("")
                .file("q/launchers/my-launcher", TestLauncher.CONTENT)
                .file("qqq/runtime/my-runtime", TestRuntimeBuilder.CONTENT);

        assertEquals(expectedAppImage, ExpectedAppImage.load(dstEnv.appImageDir()));
    }

    @Test
    void testCreatePackage(@TempDir Path workDir) throws IOException {

        final var outputDir = workDir.resolve("bundles");
        final var pkg = buildPackage(createApp(TEST_LAYOUT_1_WITH_INSTALL_DIR, TestRuntimeBuilder.INSTANCE)).create();
        final var env = buildEnv(workDir.resolve("build")).appImageDirFor(pkg).create();

        final var builder = buildPipeline();

        // Will create an app image in `env.appImageDir()` directory with `pkg.appImageLayout()` layout.
        // Will convert the created app image into a package.
        builder.create().execute(env, pkg, outputDir);

        final var expected = createTestPackageFileContents(env.appImageLayout());
        final var actual = Files.readString(outputDir.resolve(pkg.packageFileNameWithSuffix()));

        assertEquals(expected, actual);
        System.out.println(String.format("testCreatePackage:\n---\n%s\n---", actual));

        var executedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(env, pkg, outputDir);
        }));

        assertEquals(List.of(
                BuildApplicationTaskID.RUNTIME,
                BuildApplicationTaskID.LAUNCHERS,
                BuildApplicationTaskID.CONTENT,
                PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT,
                PrimaryTaskID.PACKAGE
        ), executedTaskActions);

        final var expectedAppImage = ExpectedAppImage.build().dir("")
                .file(TEST_INSTALL_DIR.resolve("launchers/my-launcher"), TestLauncher.CONTENT)
                .file(TEST_INSTALL_DIR.resolve("runtime/my-runtime"), TestRuntimeBuilder.CONTENT);

        assertEquals(expectedAppImage, ExpectedAppImage.load(env.appImageDir()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCreateRuntimeInstaller(boolean transformLayout, @TempDir Path workDir) throws IOException {

        final AppImageLayout srcLayout;
        if (transformLayout) {
            // Create an application layout such that the runtime directory doesn't
            // have a common parent with other directories otherwise the runtime directory
            // will be skipped when copying the app image layout as path groups because
            // the destination app image layout is of type RuntimeLayout and have only
            // the runtime directory.
            srcLayout = ApplicationLayout.build()
                    .launchersDirectory("launchers")
                    .appDirectory("lib")
                    .runtimeDirectory("runtime")
                    .appModsDirectory("lib")
                    .contentDirectory("lib")
                    .desktopIntegrationDirectory("lib")
                    .create();
        } else {
            srcLayout = RuntimeLayout.DEFAULT;
        }

        // Create a runtime image in `env.appImageDir()` directory.
        final var env = buildEnv(workDir.resolve("build"))
                .appImageLayout(srcLayout)
                .appImageDir(workDir.resolve("rt"))
                .create();
        TestRuntimeBuilder.INSTANCE.create(env.appImageLayout());

        final var pipeline = buildPackage(createApp(
                RuntimeLayout.DEFAULT.resolveAt(TEST_INSTALL_DIR).resetRootDirectory())).create();

        final var expectedAppImage = ExpectedAppImage.build().dir("")
                .file(TEST_INSTALL_DIR.resolve("my-runtime"), TestRuntimeBuilder.CONTENT);

        createAndVerifyPackage(buildPipeline(), pipeline, env, workDir.resolve("bundles"),
                String.format("testCreateRuntimeInstaller(%s)", transformLayout), expectedAppImage,
                CopyAppImageTaskID.COPY,
                PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT,
                PrimaryTaskID.PACKAGE);
    }

    private enum ExternalAppImageMode {

        // Copy predefined app image from `BuildEnv.appImageDir()`.
        // Layout of the predefined app image is `BuildEnv.appImageLayout()` and
        // its unresolved variant equals to `Package.appImageLayout()`.
        COPY_FROM_BUILD_ENV,

        // Copy predefined app image from some directory.
        // Layout of the predefined app image is `Package.appImageLayout()`.
        COPY,

        // Copy predefined app image from `BuildEnv.appImageDir()`.
        // Layout of the predefined app image is `BuildEnv.appImageLayout()` and
        // its unresolved variant is NOT equal to `Package.appImageLayout()`.
        TRANSFORM_FROM_BUILD_ENV,
        ;

        static final Set<ExternalAppImageMode> FROM_BUILD_ENV = Set.of(
                COPY_FROM_BUILD_ENV, TRANSFORM_FROM_BUILD_ENV);
    }

    @ParameterizedTest
    @EnumSource(ExternalAppImageMode.class)
    void testCreatePackageFromExternalAppImage(ExternalAppImageMode mode, @TempDir Path workDir) throws IOException {

        final ApplicationLayout appLayout;
        final ExpectedAppImage expectedAppImage;
        if (ExternalAppImageMode.TRANSFORM_FROM_BUILD_ENV == mode) {
            appLayout = TEST_LAYOUT_2_WITH_INSTALL_DIR;
            expectedAppImage = ExpectedAppImage.build().dir("")
                    .file(TEST_INSTALL_DIR.resolve("q/launchers/my-launcher"), TestLauncher.CONTENT)
                    .file(TEST_INSTALL_DIR.resolve("qqq/runtime/my-runtime"), TestRuntimeBuilder.CONTENT);
        } else {
            appLayout = TEST_LAYOUT_1_WITH_INSTALL_DIR;
            expectedAppImage = ExpectedAppImage.build().dir("")
                    .file(TEST_INSTALL_DIR.resolve("launchers/my-launcher"), TestLauncher.CONTENT)
                    .file(TEST_INSTALL_DIR.resolve("runtime/my-runtime"), TestRuntimeBuilder.CONTENT);
        }

        final BuildEnv env;
        final Path predefinedAppImage;
        if (ExternalAppImageMode.FROM_BUILD_ENV.contains(mode)) {
            // External app image is stored in the build env app image directory.
            env = setupBuildEnvForExternalAppImage(workDir);
            predefinedAppImage = env.appImageDir();
        } else {
            // External app image is stored outside of the build env app image directory
            // and should have the same layout as the app's app image layout.
            env = buildEnv(workDir.resolve("build"))
                    .appImageDir(workDir)
                    // Always need some app image layout.
                    .appImageLayout(new AppImageLayout.Stub(Path.of("")))
                    .create();
            final var externalAppImageLayout = appLayout.resolveAt(workDir.resolve("app-image"));
            TestRuntimeBuilder.INSTANCE.create(externalAppImageLayout);
            TestLauncher.INSTANCE.create(externalAppImageLayout);
            predefinedAppImage = externalAppImageLayout.rootDirectory();
        }

        final var pkg = buildPackage(createApp(appLayout))
                .predefinedAppImage(predefinedAppImage)
                .create();

        createAndVerifyPackage(buildPipeline(), pkg, env, workDir.resolve("bundles"),
                String.format("testCreatePackageFromExternalAppImage(%s)", mode), expectedAppImage,
                CopyAppImageTaskID.COPY,
                PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT,
                PrimaryTaskID.PACKAGE);
    }

    @ParameterizedTest
    @EnumSource(names={"COPY", "COPY_FROM_BUILD_ENV"})
    void testCreatePackageFromExternalAppImageNoCopyAction(ExternalAppImageMode mode, @TempDir Path workDir) throws IOException {

        final ApplicationLayout appLayout = TEST_LAYOUT_1_WITH_INSTALL_DIR;

        final BuildEnv env;
        final ApplicationLayout predefinedAppImageLayout;
        if (ExternalAppImageMode.FROM_BUILD_ENV.contains(mode)) {
            // External app image is stored in the build env app image directory.
            env = setupBuildEnvForExternalAppImage(workDir);
            predefinedAppImageLayout = env.asApplicationLayout().orElseThrow();
        } else {
            // External app image is stored outside of the build env app image directory
            // and should have the same layout as the app's app image layout.
            env = buildEnv(workDir.resolve("build"))
                    .appImageDir(workDir)
                    // Always need some app image layout.
                    .appImageLayout(new AppImageLayout.Stub(Path.of("")))
                    .create();
            predefinedAppImageLayout = appLayout.resolveAt(workDir.resolve("app-image"));
            TestRuntimeBuilder.INSTANCE.create(predefinedAppImageLayout);
            TestLauncher.INSTANCE.create(predefinedAppImageLayout);
        }

        final var pkg = buildPackage(createApp(appLayout))
                .predefinedAppImage(predefinedAppImageLayout.rootDirectory())
                .create();

        final var outputDir = workDir.resolve("bundles");

        final var builder = buildPipeline().configuredTasks().filter(task -> {
            return CopyAppImageTaskID.COPY.equals(task.task());
        }).findFirst().orElseThrow().noaction().add();

        final var startupParameters = builder.createStartupParameters(env, pkg, outputDir);

        builder.create().execute(startupParameters);

        final var expected = createTestPackageFileContents(predefinedAppImageLayout);
        final var actual = Files.readString(outputDir.resolve(pkg.packageFileNameWithSuffix()));
        assertEquals(expected, actual);
        System.out.println(String.format("%s:\n---\n%s\n---",
                String.format("testCreatePackageFromExternalAppImage(%s)", mode), actual));

        final ExpectedAppImage expectedAppImage = ExpectedAppImage.build().dir("")
                .file(predefinedAppImageLayout.unresolve().launchersDirectory().resolve("my-launcher"), TestLauncher.CONTENT)
                .file(predefinedAppImageLayout.unresolve().runtimeDirectory().resolve("my-runtime"), TestRuntimeBuilder.CONTENT);
        assertEquals(expectedAppImage, ExpectedAppImage.load(pkg.predefinedAppImage().orElseThrow()));

        var actualExecutedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(startupParameters);
        }));
        assertEquals(List.of(
                PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT,
                PrimaryTaskID.PACKAGE), actualExecutedTaskActions);
    }

    @Test
    void testCreatePackageFromExternalAppImageWithoutExternalAppImageError(@TempDir Path workDir) throws IOException {

        final var env = setupBuildEnvForExternalAppImage(workDir);
        final var pkg = buildPackage(createApp(TEST_LAYOUT_1_WITH_INSTALL_DIR)).create();
        final var pipeline = buildPipeline().create();

        assertThrowsExactly(UnsupportedOperationException.class, () -> pipeline.execute(env, pkg, workDir));
    }

    @Test
    void testExceptionRethrow_RuntimeException() throws IOException {

        final var expectedException = new RuntimeException("foo");
        final var ex = testExceptionRethrow(expectedException, expectedException.getClass(), () -> {
            throw expectedException;
        });
        assertSame(expectedException, ex);
    }

    @Test
    void testExceptionRethrow_PackagerException() throws IOException {

        final var expectedException = new RuntimeException("param.vendor.default");
        final var ex = testExceptionRethrow(expectedException, expectedException.getClass(), () -> {
            throw expectedException;
        });
        assertSame(expectedException, ex);
    }

    @Test
    void testExceptionRethrow_Exception() throws IOException {

        final var expectedException = new Exception("foo");
        final var ex = testExceptionRethrow(expectedException, ExceptionBox.class, () -> {
            throw toUnchecked(expectedException);
        });
        assertSame(expectedException, ex.getCause());
    }

    @Test
    void testAppImageAction() throws IOException {

        final var app = createApp(TEST_LAYOUT_1);
        final var env = dummyBuildEnv();

        final var executed = new boolean[1];

        PackagingPipeline.build()
                // The pipleline must have at least two tasks, add a dummy.
                .task(new TaskID() {}).addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE).add()
                .task(PrimaryTaskID.BUILD_APPLICATION_IMAGE).appImageAction(ctx -> {
                    assertSame(app, ctx.app());
                    assertSame(env, ctx.env());
                    executed[0] = true;
                }).add().create().execute(env, app);

        assertTrue(executed[0]);
    }

    @Test
    void testAppImageActionWithPackage() throws IOException {

        final var pkg = buildPackage(createApp(TEST_LAYOUT_1, TestRuntimeBuilder.INSTANCE)).create();
        final var env = dummyBuildEnv();

        final var executed = new boolean[1];

        final var builder = PackagingPipeline.build()
                // The pipleline must have at least two tasks, add a dummy.
                .task(new TaskID() {}).addDependent(PrimaryTaskID.PACKAGE).add();

        final var startupParameters = builder.createStartupParameters(env,  pkg,  Path.of(""));

        builder.task(PrimaryTaskID.PACKAGE).appImageAction(ctx -> {
            assertSame(pkg.app(), ctx.app());
            assertSame(startupParameters.packagingEnv(), ctx.env());
            executed[0] = true;
        }).add().create().execute(startupParameters);

        assertTrue(executed[0]);
    }

    @Test
    void testPackageActionWithApplication() throws IOException {

        final var app = createApp(TEST_LAYOUT_1);
        final var env = dummyBuildEnv();

        final var pipeline = PackagingPipeline.build()
                // The pipleline must have at least two tasks, add a dummy.
                .task(new TaskID() {}).addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE).add()
                .task(PrimaryTaskID.BUILD_APPLICATION_IMAGE).packageAction(ctx -> {
                    throw new AssertionError();
                }).add().create();

        // If the pipeline is building an application, it can not execute actions that take a package as an argument.
        assertThrowsExactly(UnsupportedOperationException.class, () -> pipeline.execute(env, app));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testContextMapper(boolean allowAll) throws IOException {

        var builder = PackagingPipeline.buildStandard().contextMapper(ctx -> {
            return new TaskContext() {
                @Override
                public boolean test(TaskID task) {
                    return allowAll;
                }

                @Override
                public void execute(TaskAction taskAction) throws IOException {
                    if (!allowAll) {
                        throw new AssertionError();
                    }
                    ctx.execute(taskAction);
                }
            };
        });

        var actualExecutedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(dummyBuildEnv(), createApp(TEST_LAYOUT_1));
        }));

        List<TaskID> expectedExecutedTaskActions;

        if (allowAll) {
            expectedExecutedTaskActions = List.of(
                    BuildApplicationTaskID.RUNTIME,
                    BuildApplicationTaskID.LAUNCHERS,
                    BuildApplicationTaskID.CONTENT,
                    BuildApplicationTaskID.APP_IMAGE_FILE,
                    CopyAppImageTaskID.COPY,
                    PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT);
        } else {
            expectedExecutedTaskActions = List.of();
        }

        assertEquals(expectedExecutedTaskActions, actualExecutedTaskActions);
    }

    public static List<PackagingPipeline.TaskID> dryRun(PackagingPipeline.Builder builder,
            Consumer<PackagingPipeline.Builder> callback) {

        List<PackagingPipeline.TaskID> executedTaskActions = new ArrayList<>();
        builder.configuredTasks().filter(PackagingPipeline.Builder.TaskBuilder::hasAction).forEach(taskBuilder -> {
            var taskId = taskBuilder.task();
            taskBuilder.action(() -> {
                executedTaskActions.add(taskId);
            }).add();
        });

        callback.accept(builder);

        return executedTaskActions;
    }

    private static Exception testExceptionRethrow(Exception expectedException,
            Class<? extends Exception> expectedCatchExceptionType,
            NoArgTaskAction throwAction) throws IOException {

        final var app = createApp(TEST_LAYOUT_1);
        final var env = dummyBuildEnv();

        var pipeline = PackagingPipeline.build()
                // The pipleline must have at least two tasks, add a dummy.
                .task(new TaskID() {}).addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE).add()
                .task(PrimaryTaskID.BUILD_APPLICATION_IMAGE).action(throwAction).add().create();

        return assertThrowsExactly(expectedCatchExceptionType, () -> pipeline.execute(env,  app));
    }

    private static BuildEnv setupBuildEnvForExternalAppImage(Path workDir) {
        // Create an app image in `env.appImageDir()` directory.
        final var env = buildEnv(workDir.resolve("build"))
                .appImageLayout(TEST_LAYOUT_1.resolveAt(Path.of("a/b/c")).resetRootDirectory())
                .appImageDir(workDir.resolve("app-image"))
                .create();
        TestRuntimeBuilder.INSTANCE.create(env.appImageLayout());
        TestLauncher.INSTANCE.create((ApplicationLayout)env.appImageLayout());

        return env;
    }

    private static void createAndVerifyPackage(PackagingPipeline.Builder builder, Package pkg,
            BuildEnv env, Path outputDir, String logMsgHeader, ExpectedAppImage expectedAppImage,
            TaskID... expectedExecutedTaskActions) throws IOException {
        Objects.requireNonNull(logMsgHeader);

        final var startupParameters = builder.createStartupParameters(env, pkg, outputDir);

        assertNotSameAppImageDirs(env, startupParameters.packagingEnv());

        // Will create an app image in `startupParameters.packagingEnv().appImageDir()` directory
        // with `pkg.appImageLayout()` layout using an app image (runtime image) from `env.appImageDir()` as input.
        // Will convert the created app image into a package.
        // Will not overwrite the contents of `env.appImageDir()` directory.
        builder.create().execute(startupParameters);

        final var packagingAppImageDir = startupParameters.packagingEnv().appImageDir();

        final var expected = createTestPackageFileContents(pkg.appImageLayout().resolveAt(packagingAppImageDir));

        final var actual = Files.readString(outputDir.resolve(pkg.packageFileNameWithSuffix()));

        assertEquals(expected, actual);
        System.out.println(String.format("%s:\n---\n%s\n---", logMsgHeader, actual));

        assertEquals(expectedAppImage, ExpectedAppImage.load(packagingAppImageDir));

        var actualExecutedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(startupParameters);
        }));

        assertEquals(List.of(expectedExecutedTaskActions), actualExecutedTaskActions);
    }

    private static Application createApp(AppImageLayout appImageLayout) {
        return createApp(appImageLayout, Optional.empty());
    }

    private static Application createApp(AppImageLayout appImageLayout, RuntimeBuilder runtimeBuilder) {
        return createApp(appImageLayout, Optional.of(runtimeBuilder));
    }

    private static Application createApp(AppImageLayout appImageLayout, Optional<RuntimeBuilder> runtimeBuilder) {
        Objects.requireNonNull(appImageLayout);
        Objects.requireNonNull(runtimeBuilder);
        if (appImageLayout.isResolved()) {
            throw new IllegalArgumentException();
        }

        return new Application.Stub(
                "foo",
                "My app",
                "1.0",
                "Acme",
                "copyright",
                Optional.empty(),
                List.of(),
                appImageLayout,
                runtimeBuilder,
                List.of(),
                Map.of());
    }


    private static final class PackageBuilder {
        PackageBuilder(Application app) {
            this.app = Objects.requireNonNull(app);
        }

        Package create() {
            return new Package.Stub(
                    app,
                    new PackageType() {
                        @Override
                        public String label() {
                            throw new UnsupportedOperationException();
                        }
                    },
                    "the-package",
                    "My package",
                    "1.0",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.ofNullable(predefinedAppImage),
                    null,
                    TEST_INSTALL_DIR);
        }

        PackageBuilder predefinedAppImage(Path v) {
            predefinedAppImage = v;
            return this;
        }

        private Path predefinedAppImage;
        private final Application app;
    }


    private static PackageBuilder buildPackage(Application app) {
        return new PackageBuilder(app);
    }

    private static BuildEnvBuilder buildEnv(Path rootDir) {
        return new BuildEnvBuilder(rootDir);
    }

    private static BuildEnv dummyBuildEnv() {
        return BuildEnv.create(Path.of("foo"), Optional.empty(), false, PackagingPipeline.class, RuntimeLayout.DEFAULT);
    }

    private static PackagingPipeline.Builder buildPipeline() {
        return PackagingPipeline.buildStandard()
                // Disable building the app image file (.jpackage.xml) as we don't have launchers in the test app.
                .task(BuildApplicationTaskID.APP_IMAGE_FILE).noaction().add()
                .task(BuildApplicationTaskID.LAUNCHERS).applicationAction(cfg -> {
                    TestLauncher.INSTANCE.create(cfg.resolvedLayout());
                }).add()
                .task(PrimaryTaskID.PACKAGE).packageAction(cfg -> {
                    var str = createTestPackageFileContents(cfg.resolvedLayout());
                    var packageFile = cfg.outputDir().resolve(cfg.pkg().packageFileNameWithSuffix());
                    Files.createDirectories(packageFile.getParent());
                    Files.writeString(packageFile, str);
                }).add();
    }

    private static String createTestPackageFileContents(AppImageLayout pkgLayout) throws IOException {
        return ExpectedAppImage.load(pkgLayout.rootDirectory()).toString();
    }

    private static void assertNotSameAppImageDirs(BuildEnv a, BuildEnv b) {
        assertNotEquals(a.appImageDir(), b.appImageDir());
        assertEquals(a.buildRoot(), b.buildRoot());
        assertEquals(a.configDir(), b.configDir());
        assertEquals(a.resourceDir(), b.resourceDir());
    }


    private static final class TestRuntimeBuilder implements RuntimeBuilder {
        @Override
        public void create(AppImageLayout appImageLayout) {
            assertTrue(appImageLayout.isResolved());
            try {
                Files.createDirectories(appImageLayout.runtimeDirectory());
                Files.writeString(runtimeFile(appImageLayout), CONTENT);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static Path runtimeFile(AppImageLayout appImageLayout) {
            return appImageLayout.runtimeDirectory().resolve("my-runtime");
        }

        static final String CONTENT = "this is the runtime";

        static final TestRuntimeBuilder INSTANCE = new TestRuntimeBuilder();
    }


    private static final class TestLauncher {
        public void create(ApplicationLayout appLayout) {
            assertTrue(appLayout.isResolved());
            try {
                Files.createDirectories(appLayout.launchersDirectory());
                Files.writeString(launcherFile(appLayout), CONTENT);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static Path launcherFile(ApplicationLayout appLayout) {
            return appLayout.launchersDirectory().resolve("my-launcher");
        }

        static final String CONTENT = "this is the launcher";

        static final TestLauncher INSTANCE = new TestLauncher();
    }


    private static final class ExpectedAppImage {

        static ExpectedAppImage build() {
            return new ExpectedAppImage(new HashSet<>());
        }

        static ExpectedAppImage load(Path appImageRoot) throws IOException {
            try (var walk = Files.walk(appImageRoot)) {
                return new ExpectedAppImage(walk.sorted().map(path -> {
                    var relativePath = appImageRoot.relativize(path);
                    if (Files.isDirectory(path)) {
                        return new Directory(relativePath);
                    } else {
                        return new File(relativePath, toSupplier(() -> Files.readString(path)).get());
                    }
                }).collect(Collectors.toSet()));
            }
        }

        ExpectedAppImage file(Path path, String content) {
            return add(new File(path, content));
        }

        ExpectedAppImage file(String path, String content) {
            return file(Path.of(path), content);
        }

        ExpectedAppImage dir(Path path) {
            return add(new Directory(path));
        }

        ExpectedAppImage dir(String path) {
            return dir(Path.of(path));
        }

        @Override
        public String toString() {
            return items.stream().map(AppImageItem::toString).sorted().collect(Collectors.joining("\n"));
        }

        @Override
        public int hashCode() {
            return Objects.hash(items);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (getClass() != obj.getClass())) {
                return false;
            }
            ExpectedAppImage other = (ExpectedAppImage) obj;
            return Objects.equals(items, other.items);
        }

        private ExpectedAppImage(Set<AppImageItem> items) {
            this.items = Objects.requireNonNull(items);
        }

        private ExpectedAppImage add(AppImageItem v) {
            var path = v.path();
            if (path.isAbsolute()) {
                throw new IllegalArgumentException();
            }

            items.add(v);
            while (path.getNameCount() > 1) {
                items.add(new Directory(path = path.getParent()));
            }
            return this;
        }

        private interface AppImageItem {
            Path path();
        }

        private record File(Path path, String content) implements AppImageItem {

            File {
                Objects.requireNonNull(path);
                Objects.requireNonNull(content);
            }

            @Override
            public String toString() {
                return String.format("%s[%s]", path, content);
            }
        }

        private record Directory(Path path) implements AppImageItem {

            Directory {
                Objects.requireNonNull(path);
            }

            @Override
            public String toString() {
                return path.toString();
            }
        }

        private final Set<AppImageItem> items;
    }


    private static final ApplicationLayout TEST_LAYOUT_1 = ApplicationLayout.build()
            .launchersDirectory("launchers")
            .appDirectory("")
            .runtimeDirectory("runtime")
            .appModsDirectory("")
            .contentDirectory("")
            .desktopIntegrationDirectory("")
            .create();

    private static final ApplicationLayout TEST_LAYOUT_2 = ApplicationLayout.build()
            .launchersDirectory("q/launchers")
            .appDirectory("")
            .runtimeDirectory("qqq/runtime")
            .appModsDirectory("")
            .contentDirectory("")
            .desktopIntegrationDirectory("")
            .create();

    private static final Path TEST_INSTALL_DIR = Path.of("Acme/My app");

    private static final ApplicationLayout TEST_LAYOUT_1_WITH_INSTALL_DIR =
            TEST_LAYOUT_1.resolveAt(TEST_INSTALL_DIR).resetRootDirectory();

    private static final ApplicationLayout TEST_LAYOUT_2_WITH_INSTALL_DIR =
            TEST_LAYOUT_2.resolveAt(TEST_INSTALL_DIR).resetRootDirectory();
}
