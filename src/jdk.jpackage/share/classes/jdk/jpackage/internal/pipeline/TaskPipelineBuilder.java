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

package jdk.jpackage.internal.pipeline;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;

public final class TaskPipelineBuilder {

    public final class TaskSpecBuilder {

        TaskSpecBuilder(Callable<Void> task) {
            Objects.requireNonNull(task);
            this.task = task;
        }

        public TaskSpecBuilder dependent(Callable<Void> v) {
            dependent = v;
            return this;
        }

        public TaskSpecBuilder addDependency(Callable<Void> v) {
            Objects.requireNonNull(v);
            dependencies.add(v);
            return this;
        }

        public TaskSpecBuilder addDependencies(Collection<? extends Callable<Void>> v) {
            Objects.requireNonNull(v);
            v.forEach(Objects::requireNonNull);
            dependencies.addAll(v);
            return this;
        }

        public TaskPipelineBuilder add() {
            if (taskGraphBuilder == null) {
                taskGraphBuilder = new ImmutableDAG.Builder<>();
                taskGraphBuilder.addEdge(task, ROOT_ACTION);
                taskGraphBuilder.canAddEdgeToUnknownNode(false);
            } else {
                taskGraphBuilder.addEdge(task, Optional.ofNullable(dependent).orElse(ROOT_ACTION));
            }

            for (var dependency : dependencies) {
                taskGraphBuilder.addEdge(dependency, task);
            }

            return TaskPipelineBuilder.this;
        }

        private Set<Callable<Void>> dependencies = new LinkedHashSet<>();
        private Callable<Void> dependent;
        private final Callable<Void> task;
    }

    public TaskSpecBuilder task(Callable<Void> task) {
        return new TaskSpecBuilder(task);
    }

    public TaskPipelineBuilder executor(ForkJoinPool v) {
        Objects.requireNonNull(v);
        fjp = v;
        return this;
    }

    public TaskPipelineBuilder sequentialExecutor() {
        fjp = new ForkJoinPool(1);
        return this;
    }

    public Callable<Void> create() {
        final var taskGraph = taskGraphBuilder.create();

        final var rootCompleter = new CountedCompleterBuilder(taskGraph).create();

        return new WrapperTask(rootCompleter, fjp);
    }

    private record WrapperTask(CountedCompleter<Void> rootCompleter, ForkJoinPool fjp) implements Callable<Void> {

        WrapperTask {
            Objects.requireNonNull(rootCompleter);
            Objects.requireNonNull(fjp);
        }

        @Override
        public Void call() {
            fjp.invoke(rootCompleter);
            return null;
        }

    }

    private static final Callable<Void> ROOT_ACTION = new Callable<Void>() {

        @Override
        public Void call() {
            return null;
        }

        @Override
        public String toString() {
            return super.toString() + "(root)";
        }
    };

    private ImmutableDAG.Builder<Callable<Void>> taskGraphBuilder;
    private ForkJoinPool fjp;
}
