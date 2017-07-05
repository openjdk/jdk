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
package org.graalvm.compiler.api.collections;

import java.util.Map;
import java.util.Set;

/**
 * A factory for creating collections.
 */
public interface CollectionsProvider {

    /**
     * Creates a set that uses reference-equality in place of object-equality when comparing
     * entries.
     */
    <E> Set<E> newIdentitySet();

    /**
     * Creates a map that uses reference-equality in place of object-equality when comparing keys.
     */
    <K, V> Map<K, V> newIdentityMap();

    /**
     * Creates a map that uses reference-equality in place of object-equality when comparing keys.
     *
     * @param expectedMaxSize the expected maximum size of the map
     */
    <K, V> Map<K, V> newIdentityMap(int expectedMaxSize);

    /**
     * Creates a map that uses reference-equality in place of object-equality when comparing keys.
     *
     * @param initFrom the returned map is populated with the entries in this map
     */
    <K, V> Map<K, V> newIdentityMap(Map<K, V> initFrom);
}
