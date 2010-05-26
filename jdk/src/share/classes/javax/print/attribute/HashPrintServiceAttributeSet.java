/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package javax.print.attribute;

import java.io.Serializable;

/**
 * Class HashPrintServiceAttributeSet provides an attribute set
 * which inherits its implementation from class {@link HashAttributeSet
 * HashAttributeSet} and enforces the semantic restrictions of interface
 * {@link PrintServiceAttributeSet PrintServiceAttributeSet}.
 * <P>
 *
 * @author  Alan Kaminsky
 */
public class HashPrintServiceAttributeSet extends HashAttributeSet
    implements PrintServiceAttributeSet, Serializable {

    private static final long serialVersionUID = 6642904616179203070L;

    /**
     * Construct a new, empty hash print service attribute set.
     */
    public HashPrintServiceAttributeSet() {
        super (PrintServiceAttribute.class);
    }


    /**
     * Construct a new hash print service attribute set,
     *  initially populated with the given value.
     *
     * @param  attribute  Attribute value to add to the set.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>attribute</CODE> is null.
     */
    public HashPrintServiceAttributeSet(PrintServiceAttribute attribute) {
        super (attribute, PrintServiceAttribute.class);
    }

    /**
     * Construct a new print service attribute set, initially populated with
     * the values from the given array. The new attribute set is populated
     * by adding the elements of <CODE>attributes</CODE> array to the set in
     * sequence, starting at index 0. Thus, later array elements may replace
     * earlier array elements if the array contains duplicate attribute
     * values or attribute categories.
     *
     * @param  attributes  Array of attribute values to add to the set.
     *                    If null, an empty attribute set is constructed.
     *
     * @exception  NullPointerException
     *     (unchecked exception)
     *      Thrown if any element of <CODE>attributes</CODE> is null.
     */
    public HashPrintServiceAttributeSet(PrintServiceAttribute[] attributes) {
        super (attributes, PrintServiceAttribute.class);
    }


    /**
     * Construct a new attribute set, initially populated with the
     * values from the  given set where the members of the attribute set
     * are restricted to the <code>PrintServiceAttribute</code> interface.
     *
     * @param  attributes set of attribute values to initialise the set. If
     *                    null, an empty attribute set is constructed.
     *
     * @exception  ClassCastException
     *     (unchecked exception) Thrown if any element of
     * <CODE>attributes</CODE> is not an instance of
     * <CODE>PrintServiceAttribute</CODE>.
     */
    public HashPrintServiceAttributeSet(PrintServiceAttributeSet attributes)
    {
        super(attributes, PrintServiceAttribute.class);
    }
}
