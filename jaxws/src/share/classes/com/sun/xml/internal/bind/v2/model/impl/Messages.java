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

package com.sun.xml.internal.bind.v2.model.impl;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Message resources
 */
enum Messages {
    // ClassInfoImpl
    ID_MUST_BE_STRING, // 1 arg

    MUTUALLY_EXCLUSIVE_ANNOTATIONS,  // 2 args
    DUPLICATE_ANNOTATIONS, // 1 arg
    NO_DEFAULT_CONSTRUCTOR, // 1 arg
    CANT_HANDLE_INTERFACE, // 1 arg
    CANT_HANDLE_INNER_CLASS, // 1 arg
    ANNOTATION_ON_WRONG_METHOD, // 0 args
    GETTER_SETTER_INCOMPATIBLE_TYPE, // 2 args
    DUPLICATE_ENTRY_IN_PROP_ORDER, // 1 arg
    DUPLICATE_PROPERTIES, // 1 arg

    XML_ELEMENT_MAPPING_ON_NON_IXMLELEMENT_METHOD, // 1 arg
    SCOPE_IS_NOT_COMPLEXTYPE, // 1 arg
    CONFLICTING_XML_ELEMENT_MAPPING,    // 2 args

    REFERENCE_TO_NON_ELEMENT, // 1 arg

    NON_EXISTENT_ELEMENT_MAPPING, // 2 args

    TWO_ATTRIBUTE_WILDCARDS, // 1 arg
    SUPER_CLASS_HAS_WILDCARD, // 0 args
    INVALID_ATTRIBUTE_WILDCARD_TYPE, // 1 arg
    PROPERTY_MISSING_FROM_ORDER, // 1 arg
    PROPERTY_ORDER_CONTAINS_UNUSED_ENTRY, // 2 args

    INVALID_XML_ENUM_VALUE, // 2 arg
    FAILED_TO_INITIALE_DATATYPE_FACTORY, // 0 args
    NO_IMAGE_WRITER, // 1 arg

    ILLEGAL_MIME_TYPE, // 2 args
    ILLEGAL_ANNOTATION, // 1 arg

    MULTIPLE_VALUE_PROPERTY, // 0 args
    ELEMENT_AND_VALUE_PROPERTY, // 0 args
    CONFLICTING_XML_TYPE_MAPPING, // 1 arg
    XMLVALUE_IN_DERIVED_TYPE, // 0 args
    SIMPLE_TYPE_IS_REQUIRED, // 1 arg
    PROPERTY_COLLISION, // 1 arg
    INVALID_IDREF, // 1 arg
    INVALID_XML_ELEMENT_REF, // 1 arg
    NO_XML_ELEMENT_DECL, // 2 args
    XML_ELEMENT_WRAPPER_ON_NON_COLLECTION, // 1 arg

    ANNOTATION_NOT_ALLOWED, // 1 arg
    XMLLIST_NEEDS_SIMPLETYPE, // 1 arg
    XMLLIST_ON_SINGLE_PROPERTY, // 0 arg
    NO_FACTORY_METHOD, // 2 args
    FACTORY_CLASS_NEEDS_FACTORY_METHOD, // 1 arg

    INCOMPATIBLE_API_VERSION, // 2 args
    INCOMPATIBLE_API_VERSION_MUSTANG, // 2 args
    RUNNING_WITH_1_0_RUNTIME, // 2 args

    MISSING_JAXB_PROPERTIES, // 1arg
    TRANSIENT_FIELD_NOT_BINDABLE, // 1 arg
    THERE_MUST_BE_VALUE_IN_XMLVALUE, // 1 arg
    UNMATCHABLE_ADAPTER, // 2 args
    ANONYMOUS_ARRAY_ITEM, // 1 arg

    ACCESSORFACTORY_INSTANTIATION_EXCEPTION, // 2 arg
    ACCESSORFACTORY_ACCESS_EXCEPTION, // 2 arg
    CUSTOM_ACCESSORFACTORY_PROPERTY_ERROR, // 2 arg
    CUSTOM_ACCESSORFACTORY_FIELD_ERROR, // 2 arg
        XMLGREGORIANCALENDAR_INVALID, // 1 arg
        XMLGREGORIANCALENDAR_SEC, // 0 arg
        XMLGREGORIANCALENDAR_MIN, // 0 arg
        XMLGREGORIANCALENDAR_HR, // 0 arg
        XMLGREGORIANCALENDAR_DAY, // 0 arg
        XMLGREGORIANCALENDAR_MONTH, // 0 arg
        XMLGREGORIANCALENDAR_YEAR, // 0 arg
        XMLGREGORIANCALENDAR_TIMEZONE, // 0 arg
    ;

    private static final ResourceBundle rb = ResourceBundle.getBundle(Messages.class.getName());

    @Override
    public String toString() {
        return format();
    }

    public String format( Object... args ) {
        return MessageFormat.format( rb.getString(name()), args );
    }
}
