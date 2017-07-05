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
package com.sun.tools.internal.ws.wsdl.document.soap;

import javax.xml.namespace.QName;

/**
 * Interface defining SOAP1.2-related constants.
 *
 * @author WS Development Team
 */
public interface SOAP12Constants {

    // namespace URIs
    public static String NS_WSDL_SOAP = "http://schemas.xmlsoap.org/wsdl/soap12/";
    public static String NS_SOAP_ENCODING =
        "http://schemas.xmlsoap.org/soap/encoding/";

    // other URIs
    public static String URI_SOAP_TRANSPORT_HTTP =
        "http://www.w3.org/2003/05/soap/bindings/HTTP/";
    ;

    // QNames
    public static QName QNAME_ADDRESS = new QName(NS_WSDL_SOAP, "address");
    public static QName QNAME_BINDING = new QName(NS_WSDL_SOAP, "binding");
    public static QName QNAME_BODY = new QName(NS_WSDL_SOAP, "body");
    public static QName QNAME_FAULT = new QName(NS_WSDL_SOAP, "fault");
    public static QName QNAME_HEADER = new QName(NS_WSDL_SOAP, "header");
    public static QName QNAME_HEADERFAULT =
        new QName(NS_WSDL_SOAP, "headerfault");
    public static QName QNAME_OPERATION = new QName(NS_WSDL_SOAP, "operation");

    // SOAP encoding QNames
    public static QName QNAME_TYPE_ARRAY = new QName(NS_SOAP_ENCODING, "Array");
    public static QName QNAME_ATTR_GROUP_COMMON_ATTRIBUTES =
        new QName(NS_SOAP_ENCODING, "commonAttributes");
    public static QName QNAME_ATTR_ARRAY_TYPE =
        new QName(NS_SOAP_ENCODING, "arrayType");
    public static QName QNAME_ATTR_ITEM_TYPE =
        new QName(NS_SOAP_ENCODING, "itemType");
    public static QName QNAME_ATTR_ARRAY_SIZE =
        new QName(NS_SOAP_ENCODING, "arraySize");
    public static QName QNAME_ATTR_OFFSET =
        new QName(NS_SOAP_ENCODING, "offset");
    public static QName QNAME_ATTR_POSITION =
        new QName(NS_SOAP_ENCODING, "position");

    public static QName QNAME_TYPE_BASE64 =
        new QName(NS_SOAP_ENCODING, "base64");

