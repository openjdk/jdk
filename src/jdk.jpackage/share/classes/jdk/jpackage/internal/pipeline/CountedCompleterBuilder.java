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
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

/**
 * Schedules execution of actions in the given action graph.
 */
final class CountedCompleterBuilder {

    CountedCompleterBuilder(ImmutableDAG<Runnable> actionGraph) {
        this.actionGraph = Objects.requireNonNull(actionGraph);

        runOnce = StreamSupport.stream(actionGraph.getNodes().spliterator(), false).filter(node -> {
            return actionGraph.getHeadsOf(node).size() > 1;
        }).collect(toMap(x -> x, x -> new ActionCompleters()));
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
        final var rootNodes = actionGraph.getNoOutgoingEdges();

        return rootNodes.stream().map(rootNode -> {
            return new ActionCompleter(null, rootNode);
        }).toList();
    }

    private Optional<ActionCompleters> registerCompleter(Runnable action, CountedCompleter<Void> completer) {
        Objects.requireNonNull(completer);
        final var dependentActions = actionGraph.getHeadsOf(action);
        if (dependentActions.size() <= 1) {
            return Optional.empty();
        } else {
            var completers = runOnce.get(action);
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

            // ForkJoinPool will execute tasks in the reverse order of how they are scheduled.
            // Reverse the order in which actions are scheduled to get them executed in the order placed in the dependency list.
            // Ordering works for sequential execution only.
            dependencyCompleters.reversed().forEach(CountedCompleter::fork);

            tryComplete();
        }

        protected abstract List<? extends CountedCompleter<Void>> dependencyCompleters();

        private static final long serialVersionUID = 1L;
    }

    private final class ActionCompleter extends DependentComleter {

        ActionCompleter(CountedCompleter<Void> dependentCompleter, Runnable action) {
            super(dependentCompleter);
            this.action = Objects.requireNonNull(action);

            dependencyCompleters = actionGraph.getTailsOf(action).stream().map(dependencyAction -> {
                return new ActionCompleter(this, dependencyAction);
            }).toList();

            actionCompleters = registerCompleter(action, this);
        }

        @Override
        public void compute() {
            if (actionCompleters.map(ActionCompleters::completer).map(ref -> ref.compareAndSet(null, this)).orElse(true)) {
                super.compute();
            }
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (actionCompleters.map(ac -> ac.isActionCompleter(this)).orElse(true)) {
                action.run();
                actionCompleters.ifPresent(ActionCompleters::complete);
            }
        }

        @Override
        protected List<? extends CountedCompleter<Void>> dependencyCompleters() {
            return dependencyCompleters;
        }

        private final Runnable action;
        private final List<? extends CountedCompleter<Void>> dependencyCompleters;
        private final Optional<ActionCompleters> actionCompleters;

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

        private final List<? extends CountedCompleter<Void>> dependencyCompleters;

        private static final long serialVersionUID = 1L;
    }

    private record ActionCompleters(AtomicReference<CountedCompleter<Void>> completer,
            List<CountedCompleter<Void>> allCompleters) {

        ActionCompleters() {
            this(new AtomicReference<>(), new ArrayList<>());
        }

        boolean isActionCompleter(CountedCompleter<Void> c) {
            return c == completer.get();
        }

        void complete() {
            allCompleters.forEach(c -> {
                if (!isActionCompleter(c)) {
                    c.complete(null);
                }
            });
        }
    }

    private final ImmutableDAG<Runnable> actionGraph;
    private final Map<Runnable, ActionCompleters> runOnce;
}
