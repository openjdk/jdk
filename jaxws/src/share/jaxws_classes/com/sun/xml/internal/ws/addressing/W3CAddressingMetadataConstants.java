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

package com.sun.xml.internal.ws.addressing;

import javax.xml.namespace.QName;

/**
 * Constants for W3C Addressing Metadata specification
 * @author Rama Pulavarthi
 */
public class W3CAddressingMetadataConstants {
    public static final String WSAM_NAMESPACE_NAME = "http://www.w3.org/2007/05/addressing/metadata";
    public static final String WSAM_PREFIX_NAME = "wsam";
    public static final QName  WSAM_ACTION_QNAME = new QName(WSAM_NAMESPACE_NAME,"Action",WSAM_PREFIX_NAME);

    public static final String WSAM_ADDRESSING_ASSERTION_NAME="Addressing";
    public static final String WSAM_ANONYMOUS_NESTED_ASSERTION_NAME="AnonymousResponses";
    public static final String WSAM_NONANONYMOUS_NESTED_ASSERTION_NAME="NonAnonymousResponses";

    public static final QName WSAM_ADDRESSING_ASSERTION = new QName(WSAM_NAMESPACE_NAME,
                    WSAM_ADDRESSING_ASSERTION_NAME, WSAM_PREFIX_NAME );

    public static final QName WSAM_ANONYMOUS_NESTED_ASSERTION = new QName(WSAM_NAMESPACE_NAME,
                        WSAM_ANONYMOUS_NESTED_ASSERTION_NAME, WSAM_PREFIX_NAME );

    public static final QName WSAM_NONANONYMOUS_NESTED_ASSERTION = new QName(WSAM_NAMESPACE_NAME,
                        WSAM_NONANONYMOUS_NESTED_ASSERTION_NAME, WSAM_PREFIX_NAME );

    public static final String WSAM_WSDLI_ATTRIBUTE_NAMESPACE="http://www.w3.org/ns/wsdl-instance";
    public static final String WSAM_WSDLI_ATTRIBUTE_PREFIX="wsdli";
    public static final String WSAM_WSDLI_ATTRIBUTE_LOCALNAME="wsdlLocation";

}
