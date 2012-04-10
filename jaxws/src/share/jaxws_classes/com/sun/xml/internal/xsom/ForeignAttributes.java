/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.xsom;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.relaxng.datatype.ValidationContext;

/**
 * Foreign attributes on schema elements.
 *
 * <p>
 * This is not a schema component as defined in the spec,
 * but this is often useful for a schema processing application.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ForeignAttributes extends Attributes {
    /**
     * Returns context information of the element to which foreign attributes
     * are attached.
     *
     * <p>
     * For example, this can be used to resolve relative references to other resources
     * (by using {@link ValidationContext#getBaseUri()}) or to resolve
     * namespace prefixes in the attribute values (by using {@link ValidationContext#resolveNamespacePrefix(String)}.
     *
     * @return
     *      always non-null.
     */
    ValidationContext getContext();

    /**
     * Returns the location of the element to which foreign attributes
     * are attached.
     */
    Locator getLocator();
}
