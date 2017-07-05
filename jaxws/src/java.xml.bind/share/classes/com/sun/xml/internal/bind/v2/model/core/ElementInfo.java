/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.bind.JAXBElement;

/**
 * A particular use (specialization) of {@link JAXBElement}.
 *
 * TODO: is ElementInfo adaptable?
 *
 * @author Kohsuke Kawaguchi
 */
public interface ElementInfo<T,C> extends Element<T,C> {

    /**
     * Gets the object that represents the value property.
     *
     * @return
     *      non-null.
     */
    ElementPropertyInfo<T,C> getProperty();

    /**
     * Short for <code>getProperty().ref().get(0)</code>.
     *
     * The type of the value this element holds.
     *
     * Normally, this is the T of {@code JAXBElement<T>}.
     * But if the property is adapted, this is the on-the-wire type.
     *
     * Or if the element has a list of values, then this field
     * represents the type of the individual item.
     *
     * @see #getContentInMemoryType()
     */
    NonElement<T,C> getContentType();

    /**
     * T of {@code JAXBElement<T>}.
     *
     * <p>
     * This is tied to the in-memory representation.
     *
     * @see #getContentType()
     */
    T getContentInMemoryType();

    /**
     * Returns the representation for {@link JAXBElement}<i>{@code <contentInMemoryType>}</i>.
     *
     * <p>
     * This returns the signature in Java and thus isn't affected by the adapter.
     */
    T getType();

    /**
     * {@inheritDoc}
     *
     * {@link ElementInfo} can only substitute {@link ElementInfo}.
     */
    ElementInfo<T,C> getSubstitutionHead();

    /**
     * All the {@link ElementInfo}s whose {@link #getSubstitutionHead()} points
     * to this object.
     *
     * @return
     *      can be empty but never null.
     */
    Collection<? extends ElementInfo<T,C>> getSubstitutionMembers();
}
