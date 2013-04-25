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

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.addressing.model.ActionNotSupportedException;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.AddressingUtils;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.resources.AddressingMessages;

import javax.xml.ws.WebServiceException;

/**
 * WsaClientTube appears in the Tubeline only if addressing is enabled.
 * This tube checks the validity of addressing headers in the incoming messages
 * based on the WSDL model.
 * @author Rama Pulavarthi
 * @author Arun Gupta
 */
public class WsaClientTube extends WsaTube {
    // capture if the request expects a reply so that it can be used to
    // determine if its oneway for response validation.
    protected boolean expectReply = true;
    public WsaClientTube(WSDLPort wsdlPort, WSBinding binding, Tube next) {
        super(wsdlPort, binding, next);
    }

    public WsaClientTube(WsaClientTube that, TubeCloner cloner) {
        super(that, cloner);
    }

    public WsaClientTube copy(TubeCloner cloner) {
        return new WsaClientTube(this, cloner);
    }

    @Override
    public @NotNull NextAction processRequest(Packet request) {
        expectReply = request.expectReply;
        return doInvoke(next,request);
   }

    @Override
    public @NotNull NextAction processResponse(Packet response) {
        // if one-way then, no validation
        if (response.getMessage() != null) {
            response = validateInboundHeaders(response);
            response.addSatellite(new WsaPropertyBag(addressingVersion,soapVersion,response));
            String msgId = AddressingUtils.
              getMessageID(response.getMessage().getHeaders(),
                      addressingVersion, soapVersion);
            response.put(WsaPropertyBag.WSA_MSGID_FROM_REQUEST, msgId);
        }

        return doReturnWith(response);
    }


    @Override
    protected void validateAction(Packet packet) {
        //There may not be a WSDL operation.  There may not even be a WSDL.
        //For instance this may be a RM CreateSequence message.
        WSDLBoundOperation wbo = getWSDLBoundOperation(packet);

        if (wbo == null)    return;

        String gotA = AddressingUtils.getAction(
                packet.getMessage().getHeaders(),
                addressingVersion, soapVersion);
        if (gotA == null)
            throw new WebServiceException(AddressingMessages.VALIDATION_CLIENT_NULL_ACTION());

        String expected = helper.getOutputAction(packet);

        if (expected != null && !gotA.equals(expected))
            throw new ActionNotSupportedException(gotA);
    }

}
