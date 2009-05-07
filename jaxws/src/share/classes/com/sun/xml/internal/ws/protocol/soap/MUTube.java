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
package com.sun.xml.internal.ws.protocol.soap;

import com.sun.xml.internal.ws.api.SOAPVersion;
import static com.sun.xml.internal.ws.api.SOAPVersion.SOAP_11;
import static com.sun.xml.internal.ws.api.SOAPVersion.SOAP_12;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.message.DOMHeader;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.SOAPFaultException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Rama Pulavarthi
 */

abstract class MUTube extends AbstractFilterTubeImpl {

    private static final String MU_FAULT_DETAIL_LOCALPART = "NotUnderstood";
    private final static QName MU_HEADER_DETAIL = new QName(SOAPVersion.SOAP_12.nsUri, MU_FAULT_DETAIL_LOCALPART);
    //TODO: change
    protected static final Logger logger = Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".soap.decoder");
    private final static String MUST_UNDERSTAND_FAULT_MESSAGE_STRING =
            "One or more mandatory SOAP header blocks not understood";

    protected final SOAPVersion soapVersion;
    private final AddressingVersion addressingVersion;

    protected MUTube(WSBinding binding, Tube next) {
        super(next);
        // MUPipe should n't be used for bindings other than SOAP.
        if (!(binding instanceof SOAPBinding)) {
            throw new WebServiceException(
                    "MUPipe should n't be used for bindings other than SOAP.");
        }
        this.soapVersion = binding.getSOAPVersion();
        this.addressingVersion = binding.getAddressingVersion();
    }

    protected MUTube(MUTube that, TubeCloner cloner) {
        super(that, cloner);
        soapVersion = that.soapVersion;
        addressingVersion = that.addressingVersion;
    }

    /**
     * @param headers      HeaderList that needs MU processing
     * @param roles        Roles configured on the Binding. Required Roles supposed to be assumbed a by a
     *                     SOAP Binding implementation are added.
     * @param knownHeaders Set of headers that this binding understands
     * @return returns the headers that have mustUnderstand attribute and are not understood
     *         by the binding.
     */
    protected final Set<QName> getMisUnderstoodHeaders(HeaderList headers, Set<String> roles, Set<QName> knownHeaders) {
        Set<QName> notUnderstoodHeaders = null;

        understandAddressingHeaders(knownHeaders);

        for (int i = 0; i < headers.size(); i++) {
            if (!headers.isUnderstood(i)) {
                Header header = headers.get(i);
                if (!header.isIgnorable(soapVersion, roles)) {
                    QName qName = new QName(header.getNamespaceURI(), header.getLocalPart());
                    if (! knownHeaders.contains(qName)) {
                        logger.finest("Element not understood=" + qName);
                        if (notUnderstoodHeaders == null)
                            notUnderstoodHeaders = new HashSet<QName>();
                        notUnderstoodHeaders.add(qName);
                    }
                }
            }
        }
        return notUnderstoodHeaders;
    }

    /**
     * Understand WS-Addressing headers if WS-Addressing is enabled
     *
     * @param knownHeaders Set of headers that this binding understands
     */
    private void understandAddressingHeaders(Set<QName> knownHeaders) {
        if (addressingVersion != null) {
            knownHeaders.add(addressingVersion.actionTag);
            knownHeaders.add(addressingVersion.faultToTag);
            knownHeaders.add(addressingVersion.fromTag);
            knownHeaders.add(addressingVersion.messageIDTag);
            knownHeaders.add(addressingVersion.relatesToTag);
            knownHeaders.add(addressingVersion.replyToTag);
            knownHeaders.add(addressingVersion.toTag);
        }
    }

    /**
     * @param notUnderstoodHeaders
     * @return SOAPfaultException with SOAPFault representing the MustUnderstand SOAP Fault.
     *         notUnderstoodHeaders are added in the fault detail.
     */
    final SOAPFaultException createMUSOAPFaultException(Set<QName> notUnderstoodHeaders) {
        try {
            SOAPFault fault = createMUSOAPFault();
            setMUFaultString(fault, notUnderstoodHeaders);
            return new SOAPFaultException(fault);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * This should be used only in ServerMUPipe
     *
     * @param notUnderstoodHeaders
     * @return Message representing a SOAPFault
     *         In SOAP 1.1, notUnderstoodHeaders are added in the fault Detail
     *         in SOAP 1.2, notUnderstoodHeaders are added as the SOAP Headers
     */

    final Message createMUSOAPFaultMessage(Set<QName> notUnderstoodHeaders) {
        try {
            SOAPFault fault = createMUSOAPFault();
            if (soapVersion == SOAP_11) {
                setMUFaultString(fault, notUnderstoodHeaders);
            }
            Message muFaultMessage = Messages.create(fault);
            if (soapVersion == SOAP_12) {
                addHeader(muFaultMessage, notUnderstoodHeaders);
            }
            return muFaultMessage;
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    private void setMUFaultString(SOAPFault fault, Set<QName> notUnderstoodHeaders) throws SOAPException {
        fault.setFaultString("MustUnderstand headers:" +
                notUnderstoodHeaders + " are not understood");
    }

    private static void addHeader(Message m, Set<QName> notUnderstoodHeaders) throws SOAPException {
        for (QName qname : notUnderstoodHeaders) {
            SOAPElement soapEl = SOAP_12.saajSoapFactory.createElement(MU_HEADER_DETAIL);
            soapEl.addNamespaceDeclaration("abc", qname.getNamespaceURI());
            soapEl.setAttribute("qname", "abc:" + qname.getLocalPart());
            Header header = new DOMHeader<Element>(soapEl);
            m.getHeaders().add(header);
        }
    }

    private SOAPFault createMUSOAPFault() throws SOAPException {
        return soapVersion.saajSoapFactory.createFault(
                MUST_UNDERSTAND_FAULT_MESSAGE_STRING,
                soapVersion.faultCodeMustUnderstand);
    }
}
