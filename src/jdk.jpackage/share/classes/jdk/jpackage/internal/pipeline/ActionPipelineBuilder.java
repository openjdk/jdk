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
import java.util.concurrent.ForkJoinPool;

public final class ActionPipelineBuilder<T extends Context> {

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
            if (actionGraphBuilder == null) {
                actionGraphBuilder = new ImmutableDAG.Builder<>();
                actionGraphBuilder.addEdge(action, Action.rootAction());
                actionGraphBuilder.canAddEdgeToUnknownNode(false);
            } else {
                actionGraphBuilder.addEdge(action, Optional.ofNullable(dependent).orElseGet(Action::rootAction));
            }

            for (var dependency : dependencies) {
                actionGraphBuilder.addEdge(dependency, action);
            }

            return ActionPipelineBuilder.this;
        }

        private Set<Action<T>> dependencies = new LinkedHashSet<>();
        private Action<T> dependent;
        private final Action<T> action;
    }

    public ActionSpecBuilder action(Action<T> action) {
        return new ActionSpecBuilder(action);
    }

    public ActionPipelineBuilder<T> executor(ForkJoinPool v) {
        Objects.requireNonNull(v);
        fjp = v;
        return this;
    }

    public ActionPipelineBuilder<T> sequentialExecutor() {
        fjp = new ForkJoinPool(1);
        return this;
    }

    public Action<T> create() {
        final var actionGraph = actionGraphBuilder.create();

        final var countedCompleterBuilder = new CountedCompleterBuilder<>(actionGraph);

        return new WrapperAction<>(countedCompleterBuilder, fjp);
    }

    private record WrapperAction<U extends Context>(CountedCompleterBuilder<U> countedCompleterBuilder,
            ForkJoinPool fjp) implements Action<U> {

        WrapperAction {
            Objects.requireNonNull(countedCompleterBuilder);
            Objects.requireNonNull(fjp);
        }

        @Override
        public void execute(U context) {
            final var rootCompleter = countedCompleterBuilder.create(context);
            fjp.invoke(rootCompleter);
        }

    }

    private ImmutableDAG.Builder<Action<T>> actionGraphBuilder;
    private ForkJoinPool fjp;
}
