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

package jdk.nashorn.internal.runtime.linker;

import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Constructor of method handles used to guard call sites.
 */
public final class NashornGuards {
    private static final MethodHandle IS_MAP              = findOwnMH("isMap", boolean.class, ScriptObject.class, PropertyMap.class);
    private static final MethodHandle IS_MAP_SCRIPTOBJECT = findOwnMH("isMap", boolean.class, Object.class, PropertyMap.class);
    private static final MethodHandle IS_INSTANCEOF_2     = findOwnMH("isInstanceOf2", boolean.class, Object.class, Class.class, Class.class);
    private static final MethodHandle IS_SCRIPTOBJECT     = findOwnMH("isScriptObject", boolean.class, Object.class);

    private static final boolean CCE_ONLY = Options.getBooleanProperty("nashorn.cce");

    // don't create me!
    private NashornGuards() {
    }

    /**
     * Given a callsite descriptor and a link request, determine whether we should use an instanceof
     * check explicitly for the guard if needed, or if we should link it with a try/catch ClassCastException
     * combinator as its relink criteria - i.e. relink when CCE is thrown.
     *
     * @param desc     callsite descriptor
     * @param request  link request
     * @return true of explicit instanceof check is needed
     */
    public static boolean explicitInstanceOfCheck(final CallSiteDescriptor desc, final LinkRequest request) {
        //THIS is currently true, as the inliner encounters several problems with sun.misc.ValueConversions.castReference
        //otherwise. We should only use the exception based relink where we have no choice, and the result is faster code,
        //for example in the NativeArray, TypedArray, ContinuousArray getters. For the standard callsite, it appears that
        //we lose performance rather than gain it, due to JVM issues. :-(
        return !CCE_ONLY;
    }

    /**
     * Returns a guard that does an instanceof ScriptObject check on the receiver
     * @return guard
     */
    public static MethodHandle getScriptObjectGuard() {
        return IS_SCRIPTOBJECT;
    }

    /**
     * Returns a guard that does an instanceof ScriptObject check on the receiver
     * @param explicitInstanceOfCheck - if false, then this is a nop, because it's all the guard does
     * @return guard
     */
    public static MethodHandle getScriptObjectGuard(final boolean explicitInstanceOfCheck) {
        return explicitInstanceOfCheck ? IS_SCRIPTOBJECT : null;
    }

    /**
     * Get the guard that checks if a {@link PropertyMap} is equal to
     * a known map, using reference comparison
     *
     * @param explicitInstanceOfCheck true if we should do an explicit script object instanceof check instead of just casting
     * @param map The map to check against. This will be bound to the guard method handle
     *
     * @return method handle for guard
     */
    public static MethodHandle getMapGuard(final PropertyMap map, final boolean explicitInstanceOfCheck) {
        return MH.insertArguments(explicitInstanceOfCheck ? IS_MAP_SCRIPTOBJECT : IS_MAP, 1, map);
    }

    /**
     * Get a guard that checks if in item is an instance of either of two classes.
     *
     * @param class1 the first class
     * @param class2 the second class
     * @return method handle for guard
     */
    public static MethodHandle getInstanceOf2Guard(final Class<?> class1, final Class<?> class2) {
        return MH.insertArguments(IS_INSTANCEOF_2, 1, class1, class2);
    }

    @SuppressWarnings("unused")
    private static boolean isScriptObject(final Object self) {
        return self instanceof ScriptObject;
    }

    @SuppressWarnings("unused")
    private static boolean isScriptObject(final Class<? extends ScriptObject> clazz, final Object self) {
        return clazz.isInstance(self);
    }

    @SuppressWarnings("unused")
    private static boolean isMap(final ScriptObject self, final PropertyMap map) {
        return self.getMap() == map;
    }

    @SuppressWarnings("unused")
    private static boolean isMap(final Object self, final PropertyMap map) {
        return self instanceof ScriptObject && ((ScriptObject)self).getMap() == map;
    }


    @SuppressWarnings("unused")
    private static boolean isInstanceOf2(final Object self, final Class<?> class1, final Class<?> class2) {
        return class1.isInstance(self) || class2.isInstance(self);
    }

    @SuppressWarnings("unused")
    private static boolean isScriptFunction(final Object self) {
        return self instanceof ScriptFunction;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NashornGuards.class, name, MH.type(rtype, types));
    }
}
