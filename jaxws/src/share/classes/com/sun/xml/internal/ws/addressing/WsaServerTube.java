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
import static com.sun.xml.internal.ws.addressing.W3CAddressingConstants.ONLY_ANONYMOUS_ADDRESS_SUPPORTED;
import static com.sun.xml.internal.ws.addressing.W3CAddressingConstants.ONLY_NON_ANONYMOUS_ADDRESS_SUPPORTED;
import com.sun.xml.internal.ws.addressing.model.ActionNotSupportedException;
import com.sun.xml.internal.ws.addressing.model.InvalidMapException;
import com.sun.xml.internal.ws.addressing.model.MapRequiredException;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.ClientTubeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.TransportTubeFactory;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.message.FaultDetailHeader;
import com.sun.xml.internal.ws.resources.AddressingMessages;

import javax.xml.soap.SOAPFault;
import javax.xml.ws.WebServiceException;
import java.net.URI;
import java.util.logging.Logger;

/**
 * Handles WS-Addressing for the server.
 *
 * @author Kohsuke Kawaguchi
 * @author Arun Gupta
 */
public final class WsaServerTube extends WsaTube {

    // store the replyTo/faultTo of the message currently being processed.
    // both will be set to non-null in processRequest
    private WSEndpointReference replyTo;
    private WSEndpointReference faultTo;
    private boolean isAnonymousRequired = false;
    /**
     * WSDLBoundOperation calculated on the Request payload.
     * Used for determining ReplyTo or Fault Action for non-anonymous responses     *
     */
    private WSDLBoundOperation wbo;
    public WsaServerTube(@NotNull WSDLPort wsdlPort, WSBinding binding, Tube next) {
        super(wsdlPort, binding, next);
    }

    public WsaServerTube(WsaServerTube that, TubeCloner cloner) {
        super(that, cloner);
    }

    public WsaServerTube copy(TubeCloner cloner) {
        return new WsaServerTube(this, cloner);
    }

    public @NotNull NextAction processRequest(Packet request) {
        Message msg = request.getMessage();
        if(msg==null)   return doInvoke(next,request); // hmm?


        // Store request ReplyTo and FaultTo in requestPacket.invocationProperties
        // so that they can be used after responsePacket is received.
        // These properties are used if a fault is thrown from the subsequent Pipe/Tubes.

        HeaderList hl = request.getMessage().getHeaders();
        try {
        replyTo = hl.getReplyTo(addressingVersion, soapVersion);
        faultTo = hl.getFaultTo(addressingVersion, soapVersion);
        } catch (InvalidMapException e) {
            SOAPFault soapFault = helper.newInvalidMapFault(e, addressingVersion);
            // WS-A fault processing for one-way methods
            if (request.getMessage().isOneWay(wsdlPort)) {
                request.createServerResponse(null, wsdlPort, null, binding);
                return doInvoke(next, request);
            }

            Message m = Messages.create(soapFault);
            if (soapVersion == SOAPVersion.SOAP_11) {
                FaultDetailHeader s11FaultDetailHeader = new FaultDetailHeader(addressingVersion, addressingVersion.problemHeaderQNameTag.getLocalPart(), e.getMapQName());
                m.getHeaders().add(s11FaultDetailHeader);
            }

            Packet response = request.createServerResponse(m, wsdlPort, null, binding);
            return doReturnWith(response);
        }
        String messageId = hl.getMessageID(addressingVersion, soapVersion);

        // TODO: This is probably not a very good idea.
        // if someone wants to get this data, let them get from HeaderList.
        // we can even provide a convenience method
        //  -- KK.
        request.invocationProperties.put(REQUEST_MESSAGE_ID, messageId);


        // defaulting
        if (replyTo == null)    replyTo = addressingVersion.anonymousEpr;
        if (faultTo == null)    faultTo = replyTo;

        wbo = getWSDLBoundOperation(request);
        if(wbo != null && wbo.getAnonymous()== WSDLBoundOperation.ANONYMOUS.required)
            isAnonymousRequired = true;
        Packet p = validateInboundHeaders(request);
        // if one-way message and WS-A header processing fault has occurred,
        // then do no further processing
        if (p.getMessage() == null)
            // TODO: record the problem that we are dropping this problem on the floor.
            return doReturnWith(p);

        // if we find an error in addressing header, just turn around the direction here
        if (p.getMessage().isFault()) {
            // close the transportBackChannel if we know that
            // we'll never use them
            if (!(isAnonymousRequired) &&
                    !faultTo.isAnonymous() && request.transportBackChannel != null)
                request.transportBackChannel.close();
            return processResponse(p);
        }
        // close the transportBackChannel if we know that
        // we'll never use them
        if (!(isAnonymousRequired) &&
                !replyTo.isAnonymous() && !faultTo.isAnonymous() &&
                request.transportBackChannel != null)
            request.transportBackChannel.close();
        return doInvoke(next,p);
    }