    public static QName QNAME_ELEMENT_STRING =
        new QName(NS_SOAP_ENCODING, "string");
    public static QName QNAME_ELEMENT_NORMALIZED_STRING =
        new QName(NS_SOAP_ENCODING, "normalizedString");
    public static QName QNAME_ELEMENT_TOKEN =
        new QName(NS_SOAP_ENCODING, "token");
    public static QName QNAME_ELEMENT_BYTE =
        new QName(NS_SOAP_ENCODING, "byte");
    public static QName QNAME_ELEMENT_UNSIGNED_BYTE =
        new QName(NS_SOAP_ENCODING, "unsignedByte");
    public static QName QNAME_ELEMENT_BASE64_BINARY =
        new QName(NS_SOAP_ENCODING, "base64Binary");
    public static QName QNAME_ELEMENT_HEX_BINARY =
        new QName(NS_SOAP_ENCODING, "hexBinary");
    public static QName QNAME_ELEMENT_INTEGER =
        new QName(NS_SOAP_ENCODING, "integer");
    public static QName QNAME_ELEMENT_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "positiveInteger");
    public static QName QNAME_ELEMENT_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "negativeInteger");
    public static QName QNAME_ELEMENT_NON_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonNegativeInteger");
    public static QName QNAME_ELEMENT_NON_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonPositiveInteger");
    public static QName QNAME_ELEMENT_INT = new QName(NS_SOAP_ENCODING, "int");
    public static QName QNAME_ELEMENT_UNSIGNED_INT =
        new QName(NS_SOAP_ENCODING, "unsignedInt");
    public static QName QNAME_ELEMENT_LONG =
        new QName(NS_SOAP_ENCODING, "long");
    public static QName QNAME_ELEMENT_UNSIGNED_LONG =
        new QName(NS_SOAP_ENCODING, "unsignedLong");
    public static QName QNAME_ELEMENT_SHORT =
        new QName(NS_SOAP_ENCODING, "short");
    public static QName QNAME_ELEMENT_UNSIGNED_SHORT =
        new QName(NS_SOAP_ENCODING, "unsignedShort");
    public static QName QNAME_ELEMENT_DECIMAL =
        new QName(NS_SOAP_ENCODING, "decimal");
    public static QName QNAME_ELEMENT_FLOAT =
        new QName(NS_SOAP_ENCODING, "float");
    public static QName QNAME_ELEMENT_DOUBLE =
        new QName(NS_SOAP_ENCODING, "double");
    public static QName QNAME_ELEMENT_BOOLEAN =
        new QName(NS_SOAP_ENCODING, "boolean");
    public static QName QNAME_ELEMENT_TIME =
        new QName(NS_SOAP_ENCODING, "time");
    public static QName QNAME_ELEMENT_DATE_TIME =
        new QName(NS_SOAP_ENCODING, "dateTime");
    public static QName QNAME_ELEMENT_DURATION =
        new QName(NS_SOAP_ENCODING, "duration");
    public static QName QNAME_ELEMENT_DATE =
        new QName(NS_SOAP_ENCODING, "date");
    public static QName QNAME_ELEMENT_G_MONTH =
        new QName(NS_SOAP_ENCODING, "gMonth");
    public static QName QNAME_ELEMENT_G_YEAR =
        new QName(NS_SOAP_ENCODING, "gYear");
    public static QName QNAME_ELEMENT_G_YEAR_MONTH =
        new QName(NS_SOAP_ENCODING, "gYearMonth");
    public static QName QNAME_ELEMENT_G_DAY =
        new QName(NS_SOAP_ENCODING, "gDay");
    public static QName QNAME_ELEMENT_G_MONTH_DAY =
        new QName(NS_SOAP_ENCODING, "gMonthDay");
    public static QName QNAME_ELEMENT_NAME =
        new QName(NS_SOAP_ENCODING, "Name");
    public static QName QNAME_ELEMENT_QNAME =
        new QName(NS_SOAP_ENCODING, "QName");
    public static QName QNAME_ELEMENT_NCNAME =
        new QName(NS_SOAP_ENCODING, "NCName");
    public static QName QNAME_ELEMENT_ANY_URI =
        new QName(NS_SOAP_ENCODING, "anyURI");
    public static QName QNAME_ELEMENT_ID = new QName(NS_SOAP_ENCODING, "ID");
    public static QName QNAME_ELEMENT_IDREF =
        new QName(NS_SOAP_ENCODING, "IDREF");
    public static QName QNAME_ELEMENT_IDREFS =
        new QName(NS_SOAP_ENCODING, "IDREFS");
    public static QName QNAME_ELEMENT_ENTITY =
        new QName(NS_SOAP_ENCODING, "ENTITY");
    public static QName QNAME_ELEMENT_ENTITIES =
        new QName(NS_SOAP_ENCODING, "ENTITIES");
    public static QName QNAME_ELEMENT_NOTATION =
        new QName(NS_SOAP_ENCODING, "NOTATION");
    public static QName QNAME_ELEMENT_NMTOKEN =
        new QName(NS_SOAP_ENCODING, "NMTOKEN");
    public static QName QNAME_ELEMENT_NMTOKENS =
        new QName(NS_SOAP_ENCODING, "NMTOKENS");

    public static QName QNAME_TYPE_STRING =
        new QName(NS_SOAP_ENCODING, "string");
    public static QName QNAME_TYPE_NORMALIZED_STRING =
        new QName(NS_SOAP_ENCODING, "normalizedString");
    public static QName QNAME_TYPE_TOKEN = new QName(NS_SOAP_ENCODING, "token");
    public static QName QNAME_TYPE_BYTE = new QName(NS_SOAP_ENCODING, "byte");
    public static QName QNAME_TYPE_UNSIGNED_BYTE =
        new QName(NS_SOAP_ENCODING, "unsignedByte");
    public static QName QNAME_TYPE_BASE64_BINARY =
        new QName(NS_SOAP_ENCODING, "base64Binary");
    public static QName QNAME_TYPE_HEX_BINARY =
        new QName(NS_SOAP_ENCODING, "hexBinary");
    public static QName QNAME_TYPE_INTEGER =
        new QName(NS_SOAP_ENCODING, "integer");
    public static QName QNAME_TYPE_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "positiveInteger");
    public static QName QNAME_TYPE_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "negativeInteger");
    public static QName QNAME_TYPE_NON_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonNegativeInteger");
    public static QName QNAME_TYPE_NON_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonPositiveInteger");
    public static QName QNAME_TYPE_INT = new QName(NS_SOAP_ENCODING, "int");
    public static QName QNAME_TYPE_UNSIGNED_INT =
        new QName(NS_SOAP_ENCODING, "unsignedInt");
    public static QName QNAME_TYPE_LONG = new QName(NS_SOAP_ENCODING, "long");
    public static QName QNAME_TYPE_UNSIGNED_LONG =
        new QName(NS_SOAP_ENCODING, "unsignedLong");
    public static QName QNAME_TYPE_SHORT = new QName(NS_SOAP_ENCODING, "short");
    public static QName QNAME_TYPE_UNSIGNED_SHORT =
        new QName(NS_SOAP_ENCODING, "unsignedShort");
    public static QName QNAME_TYPE_DECIMAL =
        new QName(NS_SOAP_ENCODING, "decimal");
    public static QName QNAME_TYPE_FLOAT = new QName(NS_SOAP_ENCODING, "float");
    public static QName QNAME_TYPE_DOUBLE =
        new QName(NS_SOAP_ENCODING, "double");
    public static QName QNAME_TYPE_BOOLEAN =
        new QName(NS_SOAP_ENCODING, "boolean");
    public static QName QNAME_TYPE_TIME = new QName(NS_SOAP_ENCODING, "time");
    public static QName QNAME_TYPE_DATE_TIME =
        new QName(NS_SOAP_ENCODING, "dateTime");
    public static QName QNAME_TYPE_DURATION =
        new QName(NS_SOAP_ENCODING, "duration");
    public static QName QNAME_TYPE_DATE = new QName(NS_SOAP_ENCODING, "date");
    public static QName QNAME_TYPE_G_MONTH =
        new QName(NS_SOAP_ENCODING, "gMonth");
    public static QName QNAME_TYPE_G_YEAR =
        new QName(NS_SOAP_ENCODING, "gYear");
    public static QName QNAME_TYPE_G_YEAR_MONTH =
        new QName(NS_SOAP_ENCODING, "gYearMonth");
    public static QName QNAME_TYPE_G_DAY = new QName(NS_SOAP_ENCODING, "gDay");
    public static QName QNAME_TYPE_G_MONTH_DAY =
        new QName(NS_SOAP_ENCODING, "gMonthDay");
    public static QName QNAME_TYPE_NAME = new QName(NS_SOAP_ENCODING, "Name");
    public static QName QNAME_TYPE_QNAME = new QName(NS_SOAP_ENCODING, "QName");
    public static QName QNAME_TYPE_NCNAME =
        new QName(NS_SOAP_ENCODING, "NCName");
    public static QName QNAME_TYPE_ANY_URI =
        new QName(NS_SOAP_ENCODING, "anyURI");
    public static QName QNAME_TYPE_ID = new QName(NS_SOAP_ENCODING, "ID");
    public static QName QNAME_TYPE_IDREF = new QName(NS_SOAP_ENCODING, "IDREF");
    public static QName QNAME_TYPE_IDREFS =
        new QName(NS_SOAP_ENCODING, "IDREFS");
    public static QName QNAME_TYPE_ENTITY =
        new QName(NS_SOAP_ENCODING, "ENTITY");
    public static QName QNAME_TYPE_ENTITIES =
        new QName(NS_SOAP_ENCODING, "ENTITIES");
    public static QName QNAME_TYPE_NOTATION =
        new QName(NS_SOAP_ENCODING, "NOTATION");
    public static QName QNAME_TYPE_NMTOKEN =
        new QName(NS_SOAP_ENCODING, "NMTOKEN");
    public static QName QNAME_TYPE_NMTOKENS =
        new QName(NS_SOAP_ENCODING, "NMTOKENS");
    public static QName QNAME_TYPE_LANGUAGE =
        new QName(NS_SOAP_ENCODING, "LANGUAGE");

    // SOAP attributes with non-colonized names
    public static QName QNAME_ATTR_ID = new QName("", "id");
    public static QName QNAME_ATTR_HREF = new QName("", "ref");
}
