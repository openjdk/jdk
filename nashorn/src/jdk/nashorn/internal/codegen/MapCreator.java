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

/**
 * Class that creates PropertyMap sent to script object constructors.
 */
public class MapCreator {
    /** Object structure for objects associated with this map */
    private final Class<?> structure;

    /** key set for object map */
    final List<String> keys;

    /** corresponding symbol set for object map */
    final List<Symbol> symbols;

    /**
     * Constructor
     *
     * @param structure structure to generate map for (a JO subclass)
     * @param keys      list of keys for map
     * @param symbols   list of symbols for map
     */
    MapCreator(final Class<?> structure, final List<String> keys, final List<Symbol> symbols) {
        this.structure = structure;
        this.keys      = keys;
        this.symbols   = symbols;
    }

    /**
     * Constructs a property map based on a set of fields.
     *
     * @param hasArguments does the created object have an "arguments" property
     * @param fieldCount    Number of fields in use.
     * @param fieldMaximum Number of fields available.
     *
     * @return New map populated with accessor properties.
     */
    PropertyMap makeFieldMap(final boolean hasArguments, final int fieldCount, final int fieldMaximum) {
        final List<Property> properties = new ArrayList<>();
        assert keys != null;

        for (int i = 0, length = keys.size(); i < length; i++) {
            final String key    = keys.get(i);
            final Symbol symbol = symbols.get(i);

            if (symbol != null && !isValidArrayIndex(getArrayIndex(key))) {
                properties.add(new AccessorProperty(key, getPropertyFlags(symbol, hasArguments), structure, symbol.getFieldIndex()));
            }
        }

        return PropertyMap.newMap(properties, fieldCount, fieldMaximum, 0);
    }

    PropertyMap makeSpillMap(final boolean hasArguments) {
        final List<Property> properties = new ArrayList<>();
        int spillIndex = 0;
        assert keys != null;

        for (int i = 0, length = keys.size(); i < length; i++) {
            final String key    = keys.get(i);
            final Symbol symbol = symbols.get(i);

            if (symbol != null && !isValidArrayIndex(getArrayIndex(key))) {
                properties.add(new AccessorProperty(key, getPropertyFlags(symbol, hasArguments), spillIndex++));
            }
        }

        return PropertyMap.newMap(properties, 0, 0, spillIndex);
    }

    /**
     * Compute property flags given local state of a field. May be overridden and extended,
     *
     * @param symbol       symbol to check
     * @param hasArguments does the created object have an "arguments" property
     *
     * @return flags to use for fields
     */
    protected int getPropertyFlags(final Symbol symbol, final boolean hasArguments) {
        int flags = 0;

        if (symbol.isParam()) {
            flags |= Property.IS_ALWAYS_OBJECT | Property.IS_PARAMETER;
        }

        if (hasArguments) {
            flags |= Property.IS_ALWAYS_OBJECT | Property.HAS_ARGUMENTS;
        }

        if (symbol.isScope()) {
            flags |= Property.NOT_CONFIGURABLE;
        }

        if (symbol.canBePrimitive()) {
            flags |= Property.CAN_BE_PRIMITIVE;
        }

        if (symbol.canBeUndefined()) {
            flags |= Property.CAN_BE_UNDEFINED;
        }

        if (symbol.isFunctionDeclaration()) {
            flags |= Property.IS_FUNCTION_DECLARATION;
        }

        return flags;
    }

}
