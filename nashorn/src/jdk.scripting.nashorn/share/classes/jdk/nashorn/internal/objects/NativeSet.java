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
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

import static jdk.nashorn.internal.objects.NativeMap.convertKey;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

/**
 * This implements the ECMA6 Set object.
 */
@ScriptClass("Set")
public class NativeSet extends ScriptObject {

    // our set/map implementation
    private final LinkedMap map = new LinkedMap();

    // Invoker for the forEach callback
    private final static Object FOREACH_INVOKER_KEY = new Object();

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeSet(final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
    }

    /**
     * ECMA6 23.1 Set constructor
     *
     * @param isNew  whether the new operator used
     * @param self self reference
     * @param arg optional iterable argument
     * @return a new Set object
     */
    @Constructor(arity = 0)
    public static Object construct(final boolean isNew, final Object self, final Object arg){
        if (!isNew) {
            throw typeError("constructor.requires.new", "Set");
        }
        final Global global = Global.instance();
        final NativeSet set = new NativeSet(global.getSetPrototype(), $nasgenmap$);
        populateSet(set.getJavaMap(), arg, global);
        return set;
    }

    /**
     * ECMA6 23.2.3.1 Set.prototype.add ( value )
     *
     * @param self the self reference
     * @param value the value to add
     * @return this Set object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object add(final Object self, final Object value) {
        getNativeSet(self).map.set(convertKey(value), null);
        return self;
    }

    /**
     * ECMA6 23.2.3.7 Set.prototype.has ( value )
     *
     * @param self the self reference
     * @param value the value
     * @return true if value is contained
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean has(final Object self, final Object value) {
        return getNativeSet(self).map.has(convertKey(value));
    }

    /**
     * ECMA6 23.2.3.2 Set.prototype.clear ( )
     *
     * @param self the self reference
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static void clear(final Object self) {
        getNativeSet(self).map.clear();
    }

    /**
     * ECMA6 23.2.3.4 Set.prototype.delete ( value )
     *
     * @param self the self reference
     * @param value the value
     * @return true if value was deleted
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static boolean delete(final Object self, final Object value) {
        return getNativeSet(self).map.delete(convertKey(value));
    }

    /**
     * ECMA6 23.2.3.9 get Set.prototype.size
     *
     * @param self the self reference
     * @return the number of contained values
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.IS_ACCESSOR, where = Where.PROTOTYPE)
    public static int size(final Object self) {
        return getNativeSet(self).map.size();
    }

    /**
     * ECMA6 23.2.3.5 Set.prototype.entries ( )
     *
     * @param self the self reference
     * @return an iterator over the Set object's entries
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object entries(final Object self) {
        return new SetIterator(getNativeSet(self), AbstractIterator.IterationKind.KEY_VALUE, Global.instance());
    }

    /**
     * ECMA6 23.2.3.8 Set.prototype.keys ( )
     *
     * @param self the self reference
     * @return an iterator over the Set object's values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object keys(final Object self) {
        return new SetIterator(getNativeSet(self), AbstractIterator.IterationKind.KEY, Global.instance());
    }

    /**
     * ECMA6 23.2.3.10 Set.prototype.values ( )
     *
     * @param self the self reference
     * @return an iterator over the Set object's values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object values(final Object self) {
        return new SetIterator(getNativeSet(self), AbstractIterator.IterationKind.VALUE, Global.instance());
    }

    /**
     * ECMA6 23.2.3.11 Set.prototype [ @@iterator ] ( )
     *
     * @param self the self reference
     * @return an iterator over the Set object's values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, name = "@@iterator")
    public static Object getIterator(final Object self) {
        return new SetIterator(getNativeSet(self), AbstractIterator.IterationKind.VALUE, Global.instance());
    }

    /**
     * ECMA6 23.2.3.6 Set.prototype.forEach ( callbackfn [ , thisArg ] )
     *
     * @param self the self reference
     * @param callbackFn the callback function
     * @param thisArg optional this object
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static void forEach(final Object self, final Object callbackFn, final Object thisArg) {
        final NativeSet set = getNativeSet(self);
        if (!Bootstrap.isCallable(callbackFn)) {
            throw typeError("not.a.function", ScriptRuntime.safeToString(callbackFn));
        }
        final MethodHandle invoker = Global.instance().getDynamicInvoker(FOREACH_INVOKER_KEY,
                () -> Bootstrap.createDynamicCallInvoker(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class));

        final LinkedMap.LinkedMapIterator iterator = set.getJavaMap().getIterator();
        for (;;) {
            final LinkedMap.Node node = iterator.next();
            if (node == null) {
                break;
            }

            try {
                final Object result = invoker.invokeExact(callbackFn, thisArg, node.getKey(), node.getKey(), self);
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    @Override
    public String getClassName() {
        return "Set";
    }

    static void populateSet(final LinkedMap map, final Object arg, final Global global) {
        if (arg != null && arg != Undefined.getUndefined()) {
            AbstractIterator.iterate(arg, global, value -> map.set(convertKey(value), null));
        }
    }

    LinkedMap getJavaMap() {
        return map;
    }

    private static NativeSet getNativeSet(final Object self) {
        if (self instanceof NativeSet) {
            return (NativeSet) self;
        } else {
            throw typeError("not.a.set", ScriptRuntime.safeToString(self));
        }
    }
}
