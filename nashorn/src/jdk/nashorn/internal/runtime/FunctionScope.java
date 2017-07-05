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

/**
 * This is the base class for function scopes.  Subclasses of this class are
 * produced by the ObjectClassGenerator along with additional fields for storing
 * local vars.  The number of fields required is determined by ObjectCreator.
 *
 * The scope is also responsible for handling the var arg 'arguments' object,
 * though most of the access is via generated code.
 *
 * The constructor of this class is responsible for any function prologue
 * involving the scope.
 *
 * TODO see NASHORN-715.
 */
public class FunctionScope extends ScriptObject implements Scope {

    /** Area to store scope arguments. (public for access from scripts.) */
    public final ScriptObject arguments;

    /** Flag to indicate that a split method issued a return statement */
    private int splitState = -1;

    /**
     * Constructor
     *
     * @param map         property map
     * @param callerScope caller scope
     * @param arguments   arguments
     */
    public FunctionScope(final PropertyMap map, final ScriptObject callerScope, final ScriptObject arguments) {
        super(callerScope, map);
        this.arguments = arguments;
        setIsScope();
    }

    /**
     * Constructor
     *
     * @param map         property map
     * @param callerScope caller scope
     */
    public FunctionScope(final PropertyMap map, final ScriptObject callerScope) {
        super(callerScope, map);
        this.arguments = null;
        setIsScope();
    }

    /**
     * Get the current split state.
     * @return current split state
     */
    @Override
    public int getSplitState() {
        return splitState;
    }

    /**
     * Set the current split state.
     * @param state current split state
     */
    @Override
    public void setSplitState(final int state) {
        splitState = state;
    }
}
