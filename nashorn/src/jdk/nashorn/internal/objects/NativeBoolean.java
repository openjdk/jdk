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

package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.runtime.linker.PrimitiveLookup;

/**
 * ECMA 15.6 Boolean Objects.
 */

@ScriptClass("Boolean")
public final class NativeBoolean extends ScriptObject {
    private final boolean value;

    final static MethodHandle WRAPFILTER = findWrapFilter();

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    static PropertyMap getInitialMap() {
        return $nasgenmap$;
    }

    private NativeBoolean(final boolean value, final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        this.value = value;
    }

    NativeBoolean(final boolean flag, final Global global) {
        this(flag, global.getBooleanPrototype(), global.getBooleanMap());
    }

    NativeBoolean(final boolean flag) {
        this(flag, Global.instance());
    }

    @Override
    public String safeToString() {
        return "[Boolean " + toString() + "]";
    }

    @Override
    public String toString() {
        return Boolean.toString(getValue());
    }

    /**
     * Get the value for this NativeBoolean
     * @return true or false
     */
    public boolean getValue() {
        return booleanValue();
    }

    /**
     * Get the value for this NativeBoolean
     * @return true or false
     */
    public boolean booleanValue() {
        return value;
    }

    @Override
    public String getClassName() {
        return "Boolean";
    }

    /**
     * ECMA 15.6.4.2 Boolean.prototype.toString ( )
     *
     * @param self self reference
     * @return string representation of this boolean
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toString(final Object self) {
        return getBoolean(self).toString();
    }

    /**
     * ECMA 15.6.4.3 Boolean.prototype.valueOf ( )
     *
     * @param self self reference
     * @return value of this boolean
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object valueOf(final Object self) {
        return getBoolean(self);
    }

    /**
     * ECMA 15.6.2.1 new Boolean (value)
     *
     * @param newObj is the new operator used to instantiate this NativeBoolean
     * @param self   self reference
     * @param value  value of boolean
     * @return the new NativeBoolean
     */
    @Constructor(arity = 1)
    public static Object constructor(final boolean newObj, final Object self, final Object value) {
        final boolean flag = JSType.toBoolean(value);

        if (newObj) {
            return new NativeBoolean(flag);
        }

        return flag;
    }

    private static Boolean getBoolean(final Object self) {
        if (self instanceof Boolean) {
            return ((Boolean)self);
        } else if (self instanceof NativeBoolean) {
            return ((NativeBoolean)self).getValue();
        } else if (self != null && self == Global.instance().getBooleanPrototype()) {
            return false;
        } else {
            throw typeError("not.a.boolean", ScriptRuntime.safeToString(self));
        }
    }

    /**
     * Lookup the appropriate method for an invoke dynamic call.
     *
     * @param request  The link request
     * @param receiver The receiver for the call
     * @return Link to be invoked at call site.
     */
    public static GuardedInvocation lookupPrimitive(final LinkRequest request, final Object receiver) {
        return PrimitiveLookup.lookupPrimitive(request, Boolean.class, new NativeBoolean((Boolean)receiver), WRAPFILTER);
    }

    /**
     * Wrap a native string in a NativeString object.
     *
     * @param receiver Native string.
     * @return Wrapped object.
     */
    @SuppressWarnings("unused")
    private static NativeBoolean wrapFilter(final Object receiver) {
        return new NativeBoolean((Boolean)receiver);
    }

    private static MethodHandle findWrapFilter() {
        return MH.findStatic(MethodHandles.lookup(), NativeBoolean.class, "wrapFilter", MH.type(NativeBoolean.class, Object.class));
    }
}
