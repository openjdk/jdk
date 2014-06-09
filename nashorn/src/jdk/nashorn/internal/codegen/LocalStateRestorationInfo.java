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
package jdk.nashorn.internal.codegen;

import jdk.nashorn.internal.codegen.types.Type;

/**
 * Encapsulates the information for restoring the local state when continuing execution after a rewrite triggered by
 * an optimistic assumption failure. An instance of this class is specific to a program point.
 *
 */
public class LocalStateRestorationInfo {
    private final Type[] localVariableTypes;
    private final int[] stackLoads;

    LocalStateRestorationInfo(final Type[] localVariableTypes, final int[] stackLoads) {
        this.localVariableTypes = localVariableTypes;
        this.stackLoads = stackLoads;
    }

    /**
     * Returns the types of the local variables at the continuation of a program point.
     * @return the types of the local variables at the continuation of a program point.
     */
    public Type[] getLocalVariableTypes() {
        return localVariableTypes.clone();
    }

    /**
     * Returns the indices of local variables that need to be loaded on stack before jumping to the continuation of the
     * program point.
     * @return the indices of local variables that need to be loaded on stack.
     */
    public int[] getStackLoads() {
        return stackLoads.clone();
    }
}
