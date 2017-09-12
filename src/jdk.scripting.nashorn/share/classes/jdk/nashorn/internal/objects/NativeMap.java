/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;

import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

/**
 * This implements the ECMA6 Map object.
 */
@ScriptClass("Map")
public class NativeMap extends ScriptObject {

    // our underlying map
    private final LinkedMap map = new LinkedMap();

    // key for the forEach invoker callback
    private final static Object FOREACH_INVOKER_KEY = new Object();

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeMap(final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
    }

    /**
     * ECMA6 23.1.1 The Map Constructor
     *
     * @param isNew is this called with the new operator?
     * @param self self reference
     * @param arg optional iterable argument
     * @return  a new Map instance
     */
    @Constructor(arity = 0)
    public static Object construct(final boolean isNew, final Object self, final Object arg) {
        if (!isNew) {
            throw typeError("constructor.requires.new", "Map");
        }
        final Global global = Global.instance();
        final NativeMap map = new NativeMap(global.getMapPrototype(), $nasgenmap$);
        populateMap(map.getJavaMap(), arg, global);
        return map;
    }

    /**
     * ECMA6 23.1.3.1 Map.prototype.clear ( )
     *
     * @param self the self reference
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static void clear(final Object self) {
        getNativeMap(self).map.clear();
    }

    /**
     * ECMA6 23.1.3.3 Map.prototype.delete ( key )
     *
     * @param self the self reference
     * @param key the key to delete
     * @return true if the key was deleted
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean delete(final Object self, final Object key) {
        return getNativeMap(self).map.delete(convertKey(key));
    }

    /**
     * ECMA6 23.1.3.7 Map.prototype.has ( key )
     *
     * @param self the self reference
     * @param key the key
     * @return true if key is contained
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean has(final Object self, final Object key) {
        return getNativeMap(self).map.has(convertKey(key));
    }

    /**
     * ECMA6 23.1.3.9 Map.prototype.set ( key , value )
     *
     * @param self the self reference
     * @param key the key
     * @param value the value
     * @return this Map object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object set(final Object self, final Object key, final Object value) {
        getNativeMap(self).map.set(convertKey(key), value);
        return self;
    }

    /**
     * ECMA6 23.1.3.6 Map.prototype.get ( key )
     *
     * @param self the self reference
     * @param key the key
     * @return the associated value or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object get(final Object self, final Object key) {
        return getNativeMap(self).map.get(convertKey(key));
    }

    /**
     * ECMA6 23.1.3.10 get Map.prototype.size
     *
     * @param self the self reference
     * @return the size of the map
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static int size(final Object self) {
        return getNativeMap(self).map.size();
    }

    /**
     * ECMA6 23.1.3.4 Map.prototype.entries ( )
     *
     * @param self the self reference
     * @return an iterator over the Map's entries
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object entries(final Object self) {
        return new MapIterator(getNativeMap(self), AbstractIterator.IterationKind.KEY_VALUE, Global.instance());
    }

    /**
     * ECMA6 23.1.3.8 Map.prototype.keys ( )
     *
     * @param self the self reference
     * @return an iterator over the Map's keys
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object keys(final Object self) {
        return new MapIterator(getNativeMap(self), AbstractIterator.IterationKind.KEY, Global.instance());
    }

    /**
     * ECMA6 23.1.3.11 Map.prototype.values ( )
     *
     * @param self the self reference
     * @return an iterator over the Map's values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object values(final Object self) {
        return new MapIterator(getNativeMap(self), AbstractIterator.IterationKind.VALUE, Global.instance());
    }

    /**
     * ECMA6 23.1.3.12 Map.prototype [ @@iterator ]( )
     *
     * @param self the self reference
     * @return An iterator over the Map's entries
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@iterator")
    public static Object getIterator(final Object self) {
        return new MapIterator(getNativeMap(self), AbstractIterator.IterationKind.KEY_VALUE, Global.instance());
    }

    /**
     *
     * @param self the self reference
     * @param callbackFn the callback function
     * @param thisArg optional this-object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static void forEach(final Object self, final Object callbackFn, final Object thisArg) {
        final NativeMap map = getNativeMap(self);
        if (!Bootstrap.isCallable(callbackFn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(callbackFn));
        }
        final MethodHandle invoker = Global.instance().getDynamicInvoker(FOREACH_INVOKER_KEY,
                () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class));

        final LinkedMap.LinkedMapIterator iterator = map.getJavaMap().getIterator();
        for (;;) {
            final LinkedMap.Node node = iterator.next();
            if (node == null) {
                break;
            }

            try {
                final Object result = invoker.invokeExact(callbackFn, thisArg, node.getValue(), node.getKey(), self);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    @Override
    public String getClassName() {
        return "Map";
    }

    static void populateMap(final LinkedMap map, final Object arg, final Global global) {
        if (arg != null && arg != Undefined.getUndefined()) {
            AbstractIterator.iterate(arg, global, value -> {
                if (JSType.isPrimitive(value)) {
                    throw typeError(global, "not.an.object", ScriptRuntime.safeToString(value));
                }
                if (value instanceof ScriptObject) {
                    final ScriptObject sobj = (ScriptObject) value;
                    map.set(convertKey(sobj.get(0)), sobj.get(1));
                }
            });
        }
    }

    /**
     * Returns a canonicalized key object by converting numbers to their narrowest representation and
     * ConsStrings to strings. Conversion of Double to Integer also takes care of converting -0 to 0
     * as required by step 6 of ECMA6 23.1.3.9.
     *
     * @param key a key
     * @return the canonical key
     */
    static Object convertKey(final Object key) {
        if (key instanceof ConsString) {
            return key.toString();
        }
        if (key instanceof Double) {
            final Double d = (Double) key;
            if (JSType.isRepresentableAsInt(d.doubleValue())) {
                return d.intValue();
            }
        }
        return key;
    }

    /**
     * Get the underlying Java map.
     * @return the Java map
     */
    LinkedMap getJavaMap() {
        return map;
    }

    private static NativeMap getNativeMap(final Object self) {
        if (self instanceof NativeMap) {
            return (NativeMap)self;
        } else {
            throw typeError("not.a.map", ScriptRuntime.safeToString(self));
        }
    }

}
