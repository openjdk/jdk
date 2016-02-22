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

import java.util.Map;
import java.util.WeakHashMap;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.isPrimitive;

/**
 * This implements the ECMA6 WeakMap object.
 */
@ScriptClass("WeakMap")
public class NativeWeakMap extends ScriptObject {

    private final Map<Object, Object> jmap = new WeakHashMap<>();

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeWeakMap(final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
    }

    /**
     * ECMA6 23.3.1 The WeakMap Constructor
     *
     * @param isNew  whether the new operator used
     * @param self self reference
     * @param arg optional iterable argument
     * @return a new WeakMap object
     */
    @Constructor(arity = 0)
    public static Object construct(final boolean isNew, final Object self, final Object arg) {
        if (!isNew) {
            throw typeError("constructor.requires.new", "WeakMap");
        }
        final Global global = Global.instance();
        final NativeWeakMap weakMap = new NativeWeakMap(global.getWeakMapPrototype(), $nasgenmap$);
        populateMap(weakMap.jmap, arg, global);
        return weakMap;
    }

    /**
     * ECMA6 23.3.3.5 WeakMap.prototype.set ( key , value )
     *
     * @param self the self reference
     * @param key the key
     * @param value the value
     * @return this WeakMap object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object set(final Object self, final Object key, final Object value) {
        final NativeWeakMap map = getMap(self);
        map.jmap.put(checkKey(key), value);
        return self;
    }

    /**
     * ECMA6 23.3.3.3 WeakMap.prototype.get ( key )
     *
     * @param self the self reference
     * @param key the key
     * @return the associated value or undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object get(final Object self, final Object key) {
        final NativeWeakMap map = getMap(self);
        if (isPrimitive(key)) {
            return Undefined.getUndefined();
        }
        return map.jmap.get(key);
    }

    /**
     * ECMA6 23.3.3.2 WeakMap.prototype.delete ( key )
     *
     * @param self the self reference
     * @param key the key to delete
     * @return true if the key was deleted
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean delete(final Object self, final Object key) {
        final Map<Object, Object> map = getMap(self).jmap;
        if (isPrimitive(key)) {
            return false;
        }
        final boolean returnValue = map.containsKey(key);
        map.remove(key);
        return returnValue;
    }

    /**
     * ECMA6 23.3.3.4 WeakMap.prototype.has ( key )
     *
     * @param self the self reference
     * @param key the key
     * @return true if key is contained
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean has(final Object self, final Object key) {
        final NativeWeakMap map = getMap(self);
        return !isPrimitive(key) && map.jmap.containsKey(key);
    }

    @Override
    public String getClassName() {
        return "WeakMap";
    }

    /**
     * Make sure {@code key} is not a JavaScript primitive value.
     *
     * @param key a key object
     * @return the valid key
     */
    static Object checkKey(final Object key) {
        if (isPrimitive(key)) {
            throw typeError("invalid.weak.key", ScriptRuntime.safeToString(key));
        }
        return key;
    }

    static void populateMap(final Map<Object, Object> map, final Object arg, final Global global) {
        // This method is similar to NativeMap.populateMap, but it uses a different
        // map implementation and the checking/conversion of keys differs as well.
        if (arg != null && arg != Undefined.getUndefined()) {
            AbstractIterator.iterate(arg, global, value -> {
                if (isPrimitive(value)) {
                    throw typeError(global, "not.an.object", ScriptRuntime.safeToString(value));
                }
                if (value instanceof ScriptObject) {
                    final ScriptObject sobj = (ScriptObject) value;
                    map.put(checkKey(sobj.get(0)), sobj.get(1));
                }
            });
        }
    }

    private static NativeWeakMap getMap(final Object self) {
        if (self instanceof NativeWeakMap) {
            return (NativeWeakMap)self;
        } else {
            throw typeError("not.a.weak.map", ScriptRuntime.safeToString(self));
        }
    }

}