    @Override
    public @NotNull NextAction processResponse(Packet response) {
        Message msg = response.getMessage();
        if (msg ==null)
            return doReturnWith(response);  // one way message. Nothing to see here. Move on.

        WSEndpointReference target = msg.isFault()?faultTo:replyTo;

        if(target.isAnonymous() || isAnonymousRequired )
            // the response will go back the back channel. most common case
            return doReturnWith(response);

        if(target.isNone()) {
            // the caller doesn't want to hear about it, so proceed like one-way
            response.setMessage(null);
            return doReturnWith(response);
        }

        // send the response to this EPR.
        processNonAnonymousReply(response,target);

        // then we'll proceed the rest like one-way.
        response.setMessage(null);
        return doReturnWith(response);
    }

    /**
     * Send a response to a non-anonymous address. Also closes the transport back channel
     * of {@link Packet} if it's not closed already.
     *
     * <p>
     * TODO: ideally we should be doing this by creating a new fiber.
     *
     * @param packet
     *      The response from our server, which will be delivered to the destination.
     * @param target
     *      Where do we send the packet to?
     */
    private void processNonAnonymousReply(final Packet packet, WSEndpointReference target) {
        // at this point we know we won't be sending anything back through the back channel,
        // so close it first to let the client go.
        if (packet.transportBackChannel != null)
            packet.transportBackChannel.close();

        if (packet.getMessage().isOneWay(wsdlPort)) {
            // one way message but with replyTo. I believe this is a hack for WS-TX - KK.
            LOGGER.fine(AddressingMessages.NON_ANONYMOUS_RESPONSE_ONEWAY());
            return;
        }

        EndpointAddress adrs;
        try {
             adrs = new EndpointAddress(URI.create(target.getAddress()));
        } catch (NullPointerException e) {
            throw new WebServiceException(e);
        } catch (IllegalArgumentException e) {
            throw new WebServiceException(e);
        }

        // we need to assemble a pipeline to talk to this endpoint.
        // TODO: what to pass as WSService?
        Tube transport = TransportTubeFactory.create(Thread.currentThread().getContextClassLoader(),
            new ClientTubeAssemblerContext(adrs, wsdlPort, null, binding));

        packet.endpointAddress = adrs;
        String action = packet.getMessage().isFault() ?
                helper.getFaultAction(wbo, packet) :
                helper.getOutputAction(wbo);
        //set the SOAPAction, as its got to be same as wsa:Action
        packet.soapAction = action;
        Fiber.current().runSync(transport, packet);
    }

    @Override
    public void validateAction(Packet packet) {
        //There may not be a WSDL operation.  There may not even be a WSDL.
        //For instance this may be a RM CreateSequence message.
        WSDLBoundOperation wbo = getWSDLBoundOperation(packet);

        if (wbo == null)
            return;

        String gotA = packet.getMessage().getHeaders().getAction(addressingVersion, soapVersion);

        if (gotA == null)
            throw new WebServiceException(AddressingMessages.VALIDATION_SERVER_NULL_ACTION());

        String expected = helper.getInputAction(packet);
        String soapAction = helper.getSOAPAction(packet);
        if (helper.isInputActionDefault(packet) && (soapAction != null && !soapAction.equals("")))
            expected = soapAction;

        if (expected != null && !gotA.equals(expected)) {
            throw new ActionNotSupportedException(gotA);
        }
    }

