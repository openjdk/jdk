/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol.ClassSymbol;

/**
 * This record models early construction context state. Instances are stored inside
 * a field of AttrContext, which is updated accordingly by Attr so that e.g. early field
 * references are disallowed when inside a lambda
 */
record EarlyConstructionContext(ClassSymbol owner,
                                boolean onlyWarnings,
                                boolean restricted,
                                boolean ctorPrologue) {

    /**
     * Dummy context. Used when not in early construction context
     */
    static final EarlyConstructionContext NONE =
            new EarlyConstructionContext(null, false, false, false);

    /**
     * Create a root early context
     */
    static EarlyConstructionContext of(ClassSymbol owner,
                                       boolean onlyWarnings,
                                       boolean restricted) {
        return new EarlyConstructionContext(owner, onlyWarnings, restricted, !onlyWarnings);
    }

    /**
     * Create a derived early context for a lambda or a class nested inside
     * the early construction context
     */
    EarlyConstructionContext nested(boolean isClass) {
        if (this == NONE) {
            return this;
        }
        return new EarlyConstructionContext(owner, onlyWarnings, true,
                !isClass && ctorPrologue);
    }
}
