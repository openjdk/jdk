/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * Static factories for certain VarHandle/MethodHandle variants.
 * <p>
 * Some methods take no receiver argument. In these cases, the receiver is the
 * lookup class.
 * <p>
 * The methods will throw an {@link InternalError} if the lookup fails.
 * <p>
 * Here is an example of how one of these methods could be used:
 * {@snippet lang=java
 * static MethodHandle BAR_HANDLE =
 *         MhUtil.findVirtual(MethodHandles.lookup(),
 *                 Foo.class,"bar",MethodType.methodType(int.class));
 * }
 */
public final class MhUtil {

    private MhUtil() {}

    public static VarHandle findVarHandle(MethodHandles.Lookup lookup,
                                          String name,
                                          Class<?> type) {
        return findVarHandle(lookup, lookup.lookupClass(), name, type);
    }

    public static VarHandle findVarHandle(MethodHandles.Lookup lookup,
                                          Class<?> recv,
                                          String name,
                                          Class<?> type) {
        try {
            return lookup.findVarHandle(recv, name, type);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    public static MethodHandle findVirtual(MethodHandles.Lookup lookup,
                                           String name,
                                           MethodType type) {
        return findVirtual(lookup, lookup.lookupClass(), name, type);
    }

    public static MethodHandle findVirtual(MethodHandles.Lookup lookup,
                                           Class<?> refc,
                                           String name,
                                           MethodType type) {
        try {
            return lookup.findVirtual(refc, name, type);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    public static MethodHandle findStatic(MethodHandles.Lookup lookup,
                                          String name,
                                          MethodType type) {
        return findStatic(lookup, lookup.lookupClass(), name, type);
    }

    public static MethodHandle findStatic(MethodHandles.Lookup lookup,
                                          Class<?> refc,
                                          String name,
                                          MethodType type) {
        try {
            return lookup.findStatic(refc, name, type);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

}
