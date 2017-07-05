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

package com.sun.tools.internal.ws.wsdl.document.schema;

import javax.xml.namespace.QName;

/**
 *
 * @author WS Development Team
 */
public interface SchemaConstants {

    // namespace URIs
    public static String NS_XMLNS = "http://www.w3.org/2000/xmlns/";
    public static String NS_XSD = "http://www.w3.org/2001/XMLSchema";
    public static String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    // QNames
    public static QName QNAME_ALL = new QName(NS_XSD, "all");
    public static QName QNAME_ANNOTATION = new QName(NS_XSD, "annotation");
    public static QName QNAME_ANY = new QName(NS_XSD, "any");
    public static QName QNAME_ANY_ATTRIBUTE = new QName(NS_XSD, "anyAttribute");
    public static QName QNAME_ATTRIBUTE = new QName(NS_XSD, "attribute");
    public static QName QNAME_ATTRIBUTE_GROUP =
        new QName(NS_XSD, "attributeGroup");
    public static QName QNAME_CHOICE = new QName(NS_XSD, "choice");
    public static QName QNAME_COMPLEX_CONTENT =
        new QName(NS_XSD, "complexContent");
    public static QName QNAME_COMPLEX_TYPE = new QName(NS_XSD, "complexType");
    public static QName QNAME_ELEMENT = new QName(NS_XSD, "element");
    public static QName QNAME_ENUMERATION = new QName(NS_XSD, "enumeration");
    public static QName QNAME_EXTENSION = new QName(NS_XSD, "extension");
    public static QName QNAME_FIELD = new QName(NS_XSD, "field");
    public static QName QNAME_FRACTION_DIGITS =
        new QName(NS_XSD, "fractionDigits");
    public static QName QNAME_GROUP = new QName(NS_XSD, "group");
    public static QName QNAME_IMPORT = new QName(NS_XSD, "import");
    public static QName QNAME_INCLUDE = new QName(NS_XSD, "include");
    public static QName QNAME_KEY = new QName(NS_XSD, "key");
    public static QName QNAME_KEYREF = new QName(NS_XSD, "keyref");
    public static QName QNAME_LENGTH = new QName(NS_XSD, "length");
    public static QName QNAME_LIST = new QName(NS_XSD, "list");
    public static QName QNAME_MAX_EXCLUSIVE = new QName(NS_XSD, "maxExclusive");
    public static QName QNAME_MAX_INCLUSIVE = new QName(NS_XSD, "maxInclusive");
    public static QName QNAME_MAX_LENGTH = new QName(NS_XSD, "maxLength");
    public static QName QNAME_MIN_EXCLUSIVE = new QName(NS_XSD, "minExclusive");
    public static QName QNAME_MIN_INCLUSIVE = new QName(NS_XSD, "minInclusive");
    public static QName QNAME_MIN_LENGTH = new QName(NS_XSD, "minLength");
    public static QName QNAME_NOTATION = new QName(NS_XSD, "notation");
    public static QName QNAME_RESTRICTION = new QName(NS_XSD, "restriction");
    public static QName QNAME_PATTERN = new QName(NS_XSD, "pattern");
    public static QName QNAME_PRECISION = new QName(NS_XSD, "precision");
    public static QName QNAME_REDEFINE = new QName(NS_XSD, "redefine");
    public static QName QNAME_SCALE = new QName(NS_XSD, "scale");
    public static QName QNAME_SCHEMA = new QName(NS_XSD, "schema");
    public static QName QNAME_SELECTOR = new QName(NS_XSD, "selector");
    public static QName QNAME_SEQUENCE = new QName(NS_XSD, "sequence");
    public static QName QNAME_SIMPLE_CONTENT =
        new QName(NS_XSD, "simpleContent");
    public static QName QNAME_SIMPLE_TYPE = new QName(NS_XSD, "simpleType");
    public static QName QNAME_TOTAL_DIGITS = new QName(NS_XSD, "totalDigits");
    public static QName QNAME_UNIQUE = new QName(NS_XSD, "unique");
    public static QName QNAME_UNION = new QName(NS_XSD, "union");
    public static QName QNAME_WHITE_SPACE = new QName(NS_XSD, "whiteSpace");

