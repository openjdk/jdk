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

package com.sun.xml.internal.bind.v2.model.impl;

import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationSource;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;

/**
 * Exposes the core information that forms a {@link PropertyInfo}.
 */
interface PropertySeed<T,C,F,M> extends Locatable, AnnotationSource {

    /**
     * The name of the property is a spec defined concept --- although it doesn't do
     * so explicitly in anywhere.
     *
     * @see PropertyInfo#getName()
     */
    String getName();


    /**
     * Gets the actual data type of the field.
     *
     * <p>
     * The data of the property is stored by using this type.
     *
     * <p>
     * The difference between the {@link RuntimePropertyInfo#getIndividualType()}
     * and this method is clear when the property is a multi-value property.
     * The {@link RuntimePropertyInfo#getIndividualType()} method returns the type of the item,
     * but this method returns the actual collection type.
     */
    T getRawType();
}
