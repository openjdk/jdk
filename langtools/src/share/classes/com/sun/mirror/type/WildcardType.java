/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.mirror.type;


import java.util.Collection;


/**
 * Represents a wildcard type argument.
 * Examples include:    <pre><tt>
 *   ?
 *   ? extends Number
 *   ? super T
 * </tt></pre>
 *
 * <p> A wildcard may have its upper bound explicitly set by an
 * <tt>extends</tt> clause, its lower bound explicitly set by a
 * <tt>super</tt> clause, or neither (but not both).
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.type.WildcardType}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface WildcardType extends TypeMirror {

    /**
     * Returns the upper bounds of this wildcard.
     * If no upper bound is explicitly declared, then
     * an empty collection is returned.
     *
     * @return the upper bounds of this wildcard
     */
    Collection<ReferenceType> getUpperBounds();

    /**
     * Returns the lower bounds of this wildcard.
     * If no lower bound is explicitly declared, then
     * an empty collection is returned.
     *
     * @return the lower bounds of this wildcard
     */
    Collection<ReferenceType> getLowerBounds();
}
