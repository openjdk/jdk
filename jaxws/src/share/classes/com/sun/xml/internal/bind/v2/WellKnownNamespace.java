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
package com.sun.xml.internal.bind.v2;

/**
 * Well-known namespace URIs.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 * @since 2.0
 */
public abstract class WellKnownNamespace {
    private WellKnownNamespace() {} // no instanciation please

    public static final String XML_SCHEMA =
        "http://www.w3.org/2001/XMLSchema";

    public static final String XML_SCHEMA_INSTANCE =
        "http://www.w3.org/2001/XMLSchema-instance";

    public static final Object XML_SCHEMA_DATATYPES =
        "http://www.w3.org/2001/XMLSchema-datatypes";

    public static final String XML_NAMESPACE_URI =
        "http://www.w3.org/XML/1998/namespace";

    public static final String XOP =
        "http://www.w3.org/2004/08/xop/include";

    public static final String SWA_URI =
        "http://ws-i.org/profiles/basic/1.1/xsd";

    public static final String XML_MIME_URI = "http://www.w3.org/2005/05/xmlmime";

    public static final String JAXB = "http://java.sun.com/xml/ns/jaxb";

//    public static final QName XSI_NIL = new QName(XML_SCHEMA_INSTANCE,"nil");
}
