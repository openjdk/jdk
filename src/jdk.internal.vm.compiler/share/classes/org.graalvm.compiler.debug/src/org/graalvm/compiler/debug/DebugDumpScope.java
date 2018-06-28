/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

public class DebugDumpScope {

    public final String name;

    /**
     * Specifies if this scope decorates an inner scope. A hierarchical or tree representation of
     * nested scopes may choose to represent a decorator scope at the same level as the scope it
     * decorates.
     */
    public final boolean decorator;

    public DebugDumpScope(String name) {
        this(name, false);
    }

    public DebugDumpScope(String name, boolean decorator) {
        this.name = name;
        this.decorator = decorator;
    }

    @Override
    public String toString() {
        return "DebugDumpScope[" + name + "]";
    }
}
