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

package com.sun.xml.internal.ws.addressing;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.addressing.model.InvalidMapException;
import com.sun.xml.internal.ws.addressing.model.MapRequiredException;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressingFeature;
import com.sun.xml.internal.ws.message.FaultDetailHeader;
import com.sun.xml.internal.ws.resources.AddressingMessages;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPFault;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.AddressingFeature;
import java.util.Iterator;

/**
 * WS-Addressing processing code shared between client and server.
 *
 * <p>
 * This tube is used only when WS-Addressing is enabled.
 *
 * @author Arun Gupta
 */
abstract class WsaTube extends AbstractFilterTubeImpl {
    /**
     * Port that we are processing.
     */
    protected final @NotNull WSDLPort wsdlPort;
    protected final WSBinding binding;
    final WsaTubeHelper helper;
    protected final @NotNull AddressingVersion addressingVersion;
    protected final SOAPVersion soapVersion;

    /**
     * True if the addressing headers are mandatory.
     */
    private final boolean addressingRequired;

    public WsaTube(WSDLPort wsdlPort, WSBinding binding, Tube next) {
        super(next);
        this.wsdlPort = wsdlPort;
        this.binding = binding;
        addressingVersion = binding.getAddressingVersion();
        soapVersion = binding.getSOAPVersion();
        helper = getTubeHelper();
        addressingRequired = AddressingVersion.isRequired(binding);
    }

    public WsaTube(WsaTube that, TubeCloner cloner) {
        super(that, cloner);
        this.wsdlPort = that.wsdlPort;
        this.binding = that.binding;
        this.helper = that.helper;
        addressingVersion = that.addressingVersion;
        soapVersion = that.soapVersion;
        addressingRequired = that.addressingRequired;
    }

    @Override
    public
    @NotNull
    NextAction processException(Throwable t) {
        return super.processException(t);
    }

    protected WsaTubeHelper getTubeHelper() {
        if(binding.isFeatureEnabled(AddressingFeature.class)) {
            return new WsaTubeHelperImpl(wsdlPort, null, binding);
        } else if(binding.isFeatureEnabled(MemberSubmissionAddressingFeature.class)) {
            //seiModel is null as it is not needed.
            return new com.sun.xml.internal.ws.addressing.v200408.WsaTubeHelperImpl(wsdlPort, null, binding);
        } else {
            // Addressing is not enabled, WsaTube should not be included in the pipeline
            throw new WebServiceException(AddressingMessages.ADDRESSING_NOT_ENABLED(this.getClass().getSimpleName()));
        }
    }

    /**
     * Validates the inbound message. If an error is found, create
     * a fault message and returns that. Otherwise
     * it will pass through the parameter 'packet' object to the return value.
     */
    protected final Packet validateInboundHeaders(Packet packet) {
        SOAPFault soapFault;
        FaultDetailHeader s11FaultDetailHeader;

        try {
            checkCardinality(packet);

            return packet;
        } catch (InvalidMapException e) {
            soapFault = helper.newInvalidMapFault(e, addressingVersion);
            s11FaultDetailHeader = new FaultDetailHeader(addressingVersion, addressingVersion.problemHeaderQNameTag.getLocalPart(), e.getMapQName());
        } catch (MapRequiredException e) {
            soapFault = helper.newMapRequiredFault(e, addressingVersion);
            s11FaultDetailHeader = new FaultDetailHeader(addressingVersion, addressingVersion.problemHeaderQNameTag.getLocalPart(), e.getMapQName());
        }

        if (soapFault != null) {
            // WS-A fault processing for one-way methods
            if (packet.getMessage().isOneWay(wsdlPort)) {
                return packet.createServerResponse(null, wsdlPort, null, binding);
            }

            Message m = Messages.create(soapFault);
            if (soapVersion == SOAPVersion.SOAP_11) {
                m.getHeaders().add(s11FaultDetailHeader);
            }

            Packet response = packet.createServerResponse(m, wsdlPort, null,  binding);
            return response;
        }

        return packet;
    }

    final boolean isAddressingEngagedOrRequired(Packet packet, WSBinding binding) {
        if (AddressingVersion.isRequired(binding))
            return true;

        if (packet == null)
            return false;

        if (packet.getMessage() == null)
            return false;

        if (packet.getMessage().getHeaders() != null)
            return false;

        String action = packet.getMessage().getHeaders().getAction(addressingVersion, soapVersion);
        if (action == null)
            return true;

        return true;
    }