    // QNames for built-in XSD types
    public static QName QNAME_TYPE_STRING = new QName(NS_XSD, "string");
    public static QName QNAME_TYPE_NORMALIZED_STRING =
        new QName(NS_XSD, "normalizedString");
    public static QName QNAME_TYPE_TOKEN = new QName(NS_XSD, "token");
    public static QName QNAME_TYPE_BYTE = new QName(NS_XSD, "byte");
    public static QName QNAME_TYPE_UNSIGNED_BYTE =
        new QName(NS_XSD, "unsignedByte");
    public static QName QNAME_TYPE_BASE64_BINARY =
        new QName(NS_XSD, "base64Binary");
    public static QName QNAME_TYPE_HEX_BINARY = new QName(NS_XSD, "hexBinary");
    public static QName QNAME_TYPE_INTEGER = new QName(NS_XSD, "integer");
    public static QName QNAME_TYPE_POSITIVE_INTEGER =
        new QName(NS_XSD, "positiveInteger");
    public static QName QNAME_TYPE_NEGATIVE_INTEGER =
        new QName(NS_XSD, "negativeInteger");
    public static QName QNAME_TYPE_NON_NEGATIVE_INTEGER =
        new QName(NS_XSD, "nonNegativeInteger");
    public static QName QNAME_TYPE_NON_POSITIVE_INTEGER =
        new QName(NS_XSD, "nonPositiveInteger");
    public static QName QNAME_TYPE_INT = new QName(NS_XSD, "int");
    public static QName QNAME_TYPE_UNSIGNED_INT =
        new QName(NS_XSD, "unsignedInt");
    public static QName QNAME_TYPE_LONG = new QName(NS_XSD, "long");
    public static QName QNAME_TYPE_UNSIGNED_LONG =
        new QName(NS_XSD, "unsignedLong");
    public static QName QNAME_TYPE_SHORT = new QName(NS_XSD, "short");
    public static QName QNAME_TYPE_UNSIGNED_SHORT =
        new QName(NS_XSD, "unsignedShort");
    public static QName QNAME_TYPE_DECIMAL = new QName(NS_XSD, "decimal");
    public static QName QNAME_TYPE_FLOAT = new QName(NS_XSD, "float");
    public static QName QNAME_TYPE_DOUBLE = new QName(NS_XSD, "double");
    public static QName QNAME_TYPE_BOOLEAN = new QName(NS_XSD, "boolean");
    public static QName QNAME_TYPE_TIME = new QName(NS_XSD, "time");
    public static QName QNAME_TYPE_DATE_TIME = new QName(NS_XSD, "dateTime");
    public static QName QNAME_TYPE_DURATION = new QName(NS_XSD, "duration");
    public static QName QNAME_TYPE_DATE = new QName(NS_XSD, "date");
    public static QName QNAME_TYPE_G_MONTH = new QName(NS_XSD, "gMonth");
    public static QName QNAME_TYPE_G_YEAR = new QName(NS_XSD, "gYear");
    public static QName QNAME_TYPE_G_YEAR_MONTH =
        new QName(NS_XSD, "gYearMonth");
    public static QName QNAME_TYPE_G_DAY = new QName(NS_XSD, "gDay");
    public static QName QNAME_TYPE_G_MONTH_DAY = new QName(NS_XSD, "gMonthDay");
    public static QName QNAME_TYPE_NAME = new QName(NS_XSD, "Name");
    public static QName QNAME_TYPE_QNAME = new QName(NS_XSD, "QName");
    public static QName QNAME_TYPE_NCNAME = new QName(NS_XSD, "NCName");
    public static QName QNAME_TYPE_ANY_URI = new QName(NS_XSD, "anyURI");
    public static QName QNAME_TYPE_ID = new QName(NS_XSD, "ID");
    public static QName QNAME_TYPE_IDREF = new QName(NS_XSD, "IDREF");
    public static QName QNAME_TYPE_IDREFS = new QName(NS_XSD, "IDREFS");
    public static QName QNAME_TYPE_ENTITY = new QName(NS_XSD, "ENTITY");
    public static QName QNAME_TYPE_ENTITIES = new QName(NS_XSD, "ENTITIES");
    public static QName QNAME_TYPE_NOTATION = new QName(NS_XSD, "NOTATION");
    public static QName QNAME_TYPE_NMTOKEN = new QName(NS_XSD, "NMTOKEN");
    public static QName QNAME_TYPE_NMTOKENS = new QName(NS_XSD, "NMTOKENS");

    public static QName QNAME_TYPE_LANGUAGE = new QName(NS_XSD, "language");

    // QNames for special types
    public static QName QNAME_TYPE_URTYPE = new QName(NS_XSD, "anyType");
    public static QName QNAME_TYPE_SIMPLE_URTYPE =
        new QName(NS_XSD, "anySimpleType");
}
