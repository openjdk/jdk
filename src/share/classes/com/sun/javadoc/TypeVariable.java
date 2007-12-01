/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.javadoc;


/**
 * Represents a type variable.
 * For example, the generic interface {@code List<E>} has a single
 * type variable {@code E}.
 * A type variable may have explicit bounds, as in
 * {@code C<R extends Remote>}.
 *
 * @author Scott Seligman
 * @since 1.5
 */
public interface TypeVariable extends Type {

    /**
     * Return the bounds of this type variable.
     * These are the types given by the <i>extends</i> clause.
     * Return an empty array if there are no explicit bounds.
     *
     * @return the bounds of this type variable.
     */
    Type[] bounds();

    /**
     * Return the class, interface, method, or constructor within
     * which this type variable is declared.
     *
     * @return the class, interface, method, or constructor within
     *         which this type variable is declared.
     */
    ProgramElementDoc owner();
}
