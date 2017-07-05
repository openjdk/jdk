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
package com.sun.xml.internal.ws.server.provider;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.resources.ServerMessages;

import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Jitendra Kotamraju
 */
abstract class SOAPProviderArgumentBuilder<T> extends ProviderArgumentsBuilder<T> {
    protected final SOAPVersion soapVersion;

    private SOAPProviderArgumentBuilder(SOAPVersion soapVersion) {
        this.soapVersion = soapVersion;
    }

    static ProviderArgumentsBuilder create(ProviderEndpointModel model, SOAPVersion soapVersion) {
        if (model.mode == Service.Mode.PAYLOAD) {
            return new PayloadSource(soapVersion);
        } else {
            if(model.datatype==Source.class)
                return new MessageSource(soapVersion);
            if(model.datatype==SOAPMessage.class)
                return new SOAPMessageParameter(soapVersion);
            if(model.datatype==Message.class)
                return new MessageProviderArgumentBuilder(soapVersion);
            throw new WebServiceException(ServerMessages.PROVIDER_INVALID_PARAMETER_TYPE(model.implClass,model.datatype));
        }
    }

    private static final class PayloadSource extends SOAPProviderArgumentBuilder<Source> {
        PayloadSource(SOAPVersion soapVersion) {
            super(soapVersion);
        }

        protected Source getParameter(Packet packet) {
            return packet.getMessage().readPayloadAsSource();
        }

        protected Message getResponseMessage(Source source) {
            return Messages.createUsingPayload(source, soapVersion);
        }

        protected Message getResponseMessage(Exception e) {
            return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, e);
        }

    }

    private static final class MessageSource extends SOAPProviderArgumentBuilder<Source> {
        MessageSource(SOAPVersion soapVersion) {
            super(soapVersion);
        }

        protected Source getParameter(Packet packet) {
            return packet.getMessage().readEnvelopeAsSource();
        }

        protected Message getResponseMessage(Source source) {
            return Messages.create(source, soapVersion);
        }

        protected Message getResponseMessage(Exception e) {
            return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, e);
        }
    }

    private static final class SOAPMessageParameter extends SOAPProviderArgumentBuilder<SOAPMessage> {
        SOAPMessageParameter(SOAPVersion soapVersion) {
            super(soapVersion);
        }

        protected SOAPMessage getParameter(Packet packet) {
            try {
                return packet.getMessage().readAsSOAPMessage(packet, true);
            } catch (SOAPException se) {
                throw new WebServiceException(se);
            }
        }

        protected Message getResponseMessage(SOAPMessage soapMsg) {
            return Messages.create(soapMsg);
        }

        protected Message getResponseMessage(Exception e) {
            return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, e);
        }

        @Override
        protected Packet getResponse(Packet request, @Nullable SOAPMessage returnValue, WSDLPort port, WSBinding binding) {
            Packet response = super.getResponse(request, returnValue, port, binding);
            // Populate SOAPMessage's transport headers
            if (returnValue != null && response.supports(Packet.OUTBOUND_TRANSPORT_HEADERS)) {
                MimeHeaders hdrs = returnValue.getMimeHeaders();
                Map<String, List<String>> headers = new HashMap<String, List<String>>();
                Iterator i = hdrs.getAllHeaders();
                while(i.hasNext()) {
                    MimeHeader header = (MimeHeader)i.next();
                    if(header.getName().equalsIgnoreCase("SOAPAction"))
                        // SAAJ sets this header automatically, but it interferes with the correct operation of JAX-WS.
                        // so ignore this header.
                        continue;

                    List<String> list = headers.get(header.getName());
                    if (list == null) {
                        list = new ArrayList<String>();
                        headers.put(header.getName(), list);
                    }
                    list.add(header.getValue());
                }
                response.put(Packet.OUTBOUND_TRANSPORT_HEADERS, headers);
            }
            return response;
        }

    }

}
