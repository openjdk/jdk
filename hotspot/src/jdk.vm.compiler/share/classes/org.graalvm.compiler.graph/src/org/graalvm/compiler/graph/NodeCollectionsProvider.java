/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.graph;

import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.api.collections.CollectionsProvider;

/**
 * Extends {@link CollectionsProvider} with support for creating {@link Node} based collections.
 */
public interface NodeCollectionsProvider extends CollectionsProvider {

    /**
     * Creates a set of {@link Node}s that uses reference-equality in place of object-equality when
     * comparing entries.
     */
    <E extends Node> Set<E> newNodeIdentitySet();

    /**
     * Creates a map whose keys are {@link Node}s that uses reference-equality in place of
     * object-equality when comparing keys. All {@link Node} keys must be in the same graph.
     */
    <K extends Node, V> Map<K, V> newNodeIdentityMap();

    /**
     * Creates a map whose keys are {@link Node}s that uses reference-equality in place of
     * object-equality when comparing keys. All {@link Node} keys must be in the same graph.
     */
    <K extends Node, V> Map<K, V> newNodeIdentityMap(int expectedMaxSize);

    /**
     * Creates a map whose keys are {@link Node}s that uses reference-equality in place of
     * object-equality when comparing keys. All {@link Node} keys must be in the same graph.
     *
     * @param initFrom the returned map is populated with the entries in this map
     */
    <K extends Node, V> Map<K, V> newNodeIdentityMap(Map<K, V> initFrom);
}
