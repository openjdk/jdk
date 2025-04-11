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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TaskSpecBuilder<T> {

    public TaskSpecBuilder(T task) {
        this.task = Objects.requireNonNull(task);
    }

    public T task() {
        return task;
    }

    public TaskSpecBuilder<T> addDependent(T v) {
        Objects.requireNonNull(v);
        dependents.add(v);
        return this;
    }

    public TaskSpecBuilder<T> addDependency(T v) {
        Objects.requireNonNull(v);
        dependencies.add(v);
        return this;
    }

    public TaskSpecBuilder<T> addDependencies(Collection<? extends T> v) {
        Objects.requireNonNull(v);
        v.forEach(Objects::requireNonNull);
        dependencies.addAll(v);
        return this;
    }

    public TaskSpecBuilder<T> addDependents(Collection<? extends T> v) {
        Objects.requireNonNull(v);
        v.forEach(Objects::requireNonNull);
        dependents.addAll(v);
        return this;
    }

    public List<DirectedEdge<T>> createLinks() {
        List<DirectedEdge<T>> edges = new ArrayList<>();

        for (var dependency : dependencies) {
            edges.add(DirectedEdge.create(dependency, task));
        }

        for (var dependent : dependents) {
            edges.add(DirectedEdge.create(task, dependent));
        }

        return edges;
    }

    private final Set<T> dependencies = new LinkedHashSet<>();
    private final Set<T> dependents = new LinkedHashSet<>();
    private final T task;
}
