/*
 * Copyright (c) 2010-2013, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.codegen.CompilerConstants.constructorNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.codegen.types.Type.OBJECT;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.scripts.JO;

/**
 * An object creator that uses spill properties.
 */
public class SpillObjectCreator extends ObjectCreator {

    private final List<Expression> values;

    /**
     * Constructor
     *
     * @param codegen  code generator
     * @param keys     keys for fields in object
     * @param symbols  symbols for fields in object
     * @param values   list of values corresponding to keys
     */
    protected SpillObjectCreator(final CodeGenerator codegen, final List<String> keys, final List<Symbol> symbols, final List<Expression> values) {
        super(codegen, keys, symbols, false, false);
        this.values = values;
        makeMap();
    }

    @Override
    protected void makeObject(final MethodEmitter method) {
        assert !isScope() : "spill scope objects are not currently supported";

        final int          length        = keys.size();
        final Object[]     presetValues  = new Object[length];
        final Set<Integer> postsetValues = new LinkedHashSet<>();
        final int          callSiteFlags = codegen.getCallSiteFlags();
        ArrayData          arrayData     = ArrayData.allocate(new Object[0]);

        // Compute constant property values
        for (int i = 0; i < length; i++) {
            final String key = keys.get(i);
            final Expression value = values.get(i);

            if (value == null) {
                continue; // getter or setter
            }

            final Object constantValue = LiteralNode.objectAsConstant(value);
            if (constantValue == LiteralNode.POSTSET_MARKER) {
                postsetValues.add(i);
                continue;
            }

            final Property property = propertyMap.findProperty(key);
            if (property != null) {
                // normal property key
                presetValues[property.getSlot()] = constantValue;
            } else {
                // array index key
                final long oldLength = arrayData.length();
                final int index = ArrayIndex.getArrayIndex(key);
                assert ArrayIndex.isValidArrayIndex(index);
                final long longIndex =  ArrayIndex.toLongIndex(index);
                if (longIndex >= oldLength) {
                    arrayData = arrayData.ensure(longIndex);
                }
                arrayData = arrayData.set(index, constantValue, false);
                if (longIndex > oldLength) {
                    arrayData = arrayData.delete(oldLength, longIndex - 1);
                }
            }
        }

        // create object and invoke constructor
        method._new(JO.class).dup();
        codegen.loadConstant(propertyMap);
        method.invoke(constructorNoLookup(JO.class, PropertyMap.class));

        // Set spill array with preset values
        method.dup();
        codegen.loadConstant(presetValues);
        method.putField(Type.getInternalName(ScriptObject.class), "spill", Type.OBJECT_ARRAY.getDescriptor());

        // Set array data if any
        if (arrayData.length() > 0) {
            method.dup();
            codegen.loadConstant(arrayData);
            method.invoke(virtualCallNoLookup(ScriptObject.class, "setArray",void.class, ArrayData.class));
        }

        // Create properties with non-constant values
        for (int i : postsetValues) {
            final String key = keys.get(i);
            final Property property = propertyMap.findProperty(key);

            if (property == null) {
                final int index = ArrayIndex.getArrayIndex(key);
                assert ArrayIndex.isValidArrayIndex(index);
                method.dup();
                method.load(ArrayIndex.toLongIndex(index));
                codegen.load(values.get(i));
                method.dynamicSetIndex(callSiteFlags);
            } else {
                method.dup();
                method.getField(Type.getInternalName(ScriptObject.class), "spill", Type.OBJECT_ARRAY.getDescriptor());
                method.load(property.getSlot());
                codegen.load(values.get(i)).convert(OBJECT);
                method.arraystore();
            }
        }
    }

    @Override
    protected PropertyMap makeMap() {
        assert propertyMap == null : "property map already initialized";

        propertyMap = new MapCreator(JO.class, keys, symbols) {
            @Override
            protected int getPropertyFlags(Symbol symbol, boolean hasArguments) {
                return super.getPropertyFlags(symbol, hasArguments) | Property.IS_SPILL | Property.IS_ALWAYS_OBJECT;
            }
        }.makeSpillMap(false);

        return propertyMap;
    }
}
