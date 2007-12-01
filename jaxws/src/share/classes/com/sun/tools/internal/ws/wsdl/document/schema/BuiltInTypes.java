/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
public interface BuiltInTypes {
    public QName STRING = SchemaConstants.QNAME_TYPE_STRING;
    public QName NORMALIZED_STRING =
        SchemaConstants.QNAME_TYPE_NORMALIZED_STRING;
    public QName TOKEN = SchemaConstants.QNAME_TYPE_TOKEN;
    public QName BYTE = SchemaConstants.QNAME_TYPE_BYTE;
    public QName UNSIGNED_BYTE = SchemaConstants.QNAME_TYPE_UNSIGNED_BYTE;
    public QName BASE64_BINARY = SchemaConstants.QNAME_TYPE_BASE64_BINARY;
    public QName HEX_BINARY = SchemaConstants.QNAME_TYPE_HEX_BINARY;
    public QName INTEGER = SchemaConstants.QNAME_TYPE_INTEGER;
    public QName POSITIVE_INTEGER = SchemaConstants.QNAME_TYPE_POSITIVE_INTEGER;
    public QName NEGATIVE_INTEGER = SchemaConstants.QNAME_TYPE_NEGATIVE_INTEGER;
    public QName NON_NEGATIVE_INTEGER =
        SchemaConstants.QNAME_TYPE_NON_NEGATIVE_INTEGER;
    public QName NON_POSITIVE_INTEGER =
        SchemaConstants.QNAME_TYPE_NON_POSITIVE_INTEGER;
    public QName INT = SchemaConstants.QNAME_TYPE_INT;
    public QName UNSIGNED_INT = SchemaConstants.QNAME_TYPE_UNSIGNED_INT;
    public QName LONG = SchemaConstants.QNAME_TYPE_LONG;
    public QName UNSIGNED_LONG = SchemaConstants.QNAME_TYPE_UNSIGNED_LONG;
    public QName SHORT = SchemaConstants.QNAME_TYPE_SHORT;
    public QName UNSIGNED_SHORT = SchemaConstants.QNAME_TYPE_UNSIGNED_SHORT;
    public QName DECIMAL = SchemaConstants.QNAME_TYPE_DECIMAL;
    public QName FLOAT = SchemaConstants.QNAME_TYPE_FLOAT;
    public QName DOUBLE = SchemaConstants.QNAME_TYPE_DOUBLE;
    public QName BOOLEAN = SchemaConstants.QNAME_TYPE_BOOLEAN;
    public QName TIME = SchemaConstants.QNAME_TYPE_TIME;
    public QName DATE_TIME = SchemaConstants.QNAME_TYPE_DATE_TIME;
    public QName DURATION = SchemaConstants.QNAME_TYPE_DURATION;
    public QName DATE = SchemaConstants.QNAME_TYPE_DATE;
    public QName G_MONTH = SchemaConstants.QNAME_TYPE_G_MONTH;
    public QName G_YEAR = SchemaConstants.QNAME_TYPE_G_YEAR;
    public QName G_YEAR_MONTH = SchemaConstants.QNAME_TYPE_G_YEAR_MONTH;
    public QName G_DAY = SchemaConstants.QNAME_TYPE_G_DAY;
    public QName G_MONTH_DAY = SchemaConstants.QNAME_TYPE_G_MONTH_DAY;
    public QName NAME = SchemaConstants.QNAME_TYPE_NAME;
    public QName QNAME = SchemaConstants.QNAME_TYPE_QNAME;
    public QName NCNAME = SchemaConstants.QNAME_TYPE_NCNAME;
    public QName ANY_URI = SchemaConstants.QNAME_TYPE_ANY_URI;
    public QName ID = SchemaConstants.QNAME_TYPE_ID;
    public QName IDREF = SchemaConstants.QNAME_TYPE_IDREF;
    public QName IDREFS = SchemaConstants.QNAME_TYPE_IDREFS;
    public QName ENTITY = SchemaConstants.QNAME_TYPE_ENTITY;
    public QName ENTITIES = SchemaConstants.QNAME_TYPE_ENTITIES;
    public QName NOTATION = SchemaConstants.QNAME_TYPE_NOTATION;
    public QName NMTOKEN = SchemaConstants.QNAME_TYPE_NMTOKEN;
    public QName NMTOKENS = SchemaConstants.QNAME_TYPE_NMTOKENS;
    public QName LANGUAGE = SchemaConstants.QNAME_TYPE_LANGUAGE;
    public QName ANY_SIMPLE_URTYPE = SchemaConstants.QNAME_TYPE_SIMPLE_URTYPE;

    //xsd:list
    public QName LIST = SchemaConstants.QNAME_LIST;
}
