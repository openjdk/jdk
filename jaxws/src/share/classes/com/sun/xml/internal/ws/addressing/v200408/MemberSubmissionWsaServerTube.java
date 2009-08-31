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

package com.sun.xml.internal.ws.addressing.v200408;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.addressing.WsaServerTube;
import com.sun.xml.internal.ws.addressing.model.MissingAddressingHeaderException;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressing;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressingFeature;

/**
 * @author Rama Pulavarthi
 */
public class MemberSubmissionWsaServerTube extends WsaServerTube {
    private final MemberSubmissionAddressing.Validation validation;
    public MemberSubmissionWsaServerTube(WSEndpoint endpoint, @NotNull WSDLPort wsdlPort, WSBinding binding, Tube next) {
        super(endpoint, wsdlPort, binding, next);
        validation = binding.getFeature(MemberSubmissionAddressingFeature.class).getValidation();
    }

    public MemberSubmissionWsaServerTube(MemberSubmissionWsaServerTube that, TubeCloner cloner) {
        super(that, cloner);
        this.validation = that.validation;
    }

    public MemberSubmissionWsaServerTube copy(TubeCloner cloner) {
        return new MemberSubmissionWsaServerTube(this, cloner);
    }

    @Override
    protected void checkMandatoryHeaders(Packet packet, boolean foundAction, boolean foundTo, boolean foundReplyTo,
            boolean foundFaultTo, boolean foundMessageId, boolean foundRelatesTo) {

        super.checkMandatoryHeaders(packet, foundAction, foundTo, foundReplyTo,
                foundFaultTo, foundMessageId, foundRelatesTo);

        // if no wsa:To header is found
        if (!foundTo)
            throw new MissingAddressingHeaderException(addressingVersion.toTag,packet);

        //we can find Req/Response or Oneway only with WSDLModel
        if (wsdlPort != null) {
            WSDLBoundOperation wbo = getWSDLBoundOperation(packet);
            // if two-way, must contain wsa:ReplyTo
            // Unlike W3C version, we cannot assume default value as anonymous if not present.
            // For protocol messages, don't check as they do not have any corresponding wsdl operations
            if (wbo != null && !wbo.getOperation().isOneWay() && !foundReplyTo) {
                throw new MissingAddressingHeaderException(addressingVersion.replyToTag,packet);
            }
        }
        if (!validation.equals(MemberSubmissionAddressing.Validation.LAX)) {
            // wsa:MessageId is required if wsa:ReplyTo is present.
            if ((foundReplyTo || foundFaultTo) && !foundMessageId)
                throw new MissingAddressingHeaderException(addressingVersion.messageIDTag,packet);
        }
    }
}
