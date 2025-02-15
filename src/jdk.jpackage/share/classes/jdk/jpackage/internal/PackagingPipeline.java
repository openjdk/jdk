/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.pipeline.DirectedEdge;
import jdk.jpackage.internal.pipeline.ImmutableDAG;
import jdk.jpackage.internal.pipeline.TaskPipelineBuilder;
import jdk.jpackage.internal.pipeline.TaskSpecBuilder;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class PackagingPipeline {

    void execute(BuildEnv env, Application app) throws PackagerException {
        execute(appContextMapper.apply(createTaskContext(env, app)));
    }

    void execute(BuildEnv env, Package pkg, Path outputDir) throws PackagerException {
        execute(pkgContextMapper.apply(createTaskContext(env, pkg, outputDir)));
    }

    AppImageInfo analyzeAppImageDir(BuildEnv env, Package pkg) {
        if (pkg.app().runtimeBuilder().isPresent()) {
            // Runtime builder is present, return the path where app image will be created.
            return new AppImageInfo(inputApplicationLayoutForPackaging.apply(pkg).orElseThrow(), env.appImageDir());
        } else {
            return new AppImageInfo(pkg.app().imageLayout(), pkg.predefinedAppImage().orElseGet(() -> {
                // No predefined app image and no runtime builder.
                // This should be runtime packaging.
                if (pkg.isRuntimeInstaller()) {
                    return env.appImageDir();
                } else {
                    // Can't create app image without runtime builder.
                    throw new UnsupportedOperationException();
                }
            }));
        }
    }

    interface TaskAction {
    }

    interface TaskID {
    }

    enum AppImageTaskID implements TaskID {
        RUNTIME,
        CONTENT,
        LAUNCHERS,
        APP_IMAGE_FILE
    }

    enum PrimaryTaskID implements TaskID {
        BUILD_APPLICATION_IMAGE,
        COPY_APP_IMAGE,
        PACKAGE
    }

    interface TaskContext {
        boolean test(TaskID taskID);

        void execute(TaskAction taskAction) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface ApplicationImageTaskAction extends TaskAction {
        void execute(BuildEnv env, Application app, ApplicationLayout appLayout) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface CopyAppImageTaskAction extends TaskAction {
        void execute(Package pkg, Path srcAppImageRoot, Path dstAppImageRoot) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface PackageTaskAction extends TaskAction {
        void execute(BuildEnv env, Package pkg) throws IOException, PackagerException;
    }

    @FunctionalInterface
    interface NoArgTaskAction extends TaskAction {
        void execute() throws IOException, PackagerException;
    }

    record TaskConfig(TaskAction action) {
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

            TaskBuilder action(ApplicationImageTaskAction action) {
                return setAction(action);
            }

            TaskBuilder copyAction(CopyAppImageTaskAction action) {
                return setAction(action);
            }

            TaskBuilder action(PackageTaskAction action) {
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

            Builder add() {
                final var config = new TaskConfig(action);
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

        Builder pkgBuildEnvFactory(BiFunction<BuildEnv, Package, BuildEnv> v) {
            pkgBuildEnvFactory = v;
            return this;
        }

        Builder inputApplicationLayoutForPackaging(Function<Package, Optional<ApplicationLayout>> v) {
            inputApplicationLayoutForPackaging = v;
            return this;
        }

        ImmutableDAG<TaskID> taskGraphSnapshot() {
            if (taskGraphSnapshot == null) {
                taskGraphSnapshot = taskGraphBuilder.create();
            }
            return taskGraphSnapshot;
        }

        PackagingPipeline create() {
            return new PackagingPipeline(taskGraphSnapshot(), taskConfig,
                    Optional.ofNullable(appContextMapper).orElse(UnaryOperator.identity()),
                    Optional.ofNullable(pkgContextMapper).orElse(UnaryOperator.identity()),
                    Optional.ofNullable(inputApplicationLayoutForPackaging).orElse(Package::asPackageApplicationLayout),
                    Optional.ofNullable(pkgBuildEnvFactory).orElse((env, pkg) -> {
                        return BuildEnv.withAppImageDir(env, env.buildRoot().resolve("image"));
                    }));
        }

        private final ImmutableDAG.Builder<TaskID> taskGraphBuilder = ImmutableDAG.build();
        private final List<Path> excludeCopyDirs = new ArrayList<>();
        private final Map<TaskID, TaskConfig> taskConfig = new HashMap<>();
        private UnaryOperator<TaskContext> appContextMapper;
        private UnaryOperator<TaskContext> pkgContextMapper;
        private Function<Package, Optional<ApplicationLayout>> inputApplicationLayoutForPackaging;
        private BiFunction<BuildEnv, Package, BuildEnv> pkgBuildEnvFactory;
        private ImmutableDAG<TaskID> taskGraphSnapshot;
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
        builder.task(AppImageTaskID.RUNTIME)
                .addDependent(AppImageTaskID.CONTENT)
                .action(ApplicationImageUtils.createWriteRuntimeAction()).add();

        builder.task(AppImageTaskID.LAUNCHERS)
                .addDependent(AppImageTaskID.CONTENT)
                .action(ApplicationImageUtils.createWriteLaunchersAction()).add();

        builder.task(AppImageTaskID.APP_IMAGE_FILE)
                .addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE)
                .action(ApplicationImageUtils.createWriteAppImageFileAction()).add();

        builder.task(AppImageTaskID.CONTENT)
                .addDependent(AppImageTaskID.APP_IMAGE_FILE)
                .action(ApplicationImageUtils.createCopyContentAction(() -> builder.excludeCopyDirs)).add();

        return builder;
    }

    static Builder configurePackageTasks(Builder builder) {
        builder.task(PrimaryTaskID.COPY_APP_IMAGE).copyAction(createCopyAppImageAction()).add();

        builder.task(PrimaryTaskID.BUILD_APPLICATION_IMAGE).noaction().add();

        builder.task(PrimaryTaskID.PACKAGE)
                .addDependencies(List.of(PrimaryTaskID.BUILD_APPLICATION_IMAGE, PrimaryTaskID.COPY_APP_IMAGE))
                .noaction().add();

        return builder;
    }

    static CopyAppImageTaskAction createCopyAppImageAction() {
        return (pkg, srcAppImageRoot, dstAppImageRoot) -> {
            final var srcLayout = pkg.appImageLayout().resolveAt(srcAppImageRoot);
            final var srcLayoutPathGroup = AppImageLayout.toPathGroup(srcLayout);

            if (srcLayout instanceof ApplicationLayout appLayout) {
                // Copy app layout omitting application image info file.
                srcLayoutPathGroup.ghostPath(AppImageFile.getPathInAppImage(appLayout));
            }

            srcLayoutPathGroup.copy(AppImageLayout.toPathGroup(pkg.packageLayout().resolveAt(dstAppImageRoot)));
        };
    }

    private PackagingPipeline(ImmutableDAG<TaskID> taskGraph, Map<TaskID, TaskConfig> taskConfig,
            UnaryOperator<TaskContext> appContextMapper, UnaryOperator<TaskContext> pkgContextMapper,
            Function<Package, Optional<ApplicationLayout>> inputApplicationLayoutForPackaging,
            BiFunction<BuildEnv, Package, BuildEnv> pkgBuildEnvFactory) {
        this.taskGraph = Objects.requireNonNull(taskGraph);
        this.taskConfig = Objects.requireNonNull(taskConfig);
        this.appContextMapper = Objects.requireNonNull(appContextMapper);
        this.pkgContextMapper = Objects.requireNonNull(pkgContextMapper);
        this.inputApplicationLayoutForPackaging = Objects.requireNonNull(inputApplicationLayoutForPackaging);
        this.pkgBuildEnvFactory = Objects.requireNonNull(pkgBuildEnvFactory);
    }

    private TaskContext createTaskContext(BuildEnv env, Application app) {
        final var appImageLayout = app.asApplicationLayout().map(layout -> layout.resolveAt(env.appImageDir()));
        return new DefaultTaskContext(taskGraph, env, app, appImageLayout, Optional.empty());
    }

    private TaskContext createTaskContext(BuildEnv env, Package pkg, Path outputDir) {
        final BuildEnv pkgEnv;
        if (pkg.app().runtimeBuilder().isPresent()) {
            // Will build application image. Use the same build environment to package it.
            pkgEnv = env;
        } else {
            // Use existing app image. Set up a new directory to copy the existing app image for packaging.
            pkgEnv = pkgBuildEnvFactory.apply(env, pkg);
        }

        final var appImageInfo = analyzeAppImageDir(env, pkg);
        return new DefaultTaskContext(taskGraph, env, pkg.app(),
                appImageInfo.asResolvedApplicationLayout(),
                Optional.of(new PackagingTaskContext(pkg, pkgEnv, appImageInfo.path(), outputDir)));
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
            } else {
                throw new PackagerException(ex);
            }
        }
    }

    private static record PackagingTaskContext(Package pkg, BuildEnv env, Path srcAppImageDir,
            Path outputDir) implements TaskContext {

        PackagingTaskContext {
            Objects.requireNonNull(pkg);
            Objects.requireNonNull(env);
            Objects.requireNonNull(srcAppImageDir);
            Objects.requireNonNull(outputDir);
        }

        @Override
        public boolean test(TaskID taskID) {
            if (taskID == AppImageTaskID.APP_IMAGE_FILE) {
                // Application image for packaging, skip adding application image info file.
                return false;
            }

            return true;
        }

        @Override
        public void execute(TaskAction taskAction) throws IOException, PackagerException {
            if (taskAction instanceof PackageTaskAction pkgAction) {
                pkgAction.execute(env, pkg);
            } else if (taskAction instanceof CopyAppImageTaskAction copyAction) {
                copyAction.execute(pkg, srcAppImageDir, env.appImageDir());
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private static record DefaultTaskContext(ImmutableDAG<TaskID> taskGraph, BuildEnv env, Application app,
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
            if (pkg.isPresent() && !pkg.orElseThrow().test(taskID)) {
                return false;
            } else if (pkg.isEmpty() && isPackageTask(taskID)) {
                // Building application image, skip packaging tasks.
                return false;
            } else if (app.runtimeBuilder().isEmpty() && isBuildApplicationImageTask(taskID)) {
                // Runtime builder is not present, skip building application image tasks.
                return false;
            } else if (app.runtimeBuilder().isPresent() && isCopyAppImageTask(taskID)) {
                // Runtime builder is present, skip copying app image tasks.
                return false;
            } else {
                return true;
            }
        }

        @Override
        public void execute(TaskAction taskAction) throws IOException, PackagerException {
            if (taskAction instanceof ApplicationImageTaskAction appImageAction) {
                appImageAction.execute(env, app, appLayout.orElseThrow());
            } else if (taskAction instanceof NoArgTaskAction noArgAction) {
                noArgAction.execute();
            } else {
                pkg.orElseThrow().execute(taskAction);
            }
        }

        private boolean isPackageTask(TaskID taskID) {
            if (taskID == PrimaryTaskID.PACKAGE) {
                return true;
            } else {
                return Stream.of(PrimaryTaskID.BUILD_APPLICATION_IMAGE,
                        PrimaryTaskID.COPY_APP_IMAGE).noneMatch(taskGraph.getAllHeadsOf(taskID)::contains);
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

    }

    private static Callable<Void> createTask(TaskContext context, TaskID id, TaskConfig config) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(id);
        return () -> {
            if (config.action != null && context.test(id)) {
                try {
                    context.execute(config.action);
                } catch (ExceptionBox ex) {
                    throw ExceptionBox.rethrowUnchecked(ex);
                }
            }
            return null;
        };
    }

    private final ImmutableDAG<TaskID> taskGraph;
    private final Map<TaskID, TaskConfig> taskConfig;
    private final Function<Package, Optional<ApplicationLayout>> inputApplicationLayoutForPackaging;
    private final UnaryOperator<TaskContext> appContextMapper;
    private final UnaryOperator<TaskContext> pkgContextMapper;
    private final BiFunction<BuildEnv, Package, BuildEnv> pkgBuildEnvFactory;
}
