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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import jdk.jpackage.internal.util.function.ExceptionBox;

public final class ActionPipelineBuilder<T extends Context> {

    ActionPipelineBuilder() {
        rootAction = new RootAction<>();
        actions = new HashSet<>();
        actions.add(rootAction);
        actionGraph = new ActionGraph<>();
    }

    public final class ActionSpecBuilder {

        ActionSpecBuilder(Action<T> action) {
            Objects.requireNonNull(action);
            this.action = action;
        }

        public ActionSpecBuilder dependent(Action<T> v) {
            dependent = v;
            return this;
        }

        public ActionSpecBuilder addDependency(Action<T> v) {
            Objects.requireNonNull(v);
            dependencies.add(v);
            return this;
        }

        public ActionSpecBuilder addDependencies(Collection<? extends Action<T>> v) {
            Objects.requireNonNull(v);
            v.forEach(Objects::requireNonNull);
            dependencies.addAll(v);
            return this;
        }

        public ActionPipelineBuilder<T> add() {
            actionGraph.addEdge(action, validatedDependent());

            actions.add(action);
            actions.addAll(dependencies);
            dependencies.forEach(dependency -> {
                actionGraph.addEdge(dependency, action);
            });

            return ActionPipelineBuilder.this;
        }

        @SuppressWarnings("unchecked")
        private Action<T> validatedDependent() {
            if (dependent == null) {
                return rootAction;
            } else {
                if (!actions.contains(dependent)) {
                    throw new IllegalArgumentException("Unknown dependent action");
                }
                return dependent;
            }
        }

        private Set<Action<T>> dependencies = new LinkedHashSet<>();
        private Action<T> dependent;
        private final Action<T> action;
    }

    public ActionSpecBuilder action(Action<T> action) {
        return new ActionSpecBuilder(action);
    }

    public ActionPipelineBuilder<T> executor(Executor v) {
        Objects.requireNonNull(v);
        executor = v;
        return this;
    }

    public ActionPipelineBuilder<T> sequentialExecutor() {
        executor = Executors.newSingleThreadExecutor();
        return this;
    }

    public Action<T> create() {
        final List<Action<T>> orderedActions;

        try {
            orderedActions = actionGraph.topologicalSort();
        } catch (CycleException ex) {
            throw new UnsupportedOperationException(ex);
        }

        final var dependentActions = orderedActions.stream().map(action -> {
            final var dependencies = actionGraph.getNodeDependencies(action);
            return new DependentAction<T>(action, dependencies);
        }).toList();

        return new Impl<>(dependentActions, executor);
    }

    private record DependentAction<U extends Context>(Action<U> action, Set<Action<U>> dependencies) {

        DependentAction {
            Objects.requireNonNull(action);
            Objects.requireNonNull(dependencies);
            dependencies.forEach(Objects::requireNonNull);
        }
    }

    private record RunnableAdapter<U extends Context>(Action<U> action, U context,
            Optional<CompletableFuture<Void>> dependenciesFuture) implements Runnable {

        RunnableAdapter {
            Objects.requireNonNull(action);
            Objects.requireNonNull(context);
            Objects.requireNonNull(dependenciesFuture);
        }

        @Override
        public void run() {
            try {
                dependenciesFuture.ifPresent(CompletableFuture::join);
            } catch (CancellationException|CompletionException ex) {
                return;
            }

            try {
                action.execute(context);
            } catch (ActionException ex) {
                throw ExceptionBox.rethrowUnchecked(ex);
            }
        }
    }

    private record Impl<U extends Context>(List<DependentAction<U>> dependentActions, Executor executor) implements Action<U> {

        Impl {
            Objects.requireNonNull(dependentActions);
            dependentActions.forEach(Objects::requireNonNull);
            Objects.requireNonNull(executor);
        }

        @Override
        public void execute(U context) throws ActionException {
            final var rootAction = scheduleActions(context);
            rootAction.join();
        }

        private CompletableFuture<Void> scheduleActions(U context) {

            final Map<Action<U>, CompletableFuture<Void>> scheduledActions = new HashMap<>();

            CompletableFuture<Void> rootAction = null;
            for (final var dependentAction : dependentActions) {
                final Optional<CompletableFuture<Void>> dependenciesFuture;
                if (dependentAction.dependencies().isEmpty()) {
                    dependenciesFuture = Optional.empty();
                } else {
                    final var dependencyFutures = dependentAction.dependencies().stream()
                            .map(scheduledActions::get)
                            .toArray(CompletableFuture<?>[]::new);
                    dependenciesFuture = Optional.of(CompletableFuture.allOf(dependencyFutures));
                }

                final var actionAsRunnable = new RunnableAdapter<>(dependentAction.action(), context, dependenciesFuture);

                rootAction = CompletableFuture.runAsync(actionAsRunnable, executor);

                scheduledActions.put(dependentAction.action(), rootAction);
            }

            return rootAction;
        }
    }

    private static class RootAction<U extends Context> implements Action<U> {

        @Override
        public void execute(U context) throws ActionException {
        }

        @Override
        public String toString() {
            return String.format("%s(root)", super.toString());
        }
    }

    private final Action<T> rootAction;
    private final Set<Action<T>> actions;
    private final ActionGraph<Action<T>> actionGraph;

    private Executor executor;

}
