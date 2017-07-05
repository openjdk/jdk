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
package com.sun.tools.internal.xjc.reader;

import com.sun.xml.internal.bind.v2.WellKnownNamespace;


/**
 * Useful constant values.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class Const {

    /** XML namespace URI. */
    public final static String XMLNS_URI =
        "http://www.w3.org/2000/xmlns/";

    /** JAXB customization URI. */
    public final static String JAXB_NSURI =
        "http://java.sun.com/xml/ns/jaxb";

    /** XJC vendor extension namespace URI. */
    public final static String XJC_EXTENSION_URI =
        "http://java.sun.com/xml/ns/jaxb/xjc";

    /** RELAX NG namespace URI. */
    public static final String RELAXNG_URI =
        "http://relaxng.org/ns/structure/1.0";

    /** URI to represent DTD. */
    public static final String DTD = "DTD";

    /**
     * Attribute name of the expected media type.
     *
     * @see WellKnownNamespace#XML_MIME_URI
     * @see http://www.w3.org/TR/xml-media-types/
     */
    public static final String EXPECTED_CONTENT_TYPES = "expectedContentTypes";
}
