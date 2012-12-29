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

import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import javax.xml.namespace.QName;

/**
 * {@link TypeInfo} that maps to an element.
 *
 * Either {@link LeafInfo} or {@link ClassInfo}.
 *
 * TODO: better ANYTYPE_NAME.
 *
 * @author Kohsuke Kawaguchi
 */
public interface NonElement<T,C> extends TypeInfo<T,C> {
    public static final QName ANYTYPE_NAME = new QName(WellKnownNamespace.XML_SCHEMA, "anyType");

    /**
     * Gets the primary XML type ANYTYPE_NAME of the class.
     *
     * <p>
     * A Java type can be mapped to multiple XML types, but one of them is
     * considered "primary" and used when we generate a schema.
     *
     * @return
     *      null if the object doesn't have an explicit type ANYTYPE_NAME (AKA anonymous.)
     */
    QName getTypeName();

    /**
     * Returns true if this {@link NonElement} maps to text in XML,
     * without any attribute nor child elements.
     */
    boolean isSimpleType();
}
