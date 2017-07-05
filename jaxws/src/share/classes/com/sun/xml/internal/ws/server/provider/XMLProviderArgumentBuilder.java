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

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import com.sun.xml.internal.ws.resources.ServerMessages;

import javax.activation.DataSource;
import javax.xml.transform.Source;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.http.HTTPException;

/**
 * @author Jitendra Kotamraju
 */
abstract class XMLProviderArgumentBuilder<T> extends ProviderArgumentsBuilder<T> {

    @Override
    protected Packet getResponse(Packet request, Exception e, WSDLPort port, WSBinding binding) {
        Packet response = super.getResponse(request, e, port, binding);
        if (e instanceof HTTPException) {
            if (response.supports(MessageContext.HTTP_RESPONSE_CODE)) {
                response.put(MessageContext.HTTP_RESPONSE_CODE, ((HTTPException)e).getStatusCode());
            }
        }
        return response;
    }

    static XMLProviderArgumentBuilder create(ProviderEndpointModel model) {
        if (model.mode == Service.Mode.PAYLOAD) {
            return new PayloadSource();
        } else {
            if(model.datatype==Source.class)
                return new PayloadSource();
            if(model.datatype== DataSource.class)
                return new DataSourceParameter();
            throw new WebServiceException(ServerMessages.PROVIDER_INVALID_PARAMETER_TYPE(model.implClass,model.datatype));
        }
    }

    private static final class PayloadSource extends XMLProviderArgumentBuilder<Source> {
        public Source getParameter(Packet packet) {
            return packet.getMessage().readPayloadAsSource();
        }

        public Message getResponseMessage(Source source) {
            return Messages.createUsingPayload(source, SOAPVersion.SOAP_11);
        }

        protected Message getResponseMessage(Exception e) {
            return XMLMessage.create(e);
        }
    }

    private static final class DataSourceParameter extends XMLProviderArgumentBuilder<DataSource> {
        public DataSource getParameter(Packet packet) {
            Message msg = packet.getMessage();
            return (msg instanceof XMLMessage.MessageDataSource)
                    ? ((XMLMessage.MessageDataSource) msg).getDataSource()
                    : XMLMessage.getDataSource(msg);
        }

        public Message getResponseMessage(DataSource ds) {
            return XMLMessage.create(ds);
        }

        protected Message getResponseMessage(Exception e) {
            return XMLMessage.create(e);
        }
    }

}
