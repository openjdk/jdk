/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.xsom.impl.parser;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Formats error messages.
 */
public class Messages
{
    /** Loads a string resource and formats it with specified arguments. */
    public static String format( String property, Object... args ) {
        String text = ResourceBundle.getBundle(
            Messages.class.getName()).getString(property);
        return MessageFormat.format(text,args);
    }

//
//
// Message resources
//
//
    public static final String ERR_UNDEFINED_SIMPLETYPE =
        "UndefinedSimpleType"; // arg:1
    public static final String ERR_UNDEFINED_COMPLEXTYPE =
        "UndefinedCompplexType"; // arg:1
    public static final String ERR_UNDEFINED_TYPE =
        "UndefinedType"; // arg:1
    public static final String ERR_UNDEFINED_ELEMENT =
        "UndefinedElement"; // arg:1
    public static final String ERR_UNDEFINED_MODELGROUP =
        "UndefinedModelGroup"; // arg:1
    public static final String ERR_UNDEFINED_ATTRIBUTE =
        "UndefinedAttribute"; // arg:1
    public static final String ERR_UNDEFINED_ATTRIBUTEGROUP =
        "UndefinedAttributeGroup"; // arg:1
    public static final String ERR_UNDEFINED_IDENTITY_CONSTRAINT =
        "UndefinedIdentityConstraint"; // arg:1
    public static final String ERR_UNDEFINED_PREFIX =
        "UndefinedPrefix"; // arg:1

    public static final String ERR_DOUBLE_DEFINITION =
        "DoubleDefinition"; // arg:1
    public static final String ERR_DOUBLE_DEFINITION_ORIGINAL =
        "DoubleDefinition.Original"; // arg:0

    public static final String ERR_MISSING_SCHEMALOCATION =
        "MissingSchemaLocation"; // arg:0

    public static final String ERR_ENTITY_RESOLUTION_FAILURE =
        "EntityResolutionFailure"; // arg:2

    public static final String ERR_SIMPLE_CONTENT_EXPECTED =
        "SimpleContentExpected"; // arg:2

    public static final String JAXP_UNSUPPORTED_PROPERTY =
        "JAXPUnsupportedProperty"; // arg:1

    public static final String JAXP_SUPPORTED_PROPERTY =
        "JAXPSupportedProperty"; // arg:1
}
