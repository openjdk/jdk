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
import com.sun.xml.internal.ws.addressing.model.ActionNotSupportedException;
import com.sun.xml.internal.ws.addressing.model.MapRequiredException;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.HeaderList;
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
 *
 * @author Arun Gupta
 */
public final class WsaClientTube extends WsaTube {
    public WsaClientTube(WSDLPort wsdlPort, WSBinding binding, Tube next) {
        super(wsdlPort, binding, next);
    }

    public WsaClientTube(WsaClientTube that, TubeCloner cloner) {
        super(that, cloner);
    }

    public WsaClientTube copy(TubeCloner cloner) {
        return new WsaClientTube(this, cloner);
    }

    public @NotNull NextAction processRequest(Packet request) {
        return doInvoke(next,request);
   }

    public @NotNull NextAction processResponse(Packet response) {
        // if one-way then, no validation
        if (response.getMessage() != null)
            response = validateInboundHeaders(response);

        return doReturnWith(response);
    }


    @Override
    public void validateAction(Packet packet) {
        //There may not be a WSDL operation.  There may not even be a WSDL.
        //For instance this may be a RM CreateSequence message.
        WSDLBoundOperation wbo = getWSDLBoundOperation(packet);

        if (wbo == null)    return;

        String gotA = packet.getMessage().getHeaders().getAction(addressingVersion, soapVersion);
        if (gotA == null)
            throw new WebServiceException(AddressingMessages.VALIDATION_CLIENT_NULL_ACTION());

        String expected = helper.getOutputAction(packet);

        if (expected != null && !gotA.equals(expected))
            throw new ActionNotSupportedException(gotA);
    }

    @Override
    protected void checkMandatoryHeaders(Packet packet, boolean foundAction, boolean foundTo, boolean foundMessageID, boolean foundRelatesTo) {
        super.checkMandatoryHeaders(packet, foundAction, foundTo, foundMessageID, foundRelatesTo);

//        if(!foundRelatesTo)
//            // RelatesTo required as per
//            // Table 5-3 of http://www.w3.org/TR/2006/WD-ws-addr-wsdl-20060216/#wsdl11requestresponse
//            throw new MapRequiredException(addressingVersion.relatesToTag);

    }
}
