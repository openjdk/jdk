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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import jdk.nashorn.internal.lookup.Lookup;

/**
 * The SpillProperty is a subclass of AccessorProperties. Anything not in the initial property map
 * will end up in the embed fields of the ScriptObject or in the Spill, which currently is a growing
 * Object only array in ScriptObject
 *
 * @see AccessorProperty
 * @see ScriptObject
 */
public final class SpillProperty extends AccessorProperty {
    private static final MethodHandle SPILLGETTER = MH.asType(MH.getter(MethodHandles.lookup(), ScriptObject.class, "spill", Object[].class), Lookup.GET_OBJECT_TYPE);

    /**
     * Constructor
     *
     * @param key    property key
     * @param flags  property flags
     * @param slot   property slot/index
     * @param getter getter for property
     * @param setter setter for property, or null if not configurable and writable
     */
    public SpillProperty(final String key, final int flags, final int slot, final MethodHandle getter, final MethodHandle setter) {
        super(key, flags, slot, getter, setter);
    }

    private SpillProperty(final SpillProperty property) {
        super(property);
    }

    @Override
    protected Property copy() {
        return new SpillProperty(this);
    }

    @Override
    public MethodHandle getGetter(final Class<?> type) {
        if (isSpill()) {
            return MH.filterArguments(super.getGetter(type), 0, SPILLGETTER);
        }

        return super.getGetter(type);
    }

    @Override
    public MethodHandle getSetter(final Class<?> type, final PropertyMap currentMap) {
        if (isSpill()) {
            return MH.filterArguments(super.getSetter(type, currentMap), 0, SPILLGETTER);
        }

        return super.getSetter(type, currentMap);
    }

}
