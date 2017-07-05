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

import com.sun.tools.internal.ws.processor.model.java.JavaSimpleType;

import java.util.HashMap;
import java.util.Map;

import static com.sun.tools.internal.ws.processor.modeler.ModelerConstants.*;
/**
 *
 * @author WS Development Team
 */
public final class JavaSimpleTypeCreator {

    /*
     * Mapped JavaSimpleTypes
     */
    public static final JavaSimpleType BOOLEAN_JAVATYPE = new JavaSimpleType(BOOLEAN_CLASSNAME.getValue(), FALSE_STR.getValue());
    public static final JavaSimpleType BOXED_BOOLEAN_JAVATYPE = new JavaSimpleType(BOXED_BOOLEAN_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType BYTE_JAVATYPE = new JavaSimpleType(BYTE_CLASSNAME.getValue(), "(byte)" + ZERO_STR.getValue());
    public static final JavaSimpleType BYTE_ARRAY_JAVATYPE = new JavaSimpleType(BYTE_ARRAY_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType BOXED_BYTE_JAVATYPE = new JavaSimpleType(BOXED_BYTE_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType BOXED_BYTE_ARRAY_JAVATYPE = new JavaSimpleType(BOXED_BYTE_ARRAY_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType DOUBLE_JAVATYPE = new JavaSimpleType(DOUBLE_CLASSNAME.getValue(), ZERO_STR.getValue());
    public static final JavaSimpleType BOXED_DOUBLE_JAVATYPE = new JavaSimpleType(BOXED_DOUBLE_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType FLOAT_JAVATYPE = new JavaSimpleType(FLOAT_CLASSNAME.getValue(), ZERO_STR.getValue());
    public static final JavaSimpleType BOXED_FLOAT_JAVATYPE = new JavaSimpleType(BOXED_FLOAT_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType INT_JAVATYPE = new JavaSimpleType(INT_CLASSNAME.getValue(), ZERO_STR.getValue());
    public static final JavaSimpleType BOXED_INTEGER_JAVATYPE = new JavaSimpleType(BOXED_INTEGER_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType LONG_JAVATYPE = new JavaSimpleType(LONG_CLASSNAME.getValue(), ZERO_STR.getValue());
    public static final JavaSimpleType BOXED_LONG_JAVATYPE = new JavaSimpleType(BOXED_LONG_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType SHORT_JAVATYPE = new JavaSimpleType(SHORT_CLASSNAME.getValue(), "(short)" + ZERO_STR.getValue());
    public static final JavaSimpleType BOXED_SHORT_JAVATYPE = new JavaSimpleType(BOXED_SHORT_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType DECIMAL_JAVATYPE = new JavaSimpleType(BIGDECIMAL_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType BIG_INTEGER_JAVATYPE = new JavaSimpleType(BIGINTEGER_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType CALENDAR_JAVATYPE = new JavaSimpleType(CALENDAR_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType DATE_JAVATYPE = new JavaSimpleType(DATE_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType STRING_JAVATYPE = new JavaSimpleType(STRING_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType STRING_ARRAY_JAVATYPE = new JavaSimpleType(STRING_ARRAY_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType QNAME_JAVATYPE = new JavaSimpleType(QNAME_CLASSNAME.getValue(), NULL_STR.getValue());
    public static final JavaSimpleType VOID_JAVATYPE = new JavaSimpleType(VOID_CLASSNAME.getValue(), null);
    public static final JavaSimpleType OBJECT_JAVATYPE = new JavaSimpleType(OBJECT_CLASSNAME.getValue(), null);
    public static final JavaSimpleType SOAPELEMENT_JAVATYPE = new JavaSimpleType(SOAPELEMENT_CLASSNAME.getValue(), null);
    public static final JavaSimpleType URI_JAVATYPE = new JavaSimpleType(URI_CLASSNAME.getValue(), null);

    // Attachment types
    public static final JavaSimpleType IMAGE_JAVATYPE = new JavaSimpleType(IMAGE_CLASSNAME.getValue(), null);
    public static final JavaSimpleType MIME_MULTIPART_JAVATYPE = new JavaSimpleType(MIME_MULTIPART_CLASSNAME.getValue(), null);
    public static final JavaSimpleType SOURCE_JAVATYPE = new JavaSimpleType(SOURCE_CLASSNAME.getValue(), null);
    public static final JavaSimpleType DATA_HANDLER_JAVATYPE = new JavaSimpleType(DATA_HANDLER_CLASSNAME.getValue(), null);

    // bug fix: 4923650
    private static final Map<String, JavaSimpleType> JAVA_TYPES = new HashMap<String, JavaSimpleType>(31);

    static {
        JAVA_TYPES.put(BOOLEAN_CLASSNAME.getValue(), BOOLEAN_JAVATYPE);
        JAVA_TYPES.put(BOXED_BOOLEAN_CLASSNAME.getValue(), BOXED_BOOLEAN_JAVATYPE);
        JAVA_TYPES.put(BYTE_CLASSNAME.getValue(), BYTE_JAVATYPE);
        JAVA_TYPES.put(BYTE_ARRAY_CLASSNAME.getValue(), BYTE_ARRAY_JAVATYPE);
        JAVA_TYPES.put(BOXED_BYTE_CLASSNAME.getValue(), BOXED_BYTE_JAVATYPE);
        JAVA_TYPES.put(BOXED_BYTE_ARRAY_CLASSNAME.getValue(), BOXED_BYTE_ARRAY_JAVATYPE);
        JAVA_TYPES.put(DOUBLE_CLASSNAME.getValue(), DOUBLE_JAVATYPE);
        JAVA_TYPES.put(BOXED_DOUBLE_CLASSNAME.getValue(), BOXED_DOUBLE_JAVATYPE);
        JAVA_TYPES.put(FLOAT_CLASSNAME.getValue(), FLOAT_JAVATYPE);
        JAVA_TYPES.put(BOXED_FLOAT_CLASSNAME.getValue(), BOXED_FLOAT_JAVATYPE);
        JAVA_TYPES.put(INT_CLASSNAME.getValue(), INT_JAVATYPE);
        JAVA_TYPES.put(BOXED_INTEGER_CLASSNAME.getValue(), BOXED_INTEGER_JAVATYPE);
        JAVA_TYPES.put(LONG_CLASSNAME.getValue(), LONG_JAVATYPE);
        JAVA_TYPES.put(BOXED_LONG_CLASSNAME.getValue(), BOXED_LONG_JAVATYPE);
        JAVA_TYPES.put(SHORT_CLASSNAME.getValue(), SHORT_JAVATYPE);
        JAVA_TYPES.put(BOXED_SHORT_CLASSNAME.getValue(), BOXED_SHORT_JAVATYPE);
        JAVA_TYPES.put(BIGDECIMAL_CLASSNAME.getValue(), DECIMAL_JAVATYPE);
        JAVA_TYPES.put(BIGINTEGER_CLASSNAME.getValue(), BIG_INTEGER_JAVATYPE);
        JAVA_TYPES.put(CALENDAR_CLASSNAME.getValue(), CALENDAR_JAVATYPE);
        JAVA_TYPES.put(DATE_CLASSNAME.getValue(), DATE_JAVATYPE);
        JAVA_TYPES.put(STRING_CLASSNAME.getValue(), STRING_JAVATYPE);
        JAVA_TYPES.put(STRING_ARRAY_CLASSNAME.getValue(), STRING_ARRAY_JAVATYPE);
        JAVA_TYPES.put(QNAME_CLASSNAME.getValue(), QNAME_JAVATYPE);
        JAVA_TYPES.put(VOID_CLASSNAME.getValue(), VOID_JAVATYPE);
        JAVA_TYPES.put(OBJECT_CLASSNAME.getValue(), OBJECT_JAVATYPE);
        JAVA_TYPES.put(SOAPELEMENT_CLASSNAME.getValue(), SOAPELEMENT_JAVATYPE);
        JAVA_TYPES.put(URI_CLASSNAME.getValue(), URI_JAVATYPE);
        JAVA_TYPES.put(IMAGE_CLASSNAME.getValue(), IMAGE_JAVATYPE);
        JAVA_TYPES.put(MIME_MULTIPART_CLASSNAME.getValue(), MIME_MULTIPART_JAVATYPE);
        JAVA_TYPES.put(SOURCE_CLASSNAME.getValue(), SOURCE_JAVATYPE);
        JAVA_TYPES.put(DATA_HANDLER_CLASSNAME.getValue(), DATA_HANDLER_JAVATYPE);
    }

    private JavaSimpleTypeCreator() {
    }

    //  bug fix: 4923650
    public static JavaSimpleType getJavaSimpleType(String className) {
        return JAVA_TYPES.get(className);
    }
}
