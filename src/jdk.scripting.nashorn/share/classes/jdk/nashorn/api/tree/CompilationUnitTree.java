/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;

import java.util.List;

/**
 * Represents the abstract syntax tree for compilation units (source
 * files)
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @since 9
 */
@Deprecated(since="11", forRemoval=true)
public interface CompilationUnitTree extends Tree {
    /**
     * Return the list of source elements in this compilation unit.
     *
     * @return the list of source elements in this compilation unit
     */
    List<? extends Tree> getSourceElements();

    /**
     * Return the source name of this script compilation unit.
     *
     * @return the source name of this script compilation unit
     */
    String getSourceName();

    /**
     * Returns if this is a ECMAScript "strict" compilation unit or not.
     *
     * @return true if this compilation unit is declared "strict"
     */
    boolean isStrict();

    /**
     * Returns the line map for this compilation unit, if available.
     * Returns null if the line map is not available.
     *
     * @return the line map for this compilation unit
     */
    LineMap getLineMap();

    /**
     * Return the {@link ModuleTree} associated with this compilation unit. This is null,
     * if there is no module information from this compilation unit.
     *
     * @return the Module info or null
     */
    ModuleTree getModule();
}
