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
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.PropertyMap;

/**
 * Base class for object creation code generation.
 */
public abstract class ObjectCreator {

    /** List of keys to initiate in this ObjectCreator */
    protected final List<String>  keys;

    /** List of symbols to initiate in this ObjectCreator */
    protected final List<Symbol>  symbols;

    /** Code generator */
    protected final CodeGenerator codegen;

    /** Property map */
    protected PropertyMap   propertyMap;

    private final boolean       isScope;
    private final boolean       hasArguments;

    /**
     * Constructor
     *
     * @param codegen      the code generator
     * @param keys         the keys
     * @param symbols      the symbols corresponding to keys, same index
     * @param isScope      is this object scope
     * @param hasArguments does the created object have an "arguments" property
     */
    protected ObjectCreator(final CodeGenerator codegen, final List<String> keys, final List<Symbol> symbols, final boolean isScope, final boolean hasArguments) {
        this.codegen       = codegen;
        this.keys          = keys;
        this.symbols       = symbols;
        this.isScope       = isScope;
        this.hasArguments  = hasArguments;
    }

    /**
     * Generate code for making the object.
     * @param method Script method.
     */
    protected abstract void makeObject(final MethodEmitter method);

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
    protected MapCreator newMapCreator(final Class<?> clazz) {
        return new MapCreator(clazz, keys, symbols);
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
}
