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

package com.sun.xml.internal.ws.handler;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.handler.MessageHandlerContext;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;

import java.util.Set;

/**
 * @author Rama Pulavarthi
 */
public class MessageHandlerContextImpl extends MessageUpdatableContext implements MessageHandlerContext {
    private @Nullable SEIModel seiModel;
    private Set<String> roles;
    private WSBinding binding;
    private @Nullable WSDLPort wsdlModel;

    public MessageHandlerContextImpl(@Nullable SEIModel seiModel, WSBinding binding, @Nullable WSDLPort wsdlModel, Packet packet, Set<String> roles) {
        super(packet);
        this.seiModel = seiModel;
        this.binding = binding;
        this.wsdlModel = wsdlModel;
        this.roles = roles;
    }
    public Message getMessage() {
        return packet.getMessage();
    }

    public void setMessage(Message message) {
        packet.setMessage(message);
    }

    public Set<String> getRoles() {
        return roles;
    }

    public WSBinding getWSBinding() {
        return binding;
    }

    public @Nullable SEIModel getSEIModel() {
        return seiModel;
    }

    public @Nullable WSDLPort getPort() {
        return wsdlModel;
    }

    void updateMessage() {
       // Do Nothing
    }

    void setPacketMessage(Message newMessage) {
        setMessage(newMessage);
    }
}
