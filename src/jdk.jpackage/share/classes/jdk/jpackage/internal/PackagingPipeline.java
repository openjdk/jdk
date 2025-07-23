/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.pipeline.DirectedEdge;
import jdk.jpackage.internal.pipeline.FixedDAG;
import jdk.jpackage.internal.pipeline.TaskPipelineBuilder;
import jdk.jpackage.internal.pipeline.TaskSpecBuilder;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class PackagingPipeline {

    void execute(BuildEnv env, Application app) throws PackagerException {
        execute(appContextMapper.apply(createTaskContext(env, app)));
    }

    void execute(BuildEnv env, Package pkg, Path outputDir) throws PackagerException {
        execute((StartupParameters)createPackagingTaskContext(env, pkg, outputDir,
                taskConfig, appImageLayoutForPackaging.apply(pkg)));
    }

    void execute(StartupParameters startupParameters) throws PackagerException {
        execute(pkgContextMapper.apply(createTaskContext((PackagingTaskContext)startupParameters)));
    }

    interface StartupParameters {
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
        CREATE_PACKAGE_FILE
    }

    interface TaskContext extends Predicate<TaskID> {
        void execute(TaskAction taskAction) throws IOException, PackagerException;
    }

    record AppImageBuildEnv<T extends Application, U extends AppImageLayout>(BuildEnv env, T app, U envLayout) {
        @SuppressWarnings("unchecked")
        U resolvedLayout() {
            return (U)envLayout.resolveAt(env.appImageDir());
        }
    }

    record PackageBuildEnv<T extends Package, U extends AppImageLayout>(BuildEnv env, T pkg, U envLayout, Path outputDir) {
        @SuppressWarnings("unchecked")
        U resolvedLayout() {
            return (U)envLayout.resolveAt(env.appImageDir());
        }

        AppImageBuildEnv<Application, U> appImageBuildEnv() {
            return new AppImageBuildEnv<>(env, pkg.app(), envLayout);
        }
    }

    @FunctionalInterface
    interface ApplicationImageTaskAction<T extends Application, U extends ApplicationLayout> extends TaskAction {
        void execute(AppImageBuildEnv<T, U> env) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface AppImageTaskAction<T extends Application, U extends AppImageLayout> extends TaskAction {
        void execute(AppImageBuildEnv<T, U> env) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface CopyAppImageTaskAction<T extends Package> extends TaskAction {
        void execute(T pkg, AppImageDesc srcAppImage, AppImageDesc dstAppImage) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface PackageTaskAction<T extends Package, U extends AppImageLayout> extends TaskAction {
        void execute(PackageBuildEnv<T, U> env) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface NoArgTaskAction extends TaskAction {
        void execute() throws IOException, PackagerException;
    }

    record TaskConfig(Optional<TaskAction> action) {
        TaskConfig {
            Objects.requireNonNull(action);
        }
    }

    static final class Builder {

        private Builder() {
        }

        final class TaskBuilder extends TaskSpecBuilder<TaskID> {

            private TaskBuilder(TaskID id) {
                super(id);
            }

            private TaskBuilder setAction(TaskAction v) {
                action = v;
                return this;
            }

            TaskBuilder noaction() {
                action = null;
                return this;
            }

            <T extends Application, U extends ApplicationLayout> TaskBuilder applicationAction(ApplicationImageTaskAction<T, U> action) {
                return setAction(action);
            }

            <T extends Application, U extends AppImageLayout> TaskBuilder appImageAction(AppImageTaskAction<T, U> action) {
                return setAction(action);
            }

            <T extends Package> TaskBuilder copyAction(CopyAppImageTaskAction<T> action) {
                return setAction(action);
            }

            <T extends Package, U extends AppImageLayout> TaskBuilder packageAction(PackageTaskAction<T, U> action) {
                return setAction(action);
            }

            TaskBuilder action(NoArgTaskAction action) {
                return setAction(action);
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
                final var config = new TaskConfig(Optional.ofNullable(action));
                taskConfig.put(task(), config);
                createLinks().forEach(Builder.this::linkTasks);
                return Builder.this;
            }

            private TaskAction action;
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
            return new TaskBuilder(id);
        }

        Builder excludeDirFromCopying(Path path) {
            Objects.requireNonNull(path);
            excludeCopyDirs.add(path);
            return this;
        }

        Builder contextMapper(UnaryOperator<TaskContext> v) {
            appContextMapper(v);
            pkgContextMapper(v);
            return this;
        }

        Builder appContextMapper(UnaryOperator<TaskContext> v) {
            appContextMapper = v;
            return this;
        }

        Builder pkgContextMapper(UnaryOperator<TaskContext> v) {
            pkgContextMapper = v;
            return this;
        }

        Builder appImageLayoutForPackaging(Function<Package, AppImageLayout> v) {
            appImageLayoutForPackaging = v;
            return this;
        }

        FixedDAG<TaskID> taskGraphSnapshot() {
            if (taskGraphSnapshot == null) {
                taskGraphSnapshot = taskGraphBuilder.create();
            }
            return taskGraphSnapshot;
        }

        StartupParameters createStartupParameters(BuildEnv env, Package pkg, Path outputDir) {
            return createPackagingTaskContext(env, pkg, outputDir, taskConfig,
                    validatedAppImageLayoutForPackaging().apply(pkg));
        }

        private Function<Package, AppImageLayout> validatedAppImageLayoutForPackaging() {
            return Optional.ofNullable(appImageLayoutForPackaging).orElse(Package::packageLayout);
        }

        PackagingPipeline create() {
            return new PackagingPipeline(taskGraphSnapshot(), taskConfig,
                    Optional.ofNullable(appContextMapper).orElse(UnaryOperator.identity()),
                    Optional.ofNullable(pkgContextMapper).orElse(UnaryOperator.identity()),
                    validatedAppImageLayoutForPackaging());
        }

        private final FixedDAG.Builder<TaskID> taskGraphBuilder = FixedDAG.build();
        private final List<Path> excludeCopyDirs = new ArrayList<>();
        private final Map<TaskID, TaskConfig> taskConfig = new HashMap<>();
        private UnaryOperator<TaskContext> appContextMapper;
        private UnaryOperator<TaskContext> pkgContextMapper;
        private Function<Package, AppImageLayout> appImageLayoutForPackaging;
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
                .add();

        builder.task(PrimaryTaskID.PACKAGE).add();

        return builder;
    }

    static void copyAppImage(Package pkg, AppImageDesc srcAppImage, AppImageDesc dstAppImage) throws IOException {
        copyAppImage(srcAppImage, dstAppImage, true);
    }

    static void copyAppImage(AppImageDesc srcAppImage, AppImageDesc dstAppImage,
            boolean removeAppImageFile) throws IOException {
        final var srcLayout = srcAppImage.resolvedAppImagelayout();
        final var srcLayoutPathGroup = AppImageLayout.toPathGroup(srcLayout);

        if (removeAppImageFile && srcLayout instanceof ApplicationLayout appLayout) {
            // Copy app layout omitting application image info file.
            srcLayoutPathGroup.ghostPath(AppImageFile.getPathInAppImage(appLayout));
        }

        srcLayoutPathGroup.copy(AppImageLayout.toPathGroup(dstAppImage.resolvedAppImagelayout()), LinkOption.NOFOLLOW_LINKS);
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

    private PackagingPipeline(FixedDAG<TaskID> taskGraph, Map<TaskID, TaskConfig> taskConfig,
            UnaryOperator<TaskContext> appContextMapper, UnaryOperator<TaskContext> pkgContextMapper,
            Function<Package, AppImageLayout> appImageLayoutForPackaging) {
        this.taskGraph = Objects.requireNonNull(taskGraph);
        this.taskConfig = Objects.requireNonNull(taskConfig);
        this.appContextMapper = Objects.requireNonNull(appContextMapper);
        this.pkgContextMapper = Objects.requireNonNull(pkgContextMapper);
        this.appImageLayoutForPackaging = Objects.requireNonNull(appImageLayoutForPackaging);
    }

    private TaskContext createTaskContext(BuildEnv env, Application app) {
        return new DefaultTaskContext(taskGraph, env, app, app.asApplicationLayout(), Optional.empty());
    }

    private TaskContext createTaskContext(PackagingTaskContext packagingContext) {
        final var pkgEnv = BuildEnv.withAppImageDir(packagingContext.env.env(), packagingContext.srcAppImage.path());
        return new DefaultTaskContext(taskGraph, pkgEnv, packagingContext.env.pkg.app(),
                packagingContext.srcAppImage.asApplicationLayout(), Optional.of(packagingContext));
    }

    private static PackagingTaskContext createPackagingTaskContext(BuildEnv env, Package pkg,
            Path outputDir, Map<TaskID, TaskConfig> taskConfig, AppImageLayout appImageLayoutForPackaging) {

        Objects.requireNonNull(env);
        Objects.requireNonNull(outputDir);
        Objects.requireNonNull(taskConfig);
        Objects.requireNonNull(appImageLayoutForPackaging);

        final AppImageDesc srcAppImageDesc;
        final AppImageDesc dstAppImageDesc;
        if (pkg.app().runtimeBuilder().isPresent()) {
            // Runtime builder is present, will build application image.
            // appImageDir() should point to a directory where the application image will be created.
            srcAppImageDesc = new AppImageDesc(appImageLayoutForPackaging, env.appImageDir());
            dstAppImageDesc = srcAppImageDesc;
        } else {
            srcAppImageDesc = new AppImageDesc(pkg.app().imageLayout(),
                    pkg.predefinedAppImage().orElseThrow(UnsupportedOperationException::new));

            if (taskConfig.get(CopyAppImageTaskID.COPY).action().isEmpty()) {
                // "copy app image" task action is undefined indicating
                // the package will use provided app image as-is.
                dstAppImageDesc = srcAppImageDesc;
            } else {
                dstAppImageDesc = new AppImageDesc(appImageLayoutForPackaging, env.buildRoot().resolve("image"));
            }
        }

        final var pkgEnv = new PackageBuildEnv<>(
                BuildEnv.withAppImageDir(env, dstAppImageDesc.path()), pkg, dstAppImageDesc.appImageLayout(), outputDir);

        return new PackagingTaskContext(pkgEnv, srcAppImageDesc);
    }

    private void execute(TaskContext context) throws PackagerException {
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
            if (ex instanceof PackagerException pex) {
                throw pex;
            } else if (ex instanceof ExceptionBox bex) {
                throw new PackagerException(bex.getCause());
            } else {
                throw new PackagerException(ex);
            }
        }
    }

    private record PackagingTaskContext(PackageBuildEnv<Package, AppImageLayout> env,
            AppImageDesc srcAppImage) implements TaskContext, StartupParameters {

        PackagingTaskContext {
            Objects.requireNonNull(env);
            Objects.requireNonNull(srcAppImage);
        }

        @Override
        public BuildEnv packagingEnv() {
            return env.env;
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
        public void execute(TaskAction taskAction) throws IOException, PackagerException {
            if (taskAction instanceof PackageTaskAction<?, ?>) {
                ((PackageTaskAction<Package, AppImageLayout>)taskAction).execute(env);
            } else if (taskAction instanceof CopyAppImageTaskAction<?>) {
                ((CopyAppImageTaskAction<Package>)taskAction).execute(env.pkg(),
                        srcAppImage, new AppImageDesc(env.envLayout(), env.env().appImageDir()));
            } else {
                throw new IllegalArgumentException();
            }
        }

        AppImageBuildEnv<Application, AppImageLayout> appImageBuildEnv() {
            return env.appImageBuildEnv();
        }
    }

    private record DefaultTaskContext(FixedDAG<TaskID> taskGraph, BuildEnv env, Application app,
            Optional<ApplicationLayout> appLayout, Optional<PackagingTaskContext> pkg) implements TaskContext {

        DefaultTaskContext {
            Objects.requireNonNull(taskGraph);
            Objects.requireNonNull(env);
            Objects.requireNonNull(app);
            Objects.requireNonNull(appLayout);
            Objects.requireNonNull(pkg);
        }

        @Override
        public boolean test(TaskID taskID) {
            final var isBuildApplicationImageTask = isBuildApplicationImageTask(taskID);
            final var isCopyAppImageTask = isCopyAppImageTask(taskID);
            final var isPackageTask = !isBuildApplicationImageTask && !isCopyAppImageTask;

            if (pkg.isPresent() && !pkg.orElseThrow().test(taskID)) {
                return false;
            } else if (pkg.isEmpty() && isPackageTask) {
                // Building application image, skip packaging tasks.
                return false;
            } else if (app.runtimeBuilder().isEmpty() && isBuildApplicationImageTask && !isCopyAppImageTask) {
                // Runtime builder is not present, skip building application image tasks.
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
        public void execute(TaskAction taskAction) throws IOException, PackagerException {
            if (taskAction instanceof AppImageTaskAction<?, ?>) {
                final var taskEnv = pkg.map(PackagingTaskContext::appImageBuildEnv).orElseGet(this::appBuildEnv);
                ((AppImageTaskAction<Application, AppImageLayout>)taskAction).execute(taskEnv);
            } else if (taskAction instanceof ApplicationImageTaskAction<?, ?>) {
                ((ApplicationImageTaskAction<Application, ApplicationLayout>)taskAction).execute(appBuildEnv());
            } else if (taskAction instanceof NoArgTaskAction noArgAction) {
                noArgAction.execute();
            } else {
                pkg.orElseThrow().execute(taskAction);
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
            return new AppImageBuildEnv<>(env, app, (T)appLayout.orElseThrow());
        }
    }

    private static Callable<Void> createTask(TaskContext context, TaskID id, TaskConfig config) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(id);
        Objects.requireNonNull(config);
        return () -> {
            if (config.action.isPresent() && context.test(id)) {
                try {
                    context.execute(config.action.orElseThrow());
                } catch (ExceptionBox ex) {
                    throw ExceptionBox.rethrowUnchecked(ex);
                }
            }
            return null;
        };
    }

    private final FixedDAG<TaskID> taskGraph;
    private final Map<TaskID, TaskConfig> taskConfig;
    private final Function<Package, AppImageLayout> appImageLayoutForPackaging;
    private final UnaryOperator<TaskContext> appContextMapper;
    private final UnaryOperator<TaskContext> pkgContextMapper;
}
