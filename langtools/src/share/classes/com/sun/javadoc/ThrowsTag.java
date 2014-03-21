/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Represents a @throws or @exception documentation tag.
 * Parses and holds the exception name and exception comment.
 * Note: @exception is a backwards compatible synonymy for @throws.
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @see ExecutableMemberDoc#throwsTags()
 *
 */
public interface ThrowsTag extends Tag {

    /**
     * Return the name of the exception
     * associated with this {@code ThrowsTag}.
     *
     * @return name of the exception.
     */
    String exceptionName();

    /**
     * Return the exception comment
     * associated with this {@code ThrowsTag}.
     *
     * @return exception comment.
     */
    String exceptionComment();

    /**
     * Return a {@code ClassDoc} that represents the exception.
     * If the type of the exception is a type variable, return the
     * {@code ClassDoc} of its erasure.
     *
     * <p> <i>This method cannot accommodate certain generic type
     * constructs.  The {@code exceptionType} method
     * should be used instead.</i>
     *
     * @return {@code ClassDoc} that represents the exception.
     * @see #exceptionType
     */
    ClassDoc exception();

    /**
     * Return the type of the exception
     * associated with this {@code ThrowsTag}.
     * This may be a {@code ClassDoc} or a {@code TypeVariable}.
     *
     * @return the type of the exception.
     * @since 1.5
     */
    Type exceptionType();
}
