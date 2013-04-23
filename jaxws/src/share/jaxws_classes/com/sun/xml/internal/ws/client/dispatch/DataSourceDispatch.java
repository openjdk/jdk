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
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.client.PortInfo;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage.MessageDataSource;
import com.sun.xml.internal.ws.message.source.PayloadSourceMessage;

import javax.activation.DataSource;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

/**
 *
 * @author WS Development Team
 * @version 1.0
 */
public class DataSourceDispatch extends DispatchImpl<DataSource> {
    @Deprecated
    public DataSourceDispatch(QName port, Service.Mode mode, WSServiceDelegate service, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
       super(port, mode, service, pipe, binding, epr );
    }

    public DataSourceDispatch(WSPortInfo portInfo, Service.Mode mode,BindingImpl binding, WSEndpointReference epr) {
       super(portInfo, mode, binding, epr );
    }

    Packet createPacket(DataSource arg) {

         switch (mode) {
            case PAYLOAD:
                throw new IllegalArgumentException("DataSource use is not allowed in Service.Mode.PAYLOAD\n");
            case MESSAGE:
                return new Packet(XMLMessage.create(arg, binding.getFeatures()));
            default:
                throw new WebServiceException("Unrecognized message mode");
        }
    }

    DataSource toReturnValue(Packet response) {
        Message message = response.getInternalMessage();
        return (message instanceof MessageDataSource)
                ? ((MessageDataSource)message).getDataSource()
                : XMLMessage.getDataSource(message, binding.getFeatures());
    }
}
