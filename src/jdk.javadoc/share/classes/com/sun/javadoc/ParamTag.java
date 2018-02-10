/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javadoc;

/**
 * Represents an @param documentation tag.
 * Stores the name and comment parts of the parameter tag.
 * An @param tag may represent either a method or constructor parameter,
 * or a type parameter.
 *
 * @author Robert Field
 *
 * @deprecated
 *   The declarations in this package have been superseded by those
 *   in the package {@code jdk.javadoc.doclet}.
 *   For more information, see the <i>Migration Guide</i> in the documentation for that package.
 */
@Deprecated(since="9", forRemoval=true)
@SuppressWarnings("removal")
public interface ParamTag extends Tag {

    /**
     * Return the name of the parameter or type parameter
     * associated with this {@code ParamTag}.
     * The angle brackets delimiting a type parameter are not part of
     * its name.
     *
     * @return the parameter name.
     */
    String parameterName();

    /**
     * Return the parameter comment
     * associated with this {@code ParamTag}.
     *
     * @return the parameter comment.
     */
    String parameterComment();

    /**
     * Return true if this {@code ParamTag} corresponds to a type
     * parameter.  Return false if it corresponds to an ordinary parameter
     * of a method or constructor.
     *
     * @return true if this {@code ParamTag} corresponds to a type
     * parameter.
     * @since 1.5
     */
    boolean isTypeParameter();
}
