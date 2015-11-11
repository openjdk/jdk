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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.scripts.JD;
import jdk.nashorn.internal.scripts.JO;

/**
 * An object creator that uses spill properties.
 */
public final class SpillObjectCreator extends ObjectCreator<Expression> {

    /**
     * Constructor
     *
     * @param codegen  code generator
     * @param tuples   tuples for key, symbol, value
     */
    SpillObjectCreator(final CodeGenerator codegen, final List<MapTuple<Expression>> tuples) {
        super(codegen, tuples, false, false);
        makeMap();
    }

    @Override
    public void createObject(final MethodEmitter method) {
        assert !isScope() : "spill scope objects are not currently supported";

        final int          length        = tuples.size();
        final boolean      dualFields    = codegen.useDualFields();
        final int          spillLength   = ScriptObject.spillAllocationLength(length);
        final long[]       jpresetValues = dualFields ? new long[spillLength] : null;
        final Object[]     opresetValues = new Object[spillLength];
        final Class<?>     objectClass   = getAllocatorClass();
        ArrayData          arrayData     = ArrayData.allocate(ScriptRuntime.EMPTY_ARRAY);

        // Compute constant property values
        int pos = 0;
        for (final MapTuple<Expression> tuple : tuples) {
            final String     key   = tuple.key;
            final Expression value = tuple.value;

            //this is a nop of tuple.key isn't e.g. "apply" or another special name
            method.invalidateSpecialName(tuple.key);

            if (value != null) {
                final Object constantValue = LiteralNode.objectAsConstant(value);
                if (constantValue != LiteralNode.POSTSET_MARKER) {
                    final Property property = propertyMap.findProperty(key);
                    if (property != null) {
                        // normal property key
                        property.setType(dualFields ? JSType.unboxedFieldType(constantValue) : Object.class);
                        final int slot = property.getSlot();
                        if (dualFields && constantValue instanceof Number) {
                            jpresetValues[slot] = ObjectClassGenerator.pack((Number)constantValue);
                        } else {
                            opresetValues[slot] = constantValue;
                        }
                    } else {
                        // array index key
                        final long oldLength = arrayData.length();
                        final int  index     = ArrayIndex.getArrayIndex(key);
                        final long longIndex = ArrayIndex.toLongIndex(index);

                        assert ArrayIndex.isValidArrayIndex(index);

                        if (longIndex >= oldLength) {
                            arrayData = arrayData.ensure(longIndex);
                        }

                        //avoid blowing up the array if we can
                        if (constantValue instanceof Integer) {
                            arrayData = arrayData.set(index, ((Integer)constantValue).intValue(), false);
                        } else if (constantValue instanceof Long) {
                            arrayData = arrayData.set(index, ((Long)constantValue).longValue(), false);
                        } else if (constantValue instanceof Double) {
                            arrayData = arrayData.set(index, ((Double)constantValue).doubleValue(), false);
                        } else {
                            arrayData = arrayData.set(index, constantValue, false);
                        }

                        if (longIndex > oldLength) {
                            arrayData = arrayData.delete(oldLength, longIndex - 1);
                        }
                    }
                }
            }
            pos++;
        }

        // create object and invoke constructor
        method._new(objectClass).dup();
        codegen.loadConstant(propertyMap);

        // load primitive value spill array
        if (dualFields) {
            codegen.loadConstant(jpresetValues);
        } else {
            method.loadNull();
        }
        // load object value spill array
        codegen.loadConstant(opresetValues);

        // instantiate the script object with spill objects
        method.invoke(constructorNoLookup(objectClass, PropertyMap.class, long[].class, Object[].class));

        // Set prefix array data if any
        if (arrayData.length() > 0) {
            method.dup();
            codegen.loadConstant(arrayData);
            method.invoke(virtualCallNoLookup(ScriptObject.class, "setArray", void.class, ArrayData.class));
        }
    }

    @Override
    public void populateRange(final MethodEmitter method, final Type objectType, final int objectSlot, final int start, final int end) {
        final int  callSiteFlags = codegen.getCallSiteFlags();
        method.load(objectType, objectSlot);

        // set postfix values
        for (int i = start; i < end; i++) {
            final MapTuple<Expression> tuple = tuples.get(i);

            if (LiteralNode.isConstant(tuple.value)) {
                continue;
            }

            final Property property = propertyMap.findProperty(tuple.key);

            if (property == null) {
                final int index = ArrayIndex.getArrayIndex(tuple.key);
                assert ArrayIndex.isValidArrayIndex(index);
                method.dup();
                method.load(ArrayIndex.toLongIndex(index));
                loadTuple(method, tuple);
                method.dynamicSetIndex(callSiteFlags);
            } else {
                assert property.getKey() instanceof String; // symbol keys not yet supported in object literals
                method.dup();
                loadTuple(method, tuple);
                method.dynamicSet((String) property.getKey(), codegen.getCallSiteFlags(), false);
            }
        }
    }

    @Override
    protected PropertyMap makeMap() {
        assert propertyMap == null : "property map already initialized";
        final Class<? extends ScriptObject> clazz = getAllocatorClass();
        propertyMap = new MapCreator<>(clazz, tuples).makeSpillMap(false, codegen.useDualFields());
        return propertyMap;
    }

    @Override
    protected void loadValue(final Expression expr, final Type type) {
        codegen.loadExpressionAsType(expr, type);
    }

    @Override
    protected Class<? extends ScriptObject> getAllocatorClass() {
        return codegen.useDualFields() ? JD.class : JO.class;
    }
}
