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

package com.sun.xml.internal.bind.v2;

import javax.xml.XMLConstants;

/**
 * Well-known namespace URIs.
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com), Martin Grebac (martin.grebac@oracle.com)
 * @since 2.0
 */
public abstract class WellKnownNamespace {
    private WellKnownNamespace() {} // no instanciation please

    /**
     * @deprecated Use javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI instead;
     * @return
     * @throws CloneNotSupportedException
     */
    @Deprecated()
    public static final String XML_SCHEMA = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    /**
     * @deprecated Use javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI instead
     * @return
     * @throws CloneNotSupportedException
     */
    @Deprecated()
    public static final String XML_SCHEMA_INSTANCE = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;

    /**
     * @deprecated Use javax.xml.XMLConstants.XML_NS_URI instead;
     * @return
     * @throws CloneNotSupportedException
     */
    @Deprecated()
    public static final String XML_NAMESPACE_URI = XMLConstants.XML_NS_URI;

    public static final String XOP = "http://www.w3.org/2004/08/xop/include";

    public static final String SWA_URI = "http://ws-i.org/profiles/basic/1.1/xsd";

    public static final String XML_MIME_URI = "http://www.w3.org/2005/05/xmlmime";

    public static final String JAXB = "http://java.sun.com/xml/ns/jaxb";

}
