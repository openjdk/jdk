/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.getArrayIndex;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.isValidArrayIndex;

import java.util.ArrayList;
import java.util.List;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.SpillProperty;

/**
 * Class that creates PropertyMap sent to script object constructors.
 * @param <T> value type for tuples, e.g. Symbol
 */
public class MapCreator<T> {
    /** Object structure for objects associated with this map */
    private final Class<?> structure;

    /** key set for object map */
    private final List<MapTuple<T>> tuples;

    /**
     * Constructor
     *
     * @param structure structure to generate map for (a JO subclass)
     * @param keys      list of keys for map
     * @param symbols   list of symbols for map
     */
    MapCreator(final Class<? extends ScriptObject> structure, final List<MapTuple<T>> tuples) {
        this.structure = structure;
        this.tuples    = tuples;
    }

    /**
     * Constructs a property map based on a set of fields.
     *
     * @param hasArguments  does the created object have an "arguments" property
     * @param fieldCount    Number of fields in use.
     * @param fieldMaximum  Number of fields available.
     * @param evalCode      is this property map created for 'eval' code?
     * @return New map populated with accessor properties.
     */
    PropertyMap makeFieldMap(final boolean hasArguments, final int fieldCount, final int fieldMaximum, final boolean evalCode) {
        final List<Property> properties = new ArrayList<>();
        assert tuples != null;

        for (final MapTuple<T> tuple : tuples) {
            final String   key         = tuple.key;
            final Symbol   symbol      = tuple.symbol;
            final Class<?> initialType = tuple.getValueType();

            if (symbol != null && !isValidArrayIndex(getArrayIndex(key))) {
                final int      flags    = getPropertyFlags(symbol, hasArguments, evalCode);
                final Property property = new AccessorProperty(
                        key,
                        flags,
                        structure,
                        symbol.getFieldIndex(),
                        initialType);
                properties.add(property);
            }
        }

        return PropertyMap.newMap(properties, structure.getName(), fieldCount, fieldMaximum, 0);
    }

    PropertyMap makeSpillMap(final boolean hasArguments) {
        final List<Property> properties = new ArrayList<>();
        int spillIndex = 0;
        assert tuples != null;

        for (final MapTuple<T> tuple : tuples) {
            final String key    = tuple.key;
            final Symbol symbol = tuple.symbol;

            //TODO initial type is object here no matter what. Is that right?
            if (symbol != null && !isValidArrayIndex(getArrayIndex(key))) {
                final int flags = getPropertyFlags(symbol, hasArguments, false);
                properties.add(
                        new SpillProperty(
                                key,
                                flags,
                                spillIndex++));
            }
        }

        return PropertyMap.newMap(properties, structure.getName(), 0, 0, spillIndex);
    }

    /**
     * Compute property flags given local state of a field. May be overridden and extended,
     *
     * @param symbol       symbol to check
     * @param hasArguments does the created object have an "arguments" property
     *
     * @return flags to use for fields
     */
    static int getPropertyFlags(final Symbol symbol, final boolean hasArguments, final boolean evalCode) {
        int flags = 0;

        if (symbol.isParam()) {
            flags |= Property.IS_PARAMETER;
        }

        if (hasArguments) {
            flags |= Property.HAS_ARGUMENTS;
        }

        // See ECMA 5.1 10.5 Declaration Binding Instantiation.
        // Step 2  If code is eval code, then let configurableBindings
        // be true else let configurableBindings be false.
        // We have to make vars, functions declared in 'eval' code
        // configurable. But vars, functions from any other code is
        // not configurable.
        if (symbol.isScope() && !evalCode) {
            flags |= Property.NOT_CONFIGURABLE;
        }

        if (symbol.isFunctionDeclaration()) {
            flags |= Property.IS_FUNCTION_DECLARATION;
        }

        return flags;
    }
}
