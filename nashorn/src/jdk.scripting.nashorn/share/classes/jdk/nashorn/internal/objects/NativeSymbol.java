/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import jdk.nashorn.internal.WeakValueCache;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Symbol;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * ECMAScript 6 - 19.4 Symbol Objects
 */
@ScriptClass("Symbol")
public final class NativeSymbol extends ScriptObject {

    private final Symbol symbol;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /** See ES6 19.4.2.1 */
    private static WeakValueCache<String, Symbol> globalSymbolRegistry = new WeakValueCache<>();

    NativeSymbol(final Symbol symbol, final Global global) {
        this(symbol, global.getSymbolPrototype(), $nasgenmap$);
    }

    private NativeSymbol(final Symbol symbol, final ScriptObject prototype, final PropertyMap map) {
        super(prototype, map);
        this.symbol = symbol;
    }

    private static Symbol getSymbolValue(final Object self) {
        if (self instanceof Symbol) {
            return (Symbol) self;
        } else if (self instanceof NativeSymbol) {
            return ((NativeSymbol) self).symbol;
        } else {
            throw typeError("not.a.symbol");
        }
    }

    // ECMA 6 19.4.3.4 Symbol.prototype [ @@toPrimitive ] ( hint )
    @Override
    public Object getDefaultValue(final Class<?> typeHint) {
        // Just return the symbol value.
        return symbol;
    }

    /**
     * ECMA 6 19.4.3.2 Symbol.prototype.toString ( )
     *
     * @param self self reference
     * @return localized string for this Number
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static String toString(final Object self) {
        return getSymbolValue(self).toString();
    }


    /**
     * ECMA 6 19.4.3.3  Symbol.prototype.valueOf ( )
     *
     * @param self self reference
     * @return number value for this Number
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object valueOf(final Object self) {
        return getSymbolValue(self);
    }

    /**
     * ECMA 6 19.4.1.1 Symbol ( [ description ] )
     *
     * @param newObj is this function invoked with the new operator
     * @param self   self reference
     * @param args   arguments
     * @return new symbol value
     */
    @Constructor(arity = 1)
    public static Object constructor(final boolean newObj, final Object self, final Object... args) {
        if (newObj) {
            throw typeError("symbol.as.constructor");
        }
        final String description = args.length > 0 && args[0] != Undefined.getUndefined() ?
                JSType.toString(args[0]) : "";
        return new Symbol(description);
    }

    /**
     * ES6 19.4.2.1 Symbol.for ( key )
     *
     * @param self self reference
     * @param arg the argument
     * @return the symbol value
     */
    @Function(name = "for", attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public synchronized static Object _for(final Object self, final Object arg) {
        final String name = JSType.toString(arg);
        return globalSymbolRegistry.getOrCreate(name, Symbol::new);
    }

    /**
     * ES6 19.4.2.5 Symbol.keyFor ( sym )
     *
     * @param self self reference
     * @param arg the argument
     * @return the symbol name
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public synchronized static Object keyFor(final Object self, final Object arg) {
        if (!(arg instanceof Symbol)) {
            throw typeError("not.a.symbol", ScriptRuntime.safeToString(arg));
        }
        final String name = ((Symbol) arg).getName();
        return globalSymbolRegistry.get(name) == arg ? name : Undefined.getUndefined();
    }
}
