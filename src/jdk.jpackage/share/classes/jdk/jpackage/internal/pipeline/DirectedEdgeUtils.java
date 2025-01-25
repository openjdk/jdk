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

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class DirectedEdgeUtils {

    static <T> Set<T> getNodes(Collection<DirectedEdge<T>> edges) {
        return getNodes(edges, Optional.empty());
    }

    static <T> Set<T> getNodes(Collection<DirectedEdge<T>> edges, Supplier<Set<T>> collectionCtor) {
        return getNodes(edges, Optional.of(collectionCtor));
    }

    static <T> Set<T> getNoIncomingEdgeNodes(Collection<DirectedEdge<T>> edges) {
        return getNoIncomingEdgeNodes(edges, Optional.empty());
    }

    static <T> Set<T> getNoIncomingEdgeNodes(Collection<DirectedEdge<T>> edges, Supplier<Set<T>> collectionCtor) {
        return getNoIncomingEdgeNodes(edges, Optional.of(collectionCtor));
    }
    
    static <T> Collection<DirectedEdge<T>> getEdgesTo(T node, Collection<DirectedEdge<T>> edges) {
        return getEdgesTo(node, edges, Optional.empty());
    }

    static <T> Collection<DirectedEdge<T>> getEdgesTo(T node, Collection<DirectedEdge<T>> edges, 
            Supplier<Collection<DirectedEdge<T>>> collectionCtor) {
        return getEdgesTo(node, edges, Optional.of(collectionCtor));
    }
    
    static <T> Collection<DirectedEdge<T>> getEdgesFrom(T node, Collection<DirectedEdge<T>> edges) {
        return getEdgesFrom(node, edges, Optional.empty());
    }

    static <T> Collection<DirectedEdge<T>> getEdgesFrom(T node, Collection<DirectedEdge<T>> edges, 
            Supplier<Collection<DirectedEdge<T>>> collectionCtor) {
        return getEdgesFrom(node, edges, Optional.of(collectionCtor));
    }

    private static <T> Set<T> getNodes(Collection<DirectedEdge<T>> edges, Optional<Supplier<Set<T>>> collectionCtor) {
        return collectToSet(edges.parallelStream().flatMap(DirectedEdge::asStream), collectionCtor);
    }

    private static <T> Collection<DirectedEdge<T>> getEdgesTo(T node, Collection<DirectedEdge<T>> edges, 
            Optional<Supplier<Collection<DirectedEdge<T>>>> collectionCtor) {
        return filterEdges(node, edges, DirectedEdge::to, collectionCtor);
    }

    private static <T> Collection<DirectedEdge<T>> getEdgesFrom(T node, Collection<DirectedEdge<T>> edges, 
            Optional<Supplier<Collection<DirectedEdge<T>>>> collectionCtor) {
        return filterEdges(node, edges, DirectedEdge::from, collectionCtor);
    }

    private static <T> Set<T> getNoIncomingEdgeNodes(Collection<DirectedEdge<T>> edges, Optional<Supplier<Set<T>>> collectionCtor) {
        final Set<T> noIncomingEdgeNodes = getNodes(edges, collectionCtor);
        final var incomingEdgeNodes = edges.parallelStream().map(DirectedEdge::to).collect(toSet());
        noIncomingEdgeNodes.removeAll(incomingEdgeNodes);
        return noIncomingEdgeNodes;
    }

    private static <T> Set<T> collectToSet(Stream<T> stream, Optional<Supplier<Set<T>>> collectionCtor) {
        return collectionCtor.map(ctor -> stream.collect(toCollection(ctor))).orElseGet(() -> {
            return stream.collect(toSet());
        });
    }

    private static <T> Collection<T> collect(Stream<T> stream, Optional<Supplier<Collection<T>>> collectionCtor) {
        return collectionCtor.map(ctor -> stream.collect(toCollection(ctor))).orElseGet(() -> {
            return stream.toList();
        });
    }

    private static <T> Collection<DirectedEdge<T>> filterEdges(T node, Collection<DirectedEdge<T>> edges, 
            Function<DirectedEdge<T>, T> getNode, Optional<Supplier<Collection<DirectedEdge<T>>>> collectionCtor) {
        Objects.requireNonNull(node);
        Objects.requireNonNull(getNode);
        return collect(edges.parallelStream().filter(edge -> {
            return getNode.apply(edge).equals(node);
        }), collectionCtor);
    }
}
