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

import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.client.PortInfo;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import com.sun.xml.internal.ws.message.source.PayloadSourceMessage;
import static com.sun.xml.internal.ws.binding.WebServiceFeatureList.toFeatureArray;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service.Mode;
import java.io.IOException;

/**
 * {@link Dispatch} implementation for {@link Source} and XML/HTTP binding.
 *
 * @author Kathy Walsh
 * @author Kohsuke Kawaguchi
 * @see SOAPSourceDispatch
 */
final class RESTSourceDispatch extends DispatchImpl<Source> {
    @Deprecated
    public RESTSourceDispatch(QName port, Mode mode, WSServiceDelegate owner, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
        super(port, mode, owner, pipe, binding, epr);
        assert isXMLHttp(binding);
    }

    public RESTSourceDispatch(WSPortInfo portInfo, Mode mode, BindingImpl binding, WSEndpointReference epr) {
        super(portInfo, mode, binding, epr);
        assert isXMLHttp(binding);
    }

    @Override
    Source toReturnValue(Packet response) {
        Message msg = response.getMessage();
        try {
            return new StreamSource(XMLMessage.getDataSource(msg, binding.getFeatures()).getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    Packet createPacket(Source msg) {
        Message message;

        if(msg==null)
            message = Messages.createEmpty(soapVersion);
        else
            message = new PayloadSourceMessage(null, msg, setOutboundAttachments(), soapVersion);

        return new Packet(message);
    }
}
