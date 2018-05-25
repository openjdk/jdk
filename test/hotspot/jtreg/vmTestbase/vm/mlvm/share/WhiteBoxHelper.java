/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package vm.mlvm.share;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

/**
 * WhiteBoxHelper class obtains a MethodHandle to a method defined in sun.hotspot.WhiteBox class.
 * If WhiteBox is not available or cannot be initialized, or the method is not available,
 * it throws an appropriate exception.
 *
 * This class was introduced to avoid "hard link" to WhiteBox.
 *
 */
public class WhiteBoxHelper {

    /**
     * Obtains {@link java.lang.invoke.MethodHandle} to a method in sun.hotspot.WhiteBox class.
     * If there is an error obtaining method handle, an exception is thrown.
     *
     * @param name Method name
     * @type Method type
     * @return {@link java.lang.invoke.MethodHandle} to the method. You can call it directly, WhiteBox instance is already bound to the handle.
     * @throws IllegalAccessException if method cannot be accessed (see {@link java.lang.invoke.MethodHandles.Lookup#findStatic()} for details)
     * @throws NoSuchMethodException if method cannot be found (see {@link java.lang.invoke.MethodHandles.Lookup#findStatic()} for details)
     * @throws ClassNotFoundException if WhiteBox class cannot be loaded
     * @throws InvocationTargetException if WhiteBox.getWhiteBox() method throws exception for some reason
     */
    public static MethodHandle getMethod(String name, MethodType type)
            throws IllegalAccessException, NoSuchMethodException, ClassNotFoundException, InvocationTargetException {

        Class<?> wbClass = Class.forName("sun.hotspot.WhiteBox");
        MethodHandles.Lookup lu = MethodHandles.lookup();
        MethodHandle getWB = lu.findStatic(wbClass, "getWhiteBox", MethodType.methodType(wbClass));
        Object wb;
        try {
            wb = getWB.invoke();
        } catch (Throwable e) {
            throw new InvocationTargetException(e, "Can't obtain WhiteBox instance");
        }
        return lu.findVirtual(wbClass, name, type).bindTo(wb);
    }

}
