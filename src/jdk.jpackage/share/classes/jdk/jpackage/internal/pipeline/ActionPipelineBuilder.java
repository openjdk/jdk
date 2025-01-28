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
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;

public final class ActionPipelineBuilder {

    public final class ActionSpecBuilder {

        ActionSpecBuilder(Runnable action) {
            Objects.requireNonNull(action);
            this.action = action;
        }

        public ActionSpecBuilder dependent(Runnable v) {
            dependent = v;
            return this;
        }

        public ActionSpecBuilder addDependency(Runnable v) {
            Objects.requireNonNull(v);
            dependencies.add(v);
            return this;
        }

        public ActionSpecBuilder addDependencies(Collection<? extends Runnable> v) {
            Objects.requireNonNull(v);
            v.forEach(Objects::requireNonNull);
            dependencies.addAll(v);
            return this;
        }

        public ActionPipelineBuilder add() {
            if (actionGraphBuilder == null) {
                actionGraphBuilder = new ImmutableDAG.Builder<>();
                actionGraphBuilder.addEdge(action, ROOT_ACTION);
                actionGraphBuilder.canAddEdgeToUnknownNode(false);
            } else {
                actionGraphBuilder.addEdge(action, Optional.ofNullable(dependent).orElse(ROOT_ACTION));
            }

            for (var dependency : dependencies) {
                actionGraphBuilder.addEdge(dependency, action);
            }

            return ActionPipelineBuilder.this;
        }

        private Set<Runnable> dependencies = new LinkedHashSet<>();
        private Runnable dependent;
        private final Runnable action;
    }

    public ActionSpecBuilder action(Runnable action) {
        return new ActionSpecBuilder(action);
    }

    public ActionPipelineBuilder executor(ForkJoinPool v) {
        Objects.requireNonNull(v);
        fjp = v;
        return this;
    }

    public ActionPipelineBuilder sequentialExecutor() {
        fjp = new ForkJoinPool(1);
        return this;
    }

    public Runnable create() {
        final var actionGraph = actionGraphBuilder.create();

        final var rootCompleter = new CountedCompleterBuilder(actionGraph).create();

        return new WrapperAction(rootCompleter, fjp);
    }

    private record WrapperAction(CountedCompleter<Void> rootCompleter, ForkJoinPool fjp) implements Runnable {

        WrapperAction {
            Objects.requireNonNull(rootCompleter);
            Objects.requireNonNull(fjp);
        }

        @Override
        public void run() {
            fjp.invoke(rootCompleter);
        }

    }

    private static final Runnable ROOT_ACTION = new Runnable() {

        @Override
        public void run() {
        }

        @Override
        public String toString() {
            return super.toString() + "(root)";
        }
    };

    private ImmutableDAG.Builder<Runnable> actionGraphBuilder;
    private ForkJoinPool fjp;
}