    @Override
    public void checkCardinality(Packet packet) {
        super.checkCardinality(packet);

        // wsaw:Anonymous validation
        WSDLBoundOperation wbo = getWSDLBoundOperation(packet);
        checkAnonymousSemantics(wbo, replyTo, faultTo);
         // check if addresses are valid
        checkNonAnonymousAddresses(replyTo,faultTo);
    }

    private void checkNonAnonymousAddresses(WSEndpointReference replyTo, WSEndpointReference faultTo) {
        if (!replyTo.isAnonymous()) {
            try {
                new EndpointAddress(URI.create(replyTo.getAddress()));
            } catch (Exception e) {
                throw new InvalidMapException(addressingVersion.replyToTag, addressingVersion.invalidAddressTag);
            }
        }
        //for now only validate ReplyTo
        /*
        if (!faultTo.isAnonymous()) {
            try {
                new EndpointAddress(URI.create(faultTo.getAddress()));
            } catch (IllegalArgumentException e) {
                throw new InvalidMapException(addressingVersion.faultToTag, addressingVersion.invalidAddressTag);
            }
        }
        */

    }

    protected void checkMandatoryHeaders(
        Packet packet, boolean foundAction, boolean foundTo, boolean foundMessageId, boolean foundRelatesTo) {
        super.checkMandatoryHeaders(packet, foundAction, foundTo, foundMessageId, foundRelatesTo);
        WSDLBoundOperation wbo = getWSDLBoundOperation(packet);
        // no need to check for for non-application messages
        if (wbo == null)
            return;

        // if no wsa:To header is found
        if (!foundTo)
            throw new MapRequiredException(addressingVersion.toTag);

        // if two-way and no wsa:MessageID is found
        if (!wbo.getOperation().isOneWay() && !foundMessageId)
            throw new MapRequiredException(addressingVersion.messageIDTag);
    }


    final void checkAnonymousSemantics(WSDLBoundOperation wbo, WSEndpointReference replyTo, WSEndpointReference faultTo) {
        // no check if Addressing is not enabled or is Member Submission
        if (addressingVersion == null || addressingVersion == AddressingVersion.MEMBER)
            return;

        if (wbo == null)
            return;

        WSDLBoundOperation.ANONYMOUS anon = wbo.getAnonymous();

        String replyToValue = null;
        String faultToValue = null;

        if (replyTo != null)
            replyToValue = replyTo.getAddress();

        if (faultTo != null)
            faultToValue = faultTo.getAddress();

        switch (anon) {
        case optional:
            // no check is required
            break;
        case prohibited:
            if (replyToValue != null && replyToValue.equals(addressingVersion.anonymousUri))
                throw new InvalidMapException(addressingVersion.replyToTag, ONLY_NON_ANONYMOUS_ADDRESS_SUPPORTED);

            if (faultToValue != null && faultToValue.equals(addressingVersion.anonymousUri))
                throw new InvalidMapException(addressingVersion.faultToTag, ONLY_NON_ANONYMOUS_ADDRESS_SUPPORTED);
            break;
        case required:
            if (replyToValue != null && !replyToValue.equals(addressingVersion.anonymousUri))
                throw new InvalidMapException(addressingVersion.replyToTag, ONLY_ANONYMOUS_ADDRESS_SUPPORTED);

            if (faultToValue != null && !faultToValue.equals(addressingVersion.anonymousUri))
                throw new InvalidMapException(addressingVersion.faultToTag, ONLY_ANONYMOUS_ADDRESS_SUPPORTED);
            break;
        default:
            // cannot reach here
            throw new WebServiceException(AddressingMessages.INVALID_WSAW_ANONYMOUS(anon.toString()));
        }
    }

    public static final String REQUEST_MESSAGE_ID = "com.sun.xml.internal.ws.addressing.request.messageID";

    private static final Logger LOGGER = Logger.getLogger(WsaServerTube.class.getName());
}
