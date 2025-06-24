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

import static jdk.jpackage.internal.pipeline.FixedDAG.getIncomingEdges;
import static jdk.jpackage.internal.pipeline.FixedDAG.getNoOutgoingEdges;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public final class TaskPipelineBuilder {

    public final class TaskBuilder extends TaskSpecBuilder<Callable<Void>> {

        TaskBuilder(Callable<Void> task) {
            super(task);
        }

        @Override
        public TaskBuilder addDependent(Callable<Void> v) {
            super.addDependent(v);
            return this;
        }

        @Override
        public TaskBuilder addDependency(Callable<Void> v) {
            super.addDependency(v);
            return this;
        }

        @Override
        public TaskBuilder addDependencies(Collection<? extends Callable<Void>> v) {
            super.addDependencies(v);
            return this;
        }

        @Override
        public TaskBuilder addDependents(Collection<? extends Callable<Void>> v) {
            super.addDependents(v);
            return this;
        }

        public TaskPipelineBuilder add() {
            final var links = createLinks();
            if (links.isEmpty()) {
                return addTask(task());
            } else {
                links.forEach(TaskPipelineBuilder.this::linkTasks);
                return TaskPipelineBuilder.this;
            }
        }
    }

    public TaskBuilder task(Callable<Void> task) {
        return new TaskBuilder(task);
    }

    public TaskPipelineBuilder executor(Executor v) {
        executor = v;
        return this;
    }

    public TaskPipelineBuilder sequentialExecutor() {
        executor = null;
        return this;
    }

    public TaskPipelineBuilder addTask(Callable<Void> task) {
        taskGraphBuilder.addNode(task);
        return this;
    }

    public TaskPipelineBuilder linkTasks(Callable<Void> tail, Callable<Void> head) {
        return linkTasks(DirectedEdge.create(tail, head));
    }

    public TaskPipelineBuilder linkTasks(DirectedEdge<Callable<Void>> edge) {
        taskGraphBuilder.addEdge(edge);
        return this;
    }

    public Callable<Void> create() {
        final var taskGraph = taskGraphBuilder.create();
        if (executor == null) {
            final var tasks = taskGraph.topologicalSort();
            return new SequentialWrapperTask(tasks);
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

    private record ParallelWrapperTask(FixedDAG<Callable<Void>> taskGraph, Executor executor) implements Callable<Void> {

        ParallelWrapperTask {
            Objects.requireNonNull(taskGraph);
            Objects.requireNonNull(executor);
        }

        @Override
        public Void call() throws Exception {

            final var taskFutures = new CompletableFuture<?>[taskGraph.nodes().size()];

            // Schedule tasks in the order they should be executed: dependencies before dependents.
            for (final var task : taskGraph.topologicalSort()) {
                final var taskIndex = taskGraph.nodes().indexOf(task);

                final var dependencyTaskFutures = getIncomingEdges(taskIndex, taskGraph.edgeMatrix())
                        .map(BinaryMatrix.Cursor::row)
                        .map(dependencyTaskIndex -> {
                            return taskFutures[dependencyTaskIndex];
                        }).toArray(CompletableFuture<?>[]::new);

                final CompletableFuture<?> f;
                if (dependencyTaskFutures.length == 0) {
                    f = CompletableFuture.runAsync(toRunnable(task), executor);
                } else {
                    f = CompletableFuture.allOf(dependencyTaskFutures).thenRun(toRunnable(task));
                }

                taskFutures[taskIndex] = f;
            }

            final var rootFutures = getNoOutgoingEdges(taskGraph.edgeMatrix()).mapToObj(taskIndex -> {
                return taskFutures[taskIndex];
            }).toArray(CompletableFuture[]::new);

            final CompletableFuture <?> rootFuture;
            if (rootFutures.length == 1) {
                rootFuture = rootFutures[0];
            } else {
                rootFuture = CompletableFuture.allOf(rootFutures);
            }

            try {
                rootFuture.get();
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

    private final FixedDAG.Builder<Callable<Void>> taskGraphBuilder = new FixedDAG.Builder<>();
    private Executor executor;
}
