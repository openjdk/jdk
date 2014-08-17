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

package com.sun.xml.internal.ws.addressing.v200408;

import com.sun.xml.internal.ws.addressing.WsaClientTube;
import com.sun.xml.internal.ws.addressing.model.MissingAddressingHeaderException;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.AddressingUtils;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressing;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressingFeature;

/**
 * @author Rama Pulavarthi
 */
public class MemberSubmissionWsaClientTube extends WsaClientTube {
    private final MemberSubmissionAddressing.Validation validation;

    public MemberSubmissionWsaClientTube(WSDLPort wsdlPort, WSBinding binding, Tube next) {
        super(wsdlPort, binding, next);
        validation = binding.getFeature(MemberSubmissionAddressingFeature.class).getValidation();

    }

    public MemberSubmissionWsaClientTube(MemberSubmissionWsaClientTube that, TubeCloner cloner) {
        super(that, cloner);
        this.validation = that.validation;

    }
    public MemberSubmissionWsaClientTube copy(TubeCloner cloner) {
        return new MemberSubmissionWsaClientTube(this, cloner);
    }

    @Override
    protected void checkMandatoryHeaders(Packet packet, boolean foundAction, boolean foundTo, boolean foundReplyTo,
                                         boolean foundFaultTo, boolean foundMessageID, boolean foundRelatesTo) {
        super.checkMandatoryHeaders(packet,foundAction,foundTo,foundReplyTo,foundFaultTo,foundMessageID,foundRelatesTo);

        // if no wsa:To header is found
        if (!foundTo) {
            throw new MissingAddressingHeaderException(addressingVersion.toTag,packet);
        }

        if (!validation.equals(MemberSubmissionAddressing.Validation.LAX)) {

            // if it is not one-way, response must contain wsa:RelatesTo
            // RelatesTo required as per
            // Table 5-3 of http://www.w3.org/TR/2006/WD-ws-addr-wsdl-20060216/#wsdl11requestresponse
            if (expectReply && (packet.getMessage() != null) && !foundRelatesTo) {
                String action = AddressingUtils.getAction(packet.getMessage().getHeaders(), addressingVersion, soapVersion);
                // Don't check for AddressingFaults as
                // Faults for requests with duplicate MessageId will have no wsa:RelatesTo
                if (!packet.getMessage().isFault() || !action.equals(addressingVersion.getDefaultFaultAction())) {
                    throw new MissingAddressingHeaderException(addressingVersion.relatesToTag,packet);
                }
            }
        }
    }
}
