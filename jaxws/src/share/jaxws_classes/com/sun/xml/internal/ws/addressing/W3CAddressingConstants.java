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

package com.sun.xml.internal.ws.addressing;

import javax.xml.namespace.QName;

import com.sun.xml.internal.ws.api.addressing.AddressingVersion;

/**
 * Constants for W3C WS-Addressing version
 *
 * @author Arun Gupta
 */
public interface W3CAddressingConstants {
    public static final String WSA_NAMESPACE_NAME = "http://www.w3.org/2005/08/addressing";
    public static final String WSA_NAMESPACE_WSDL_NAME = "http://www.w3.org/2006/05/addressing/wsdl";

    public static final String WSAW_SERVICENAME_NAME = "ServiceName";
    public static final String WSAW_INTERFACENAME_NAME = "InterfaceName";
    public static final String WSAW_ENDPOINTNAME_NAME = "EndpointName";

    public static final String WSA_REFERENCEPROPERTIES_NAME = "ReferenceParameters";
    public static final QName WSA_REFERENCEPROPERTIES_QNAME = new QName(WSA_NAMESPACE_NAME, WSA_REFERENCEPROPERTIES_NAME);

    public static final String WSA_REFERENCEPARAMETERS_NAME = "ReferenceParameters";
    public static final QName WSA_REFERENCEPARAMETERS_QNAME = new QName(WSA_NAMESPACE_NAME, WSA_REFERENCEPARAMETERS_NAME);

    public static final String WSA_METADATA_NAME = "Metadata";
    public static final QName WSA_METADATA_QNAME = new QName(WSA_NAMESPACE_NAME, WSA_METADATA_NAME);

    public static final String WSA_ADDRESS_NAME = "Address";
    public static final QName WSA_ADDRESS_QNAME = new QName(WSA_NAMESPACE_NAME, WSA_ADDRESS_NAME);

    public static final String WSA_ANONYMOUS_ADDRESS = WSA_NAMESPACE_NAME + "/anonymous";
    public static final String WSA_NONE_ADDRESS = WSA_NAMESPACE_NAME + "/none";

    public static final String WSA_DEFAULT_FAULT_ACTION = WSA_NAMESPACE_NAME + "/fault";

    public static final String WSA_EPR_NAME = "EndpointReference";
    public static final QName WSA_EPR_QNAME = new QName(WSA_NAMESPACE_NAME, WSA_EPR_NAME);


    public static final String WSAW_USING_ADDRESSING_NAME = "UsingAddressing";
    public static final QName WSAW_USING_ADDRESSING_QNAME = new QName(WSA_NAMESPACE_WSDL_NAME, WSAW_USING_ADDRESSING_NAME);

    public static final QName INVALID_MAP_QNAME = new QName(WSA_NAMESPACE_NAME, "InvalidAddressingHeader");
    public static final QName MAP_REQUIRED_QNAME = new QName(WSA_NAMESPACE_NAME, "MessageAddressingHeaderRequired");
    public static final QName DESTINATION_UNREACHABLE_QNAME = new QName(WSA_NAMESPACE_NAME, "DestinationUnreachable");
    public static final QName ACTION_NOT_SUPPORTED_QNAME = new QName(WSA_NAMESPACE_NAME, "ActionNotSupported");
    public static final QName ENDPOINT_UNAVAILABLE_QNAME = new QName(WSA_NAMESPACE_NAME, "EndpointUnavailable");

    public static final String ACTION_NOT_SUPPORTED_TEXT = "The \"%s\" cannot be processed at the receiver";
    public static final String DESTINATION_UNREACHABLE_TEXT = "No route can be determined to reach %s";
    public static final String ENDPOINT_UNAVAILABLE_TEXT = "The endpoint is unable to process the message at this time";
    public static final String INVALID_MAP_TEXT = "A header representing a Message Addressing Property is not valid and the message cannot be processed";
    public static final String MAP_REQUIRED_TEXT = "A required header representing a Message Addressing Property is not present";

    public static final QName PROBLEM_ACTION_QNAME = new QName(WSA_NAMESPACE_NAME, "ProblemAction");
    public static final QName PROBLEM_HEADER_QNAME_QNAME = new QName(WSA_NAMESPACE_NAME, "ProblemHeaderQName");
    public static final QName FAULT_DETAIL_QNAME = new QName(WSA_NAMESPACE_NAME, "FaultDetail");

    // Fault subsubcode when an invalid address is specified.
    public static final QName INVALID_ADDRESS_SUBCODE = new QName(WSA_NAMESPACE_NAME, "InvalidAddress",
                                                                  AddressingVersion.W3C.getPrefix());

    // Fault subsubcode when an invalid header was expected to be EndpointReference but was not valid.
    public static final QName INVALID_EPR = new QName(WSA_NAMESPACE_NAME, "InvalidEPR", AddressingVersion.W3C.getPrefix());

    // Fault subsubcode when greater than expected number of the specified header is received.
    public static final QName INVALID_CARDINALITY = new QName(WSA_NAMESPACE_NAME, "InvalidCardinality",
                                                              AddressingVersion.W3C.getPrefix());

    // Fault subsubcode when an invalid header was expected to be EndpointReference but did not contain address.
    public static final QName MISSING_ADDRESS_IN_EPR = new QName(WSA_NAMESPACE_NAME, "MissingAddressInEPR",
                                                                 AddressingVersion.W3C.getPrefix());

    // Fault subsubcode when a header contains a message id that was a duplicate of one already received.
    public static final QName DUPLICATE_MESSAGEID = new QName(WSA_NAMESPACE_NAME, "DuplicateMessageID",
                                                              AddressingVersion.W3C.getPrefix());

    // Fault subsubcode when <code>Action</code> and <code>SOAPAction</code> for the mesage did not match.
    public static final QName ACTION_MISMATCH = new QName(WSA_NAMESPACE_NAME, "ActionMismatch",
                                                          AddressingVersion.W3C.getPrefix());

    // Fault subsubcode when the only address supported is the anonymous address.
    public static final QName ONLY_ANONYMOUS_ADDRESS_SUPPORTED = new QName(WSA_NAMESPACE_NAME, "OnlyAnonymousAddressSupported",
                                                                           AddressingVersion.W3C.getPrefix());

    //Fault subsubcode when anonymous address is not supported.
    public static final QName ONLY_NON_ANONYMOUS_ADDRESS_SUPPORTED = new QName(WSA_NAMESPACE_NAME, "OnlyNonAnonymousAddressSupported",
                                                                               AddressingVersion.W3C.getPrefix());

    public static final String ANONYMOUS_EPR = "<EndpointReference xmlns=\"http://www.w3.org/2005/08/addressing\">\n" +
            "    <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>\n" +
            "</EndpointReference>";
}
