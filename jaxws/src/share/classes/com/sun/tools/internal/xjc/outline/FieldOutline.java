/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.xjc.outline;

import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.model.CPropertyInfo;

/**
 * Representation of a field of {@link ClassOutline}.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface FieldOutline {

    /**
     * Gets the enclosing {@link ClassOutline}.
     */
    ClassOutline parent();

    /** Gets the corresponding model object. */
    CPropertyInfo getPropertyInfo();

    /**
     * Gets the type of the "raw value".
     *
     * <p>
     * This type can represent the entire value of this field.
     * For fields that can carry multiple values, this is an array.
     *
     * <p>
     * This type allows the client of the outline to generate code
     * to set/get values from a property.
     */
    JType getRawType();

    /**
     * Creates a new {@link FieldAccessor} of this field
     * for the specified object.
     *
     * @param targetObject
     *      Evaluates to an object, and the field on this object
     *      will be accessed.
     */
    FieldAccessor create( JExpression targetObject );
}
