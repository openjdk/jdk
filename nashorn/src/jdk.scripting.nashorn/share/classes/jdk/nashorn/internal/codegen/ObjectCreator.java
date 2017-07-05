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

import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;

import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Base class for object creation code generation.
 * @param <T> value type
 */
public abstract class ObjectCreator<T> implements CodeGenerator.SplitLiteralCreator {

    /** List of keys & symbols to initiate in this ObjectCreator */
    final List<MapTuple<T>> tuples;

    /** Code generator */
    final CodeGenerator codegen;

    /** Property map */
    protected PropertyMap   propertyMap;

    private final boolean       isScope;
    private final boolean       hasArguments;

    /**
     * Constructor
     *
     * @param codegen      the code generator
     * @param tuples       key,symbol,value (optional) tuples
     * @param isScope      is this object scope
     * @param hasArguments does the created object have an "arguments" property
     */
    ObjectCreator(final CodeGenerator codegen, final List<MapTuple<T>> tuples, final boolean isScope, final boolean hasArguments) {
        this.codegen       = codegen;
        this.tuples        = tuples;
        this.isScope       = isScope;
        this.hasArguments  = hasArguments;
    }

    /**
     * Generate code for making the object.
     * @param method Script method.
     */
    public void makeObject(final MethodEmitter method) {
        createObject(method);
        // We need to store the object in a temporary slot as populateRange expects to load the
        // object from a slot (as it is also invoked within split methods). Note that this also
        // helps optimistic continuations to handle the stack in case an optimistic assumption
        // fails during initialization (see JDK-8079269).
        final int objectSlot = method.getUsedSlotsWithLiveTemporaries();
        final Type objectType = method.peekType();
        method.storeTemp(objectType, objectSlot);
        populateRange(method, objectType, objectSlot, 0, tuples.size());
    }

    /**
     * Generate code for creating and initializing the object.
     * @param method the method emitter
     */
    protected abstract void createObject(final MethodEmitter method);

    /**
     * Construct the property map appropriate for the object.
     * @return the newly created property map
     */
    protected abstract PropertyMap makeMap();

    /**
     * Create a new MapCreator
     * @param clazz type of MapCreator
     * @return map creator instantiated by type
     */
    protected MapCreator<?> newMapCreator(final Class<? extends ScriptObject> clazz) {
        return new MapCreator<>(clazz, tuples);
    }

    /**
     * Loads the scope on the stack through the passed method emitter.
     * @param method the method emitter to use
     */
    protected void loadScope(final MethodEmitter method) {
        method.loadCompilerConstant(SCOPE);
    }

    /**
     * Emit the correct map for the object.
     * @param method method emitter
     * @return the method emitter
     */
    protected MethodEmitter loadMap(final MethodEmitter method) {
        codegen.loadConstant(propertyMap);
        return method;
    }

    PropertyMap getMap() {
        return propertyMap;
    }

    /**
     * Is this a scope object
     * @return true if scope
     */
    protected boolean isScope() {
        return isScope;
    }

    /**
     * Does the created object have an "arguments" property
     * @return true if has an "arguments" property
     */
    protected boolean hasArguments() {
        return hasArguments;
    }

    /**
     * Get the class of objects created by this ObjectCreator
     * @return class of created object
     */
    abstract protected Class<? extends ScriptObject> getAllocatorClass();

    /**
     * Technique for loading an initial value. Defined by anonymous subclasses in code gen.
     *
     * @param value Value to load.
     * @param type the type of the value to load
     */
    protected abstract void loadValue(T value, Type type);

    MethodEmitter loadTuple(final MethodEmitter method, final MapTuple<T> tuple, final boolean pack) {
        loadValue(tuple.value, tuple.type);
        if (pack && codegen.useDualFields() && tuple.isPrimitive()) {
            method.pack();
        } else {
            method.convert(Type.OBJECT);
        }
        return method;
    }

    MethodEmitter loadTuple(final MethodEmitter method, final MapTuple<T> tuple) {
        return loadTuple(method, tuple, true);
    }
}
