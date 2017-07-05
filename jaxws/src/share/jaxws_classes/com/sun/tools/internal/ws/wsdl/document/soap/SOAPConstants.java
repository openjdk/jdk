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

package com.sun.tools.internal.ws.wsdl.document.soap;

import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;

import javax.xml.namespace.QName;

/**
 * Interface defining SOAP-related constants.
 *
 * @author WS Development Team
 */
public interface SOAPConstants {

    // namespace URIs
    public static final String URI_ENVELOPE = SOAPNamespaceConstants.ENVELOPE;
    public static final String NS_WSDL_SOAP =
        "http://schemas.xmlsoap.org/wsdl/soap/";
    public static final String NS_SOAP_ENCODING = "http://schemas.xmlsoap.org/soap/encoding/";

    // other URIs
    public final String URI_SOAP_TRANSPORT_HTTP =
        "http://schemas.xmlsoap.org/soap/http";

    // QNames
    public static final QName QNAME_ADDRESS =
        new QName(NS_WSDL_SOAP, "address");
    public static final QName QNAME_BINDING =
        new QName(NS_WSDL_SOAP, "binding");
    public static final QName QNAME_BODY = new QName(NS_WSDL_SOAP, "body");
    public static final QName QNAME_FAULT = new QName(NS_WSDL_SOAP, "fault");
    public static final QName QNAME_HEADER = new QName(NS_WSDL_SOAP, "header");
    public static final QName QNAME_HEADERFAULT =
        new QName(NS_WSDL_SOAP, "headerfault");
    public static final QName QNAME_OPERATION =
        new QName(NS_WSDL_SOAP, "operation");
    public static final QName QNAME_MUSTUNDERSTAND =
        new QName(URI_ENVELOPE, "mustUnderstand");


    // SOAP encoding QNames
    public static final QName QNAME_TYPE_ARRAY =
        new QName(NS_SOAP_ENCODING, "Array");
    public static final QName QNAME_ATTR_GROUP_COMMON_ATTRIBUTES =
        new QName(NS_SOAP_ENCODING, "commonAttributes");
    public static final QName QNAME_ATTR_ARRAY_TYPE =
        new QName(NS_SOAP_ENCODING, "arrayType");
    public static final QName QNAME_ATTR_OFFSET =
        new QName(NS_SOAP_ENCODING, "offset");
    public static final QName QNAME_ATTR_POSITION =
        new QName(NS_SOAP_ENCODING, "position");

    public static final QName QNAME_TYPE_BASE64 =
        new QName(NS_SOAP_ENCODING, "base64");

