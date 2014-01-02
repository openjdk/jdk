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

import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.arrays.ArrayData;

/**
 * ECMA 10.6 Arguments Object.
 *
 * Arguments object for strict mode functions.
 */
public final class NativeStrictArguments extends ScriptObject {

    private static final MethodHandle G$LENGTH = findOwnMH("G$length", Object.class, Object.class);

    private static final MethodHandle S$LENGTH = findOwnMH("S$length", void.class, Object.class, Object.class);

    // property map for strict mode arguments object
    private static final PropertyMap map$;

    static {
        final ArrayList<Property> properties = new ArrayList<>(1);
        properties.add(AccessorProperty.create("length", Property.NOT_ENUMERABLE, G$LENGTH, S$LENGTH));
        PropertyMap map = PropertyMap.newMap(properties);
        // In strict mode, the caller and callee properties should throw TypeError
        // Need to add properties directly to map since slots are assigned speculatively by newUserAccessors.
        final int flags = Property.NOT_ENUMERABLE | Property.NOT_CONFIGURABLE;
        map = map.addProperty(map.newUserAccessors("caller", flags));
        map = map.addProperty(map.newUserAccessors("callee", flags));
        map$ = map.setIsShared();
    }

    static PropertyMap getInitialMap() {
        return map$;
    }

    private Object   length;
    private final Object[] namedArgs;

    NativeStrictArguments(final Object[] values, final int numParams,final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        setIsArguments();

        final ScriptFunction func = Global.instance().getTypeErrorThrower();
        // We have to fill user accessor functions late as these are stored
        // in this object rather than in the PropertyMap of this object.
        setUserAccessors("caller", func, func);
        setUserAccessors("callee", func, func);

        setArray(ArrayData.allocate(values));
        this.length = values.length;

        // extend/truncate named arg array as needed and copy values
        this.namedArgs = new Object[numParams];
        if (numParams > values.length) {
            Arrays.fill(namedArgs, UNDEFINED);
        }
        System.arraycopy(values, 0, namedArgs, 0, Math.min(namedArgs.length, values.length));
    }

    @Override
    public String getClassName() {
        return "Arguments";
    }

    /**
     * getArgument is used for named argument access.
     */
    @Override
    public Object getArgument(final int key) {
        return (key >=0 && key < namedArgs.length) ? namedArgs[key] : UNDEFINED;
    }

    /**
     * setArgument is used for named argument set.
     */
    @Override
    public void setArgument(final int key, final Object value) {
        if (key >= 0 && key < namedArgs.length) {
            namedArgs[key] = value;
        }
    }

    /**
     * Length getter
     * @param self self reference
     * @return length property value
     */
    public static Object G$length(final Object self) {
        if (self instanceof NativeStrictArguments) {
            return ((NativeStrictArguments)self).getArgumentsLength();
        }
        return 0;
    }

    /**
     * Length setter
     * @param self self reference
     * @param value value for length property
     */
    public static void S$length(final Object self, final Object value) {
        if (self instanceof NativeStrictArguments) {
            ((NativeStrictArguments)self).setArgumentsLength(value);
        }
    }

    private Object getArgumentsLength() {
        return length;
    }

    private void setArgumentsLength(final Object length) {
        this.length = length;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NativeStrictArguments.class, name, MH.type(rtype, types));
    }
}
