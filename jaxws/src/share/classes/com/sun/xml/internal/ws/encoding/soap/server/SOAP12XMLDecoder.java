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

package com.sun.xml.internal.ws.encoding.soap.server;

import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPFault;
import javax.xml.stream.XMLStreamReader;
import com.sun.xml.internal.ws.encoding.soap.internal.HeaderBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.SOAP12NotUnderstoodHeaderBlock;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.SOAPFaultException;

import static javax.xml.stream.XMLStreamReader.*;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.streaming.XMLReader;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.server.*;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPUtil;

/**
 * @author WS Development Team
 */
public class SOAP12XMLDecoder extends SOAPXMLDecoder {

    private static final Set<String> requiredRoles = new HashSet<String>();

    public SOAP12XMLDecoder() {
        requiredRoles.add("http://www.w3.org/2003/05/soap-envelope/role/next");
        requiredRoles.add("http://www.w3.org/2003/05/soap-envelope/role/ultimateReceiver");
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#decodeHeader(com.sun.xml.internal.ws.streaming.XMLReader, com.sun.pept.ept.MessageInfo, com.sun.xml.internal.ws.soap.internal.InternalMessage)
     */
    @Override
    protected void decodeHeader(XMLStreamReader reader, MessageInfo messageInfo, InternalMessage request) {
        // TODO Auto-generated method stub
        super.decodeHeader(reader, messageInfo, request);
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#getBodyTag()
     */
    @Override
    protected QName getBodyTag() {
        return SOAP12Constants.QNAME_SOAP_BODY;
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#getEnvelopeTag()
     */
    @Override
    protected QName getEnvelopeTag() {
        return SOAP12Constants.QNAME_SOAP_ENVELOPE;
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.encoding.soap.SOAPDecoder#getHeaderTag()
     */
    @Override
    protected QName getHeaderTag() {
        return SOAP12Constants.QNAME_SOAP_HEADER;
    }

    @Override
    protected QName getMUAttrQName(){
        return SOAP12Constants.QNAME_MUSTUNDERSTAND;
    }

    @Override
    protected QName getRoleAttrQName(){
        return SOAP12Constants.QNAME_ROLE;
    }

    public Set<String> getRequiredRoles() {
        return requiredRoles;
    }

    /*
     * Keep track of all not-understood headers to return
     * with fault to client. In soap 1.1, the check fails
     * on the first not-understood header.
     */
    @Override
    protected void checkHeadersAgainstKnown(XMLStreamReader reader,
        Set<String> roles, Set<QName> understoodHeaders, MessageInfo mi) {

        Set<QName> notUnderstoodHeaders = new HashSet<QName>();

        while (true) {
            if (reader.getEventType() == START_ELEMENT) {
                // check MU header for each role
                QName qName = reader.getName();
                String mu = reader.getAttributeValue(
                    getMUAttrQName().getNamespaceURI(),
                    getMUAttrQName().getLocalPart());
                if (mu != null && (mu.equals("1") ||
                    mu.equalsIgnoreCase("true"))) {
                    String role = reader.getAttributeValue(
                        getRoleAttrQName().getNamespaceURI(),
                        getRoleAttrQName().getLocalPart());
                    if (role != null && roles.contains(role)) {
                        logger.finest("Element=" + qName +
                            " targeted at=" + role);
                        if (understoodHeaders == null ||
                            !understoodHeaders.contains(qName)) {
                            logger.finest("Element not understood=" + qName);
                            notUnderstoodHeaders.add(qName);
                        }
                    }
                }
                XMLStreamReaderUtil.skipElement(reader);   // Moves to END state
                XMLStreamReaderUtil.nextElementContent(reader);
            } else {
                break;
            }
        }

        if (notUnderstoodHeaders.isEmpty()) {
            return;
        }

        // need to add headers to fault
        SOAPFault sf = SOAPUtil.createSOAPFault(
            MUST_UNDERSTAND_FAULT_MESSAGE_STRING,
            SOAP12Constants.FAULT_CODE_MUST_UNDERSTAND,
            null, null, SOAPBinding.SOAP12HTTP_BINDING);
        Set<HeaderBlock> nuHeaderBlocks = new HashSet<HeaderBlock>();
        for (QName headerName : notUnderstoodHeaders) {
            nuHeaderBlocks.add(new SOAP12NotUnderstoodHeaderBlock(headerName));
        }
        MessageInfoUtil.setNotUnderstoodHeaders(mi, nuHeaderBlocks);
        throw new SOAPFaultException(sf);
    }

    @Override
    public String getBindingId() {
        return SOAPBinding.SOAP12HTTP_BINDING;
    }

    @Override
    protected QName getSenderFaultCode() {
        return SOAP12Constants.FAULT_CODE_CLIENT;

    }

    @Override
    protected QName getReceiverFaultCode() {
        return SOAP12Constants.FAULT_CODE_SERVER;

    }

    @Override
    protected QName getVersionMismatchFaultCode() {
        return SOAP12Constants.FAULT_CODE_VERSION_MISMATCH;
    }

}
