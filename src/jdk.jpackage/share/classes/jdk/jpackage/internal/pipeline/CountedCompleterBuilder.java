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

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

/**
 * Schedules execution of tasks in the given task graph.
 */
final class CountedCompleterBuilder {

    CountedCompleterBuilder(ImmutableDAG<Callable<Void>> taskGraph) {
        this.taskGraph = Objects.requireNonNull(taskGraph);

        runOnce = StreamSupport.stream(taskGraph.nodes().spliterator(), false).filter(node -> {
            return taskGraph.getHeadsOf(node).size() > 1;
        }).collect(toMap(x -> x, x -> new TaskCompleters()));
    }

    CountedCompleter<Void> create() {
        final var rootCompleters = createRootCompleters();
        if (rootCompleters.size() == 1) {
            return rootCompleters.get(0);
        } else {
            return new RootCompleter(rootCompleters);
        }
    }

    private List<? extends CountedCompleter<Void>> createRootCompleters() {
        final var rootNodes = taskGraph.getNoOutgoingEdges();

        return rootNodes.stream().map(rootNode -> {
            return new TaskCompleter(null, rootNode);
        }).toList();
    }

    private Optional<TaskCompleters> registerCompleter(Callable<Void> task, CountedCompleter<Void> completer) {
        Objects.requireNonNull(completer);
        final var dependentTasks = taskGraph.getHeadsOf(task);
        if (dependentTasks.size() <= 1) {
            return Optional.empty();
        } else {
            var completers = runOnce.get(task);
            completers.allCompleters().add(completer);
            return Optional.of(completers);
        }
    }

    private abstract class DependentComleter extends CountedCompleter<Void> {

        protected DependentComleter() {
        }

        protected DependentComleter(CountedCompleter<Void> completer) {
            super(completer);
        }

        @Override
        public void compute() {
            final var dependencyCompleters = dependencyCompleters();
            setPendingCount(dependencyCompleters.size());
            dependencyCompleters.forEach(CountedCompleter::fork);

            tryComplete();
        }

        protected abstract List<? extends CountedCompleter<Void>> dependencyCompleters();

        private static final long serialVersionUID = 1L;
    }

    private final class TaskCompleter extends DependentComleter {

        TaskCompleter(CountedCompleter<Void> dependentCompleter, Callable<Void> task) {
            super(dependentCompleter);
            this.task = Objects.requireNonNull(task);

            dependencyCompleters = taskGraph.getTailsOf(task).stream().map(dependencyTask -> {
                return new TaskCompleter(this, dependencyTask);
            }).toList();

            taskCompleters = registerCompleter(task, this);
        }

        @Override
        public void compute() {
            if (taskCompleters.map(TaskCompleters::completer).map(ref -> ref.compareAndSet(null, this)).orElse(true)) {
                super.compute();
            }
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (taskCompleters.map(ac -> ac.isTaskCompleter(this)).orElse(true)) {
                try {
                    task.call();
                } catch (Exception ex) {
                    completeExceptionally(ex);
                }
                taskCompleters.ifPresent(TaskCompleters::complete);
            }
        }

        @Override
        protected List<? extends CountedCompleter<Void>> dependencyCompleters() {
            return dependencyCompleters;
        }

        private final transient Callable<Void> task;
        private final transient List<? extends CountedCompleter<Void>> dependencyCompleters;
        private final transient Optional<TaskCompleters> taskCompleters;

        private static final long serialVersionUID = 1L;
    }

    private final class RootCompleter extends DependentComleter {

        RootCompleter(List<? extends CountedCompleter<Void>> dependencyCompleters) {
            this.dependencyCompleters = dependencyCompleters;
        }

        @Override
        protected List<? extends CountedCompleter<Void>> dependencyCompleters() {
            return dependencyCompleters;
        }

        private final transient List<? extends CountedCompleter<Void>> dependencyCompleters;

        private static final long serialVersionUID = 1L;
    }

    private record TaskCompleters(AtomicReference<CountedCompleter<Void>> completer,
            List<CountedCompleter<Void>> allCompleters) {

        TaskCompleters() {
            this(new AtomicReference<>(), new ArrayList<>());
        }

        boolean isTaskCompleter(CountedCompleter<Void> c) {
            return c == completer.get();
        }

        void complete() {
            allCompleters.forEach(c -> {
                if (!isTaskCompleter(c)) {
                    c.complete(null);
                }
            });
        }
    }

    private final ImmutableDAG<Callable<Void>> taskGraph;
    private final Map<Callable<Void>, TaskCompleters> runOnce;
}
