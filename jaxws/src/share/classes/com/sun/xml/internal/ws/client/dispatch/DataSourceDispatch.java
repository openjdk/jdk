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

package com.sun.xml.internal.ws.client.dispatch;

import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
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

    public DataSourceDispatch(QName port, Service.Mode mode, WSServiceDelegate service, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
       super(port, mode, service, pipe, binding, epr );
    }

    Packet createPacket(DataSource arg) {

         switch (mode) {
            case PAYLOAD:
                throw new IllegalArgumentException("DataSource use is not allowed in Service.Mode.PAYLOAD\n");
            case MESSAGE:
                return new Packet(XMLMessage.create(arg));
            default:
                throw new WebServiceException("Unrecognized message mode");
        }
    }

    DataSource toReturnValue(Packet response) {

        Message message = response.getMessage();

        if (message instanceof MessageDataSource) {
            MessageDataSource hasDS = (MessageDataSource)message;
            // TODO Need to call hasUnconsumedDataSource()
            return hasDS.getDataSource();
        } else if (message instanceof PayloadSourceMessage) {
            return XMLMessage.getDataSource(message);
        }
        return null;
    }
}
