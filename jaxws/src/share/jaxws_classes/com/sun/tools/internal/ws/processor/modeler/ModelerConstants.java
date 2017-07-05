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

package com.sun.tools.internal.ws.processor.modeler;


/**
 *
 * @author WS Development Team
 */
public enum ModelerConstants {

    FALSE_STR("false"),
    ZERO_STR("0"),
    NULL_STR("null"),
    ARRAY_STR("Array"),

    /*
     * Java ClassNames
     */
    /*
      * Java ClassNames
      */
    IOEXCEPTION_CLASSNAME("java.io.IOException"),
    BOOLEAN_CLASSNAME("boolean"),
    BOXED_BOOLEAN_CLASSNAME("java.lang.Boolean"),
    BYTE_CLASSNAME("byte"),
    BYTE_ARRAY_CLASSNAME("byte[]"),
    BOXED_BYTE_CLASSNAME("java.lang.Byte"),
    BOXED_BYTE_ARRAY_CLASSNAME("java.lang.Byte[]"),
    CLASS_CLASSNAME("java.lang.Class"),
    CHAR_CLASSNAME("char"),
    BOXED_CHAR_CLASSNAME("java.lang.Character"),
    DOUBLE_CLASSNAME("double"),
    BOXED_DOUBLE_CLASSNAME("java.lang.Double"),
    FLOAT_CLASSNAME("float"),
    BOXED_FLOAT_CLASSNAME("java.lang.Float"),
    INT_CLASSNAME("int"),
    BOXED_INTEGER_CLASSNAME("java.lang.Integer"),
    LONG_CLASSNAME("long"),
    BOXED_LONG_CLASSNAME("java.lang.Long"),
    SHORT_CLASSNAME("short"),
    BOXED_SHORT_CLASSNAME("java.lang.Short"),
    BIGDECIMAL_CLASSNAME("java.math.BigDecimal"),
    BIGINTEGER_CLASSNAME("java.math.BigInteger"),
    CALENDAR_CLASSNAME("java.util.Calendar"),
    DATE_CLASSNAME("java.util.Date"),
    STRING_CLASSNAME("java.lang.String"),
    STRING_ARRAY_CLASSNAME("java.lang.String[]"),
    QNAME_CLASSNAME("javax.xml.namespace.QName"),
    VOID_CLASSNAME("void"),
    OBJECT_CLASSNAME("java.lang.Object"),
    SOAPELEMENT_CLASSNAME("javax.xml.soap.SOAPElement"),
    IMAGE_CLASSNAME("java.awt.Image"),
    MIME_MULTIPART_CLASSNAME("javax.mail.internet.MimeMultipart"),
    SOURCE_CLASSNAME("javax.xml.transform.Source"),
    DATA_HANDLER_CLASSNAME("javax.activation.DataHandler"),
    URI_CLASSNAME("java.net.URI"),
    //     URI_CLASSNAME                ("java.lang.String"),
    // Collections
    COLLECTION_CLASSNAME("java.util.Collection"),
    LIST_CLASSNAME("java.util.List"),
    SET_CLASSNAME("java.util.Set"),
    VECTOR_CLASSNAME("java.util.Vector"),
    STACK_CLASSNAME("java.util.Stack"),
    LINKED_LIST_CLASSNAME("java.util.LinkedList"),
    ARRAY_LIST_CLASSNAME("java.util.ArrayList"),
    HASH_SET_CLASSNAME("java.util.HashSet"),
    TREE_SET_CLASSNAME("java.util.TreeSet"),

    // Maps
    MAP_CLASSNAME("java.util.Map"),
    HASH_MAP_CLASSNAME("java.util.HashMap"),
    TREE_MAP_CLASSNAME("java.util.TreeMap"),
    HASHTABLE_CLASSNAME("java.util.Hashtable"),
    PROPERTIES_CLASSNAME("java.util.Properties"),
    //     WEAK_HASH_MAP_CLASSNAME     ("java.util.WeakHashMap"),
    JAX_WS_MAP_ENTRY_CLASSNAME("com.sun.xml.internal.ws.encoding.soap.JAXWSMapEntry");

    private String value;

    private ModelerConstants(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
