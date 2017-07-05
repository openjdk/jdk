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

import static jdk.nashorn.internal.codegen.CompilerConstants.ARGUMENTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.constructorNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.typeDescriptor;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getPaddedFieldCount;
import static jdk.nashorn.internal.codegen.types.Type.OBJECT;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.getArrayIndex;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.isValidArrayIndex;

import java.util.Iterator;
import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;

/**
 * Analyze an object's characteristics for appropriate code generation. This
 * is used for functions and for objects. A field object take a set of values which
 * to assign to the various fields in the object. This is done by the generated code
 *
 * @param <T> the value type for the fields being written on object creation, e.g. Node
 * @see jdk.nashorn.internal.ir.Node
 */
public abstract class FieldObjectCreator<T> extends ObjectCreator {

    private         String        fieldObjectClassName;
    private         Class<?>      fieldObjectClass;
    private         int           fieldCount;
    private         int           paddedFieldCount;
    private         int           paramCount;

    /** array of corresponding values to symbols (null for no values) */
    private final List<T> values;

    /** call site flags to be used for invocations */
    private final int     callSiteFlags;

    /**
     * Constructor
     *
     * @param codegen  code generator
     * @param keys     keys for fields in object
     * @param symbols  symbols for fields in object
     * @param values   list of values corresponding to keys
     */
    FieldObjectCreator(final CodeGenerator codegen, final List<String> keys, final List<Symbol> symbols, final List<T> values) {
        this(codegen, keys, symbols, values, false, false);
    }

    /**
     * Constructor
     *
     * @param codegen      code generator
     * @param keys         keys for fields in object
     * @param symbols      symbols for fields in object
     * @param values       values (or null where no value) to be written to the fields
     * @param isScope      is this a scope object
     * @param hasArguments does the created object have an "arguments" property
     */
    FieldObjectCreator(final CodeGenerator codegen, final List<String> keys, final List<Symbol> symbols, final List<T> values, final boolean isScope, final boolean hasArguments) {
        super(codegen, keys, symbols, isScope, hasArguments);
        this.values        = values;
        this.callSiteFlags = codegen.getCallSiteFlags();

        countFields();
        findClass();
    }

    /**
     * Construct an object.
     *
     * @param method the method emitter
     */
    @Override
    protected void makeObject(final MethodEmitter method) {
        makeMap();

        method._new(getClassName()).dup(); // create instance
        loadMap(method); //load the map

        if (isScope()) {
            loadScope(method);

            if (hasArguments()) {
                method.loadCompilerConstant(ARGUMENTS);
                method.invoke(constructorNoLookup(getClassName(), PropertyMap.class, ScriptObject.class, ARGUMENTS.type()));
            } else {
                method.invoke(constructorNoLookup(getClassName(), PropertyMap.class, ScriptObject.class));
            }
        } else {
            method.invoke(constructorNoLookup(getClassName(), PropertyMap.class));
        }

        // Set values.
        final Iterator<Symbol> symbolIter = symbols.iterator();
        final Iterator<String> keyIter    = keys.iterator();
        final Iterator<T>      valueIter  = values.iterator();

        while (symbolIter.hasNext()) {
            final Symbol symbol = symbolIter.next();
            final String key    = keyIter.next();
            final T      value  = valueIter.next();

            if (symbol != null && value != null) {
                final int index = getArrayIndex(key);

                if (!isValidArrayIndex(index)) {
                    putField(method, key, symbol.getFieldIndex(), value);
                } else {
                    putSlot(method, ArrayIndex.toLongIndex(index), value);
                }
            }
        }
    }

    @Override
    protected PropertyMap makeMap() {
        assert propertyMap == null : "property map already initialized";
        propertyMap = newMapCreator(fieldObjectClass).makeFieldMap(hasArguments(), fieldCount, paddedFieldCount);
        return propertyMap;
    }

    /**
     * Technique for loading an initial value. Defined by anonymous subclasses in code gen.
     *
     * @param value Value to load.
     */
    protected abstract void loadValue(T value);

    /**
     * Store a value in a field of the generated class object.
     *
     * @param method      Script method.
     * @param key         Property key.
     * @param fieldIndex  Field number.
     * @param value       Value to store.
     */
    private void putField(final MethodEmitter method, final String key, final int fieldIndex, final T value) {
        method.dup();

        loadValue(value);
        method.convert(OBJECT);
        method.putField(getClassName(), ObjectClassGenerator.getFieldName(fieldIndex, Type.OBJECT), typeDescriptor(Object.class));
    }

    /**
     * Store a value in an indexed slot of a generated class object.
     *
     * @param method Script method.
     * @param index  Slot index.
     * @param value  Value to store.
     */
    private void putSlot(final MethodEmitter method, final long index, final T value) {
        method.dup();
        if (JSType.isRepresentableAsInt(index)) {
            method.load((int) index);
        } else {
            method.load(index);
        }
        loadValue(value);
        method.dynamicSetIndex(callSiteFlags);
    }

    /**
     * Locate (or indirectly create) the object container class.
     */
    private void findClass() {
        fieldObjectClassName = isScope() ?
                ObjectClassGenerator.getClassName(fieldCount, paramCount) :
                ObjectClassGenerator.getClassName(paddedFieldCount);

        try {
            this.fieldObjectClass = Context.forStructureClass(Compiler.binaryName(fieldObjectClassName));
        } catch (final ClassNotFoundException e) {
            throw new AssertionError("Nashorn has encountered an internal error.  Structure can not be created.");
        }
    }

    /**
     * Get the class name for the object class,
     * e.g. {@code com.nashorn.oracle.scripts.JO2P0}
     *
     * @return script class name
     */
    String getClassName() {
        return fieldObjectClassName;
    }

    /**
     * Tally the number of fields and parameters.
     */
    private void countFields() {
        for (final Symbol symbol : this.symbols) {
            if (symbol != null) {
                if (hasArguments() && symbol.isParam()) {
                    symbol.setFieldIndex(paramCount++);
                } else {
                    symbol.setFieldIndex(fieldCount++);
                }
            }
        }

        paddedFieldCount = getPaddedFieldCount(fieldCount);
    }

}
