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

import static jdk.nashorn.internal.codegen.CompilerConstants.interfaceCallNoLookup;

import jdk.nashorn.internal.codegen.CompilerConstants;

/**
 * Interface implemented by {@link ScriptObject}s that act as scope.
 */
public interface Scope {

    /** Method handle that points to {@link Scope#getSplitState}. */
    public static final CompilerConstants.Call GET_SPLIT_STATE = interfaceCallNoLookup(Scope.class, "getSplitState", int.class);

    /** Method handle that points to {@link Scope#setSplitState(int)}. */
    public static final CompilerConstants.Call SET_SPLIT_STATE = interfaceCallNoLookup(Scope.class, "setSplitState", void.class, int.class);

    /**
     * Get the scope's split method state.
     * @return the current state
     */
    public int getSplitState();

    /**
     * Set the scope's split method state.
     * @param state the new state.
     */
    public void setSplitState(int state);
}
