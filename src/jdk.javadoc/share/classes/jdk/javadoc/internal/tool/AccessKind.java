/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;

import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * The access value kinds.
 */
public enum AccessKind {

    // DO NOT REORDER THESE CONSTANTS OR CODE WILL BREAK

    /** No limits */
    PRIVATE,
    /** Limits access to public, protected and package private entities */
    PACKAGE,
    /** Limits access to public and protected entities */
    PROTECTED,
    /** Limits access to public entities */
    PUBLIC;

    public static AccessKind of(Set<Modifier> mods) {
        if (mods.contains(Modifier.PUBLIC))
            return AccessKind.PUBLIC;
        else if (mods.contains(Modifier.PROTECTED))
            return AccessKind.PROTECTED;
        else if (mods.contains(Modifier.PRIVATE))
            return AccessKind.PRIVATE;
        else
            return AccessKind.PACKAGE;
    }
}
