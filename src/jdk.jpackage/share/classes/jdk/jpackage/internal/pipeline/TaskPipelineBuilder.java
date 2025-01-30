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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

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
                taskGraphBuilder.addEdge(task, ROOT_TASK);
                taskGraphBuilder.canAddEdgeToUnknownNode(false);
            } else {
                taskGraphBuilder.addEdge(task, Optional.ofNullable(dependent).orElse(ROOT_TASK));
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

    public TaskPipelineBuilder executor(Executor v) {
        executor = v;
        return this;
    }

    public TaskPipelineBuilder sequentialExecutor() {
        executor = null;
        return this;
    }

    public Callable<Void> create() {
        final var taskGraph = taskGraphBuilder.create();

        if (executor == null) {
            final var tasks = taskGraph.topologicalSort();
            // Don't run the root task as it is NOP.
            return new SequentialWrapperTask(tasks.subList(0, tasks.size() - 1));
        } else {
            return new ParallelWrapperTask(taskGraph, executor);
        }
    }

    private record SequentialWrapperTask(List<Callable<Void>> tasks) implements Callable<Void> {

        SequentialWrapperTask {
            Objects.requireNonNull(tasks);
        }

        @Override
        public Void call() throws Exception {
            for (final var task : tasks) {
                task.call();
            }
            return null;
        }

    }

    private record ParallelWrapperTask(ImmutableDAG<Callable<Void>> taskGraph, Executor executor) implements Callable<Void> {

        ParallelWrapperTask {
            Objects.requireNonNull(taskGraph);
            Objects.requireNonNull(executor);
        }

        @Override
        public Void call() throws Exception {

            final var taskFutures = new CompletableFuture<?>[taskGraph.nodes().size()];

            CompletableFuture<?> lastFuture = null;

            // Schedule tasks in the order they should be executed: dependencies before dependents.
            for (final var task : taskGraph.topologicalSort()) {
                final var taskIndex = taskGraph.nodes().indexOf(task);

                final var dependencyTaskFutures = ImmutableDAG.getIncomingEdges(taskIndex, taskGraph.edgeMatrix())
                        .map(BinaryMatrix.Cursor::row)
                        .map(dependencyTaskIndex -> {
                            return taskFutures[dependencyTaskIndex];
                        }).toArray(CompletableFuture<?>[]::new);

                if (dependencyTaskFutures.length == 0) {
                    lastFuture = CompletableFuture.runAsync(toRunnable(task), executor);
                } else {
                    lastFuture = CompletableFuture.allOf(dependencyTaskFutures);
                    if (task != ROOT_TASK) {
                        lastFuture = lastFuture.thenRun(toRunnable(task));
                    }
                }

                taskFutures[taskIndex] = lastFuture;
            }

            try {
                lastFuture.get();
            } catch (ExecutionException ee) {
                if (ee.getCause() instanceof Exception ex) {
                    throw ex;
                } else {
                    throw ee;
                }
            }

            return null;
        }

        private static Runnable toRunnable(Callable<Void> callable) {
            return () -> {
                try {
                    callable.call();
                } catch (Error er) {
                    throw er;
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Throwable t) {
                    throw new CompletionException(t);
                }
            };
        }

    }

    private static final Callable<Void> ROOT_TASK = new Callable<>() {

        @Override
        public Void call() {
            // Should never get called.
            throw new UnsupportedOperationException();
        }
    };

    private ImmutableDAG.Builder<Callable<Void>> taskGraphBuilder;
    private Executor executor;
}
