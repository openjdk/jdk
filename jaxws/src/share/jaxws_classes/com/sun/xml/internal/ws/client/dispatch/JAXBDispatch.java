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

package com.sun.xml.internal.ws.client.dispatch;

import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Headers;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.message.jaxb.JAXBDispatchMessage;
import com.sun.xml.internal.ws.spi.db.BindingContextFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

/**
 * The <code>JAXBDispatch</code> class provides support
 * for the dynamic invocation of a service endpoint operation using
 * JAXB objects. The <code>javax.xml.ws.Service</code>
 * interface acts as a factory for the creation of <code>JAXBDispatch</code>
 * instances.
 *
 * @author WS Development Team
 * @version 1.0
 */
public class JAXBDispatch extends DispatchImpl<Object> {

    private final JAXBContext jaxbcontext;

    // We will support a JAXBContext parameter from an unknown JAXB
    // implementation by marshaling and unmarshaling directly from the
    // context object, as there is no Bond available.
    private final boolean isContextSupported;

    @Deprecated
    public JAXBDispatch(QName port, JAXBContext jc, Service.Mode mode, WSServiceDelegate service, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
        super(port, mode, service, pipe, binding, epr);
        this.jaxbcontext = jc;
        this.isContextSupported = BindingContextFactory.isContextSupported(jc);
    }

    public JAXBDispatch(WSPortInfo portInfo, JAXBContext jc, Service.Mode mode, BindingImpl binding, WSEndpointReference epr) {
        super(portInfo, mode, binding, epr);
        this.jaxbcontext = jc;
        this.isContextSupported = BindingContextFactory.isContextSupported(jc);
    }

    Object toReturnValue(Packet response) {
        try {
            Unmarshaller unmarshaller = jaxbcontext.createUnmarshaller();
            Message msg = response.getMessage();
            switch (mode) {
                case PAYLOAD:
                    return msg.<Object>readPayloadAsJAXB(unmarshaller);
                case MESSAGE:
                    Source result = msg.readEnvelopeAsSource();
                    return unmarshaller.unmarshal(result);
                default:
                    throw new WebServiceException("Unrecognized dispatch mode");
            }
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        }
    }


    Packet createPacket(Object msg) {
        assert jaxbcontext != null;

        Message message;
        if (mode == Service.Mode.MESSAGE) {
            message = isContextSupported ?
                    new JAXBDispatchMessage(BindingContextFactory.create(jaxbcontext), msg, soapVersion) :
                    new JAXBDispatchMessage(jaxbcontext, msg, soapVersion);
        } else {
            if (msg == null) {
                message = Messages.createEmpty(soapVersion);
            } else {
                message = isContextSupported ?
                        Messages.create(jaxbcontext, msg, soapVersion) :
                        Messages.createRaw(jaxbcontext, msg, soapVersion);
            }
        }

        return new Packet(message);

    }

    public void setOutboundHeaders(Object... headers) {
        if (headers == null)
            throw new IllegalArgumentException();
        Header[] hl = new Header[headers.length];
        for (int i = 0; i < hl.length; i++) {
            if (headers[i] == null)
                throw new IllegalArgumentException();
            // TODO: handle any JAXBContext.
            hl[i] = Headers.create((JAXBRIContext) jaxbcontext, headers[i]);
        }
        super.setOutboundHeaders(hl);
    }
}
