/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.mirror.type;


import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.util.Types;
import com.sun.mirror.util.TypeVisitor;


/**
 * Represents a type in the Java programming language.
 * Types include primitive types, class and interface types, array
 * types, and type variables.  Wildcard type arguments, and the
 * pseudo-type representing the type of <tt>void</tt>, are represented
 * by type mirrors as well.
 *
 * <p> Types may be compared using the utility methods in
 * {@link Types}.
 * There is no guarantee that any particular type will
 * always be represented by the same object.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.type.TypeMirror}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 *
 * @see Declaration
 * @see Types
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface TypeMirror {

    /**
     * Returns a string representation of this type.
     * Any names embedded in the expression are qualified.
     *
     * @return a string representation of this type
     */
    String toString();

    /**
     * Tests whether two types represent the same type.
     *
     * @param obj the object to be compared with this type
     * @return <tt>true</tt> if the specified object represents the same
     *          type as this.
     */
    boolean equals(Object obj);

    /**
     * Applies a visitor to this type.
     *
     * @param v the visitor operating on this type
     */
    void accept(TypeVisitor v);
}
