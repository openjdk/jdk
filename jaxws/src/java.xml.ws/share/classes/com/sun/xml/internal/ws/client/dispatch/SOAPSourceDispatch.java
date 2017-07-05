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

package com.sun.xml.internal.ws.client.dispatch;

import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.client.PortInfo;
import com.sun.xml.internal.ws.message.source.PayloadSourceMessage;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.Service.Mode;
import javax.xml.ws.WebServiceException;


/**
 * The <code>SOAPSourceDispatch</code> class provides support
 * for the dynamic invocation of a service endpoint operation using XML
 * constructs. The <code>javax.xml.ws.Service</code>
 * interface acts as a factory for the creation of <code>SOAPSourceDispatch</code>
 * instances.
 *
 * @author WS Development Team
 * @version 1.0
 * @see RESTSourceDispatch
 */
final class SOAPSourceDispatch extends DispatchImpl<Source> {
    @Deprecated
    public SOAPSourceDispatch(QName port, Mode mode, WSServiceDelegate owner, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
        super(port, mode, owner, pipe, binding, epr);
        assert !isXMLHttp(binding);
    }

    public SOAPSourceDispatch(WSPortInfo portInfo, Mode mode, BindingImpl binding, WSEndpointReference epr) {
            super(portInfo, mode, binding, epr);
            assert !isXMLHttp(binding);
    }


    Source toReturnValue(Packet response) {
        Message msg = response.getMessage();

        switch (mode) {
        case PAYLOAD:
            return msg.readPayloadAsSource();
        case MESSAGE:
            return msg.readEnvelopeAsSource();
        default:
            throw new WebServiceException("Unrecognized dispatch mode");
        }
    }

    @Override
    Packet createPacket(Source msg) {

        final Message message;

        if (msg == null)
            message = Messages.createEmpty(soapVersion);
        else {
            switch (mode) {
            case PAYLOAD:
                message = new PayloadSourceMessage(null, msg, setOutboundAttachments(), soapVersion);
                break;
            case MESSAGE:
                message = Messages.create(msg, soapVersion);
                break;
            default:
                throw new WebServiceException("Unrecognized message mode");
            }
        }

        return new Packet(message);
    }


}
