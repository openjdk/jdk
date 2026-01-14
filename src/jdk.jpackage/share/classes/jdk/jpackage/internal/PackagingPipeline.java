/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.model.AppImageLayout.toPathGroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.pipeline.DirectedEdge;
import jdk.jpackage.internal.pipeline.FixedDAG;
import jdk.jpackage.internal.pipeline.TaskPipelineBuilder;
import jdk.jpackage.internal.pipeline.TaskSpecBuilder;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class PackagingPipeline {

    /**
     * Runs the pipeline for the given application.
     *
     * @param env the build environment
     * @param app the application
     */
    void execute(BuildEnv env, Application app) {
        execute(contextMapper.apply(createTaskContext(env, app)));
    }

    /**
     * Runs the pipeline for the given package.
     * <p>
     * Building a package may require a directory where the app image bits will be
     * accumulated or the existing app image may be used. The decision is made based
     * on the properties of the given package. A new build environment will be
     * created if an intermediate directory is required. To access the build
     * environment that will be used by the pipeline before running the pipeline
     * create {@link StartupParameters} instance using
     * {@link Builder#createStartupParameters(BuildEnv, Package, Path)} method.
     *
     * @param env       the build environment
     * @param pkg       the package
     * @param outputDir the output directory for the package file
     */
    void execute(BuildEnv env, Package pkg, Path outputDir) {
        execute((StartupParameters)createPackagingTaskContext(env, pkg, outputDir, taskConfig));
    }

    /**
     * Runs the pipeline using the startup parameters created with
     * {@link Builder#createStartupParameters(BuildEnv, Package, Path)} call.
     *
     * @param startupParameters the pipeline startup parameters
     */
    void execute(StartupParameters startupParameters) {
        execute(contextMapper.apply(createTaskContext((PackagingTaskContext)startupParameters)));
    }

    /**
     * The way to access packaging build environment before building a package in a pipeline.
     */
    sealed interface StartupParameters {
        BuildEnv packagingEnv();
    }

    interface TaskAction {
    }

    interface TaskID {
    }

    enum BuildApplicationTaskID implements TaskID {
        RUNTIME,
        CONTENT,
        LAUNCHERS,
        APP_IMAGE_FILE
    }

    enum CopyAppImageTaskID implements TaskID {
        COPY
    }

    enum PrimaryTaskID implements TaskID {
        BUILD_APPLICATION_IMAGE,
        COPY_APP_IMAGE,
        PACKAGE
    }

    enum PackageTaskID implements TaskID {
        RUN_POST_IMAGE_USER_SCRIPT,
        CREATE_CONFIG_FILES,
        DELETE_OLD_PACKAGE_FILE,
        CREATE_PACKAGE_FILE
    }

    interface TaskContext extends Predicate<TaskID> {
        void execute(TaskAction taskAction) throws IOException;
    }

    record AppImageBuildEnv<T extends Application, U extends AppImageLayout>(BuildEnv env, T app) {
        @SuppressWarnings("unchecked")
        U envLayout() {
            return (U)app.imageLayout();
        }

        @SuppressWarnings("unchecked")
        U resolvedLayout() {
            return (U)env.appImageLayout();
        }
    }

    record PackageBuildEnv<T extends Package, U extends AppImageLayout>(BuildEnv env, T pkg, Path outputDir) {
        @SuppressWarnings("unchecked")
        U envLayout() {
            return (U)pkg.appImageLayout();
        }

        @SuppressWarnings("unchecked")
        U resolvedLayout() {
            return (U)env.appImageLayout();
        }
    }

    @FunctionalInterface
    interface ApplicationImageTaskAction<T extends Application, U extends ApplicationLayout> extends TaskAction {
        void execute(AppImageBuildEnv<T, U> env) throws IOException;
    }

    @FunctionalInterface
    interface AppImageTaskAction<T extends Application, U extends AppImageLayout> extends TaskAction {
        void execute(AppImageBuildEnv<T, U> env) throws IOException;
    }

    @FunctionalInterface
    interface CopyAppImageTaskAction<T extends Package> extends TaskAction {
        void execute(T pkg, AppImageLayout srcAppImage, AppImageLayout dstAppImage) throws IOException;
    }

    @FunctionalInterface
    interface PackageTaskAction<T extends Package, U extends AppImageLayout> extends TaskAction {
        void execute(PackageBuildEnv<T, U> env) throws IOException;
    }

    @FunctionalInterface
    interface NoArgTaskAction extends TaskAction {
        void execute() throws IOException;
    }

    record TaskConfig(Optional<TaskAction> action, Optional<TaskAction> beforeAction, Optional<TaskAction> afterAction) {
        TaskConfig {
            Objects.requireNonNull(action);
            Objects.requireNonNull(beforeAction);
            Objects.requireNonNull(afterAction);
        }
    }

    static final class Builder {

        private Builder() {
        }

        final class TaskBuilder extends TaskSpecBuilder<TaskID> {

            TaskBuilder noaction() {
                return setAction(ActionRole.WORKLOAD, null);
            }

            <T extends Application, U extends ApplicationLayout> TaskBuilder applicationAction(ApplicationImageTaskAction<T, U> action) {
                return applicationAction(ActionRole.WORKLOAD, action);
            }

            <T extends Application, U extends AppImageLayout> TaskBuilder appImageAction(AppImageTaskAction<T, U> action) {
                return appImageAction(ActionRole.WORKLOAD, action);
            }

            <T extends Package> TaskBuilder copyAction(CopyAppImageTaskAction<T> action) {
                return copyAction(ActionRole.WORKLOAD, action);
            }

            <T extends Package, U extends AppImageLayout> TaskBuilder packageAction(PackageTaskAction<T, U> action) {
                return packageAction(ActionRole.WORKLOAD, action);
            }

            TaskBuilder action(NoArgTaskAction action) {
                return action(ActionRole.WORKLOAD, action);
            }

            <T extends Application, U extends AppImageLayout> TaskBuilder logAppImageActionBegin(String keyId, Function<AppImageBuildEnv<T, U>, Object[]> formatArgsSupplier) {
                return logAppImageAction(ActionRole.BEFORE, keyId, formatArgsSupplier);
            }

            <T extends Application, U extends AppImageLayout> TaskBuilder logAppImageActionEnd(String keyId, Function<AppImageBuildEnv<T, U>, Object[]> formatArgsSupplier) {
                return logAppImageAction(ActionRole.AFTER, keyId, formatArgsSupplier);
            }

            <T extends Package, U extends AppImageLayout> TaskBuilder logPackageActionBegin(String keyId, Function<PackageBuildEnv<T, U>, Object[]> argsSupplier) {
                return logPackageAction(ActionRole.BEFORE, keyId, argsSupplier);
            }

            <T extends Package, U extends AppImageLayout> TaskBuilder logPackageActionEnd(String keyId, Function<PackageBuildEnv<T, U>, Object[]> argsSupplier) {
                return logPackageAction(ActionRole.AFTER, keyId, argsSupplier);
            }

            TaskBuilder logActionBegin(String keyId, Supplier<Object[]> formatArgsSupplier) {
                return logAction(ActionRole.BEFORE, keyId, formatArgsSupplier);
            }

            TaskBuilder logActionBegin(String keyId, Object... formatArgsSupplier) {
                return logAction(ActionRole.BEFORE, keyId, () -> formatArgsSupplier);
            }

            TaskBuilder logActionEnd(String keyId, Supplier<Object[]> formatArgsSupplier) {
                return logAction(ActionRole.AFTER, keyId, formatArgsSupplier);
            }

            TaskBuilder logActionEnd(String keyId, Object... formatArgsSupplier) {
                return logAction(ActionRole.AFTER, keyId, () -> formatArgsSupplier);
            }

            boolean hasAction() {
                return workloadAction != null;
            }

            @Override
            public TaskBuilder addDependent(TaskID v) {
                super.addDependent(v);
                return this;
            }

            @Override
            public TaskBuilder addDependency(TaskID v) {
                super.addDependency(v);
                return this;
            }

            @Override
            public TaskBuilder addDependencies(Collection<? extends TaskID> tasks) {
                super.addDependencies(tasks);
                return this;
            }

            @Override
            public TaskBuilder addDependents(Collection<? extends TaskID> tasks) {
                super.addDependents(tasks);
                return this;
            }

            public TaskBuilder addDependencies(TaskID ... tasks) {
                return addDependencies(List.of(tasks));
            }

            public TaskBuilder addDependents(TaskID ... tasks) {
                return addDependents(List.of(tasks));
            }

            Builder add() {
                final var config = new TaskConfig(
                        Optional.ofNullable(workloadAction),
                        Optional.ofNullable(beforeAction),
                        Optional.ofNullable(afterAction));
                taskConfig.put(task(), config);
                createLinks().forEach(Builder.this::linkTasks);
                return Builder.this;
            }


            private enum ActionRole {
                WORKLOAD(TaskBuilder::setWorkloadAction),
                BEFORE(TaskBuilder::setBeforeAction),
                AFTER(TaskBuilder::setAfterAction),
                ;

                ActionRole(BiConsumer<TaskBuilder, TaskAction> actionSetter) {
                    this.actionSetter = Objects.requireNonNull(actionSetter);
                }

                TaskBuilder setAction(TaskBuilder taskBuilder, TaskAction action) {
                    actionSetter.accept(taskBuilder, action);
                    return taskBuilder;
                }

                private final BiConsumer<TaskBuilder, TaskAction> actionSetter;
            }


            private TaskBuilder(TaskID id) {
                super(id);
            }

            private TaskBuilder(TaskID id, TaskConfig config) {
                this(id);
                config.action().ifPresent(this::setWorkloadAction);
                config.beforeAction().ifPresent(this::setBeforeAction);
                config.afterAction().ifPresent(this::setAfterAction);
            }

            private TaskBuilder setAction(ActionRole role, TaskAction v) {
                return role.setAction(this, v);
            }

            private TaskBuilder setWorkloadAction(TaskAction v) {
                workloadAction = v;
                return this;
            }

            private TaskBuilder setBeforeAction(TaskAction v) {
                beforeAction = v;
                return this;
            }

            private TaskBuilder setAfterAction(TaskAction v) {
                afterAction = v;
                return this;
            }

            private <T extends Application, U extends ApplicationLayout> TaskBuilder applicationAction(ActionRole role, ApplicationImageTaskAction<T, U> action) {
                return setAction(role, action);
            }

            private <T extends Application, U extends AppImageLayout> TaskBuilder appImageAction(ActionRole role, AppImageTaskAction<T, U> action) {
                return setAction(role, action);
            }

            private <T extends Package> TaskBuilder copyAction(ActionRole role, CopyAppImageTaskAction<T> action) {
                return setAction(role, action);
            }

            private <T extends Package, U extends AppImageLayout> TaskBuilder packageAction(ActionRole role, PackageTaskAction<T, U> action) {
                return setAction(role, action);
            }

            private TaskBuilder action(ActionRole role, NoArgTaskAction action) {
                return setAction(role, action);
            }

            private <T extends Application, U extends AppImageLayout> TaskBuilder logAppImageAction(ActionRole role, String keyId, Function<AppImageBuildEnv<T, U>, Object[]> formatArgsSupplier) {
                Objects.requireNonNull(keyId);
                return appImageAction(role, (AppImageBuildEnv<T, U> env) -> {
                    Log.verbose(I18N.format(keyId, formatArgsSupplier.apply(env)));
                });
            }

            private <T extends Package, U extends AppImageLayout> TaskBuilder logPackageAction(ActionRole role, String keyId, Function<PackageBuildEnv<T, U>, Object[]> formatArgsSupplier) {
                Objects.requireNonNull(keyId);
                return packageAction(role, (PackageBuildEnv<T, U> env) -> {
                    Log.verbose(I18N.format(keyId, formatArgsSupplier.apply(env)));
                });
            }

            private TaskBuilder logAction(ActionRole role, String keyId, Supplier<Object[]> formatArgsSupplier) {
                Objects.requireNonNull(keyId);
                return action(role, () -> {
                    Log.verbose(I18N.format(keyId, formatArgsSupplier.get()));
                });
            }

            private TaskAction workloadAction;
            private TaskAction beforeAction;
            private TaskAction afterAction;
        }

        Builder linkTasks(DirectedEdge<TaskID> edge) {
            taskGraphBuilder.addEdge(edge);
            if (taskGraphSnapshot != null) {
                taskGraphSnapshot = null;
            }
            return this;
        }

        Builder linkTasks(TaskID tail, TaskID head) {
            return linkTasks(DirectedEdge.create(tail, head));
        }

        TaskBuilder task(TaskID id) {
            return Optional.ofNullable(taskConfig.get(id)).map(taskConfig -> {
                return new TaskBuilder(id, taskConfig);
            }).orElseGet(() -> {
                return new TaskBuilder(id);
            });
        }

        Stream<TaskBuilder> configuredTasks() {
            return taskConfig.entrySet().stream().map(e -> {
                return new TaskBuilder(e.getKey(), e.getValue());
            });
        }

        Builder excludeDirFromCopying(Path path) {
            Objects.requireNonNull(path);
            excludeCopyDirs.add(path);
            return this;
        }

        Builder contextMapper(UnaryOperator<TaskContext> v) {
            contextMapper = v;
            return this;
        }

        FixedDAG<TaskID> taskGraphSnapshot() {
            if (taskGraphSnapshot == null) {
                taskGraphSnapshot = taskGraphBuilder.create();
            }
            return taskGraphSnapshot;
        }

        StartupParameters createStartupParameters(BuildEnv env, Package pkg, Path outputDir) {
            return createPackagingTaskContext(env, pkg, outputDir, taskConfig);
        }

        PackagingPipeline create() {
            return new PackagingPipeline(taskGraphSnapshot(), taskConfig,
                    Optional.ofNullable(contextMapper).orElse(UnaryOperator.identity()));
        }

        private final FixedDAG.Builder<TaskID> taskGraphBuilder = FixedDAG.build();
        private final List<Path> excludeCopyDirs = new ArrayList<>();
        private final Map<TaskID, TaskConfig> taskConfig = new HashMap<>();
        private UnaryOperator<TaskContext> contextMapper;
        private FixedDAG<TaskID> taskGraphSnapshot;
    }

    static Builder build() {
        return new Builder();
    }

    static Builder buildStandard() {
        final var builder = build();

        configureApplicationTasks(builder);
        configurePackageTasks(builder);

        return builder;
    }

    static Builder configureApplicationTasks(Builder builder) {
        builder.task(BuildApplicationTaskID.RUNTIME)
                .addDependent(BuildApplicationTaskID.CONTENT)
                .applicationAction(ApplicationImageUtils.createWriteRuntimeAction()).add();

        builder.task(BuildApplicationTaskID.LAUNCHERS)
                .addDependent(BuildApplicationTaskID.CONTENT)
                .applicationAction(ApplicationImageUtils.createWriteLaunchersAction()).add();

        builder.task(BuildApplicationTaskID.APP_IMAGE_FILE)
                .addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE)
                .applicationAction(ApplicationImageUtils.createWriteAppImageFileAction()).add();

        builder.task(BuildApplicationTaskID.CONTENT)
                .addDependent(BuildApplicationTaskID.APP_IMAGE_FILE)
                .applicationAction(ApplicationImageUtils.createCopyContentAction(() -> builder.excludeCopyDirs)).add();

        return builder;
    }

    static Builder configurePackageTasks(Builder builder) {
        builder.task(CopyAppImageTaskID.COPY)
                .copyAction(PackagingPipeline::copyAppImage)
                .addDependent(PrimaryTaskID.COPY_APP_IMAGE)
                .add();

        builder.task(PrimaryTaskID.COPY_APP_IMAGE).add();

        builder.task(PrimaryTaskID.BUILD_APPLICATION_IMAGE).add();

        builder.task(PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT)
                .addDependencies(PrimaryTaskID.BUILD_APPLICATION_IMAGE, PrimaryTaskID.COPY_APP_IMAGE)
                .addDependency(PackageTaskID.CREATE_CONFIG_FILES)
                .addDependent(PackageTaskID.CREATE_PACKAGE_FILE)
                .packageAction(PackagingPipeline::runPostAppImageUserScript).add();

        builder.task(PackageTaskID.CREATE_CONFIG_FILES)
                .addDependent(PackageTaskID.CREATE_PACKAGE_FILE)
                .add();

        builder.task(PackageTaskID.CREATE_PACKAGE_FILE)
                .addDependent(PrimaryTaskID.PACKAGE)
                .logActionBegin("message.create-package")
                .logActionEnd("message.package-created")
                .add();

        builder.task(PrimaryTaskID.PACKAGE).add();

        return builder;
    }

    static void copyAppImage(Package pkg, AppImageLayout srcAppImage, AppImageLayout dstAppImage) throws IOException {
        copyAppImage(srcAppImage, dstAppImage, true);
    }

    static void copyAppImage(AppImageLayout srcAppImage, AppImageLayout dstAppImage,
            boolean removeAppImageFile) throws IOException {
        final var srcLayoutPathGroup = toPathGroup(srcAppImage);

        if (removeAppImageFile && srcAppImage instanceof ApplicationLayout appLayout) {
            // Copy app layout omitting application image info file.
            srcLayoutPathGroup.ghostPath(AppImageFile.getPathInAppImage(appLayout));
        }

        srcLayoutPathGroup.copy(toPathGroup(dstAppImage), LinkOption.NOFOLLOW_LINKS);
    }

    static void runPostAppImageUserScript(PackageBuildEnv<Package, AppImageLayout> env) throws IOException {
        final var appImageDir = env.env().appImageDir();
        new ScriptRunner()
                .setDirectory(appImageDir)
                .setResourceCategoryId("resource.post-app-image-script")
                .setScriptNameSuffix("post-image")
                .setEnvironmentVariable("JpAppImageDir", appImageDir.toAbsolutePath().toString())
                .run(env.env(), env.pkg().app().name());
    }

    static void deleteOutputBundle(PackageBuildEnv<Package, AppImageLayout> env) throws IOException {

        var outputBundle = env.outputDir().resolve(env.pkg().packageFileNameWithSuffix());

        try {
            Files.deleteIfExists(outputBundle);
        } catch (IOException ex) {
            throw new JPackageException(I18N.format("error.output-bundle-cannot-be-overwritten", outputBundle.toAbsolutePath()), ex);
        }
    }

    private PackagingPipeline(FixedDAG<TaskID> taskGraph, Map<TaskID, TaskConfig> taskConfig,
            UnaryOperator<TaskContext> contextMapper) {
        this.taskGraph = Objects.requireNonNull(taskGraph);
        this.taskConfig = Objects.requireNonNull(taskConfig);
        this.contextMapper = Objects.requireNonNull(contextMapper);

        if (TRACE_TASK_GRAPTH) {
            taskGraph.dumpToStdout();
        }
    }

    private TaskContext createTaskContext(BuildEnv env, Application app) {
        return new DefaultTaskContext(taskGraph, env, app, Optional.empty());
    }

    private TaskContext createTaskContext(PackagingTaskContext packagingContext) {
        return new DefaultTaskContext(taskGraph, packagingContext.env(),
                packagingContext.pkg().app(), Optional.of(packagingContext));
    }

    private static PackagingTaskContext createPackagingTaskContext(BuildEnv env, Package pkg,
            Path outputDir, Map<TaskID, TaskConfig> taskConfig) {

        Objects.requireNonNull(env);
        Objects.requireNonNull(outputDir);
        Objects.requireNonNull(taskConfig);
        if (pkg.appImageLayout().isResolved()) {
            throw new IllegalArgumentException();
        }

        final AppImageLayout srcLayout;
        final AppImageLayout dstLayout;
        if (pkg.app().runtimeBuilder().isPresent()) {
            // Runtime builder is present, will build application image.
            srcLayout = pkg.appImageLayout().resolveAt(env.appImageDir());
            dstLayout = srcLayout;
        } else {
            srcLayout = pkg.predefinedAppImage().map(predefinedAppImage -> {
                // Will create a package from the predefined app image.
                if (predefinedAppImage.equals(env.appImageDir())) {
                    return env.appImageLayout();
                } else {
                    return pkg.appImageLayout().resolveAt(predefinedAppImage);
                }
            }).orElseGet(() -> {
                // No predefined app image and no runtime builder.
                // This should be runtime packaging.
                if (pkg.isRuntimeInstaller()) {
                    return env.appImageLayout();
                } else {
                    // Can't create app image without runtime builder.
                    throw new UnsupportedOperationException();
                }
            });

            if (taskConfig.get(CopyAppImageTaskID.COPY).action().isEmpty()) {
                // "copy app image" task action is empty indicating
                // the package will use provided app image in place.
                dstLayout = srcLayout;
            } else {
                dstLayout = pkg.appImageLayout().resolveAt(env.buildRoot().resolve("image"));
            }
        }

        return new PackagingTaskContext(BuildEnv.withAppImageLayout(env, dstLayout), pkg, outputDir, srcLayout);
    }

    private void execute(TaskContext context) {
        final Map<TaskID, Callable<Void>> tasks = taskConfig.entrySet().stream().collect(toMap(Map.Entry::getKey, task -> {
            return createTask(context, task.getKey(), task.getValue());
        }));

        final var builder = new TaskPipelineBuilder();

        for (final var tail : taskGraph.nodes()) {
            for (final var head : taskGraph.getHeadsOf(tail)) {
                builder.linkTasks(tasks.get(tail), tasks.get(head));
            }
        }

        try {
            builder.create().call();
        } catch (Exception ex) {
            throw ExceptionBox.toUnchecked(ex);
        }
    }

    private record PackagingTaskContext(BuildEnv env, Package pkg, Path outputDir,
            AppImageLayout srcAppImage) implements TaskContext, StartupParameters {

        PackagingTaskContext {
            Objects.requireNonNull(env);
            Objects.requireNonNull(pkg);
            Objects.requireNonNull(outputDir);
            Objects.requireNonNull(srcAppImage);
        }

        @Override
        public BuildEnv packagingEnv() {
            return env;
        }

        @Override
        public boolean test(TaskID taskID) {
            if (taskID == BuildApplicationTaskID.APP_IMAGE_FILE) {
                // Application image for packaging, skip adding application image info file.
                return false;
            }

            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(TaskAction taskAction) throws IOException {
            if (taskAction instanceof PackageTaskAction<?, ?>) {
                ((PackageTaskAction<Package, AppImageLayout>)taskAction).execute(pkgBuildEnv());
            } else if (taskAction instanceof CopyAppImageTaskAction<?>) {
                ((CopyAppImageTaskAction<Package>)taskAction).execute(pkg(),
                        srcAppImage, env.appImageLayout());
            } else {
                throw new IllegalArgumentException();
            }
        }

        AppImageBuildEnv<Application, AppImageLayout> appImageBuildEnv() {
            return new AppImageBuildEnv<>(env, pkg.app());
        }

        PackageBuildEnv<Package, AppImageLayout> pkgBuildEnv() {
            return new PackageBuildEnv<>(env, pkg, outputDir);
        }
    }

    private record DefaultTaskContext(FixedDAG<TaskID> taskGraph, BuildEnv env, Application app,
            Optional<PackagingTaskContext> pkg) implements TaskContext {

        DefaultTaskContext {
            Objects.requireNonNull(taskGraph);
            Objects.requireNonNull(env);
            Objects.requireNonNull(app);
            Objects.requireNonNull(pkg);
        }

        @Override
        public boolean test(TaskID taskID) {
            final var isBuildApplicationImageTask = isBuildApplicationImageTask(taskID);
            final var isCopyAppImageTask = isCopyAppImageTask(taskID);
            final var isPackageTask = !isBuildApplicationImageTask && !isCopyAppImageTask;

            if (pkg.isPresent() && !pkg.orElseThrow().test(taskID)) {
                return false;
            } else if (pkg.isEmpty() && (isPackageTask || isCopyAppImageTask)) {
                // Building application image, skip packaging and copying app image tasks.
                return false;
            } else if (pkg.isPresent() && app.runtimeBuilder().isEmpty() && isBuildApplicationImageTask && !isCopyAppImageTask) {
                // Building a package, runtime builder is not present, skip building application image tasks.
                return false;
            } else if (app.runtimeBuilder().isPresent() && isCopyAppImageTask && !isBuildApplicationImageTask) {
                // Runtime builder is present, skip copying app image tasks.
                return false;
            } else {
                return true;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(TaskAction taskAction) throws IOException {
            if (taskAction instanceof AppImageTaskAction<?, ?>) {
                final var taskEnv = pkg.map(PackagingTaskContext::appImageBuildEnv).orElseGet(this::appBuildEnv);
                ((AppImageTaskAction<Application, AppImageLayout>)taskAction).execute(taskEnv);
            } else if (taskAction instanceof ApplicationImageTaskAction<?, ?>) {
                ((ApplicationImageTaskAction<Application, ApplicationLayout>)taskAction).execute(appBuildEnv());
            } else if (taskAction instanceof NoArgTaskAction noArgAction) {
                noArgAction.execute();
            } else {
                pkg.orElseThrow(UnsupportedOperationException::new).execute(taskAction);
            }
        }

        private boolean isBuildApplicationImageTask(TaskID taskID) {
            return (taskID == PrimaryTaskID.BUILD_APPLICATION_IMAGE
                    || taskGraph.getAllHeadsOf(taskID).contains(PrimaryTaskID.BUILD_APPLICATION_IMAGE));
        }

        private boolean isCopyAppImageTask(TaskID taskID) {
            return (taskID == PrimaryTaskID.COPY_APP_IMAGE
                    || taskGraph.getAllHeadsOf(taskID).contains(PrimaryTaskID.COPY_APP_IMAGE));
        }

        @SuppressWarnings("unchecked")
        private <T extends AppImageLayout> AppImageBuildEnv<Application, T> appBuildEnv() {
            return new AppImageBuildEnv<>(env, app);
        }
    }

    private static Callable<Void> createTask(TaskContext context, TaskID id, TaskConfig config) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(id);
        Objects.requireNonNull(config);
        return () -> {

            final var withAction = config.action.isPresent();
            final var accepted = withAction && context.test(id);

            if (TRACE_TASK_ACTION) {
                var sb = new StringBuilder();
                sb.append("Execute task=[").append(id).append("]: ");
                if (!withAction) {
                    sb.append("no action");
                } else if (!accepted) {
                    sb.append("rejected");
                } else {
                    sb.append("run");
                }
                System.out.println(sb);
            }

            if (accepted) {
                if (config.beforeAction.isPresent()) {
                    context.execute(config.beforeAction.orElseThrow());
                }
                context.execute(config.action.orElseThrow());
                if (config.afterAction.isPresent()) {
                    context.execute(config.afterAction.orElseThrow());
                }
            }

            return null;
        };
    }

    private final FixedDAG<TaskID> taskGraph;
    private final Map<TaskID, TaskConfig> taskConfig;
    private final UnaryOperator<TaskContext> contextMapper;

    private static final boolean TRACE_TASK_GRAPTH = false;
    private static final boolean TRACE_TASK_ACTION = false;
}
