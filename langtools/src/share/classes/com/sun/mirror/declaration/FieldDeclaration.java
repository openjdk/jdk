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

package com.sun.mirror.declaration;


import com.sun.mirror.type.TypeMirror;


/**
 * Represents a field of a type declaration.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is included in {@link
 * javax.lang.model.element.VariableElement}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface FieldDeclaration extends MemberDeclaration {

    /**
     * Returns the type of this field.
     *
     * @return the type of this field
     */
    TypeMirror getType();

    /**
     * Returns the value of this field if this field is a compile-time
     * constant.  Returns <tt>null</tt> otherwise.
     * The value will be of a primitive type or <tt>String</tt>.
     * If the value is of a primitive type, it is wrapped in the
     * appropriate wrapper class (such as {@link Integer}).
     *
     * @return the value of this field if this field is a compile-time
     * constant, or <tt>null</tt> otherwise
     */
    Object getConstantValue();

    /**
     * Returns the text of a <i>constant expression</i> representing the
     * value of this field if this field is a compile-time constant.
     * Returns <tt>null</tt> otherwise.
     * The value will be of a primitive type or <tt>String</tt>.
     * The text returned is in a form suitable for representing the value
     * in source code.
     *
     * @return the text of a constant expression if this field is a
     *          compile-time constant, or <tt>null</tt> otherwise
     */
    String getConstantExpression();
}
