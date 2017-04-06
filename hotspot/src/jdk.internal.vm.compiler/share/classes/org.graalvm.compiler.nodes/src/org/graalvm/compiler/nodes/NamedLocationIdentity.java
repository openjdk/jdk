/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.EnumMap;
import java.util.HashSet;

import org.graalvm.compiler.core.common.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaKind.FormatWithToString;

/**
 * A {@link LocationIdentity} with a name.
 */
public class NamedLocationIdentity extends LocationIdentity implements FormatWithToString {

    /**
     * Map for asserting all {@link NamedLocationIdentity} instances have a unique name.
     */
    static class DB {
        private static final HashSet<String> map = new HashSet<>();

        static boolean checkUnique(String name) {
            if (!map.add(name)) {
                throw new AssertionError("identity " + name + " already exists");
            }
            return true;
        }
    }

    /**
     * Denotes the location of a value that is guaranteed to be unchanging.
     */
    public static final LocationIdentity FINAL_LOCATION = NamedLocationIdentity.immutable("FINAL_LOCATION");

    /**
     * Denotes the location of the length field of a Java array.
     */
    public static final LocationIdentity ARRAY_LENGTH_LOCATION = NamedLocationIdentity.immutable("[].length");

    public static LocationIdentity any() {
        return ANY_LOCATION;
    }

    private final String name;
    private final boolean immutable;

    protected NamedLocationIdentity(String name, boolean immutable) {
        this.name = name;
        this.immutable = immutable;
        assert DB.checkUnique(name);
    }

    /**
     * Creates a named unique location identity for read and write operations against mutable
     * memory.
     *
     * @param name the name of the new location identity
     */
    public static NamedLocationIdentity mutable(String name) {
        return create(name, false);
    }

    /**
     * Creates a named unique location identity for read operations against immutable memory.
     * Immutable memory will never have a visible write in the graph, which is more restictive than
     * Java final.
     *
     * @param name the name of the new location identity
     */
    public static NamedLocationIdentity immutable(String name) {
        return create(name, true);
    }

    /**
     * Creates a named unique location identity for read and write operations.
     *
     * @param name the name of the new location identity
     * @param immutable true if the location is immutable
     */
    private static NamedLocationIdentity create(String name, boolean immutable) {
        return new NamedLocationIdentity(name, immutable);
    }

    @Override
    public boolean isImmutable() {
        return immutable;
    }

    @Override
    public String toString() {
        return name + (isImmutable() ? ":final" : "");
    }

    /**
     * Returns the named location identity for an array of the given element kind. Array accesses of
     * the same kind must have the same location identity unless an alias analysis guarantees that
     * two distinct arrays are accessed.
     */
    public static LocationIdentity getArrayLocation(JavaKind elementKind) {
        return ARRAY_LOCATIONS.get(elementKind);
    }

    private static final EnumMap<JavaKind, LocationIdentity> ARRAY_LOCATIONS = initArrayLocations();

    private static EnumMap<JavaKind, LocationIdentity> initArrayLocations() {
        EnumMap<JavaKind, LocationIdentity> result = new EnumMap<>(JavaKind.class);
        for (JavaKind kind : JavaKind.values()) {
            result.put(kind, NamedLocationIdentity.mutable("Array: " + kind.getJavaName()));
        }
        return result;
    }
}
