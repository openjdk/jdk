/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api;

import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JType;

/**
 * Represents a property of a wrapper-style element.
 *
 * <p>
 * Carrys information about one property of a wrapper-style
 * element. This interface is solely intended for the use by
 * the JAX-RPC and otherwise the use is discouraged.
 *
 * <p>
 * REVISIT: use CodeModel.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 * @see Mapping
 */
public interface Property {
    /**
     * The name of the property.
     *
     * <p>
     * This method returns a valid identifier suitable for
     * the use as a variable name.
     *
     * @return
     *      always non-null. Camel-style name like "foo" or "barAndZot".
     *      Note that it may contain non-ASCII characters (CJK, etc.)
     *      The caller is responsible for proper escaping if it
     *      wants to print this as a variable name.
     */
    String name();

    /**
     * The Java type of the property.
     *
     * @return
     *      always non-null.
     *      {@link JType} is a representation of a Java type in a codeModel.
     *      If you just need the fully-qualified class name, call {@link JType#fullName()}.
     */
    JType type();

    /**
     * Name of the XML element that corresponds to the property.
     *
     * <p>
     * Each child of a wrapper style element corresponds with an
     * element, and this method returns that name.
     *
     * @return
     *      always non-null valid {@link QName}.
     */
    QName elementName();

    QName rawName();

}
