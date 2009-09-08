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
package com.sun.xml.internal.ws.handler;

import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.handler.MessageHandler;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.HandlerConfiguration;
import com.sun.xml.internal.ws.message.DataHandlerAttachment;

import javax.activation.DataHandler;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.Handler;
import java.util.*;

/**
 * @author Rama Pulavarthi
 */
public class ServerMessageHandlerTube extends HandlerTube{
    private SEIModel seiModel;
    private WSBinding binding;
    private Set<String> roles;

    public ServerMessageHandlerTube(SEIModel seiModel, WSBinding binding, Tube next, HandlerTube cousinTube) {
        super(next, cousinTube);
        this.seiModel = seiModel;
        this.binding = binding;
        setUpHandlersOnce();
    }

    /**
     * Copy constructor for {@link com.sun.xml.internal.ws.api.pipe.Tube#copy(com.sun.xml.internal.ws.api.pipe.TubeCloner)}.
     */
    private ServerMessageHandlerTube(ServerMessageHandlerTube that, TubeCloner cloner) {
        super(that, cloner);
        this.seiModel = that.seiModel;
        this.binding = that.binding;
        this.handlers = that.handlers;
        this.roles = that.roles;
    }

    private void setUpHandlersOnce() {
        handlers = new ArrayList<Handler>();
        HandlerConfiguration handlerConfig = ((BindingImpl) binding).getHandlerConfig();
        List<MessageHandler> msgHandlersSnapShot= handlerConfig.getMessageHandlers();
        if (!msgHandlersSnapShot.isEmpty()) {
            handlers.addAll(msgHandlersSnapShot);
            roles = new HashSet<String>();
            roles.addAll(handlerConfig.getRoles());
        }
    }

    void callHandlersOnResponse(MessageUpdatableContext context, boolean handleFault) {
        //Lets copy all the MessageContext.OUTBOUND_ATTACHMENT_PROPERTY to the message
        Map<String, DataHandler> atts = (Map<String, DataHandler>) context.get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);
        AttachmentSet attSet = packet.getMessage().getAttachments();
        for(String cid : atts.keySet()){
            if (attSet.get(cid) == null) { // Otherwise we would be adding attachments twice
                Attachment att = new DataHandlerAttachment(cid, atts.get(cid));
                attSet.add(att);
            }
        }

        try {
            //SERVER-SIDE
            processor.callHandlersResponse(HandlerProcessor.Direction.OUTBOUND, context, handleFault);

        } catch (WebServiceException wse) {
            //no rewrapping
            throw wse;
        } catch (RuntimeException re) {
            throw re;

        }
    }

    boolean callHandlersOnRequest(MessageUpdatableContext context, boolean isOneWay) {
        boolean handlerResult;
        try {
            //SERVER-SIDE
            handlerResult = processor.callHandlersRequest(HandlerProcessor.Direction.INBOUND, context, !isOneWay);

        } catch (RuntimeException re) {
            remedyActionTaken = true;
            throw re;

        }
        if (!handlerResult) {
            remedyActionTaken = true;
        }
        return handlerResult;
    }

    void setUpProcessor() {
        if(!handlers.isEmpty()) {
            processor = new SOAPHandlerProcessor(false, this, binding, handlers);
        }
    }

    void closeHandlers(MessageContext mc) {
        closeServersideHandlers(mc);

    }
    MessageUpdatableContext getContext(Packet packet) {
       MessageHandlerContextImpl context = new MessageHandlerContextImpl(seiModel, binding, port, packet, roles);
       return context;
    }

    //should be overridden by DriverHandlerTubes
    @Override
    protected void initiateClosing(MessageContext mc) {
      close(mc);
      super.initiateClosing(mc);
    }

   public AbstractFilterTubeImpl copy(TubeCloner cloner) {
        return new ServerMessageHandlerTube(this, cloner);
    }
}
