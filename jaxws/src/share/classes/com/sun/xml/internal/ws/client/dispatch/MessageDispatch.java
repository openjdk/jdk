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

import javax.xml.namespace.QName;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service.Mode;

/**
 * {@link Dispatch} implementation for {@link Message}.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.1.1
 */
public class MessageDispatch extends DispatchImpl<Message> {

    public MessageDispatch(QName port, WSServiceDelegate service, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
        super(port, Mode.MESSAGE, service, pipe, binding, epr);
    }

    @Override
    Message toReturnValue(Packet response) {
        return response.getMessage();
    }

    @Override
    Packet createPacket(Message msg) {
        return new Packet(msg);
    }
}
