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

package jdk.nashorn.internal.runtime.arrays;

import java.lang.invoke.MethodHandle;

/**
 * Interface implemented by all arrays that are directly accessible as underlying
 * native arrays
 */
public interface ContinuousArray {

    /**
     * Return the guard used for the setter, usually some kind of {@link ArrayData#has}
     * @return guard handle for setters
     */
    public MethodHandle getSetGuard();

    /**
     * Return element getter for a certain type at a certain program point
     * @param returnType   return type
     * @param programPoint program point
     * @return element getter or null if not supported (used to implement slow linkage instead
     *   as fast isn't possible)
     */
    public MethodHandle getElementGetter(final Class<?> returnType, final int programPoint);

    /**
     * Return element getter for a certain type at a certain program point
     * @param elementType element type
     * @return element setter or null if not supported (used to implement slow linkage instead
     *   as fast isn't possible)
     */
    public MethodHandle getElementSetter(final Class<?> elementType);
}
