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

package com.sun.xml.internal.bind.v2.model.runtime;

import java.lang.reflect.Type;
import java.util.Collection;

import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;

/**
 * {@link PropertyInfo} that exposes more information.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public interface RuntimePropertyInfo extends PropertyInfo<Type,Class> {

    /** {@inheritDoc} */
    Collection<? extends RuntimeTypeInfo> ref();


    /**
     * Gets the {@link Accessor} for this property.
     *
     * <p>
     * Even for a multi-value property, this method returns an accessor
     * to that property. IOW, the accessor works against the raw type.
     *
     * <p>
     * This methods returns unoptimized accessor (because optimization
     * accessors are often combined into bigger pieces, and optimization
     * generally works better if you can look at a bigger piece, as opposed
     * to individually optimize a smaller components)
     *
     * @return
     *      never null.
     *
     * @see Accessor#optimize()
     */
    Accessor getAccessor();

    /**
     * Returns true if this property has an element-only content. False otherwise.
     */
    public boolean elementOnlyContent();

    /**
     * Gets the "raw" type of the field.
     *
     * The raw type is the actual signature of the property.
     * For example, if the field is the primitive int, this will be the primitive int.
     * If the field is Object, this will be Object.
     * If the property is the collection and typed as {@code Collection<Integer>},
     * this method returns {@code Collection<Integer>}.
     *
     * @return always non-null.
     */
    Type getRawType();

    /**
     * Gets the type of the individual item.
     *
     * The individual type is the signature of the property used to store individual
     * values. For a non-collection field, this is the same as {@link #getRawType()}.
     * For acollection property, this is the type used to store individual value.
     * So if {@link #getRawType()} is {@code Collection<Integer>}, this method will
     * return {@link Integer}.
     *
     * @return always non-null.
     */
    Type getIndividualType();
}
