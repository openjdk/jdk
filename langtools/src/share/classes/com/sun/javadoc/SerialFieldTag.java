/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * Documents a Serializable field defined by an ObjectStreamField.
 * <pre>
 * The class parses and stores the three serialField tag parameters:
 *
 * - field name
 * - field type name
 *      (fully-qualified or visible from the current import context)
 * - description of the valid values for the field

 * </pre>
 * This tag is only allowed in the javadoc for the special member
 * serialPersistentFields.
 *
 * @author Joe Fialli
 *
 * @see java.io.ObjectStreamField
 */
public interface SerialFieldTag extends Tag, Comparable<Object> {

    /**
     * Return the serializable field name.
     */
    public String fieldName();

    /**
     * Return the field type string.
     */
    public String fieldType();

    /**
     * Return the ClassDoc for field type.
     *
     * @return null if no ClassDoc for field type is visible from
     *         containingClass context.
     */
    public ClassDoc fieldTypeDoc();

    /**
     * Return the field comment. If there is no serialField comment, return
     * javadoc comment of corresponding FieldDoc.
     */
    public String description();

    /**
     * Compares this Object with the specified Object for order.  Returns a
     * negative integer, zero, or a positive integer as this Object is less
     * than, equal to, or greater than the given Object.
     * <p>
     * Included to make SerialFieldTag items java.lang.Comparable.
     *
     * @param   obj the <code>Object</code> to be compared.
     * @return  a negative integer, zero, or a positive integer as this Object
     *          is less than, equal to, or greater than the given Object.
     * @exception ClassCastException the specified Object's type prevents it
     *            from being compared to this Object.
     * @since 1.2
     */
    public int compareTo(Object obj);
}
