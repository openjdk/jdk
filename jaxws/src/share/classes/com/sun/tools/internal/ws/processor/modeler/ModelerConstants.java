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

package com.sun.tools.internal.ws.processor.modeler;


/**
 *
 * @author WS Development Team
 */
public interface ModelerConstants {

    public static final String BRACKETS  = "[]";
    public static final String FALSE_STR = "false";
    public static final String ZERO_STR  = "0";
    public static final String NULL_STR  = "null";
    public static final String ARRAY_STR = "Array";

    /*
     * Java ClassNames
     */
    /*
      * Java ClassNames
      */
     public static final String IOEXCEPTION_CLASSNAME         = "java.io.IOException";
     public static final String BOOLEAN_CLASSNAME             = "boolean";
     public static final String BOXED_BOOLEAN_CLASSNAME       = "java.lang.Boolean";
     public static final String BYTE_CLASSNAME                = "byte";
     public static final String BYTE_ARRAY_CLASSNAME          = BYTE_CLASSNAME+BRACKETS;
     public static final String BOXED_BYTE_CLASSNAME          = "java.lang.Byte";
     public static final String BOXED_BYTE_ARRAY_CLASSNAME    = BOXED_BYTE_CLASSNAME+BRACKETS;
     public static final String CLASS_CLASSNAME               = "java.lang.Class";
     public static final String CHAR_CLASSNAME                = "char";
     public static final String BOXED_CHAR_CLASSNAME          = "java.lang.Character";
     public static final String DOUBLE_CLASSNAME              = "double";
     public static final String BOXED_DOUBLE_CLASSNAME        = "java.lang.Double";
     public static final String FLOAT_CLASSNAME               = "float";
     public static final String BOXED_FLOAT_CLASSNAME         = "java.lang.Float";
     public static final String INT_CLASSNAME                 = "int";
     public static final String BOXED_INTEGER_CLASSNAME       = "java.lang.Integer";
     public static final String LONG_CLASSNAME                = "long";
     public static final String BOXED_LONG_CLASSNAME          = "java.lang.Long";
     public static final String SHORT_CLASSNAME               = "short";
     public static final String BOXED_SHORT_CLASSNAME         = "java.lang.Short";
     public static final String BIGDECIMAL_CLASSNAME          = "java.math.BigDecimal";
     public static final String BIGINTEGER_CLASSNAME          = "java.math.BigInteger";
     public static final String CALENDAR_CLASSNAME            = "java.util.Calendar";
     public static final String DATE_CLASSNAME                = "java.util.Date";
     public static final String STRING_CLASSNAME              = "java.lang.String";
     public static final String STRING_ARRAY_CLASSNAME        = STRING_CLASSNAME+BRACKETS;
     public static final String QNAME_CLASSNAME               = "javax.xml.namespace.QName";
     public static final String VOID_CLASSNAME                = "void";
     public static final String OBJECT_CLASSNAME              = "java.lang.Object";
     public static final String SOAPELEMENT_CLASSNAME         = "javax.xml.soap.SOAPElement";
     public static final String IMAGE_CLASSNAME               = "java.awt.Image";
     public static final String MIME_MULTIPART_CLASSNAME      = "javax.mail.internet.MimeMultipart";
     public static final String SOURCE_CLASSNAME              = "javax.xml.transform.Source";
     public static final String DATA_HANDLER_CLASSNAME        = "javax.activation.DataHandler";
     public static final String URI_CLASSNAME                 = "java.net.URI";
//     public static final String URI_CLASSNAME                  = "java.lang.String";
     // Collections
     public static final String COLLECTION_CLASSNAME          = "java.util.Collection";
     public static final String LIST_CLASSNAME                = "java.util.List";
     public static final String SET_CLASSNAME                 = "java.util.Set";
     public static final String VECTOR_CLASSNAME              = "java.util.Vector";
     public static final String STACK_CLASSNAME               = "java.util.Stack";
     public static final String LINKED_LIST_CLASSNAME         = "java.util.LinkedList";
     public static final String ARRAY_LIST_CLASSNAME          = "java.util.ArrayList";
     public static final String HASH_SET_CLASSNAME            = "java.util.HashSet";
     public static final String TREE_SET_CLASSNAME            = "java.util.TreeSet";

     // Maps
     public static final String MAP_CLASSNAME                 = "java.util.Map";
     public static final String HASH_MAP_CLASSNAME            = "java.util.HashMap";
     public static final String TREE_MAP_CLASSNAME            = "java.util.TreeMap";
     public static final String HASHTABLE_CLASSNAME           = "java.util.Hashtable";
     public static final String PROPERTIES_CLASSNAME          = "java.util.Properties";
//     public static final String WEAK_HASH_MAP_CLASSNAME       = "java.util.WeakHashMap";
     public static final String JAX_WS_MAP_ENTRY_CLASSNAME   = "com.sun.xml.internal.ws.encoding.soap.JAXWSMapEntry";

}
