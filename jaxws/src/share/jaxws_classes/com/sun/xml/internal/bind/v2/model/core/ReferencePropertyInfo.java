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

import java.util.Collection;
import java.util.Set;

import javax.xml.namespace.QName;

/**
 * {@link PropertyInfo} that holds references to other {@link Element}s.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ReferencePropertyInfo<T,C> extends PropertyInfo<T,C> {
    /**
     * Returns the information about the possible elements in this property.
     *
     * <p>
     * As of 2004/08/17, the spec only allows you to use different element names
     * when a property is a collection, but I think there's really no reason
     * to limit it there --- if the user wants to use a different tag name
     * for different objects, I don't see why this can be limited to collections.
     *
     * <p>
     * So this is a generalization of the spec. We always allow a property to have
     * multiple types and use different tag names for it, depending on the actual type.
     *
     * <p>
     * In most of the cases, this collection only contains 1 item. So the runtime system
     * is encouraged to provide a faster code-path that is optimized toward such cases.
     *
     * @return
     *      Always non-null. Contains at least one entry.
     */
    Set<? extends Element<T,C>> getElements();

    /**
     * {@inheritDoc}.
     *
     * If this {@link ReferencePropertyInfo} has a wildcard in it,
     * then the returned list will contain {@link WildcardTypeInfo}.
     */
    Collection<? extends TypeInfo<T,C>> ref();

    /**
     * Gets the wrapper element name.
     *
     * @return
     *      must be null if not collection. If the property is a collection,
     *      this can be null (in which case there'll be no wrapper),
     *      or it can be non-null (in which case there'll be a wrapper)
     */
    QName getXmlName();

    /**
     * Returns true if this property is nillable
     * (meaning the absence of the value is treated as nil='true')
     *
     * <p>
     * This method is only used when this property is a collection.
     */
    boolean isCollectionNillable();

    /**
     * Checks if the wrapper element is required.
     *
     * @return
     *      Always false if {@link #getXmlName()}==null.
     */
    boolean isCollectionRequired();

    /**
     * Returns true if this property can hold {@link String}s to represent
     * mixed content model.
     */
    boolean isMixed();

    /**
     * If this property supports the wildcard, returns its mode.
     *
     * @return null
     *      if the wildcard is not allowed on this element.
     */
    WildcardMode getWildcard();

    /**
     * If this property supports the wildcard, returns its DOM handler.
     *
     * @return null
     *      if the wildcard is not allowed on this element.
     */
    C getDOMHandler();

    /**
     * Returns true if this element is mandatory.
     */
    boolean isRequired();

    Adapter<T,C> getAdapter();
}
