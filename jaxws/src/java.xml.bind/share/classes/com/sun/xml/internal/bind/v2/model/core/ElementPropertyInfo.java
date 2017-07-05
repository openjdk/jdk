/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.core;

import java.util.List;

import javax.xml.namespace.QName;

/**
 * Property that maps to an element.
 *
 * @author Kohsuke Kawaguchi
 */
// TODO: there seems to be too much interactions between switches, and that's no good.
public interface ElementPropertyInfo<T,C> extends PropertyInfo<T,C> {
    /**
     * Returns the information about the types allowed in this property.
     *
     * <p>
     * In a simple case like the following, an element property only has
     * one {@link TypeRef} that points to {@link String} and tag name "foo".
     * <pre>
     * &#64;XmlElement
     * String abc;
     * </pre>
     *
     * <p>
     * However, in a general case an element property can be heterogeneous,
     * meaning you can put different types in it, each with a different tag name
     * (and a few other settings.)
     * <pre>
     * // list can contain String or Integer.
     * &#64;XmlElements({
     *   &#64;XmlElement(name="a",type=String.class),
     *   &#64;XmlElement(name="b",type=Integer.class),
     * }) {@code
     * List<Object> abc;
     * }</pre>
     * <p>
     * In this case this method returns a list of two {@link TypeRef}s.
     *
     *
     * @return
     *      Always non-null. Contains at least one entry.
     *      If {@link #isValueList()}==true, there's always exactly one type.
     */
    List<? extends TypeRef<T,C>> getTypes();

    /**
     * Gets the wrapper element name.
     *
     * @return
     *      must be null if {@link #isCollection()}==false or
     *      if {@link #isValueList()}==true.
     *
     *      Otherwise,
     *      this can be null (in which case there'll be no wrapper),
     *      or it can be non-null (in which case there'll be a wrapper)
     */
    QName getXmlName();

    /**
     * Checks if the wrapper element is required.
     *
     * @return
     *      Always false if {@link #getXmlName()}==null.
     */
    boolean isCollectionRequired();

    /**
     * Returns true if this property is nillable
     * (meaning the absence of the value is treated as nil='true')
     *
     * <p>
     * This method is only used when this property is a collection.
     */
    boolean isCollectionNillable();

    /**
     * Returns true if this property is a collection but its XML
     * representation is a list of values, not repeated elements.
     *
     * <p>
     * If {@link #isCollection()}==false, this property is always false.
     *
     * <p>
     * When this flag is true, {@code getTypes().size()==1} always holds.
     */
    boolean isValueList();

    /**
     * Returns true if this element is mandatory.
     *
     * For collections, this property isn't used.
     * TODO: define the semantics when this is a collection
     */
    boolean isRequired();

    Adapter<T,C> getAdapter();
}