    /**
     * Checks the cardinality of WS-Addressing headers on an inbound {@link Packet}. This method
     * checks for the cardinality if WS-Addressing is engaged (detected by the presence of wsa:Action
     * header) or wsdl:required=true.
     *
     * @param packet The inbound packet.
     * @throws WebServiceException if:
     * <ul>
     * <li>there is an error reading ReplyTo or FaultTo</li>
     * <li>WS-Addressing is required and {@link Message} within <code>packet</code> is null</li>
     * <li>WS-Addressing is required and no headers are found in the {@link Message}</li>
     * <li>an uknown WS-Addressing header is present</li>
     * </ul>
     */
    public void checkCardinality(Packet packet) {
        Message message = packet.getMessage();
        if (message == null) {
            if (addressingRequired)
                throw new WebServiceException(AddressingMessages.NULL_MESSAGE());
            else
                return;
        }

        Iterator<Header> hIter = message.getHeaders().getHeaders(addressingVersion.nsUri, true);

        if (!hIter.hasNext()) {
            // no WS-A headers are found
            if (addressingRequired)
                // if WS-A is required, then throw an exception looking for wsa:Action header
                throw new MapRequiredException(addressingVersion.actionTag);
            else
                // else no need to process
                return;
        }

        boolean foundFrom = false;
        boolean foundTo = false;
        boolean foundReplyTo = false;
        boolean foundFaultTo = false;
        boolean foundAction = false;
        boolean foundMessageId = false;
        boolean foundRelatesTo = false;
        QName duplicateHeader = null;

        while (hIter.hasNext()) {
            Header h = hIter.next();

            // check if the Header is in current role
            if (!isInCurrentRole(h, binding)) {
                continue;
            }

            String local = h.getLocalPart();
            if (local.equals(addressingVersion.fromTag.getLocalPart())) {
                if (foundFrom) {
                    duplicateHeader = addressingVersion.fromTag;
                    break;
                }
                foundFrom = true;
            } else if (local.equals(addressingVersion.toTag.getLocalPart())) {
                if (foundTo) {
                    duplicateHeader = addressingVersion.toTag;
                    break;
                }
                foundTo = true;
            } else if (local.equals(addressingVersion.replyToTag.getLocalPart())) {
                if (foundReplyTo) {
                    duplicateHeader = addressingVersion.replyToTag;
                    break;
                }
                foundReplyTo = true;
                try { // verify that the header is in a good shape
                    h.readAsEPR(addressingVersion);
                } catch (XMLStreamException e) {
                    throw new WebServiceException(AddressingMessages.REPLY_TO_CANNOT_PARSE(), e);
                }
            } else if (local.equals(addressingVersion.faultToTag.getLocalPart())) {
                if (foundFaultTo) {
                    duplicateHeader = addressingVersion.faultToTag;
                    break;
                }
                foundFaultTo = true;
                try { // verify that the header is in a good shape
                    h.readAsEPR(addressingVersion);
                } catch (XMLStreamException e) {
                    throw new WebServiceException(AddressingMessages.FAULT_TO_CANNOT_PARSE(), e);
                }
            } else if (local.equals(addressingVersion.actionTag.getLocalPart())) {
                if (foundAction) {
                    duplicateHeader = addressingVersion.actionTag;
                    break;
                }
                foundAction = true;
            } else if (local.equals(addressingVersion.messageIDTag.getLocalPart())) {
                if (foundMessageId) {
                    duplicateHeader = addressingVersion.messageIDTag;
                    break;
                }
                foundMessageId = true;
            } else if (local.equals(addressingVersion.relatesToTag.getLocalPart())) {
                foundRelatesTo = true;
            } else if (local.equals(addressingVersion.faultDetailTag.getLocalPart())) {
                // TODO: should anything be done here ?
                // TODO: fault detail element - only for SOAP 1.1
            } else {
                System.err.println(AddressingMessages.UNKNOWN_WSA_HEADER());
            }
        }

        // check for invalid cardinality first before checking for mandatory headers
        if (duplicateHeader != null) {
            throw new InvalidMapException(duplicateHeader, addressingVersion.invalidCardinalityTag);
        }

        // WS-A is engaged if wsa:Action header is found
        boolean engaged = foundAction;

        // check for mandatory set of headers only if:
        // 1. WS-A is engaged or
        // 2. wsdl:required=true
        // Both wsa:Action and wsa:To MUST be present on request (for oneway MEP) and
        // response messages (for oneway and request/response MEP only)
        if (engaged || addressingRequired) {
            checkMandatoryHeaders(packet, foundAction, foundTo, foundMessageId, foundRelatesTo);
        }
    }

    final boolean isInCurrentRole(Header header, WSBinding binding) {
        // TODO: binding will be null for protocol messages
        // TODO: returning true assumes that protocol messages are
        // TODO: always in current role, this may not to be fixed.
        if (binding == null)
            return true;


        if (soapVersion == SOAPVersion.SOAP_11) {
            // Rama: Why not checking for SOAP 1.1?
            return true;
        } else {
            String role = header.getRole(soapVersion);
            return (role.equals(SOAPVersion.SOAP_12.implicitRole));
        }
    }

    protected final WSDLBoundOperation getWSDLBoundOperation(Packet packet) {
        return packet.getMessage().getOperation(wsdlPort);
    }

    protected void validateSOAPAction(Packet packet) {
        String gotA = packet.getMessage().getHeaders().getAction(addressingVersion, soapVersion);
        if (gotA == null)
            throw new WebServiceException(AddressingMessages.VALIDATION_SERVER_NULL_ACTION());
        if(packet.soapAction != null && !packet.soapAction.equals("\"\"") && !packet.soapAction.equals("\""+gotA+"\"")) {
            throw new InvalidMapException(addressingVersion.actionTag, addressingVersion.actionMismatchTag);
        }
    }

    protected abstract void validateAction(Packet packet);
    protected void checkMandatoryHeaders(
        Packet packet, boolean foundAction, boolean foundTo, boolean foundMessageId, boolean foundRelatesTo) {
        WSDLBoundOperation wbo = getWSDLBoundOperation(packet);
        // no need to check for for non-application messages
        if (wbo == null)
            return;

        // if no wsa:Action header is found
        if (!foundAction)
            throw new MapRequiredException(addressingVersion.actionTag);
        validateSOAPAction(packet);
    }
}