    public static final QName QNAME_ELEMENT_STRING =
        new QName(NS_SOAP_ENCODING, "string");
    public static final QName QNAME_ELEMENT_NORMALIZED_STRING =
        new QName(NS_SOAP_ENCODING, "normalizedString");
    public static final QName QNAME_ELEMENT_TOKEN =
        new QName(NS_SOAP_ENCODING, "token");
    public static final QName QNAME_ELEMENT_BYTE =
        new QName(NS_SOAP_ENCODING, "byte");
    public static final QName QNAME_ELEMENT_UNSIGNED_BYTE =
        new QName(NS_SOAP_ENCODING, "unsignedByte");
    public static final QName QNAME_ELEMENT_BASE64_BINARY =
        new QName(NS_SOAP_ENCODING, "base64Binary");
    public static final QName QNAME_ELEMENT_HEX_BINARY =
        new QName(NS_SOAP_ENCODING, "hexBinary");
    public static final QName QNAME_ELEMENT_INTEGER =
        new QName(NS_SOAP_ENCODING, "integer");
    public static final QName QNAME_ELEMENT_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "positiveInteger");
    public static final QName QNAME_ELEMENT_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "negativeInteger");
    public static final QName QNAME_ELEMENT_NON_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonNegativeInteger");
    public static final QName QNAME_ELEMENT_NON_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonPositiveInteger");
    public static final QName QNAME_ELEMENT_INT =
        new QName(NS_SOAP_ENCODING, "int");
    public static final QName QNAME_ELEMENT_UNSIGNED_INT =
        new QName(NS_SOAP_ENCODING, "unsignedInt");
    public static final QName QNAME_ELEMENT_LONG =
        new QName(NS_SOAP_ENCODING, "long");
    public static final QName QNAME_ELEMENT_UNSIGNED_LONG =
        new QName(NS_SOAP_ENCODING, "unsignedLong");
    public static final QName QNAME_ELEMENT_SHORT =
        new QName(NS_SOAP_ENCODING, "short");
    public static final QName QNAME_ELEMENT_UNSIGNED_SHORT =
        new QName(NS_SOAP_ENCODING, "unsignedShort");
    public static final QName QNAME_ELEMENT_DECIMAL =
        new QName(NS_SOAP_ENCODING, "decimal");
    public static final QName QNAME_ELEMENT_FLOAT =
        new QName(NS_SOAP_ENCODING, "float");
    public static final QName QNAME_ELEMENT_DOUBLE =
        new QName(NS_SOAP_ENCODING, "double");
    public static final QName QNAME_ELEMENT_BOOLEAN =
        new QName(NS_SOAP_ENCODING, "boolean");
    public static final QName QNAME_ELEMENT_TIME =
        new QName(NS_SOAP_ENCODING, "time");
    public static final QName QNAME_ELEMENT_DATE_TIME =
        new QName(NS_SOAP_ENCODING, "dateTime");
    public static final QName QNAME_ELEMENT_DURATION =
        new QName(NS_SOAP_ENCODING, "duration");
    public static final QName QNAME_ELEMENT_DATE =
        new QName(NS_SOAP_ENCODING, "date");
    public static final QName QNAME_ELEMENT_G_MONTH =
        new QName(NS_SOAP_ENCODING, "gMonth");
    public static final QName QNAME_ELEMENT_G_YEAR =
        new QName(NS_SOAP_ENCODING, "gYear");
    public static final QName QNAME_ELEMENT_G_YEAR_MONTH =
        new QName(NS_SOAP_ENCODING, "gYearMonth");
    public static final QName QNAME_ELEMENT_G_DAY =
        new QName(NS_SOAP_ENCODING, "gDay");
    public static final QName QNAME_ELEMENT_G_MONTH_DAY =
        new QName(NS_SOAP_ENCODING, "gMonthDay");
    public static final QName QNAME_ELEMENT_NAME =
        new QName(NS_SOAP_ENCODING, "Name");
    public static final QName QNAME_ELEMENT_QNAME =
        new QName(NS_SOAP_ENCODING, "QName");
    public static final QName QNAME_ELEMENT_NCNAME =
        new QName(NS_SOAP_ENCODING, "NCName");
    public static final QName QNAME_ELEMENT_ANY_URI =
        new QName(NS_SOAP_ENCODING, "anyURI");
    public static final QName QNAME_ELEMENT_ID =
        new QName(NS_SOAP_ENCODING, "ID");
    public static final QName QNAME_ELEMENT_IDREF =
        new QName(NS_SOAP_ENCODING, "IDREF");
    public static final QName QNAME_ELEMENT_IDREFS =
        new QName(NS_SOAP_ENCODING, "IDREFS");
    public static final QName QNAME_ELEMENT_ENTITY =
        new QName(NS_SOAP_ENCODING, "ENTITY");
    public static final QName QNAME_ELEMENT_ENTITIES =
        new QName(NS_SOAP_ENCODING, "ENTITIES");
    public static final QName QNAME_ELEMENT_NOTATION =
        new QName(NS_SOAP_ENCODING, "NOTATION");
    public static final QName QNAME_ELEMENT_NMTOKEN =
        new QName(NS_SOAP_ENCODING, "NMTOKEN");
    public static final QName QNAME_ELEMENT_NMTOKENS =
        new QName(NS_SOAP_ENCODING, "NMTOKENS");

    public static final QName QNAME_TYPE_STRING =
        new QName(NS_SOAP_ENCODING, "string");
    public static final QName QNAME_TYPE_NORMALIZED_STRING =
        new QName(NS_SOAP_ENCODING, "normalizedString");
    public static final QName QNAME_TYPE_TOKEN =
        new QName(NS_SOAP_ENCODING, "token");
    public static final QName QNAME_TYPE_BYTE =
        new QName(NS_SOAP_ENCODING, "byte");
    public static final QName QNAME_TYPE_UNSIGNED_BYTE =
        new QName(NS_SOAP_ENCODING, "unsignedByte");
    public static final QName QNAME_TYPE_BASE64_BINARY =
        new QName(NS_SOAP_ENCODING, "base64Binary");
    public static final QName QNAME_TYPE_HEX_BINARY =
        new QName(NS_SOAP_ENCODING, "hexBinary");
    public static final QName QNAME_TYPE_INTEGER =
        new QName(NS_SOAP_ENCODING, "integer");
    public static final QName QNAME_TYPE_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "positiveInteger");
    public static final QName QNAME_TYPE_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "negativeInteger");
    public static final QName QNAME_TYPE_NON_NEGATIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonNegativeInteger");
    public static final QName QNAME_TYPE_NON_POSITIVE_INTEGER =
        new QName(NS_SOAP_ENCODING, "nonPositiveInteger");
    public static final QName QNAME_TYPE_INT =
        new QName(NS_SOAP_ENCODING, "int");
    public static final QName QNAME_TYPE_UNSIGNED_INT =
        new QName(NS_SOAP_ENCODING, "unsignedInt");
    public static final QName QNAME_TYPE_LONG =
        new QName(NS_SOAP_ENCODING, "long");
    public static final QName QNAME_TYPE_UNSIGNED_LONG =
        new QName(NS_SOAP_ENCODING, "unsignedLong");
    public static final QName QNAME_TYPE_SHORT =
        new QName(NS_SOAP_ENCODING, "short");
    public static final QName QNAME_TYPE_UNSIGNED_SHORT =
        new QName(NS_SOAP_ENCODING, "unsignedShort");
    public static final QName QNAME_TYPE_DECIMAL =
        new QName(NS_SOAP_ENCODING, "decimal");
    public static final QName QNAME_TYPE_FLOAT =
        new QName(NS_SOAP_ENCODING, "float");
    public static final QName QNAME_TYPE_DOUBLE =
        new QName(NS_SOAP_ENCODING, "double");
    public static final QName QNAME_TYPE_BOOLEAN =
        new QName(NS_SOAP_ENCODING, "boolean");
    public static final QName QNAME_TYPE_TIME =
        new QName(NS_SOAP_ENCODING, "time");
    public static final QName QNAME_TYPE_DATE_TIME =
        new QName(NS_SOAP_ENCODING, "dateTime");
    public static final QName QNAME_TYPE_DURATION =
        new QName(NS_SOAP_ENCODING, "duration");
    public static final QName QNAME_TYPE_DATE =
        new QName(NS_SOAP_ENCODING, "date");
    public static final QName QNAME_TYPE_G_MONTH =
        new QName(NS_SOAP_ENCODING, "gMonth");
    public static final QName QNAME_TYPE_G_YEAR =
        new QName(NS_SOAP_ENCODING, "gYear");
    public static final QName QNAME_TYPE_G_YEAR_MONTH =
        new QName(NS_SOAP_ENCODING, "gYearMonth");
    public static final QName QNAME_TYPE_G_DAY =
        new QName(NS_SOAP_ENCODING, "gDay");
    public static final QName QNAME_TYPE_G_MONTH_DAY =
        new QName(NS_SOAP_ENCODING, "gMonthDay");
    public static final QName QNAME_TYPE_NAME =
        new QName(NS_SOAP_ENCODING, "Name");
    public static final QName QNAME_TYPE_QNAME =
        new QName(NS_SOAP_ENCODING, "QName");
    public static final QName QNAME_TYPE_NCNAME =
        new QName(NS_SOAP_ENCODING, "NCName");
    public static final QName QNAME_TYPE_ANY_URI =
        new QName(NS_SOAP_ENCODING, "anyURI");
    public static final QName QNAME_TYPE_ID = new QName(NS_SOAP_ENCODING, "ID");
    public static final QName QNAME_TYPE_IDREF =
        new QName(NS_SOAP_ENCODING, "IDREF");
    public static final QName QNAME_TYPE_IDREFS =
        new QName(NS_SOAP_ENCODING, "IDREFS");
    public static final QName QNAME_TYPE_ENTITY =
        new QName(NS_SOAP_ENCODING, "ENTITY");
    public static final QName QNAME_TYPE_ENTITIES =
        new QName(NS_SOAP_ENCODING, "ENTITIES");
    public static final QName QNAME_TYPE_NOTATION =
        new QName(NS_SOAP_ENCODING, "NOTATION");
    public static final QName QNAME_TYPE_NMTOKEN =
        new QName(NS_SOAP_ENCODING, "NMTOKEN");
    public static final QName QNAME_TYPE_NMTOKENS =
        new QName(NS_SOAP_ENCODING, "NMTOKENS");
    public static final QName QNAME_TYPE_LANGUAGE =
        new QName(NS_SOAP_ENCODING, "LANGUAGE");

    // SOAP attributes with non-colonized names
    public static final QName QNAME_ATTR_ID = new QName("", "id");
    public static final QName QNAME_ATTR_HREF = new QName("", "href");

}
