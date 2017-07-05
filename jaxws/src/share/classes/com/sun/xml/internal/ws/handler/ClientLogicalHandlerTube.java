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
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.binding.BindingImpl;

import javax.xml.ws.handler.LogicalHandler;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.WebServiceException;
import java.util.List;
import java.util.ArrayList;

/**
 *
 * @author WS Development Team
 */
public class ClientLogicalHandlerTube extends HandlerTube {

    private WSBinding binding;
    private List<LogicalHandler> logicalHandlers;

    /**
     * Creates a new instance of LogicalHandlerTube
     */
    public ClientLogicalHandlerTube(WSBinding binding, WSDLPort port, Tube next) {
        super(next, port);
        this.binding = binding;
    }

    /**
     * This constructor is used on client-side where, SOAPHandlerTube is created
     * first and then a LogicalHandlerTube is created with a handler to that
     * SOAPHandlerTube.
     * With this handle, LogicalHandlerTube can call
     * SOAPHandlerTube.closeHandlers()
     */
    public ClientLogicalHandlerTube(WSBinding binding, Tube next, HandlerTube cousinTube) {
        super(next, cousinTube);
        this.binding = binding;
    }

    /**
     * Copy constructor for {@link com.sun.xml.internal.ws.api.pipe.Tube#copy(com.sun.xml.internal.ws.api.pipe.TubeCloner)}.
     */

    private ClientLogicalHandlerTube(ClientLogicalHandlerTube that, TubeCloner cloner) {
        super(that, cloner);
        this.binding = that.binding;
    }

    boolean isHandlerChainEmpty() {
        return logicalHandlers.isEmpty();
    }

    /**
     * Close SOAPHandlers first and then LogicalHandlers on Client
     * Close LogicalHandlers first and then SOAPHandlers on Server
     */
    public void close(MessageContext msgContext) {
        //assuming cousinTube is called if requestProcessingSucessful is true
        if (requestProcessingSucessful) {
            //cousinTube is null in XML/HTTP Binding
            if (cousinTube != null) {
                // Close SOAPHandlerTube
                cousinTube.closeCall(msgContext);
            }
        }
        if (processor != null)
            closeLogicalHandlers(msgContext);

    }

    /**
     * This is called from cousinTube.
     * Close this Tubes's handlers.
     */
    public void closeCall(MessageContext msgContext) {
        closeLogicalHandlers(msgContext);
    }

    //TODO:
    private void closeLogicalHandlers(MessageContext msgContext) {
        if (processor == null)
            return;
        if (remedyActionTaken) {
            //Close only invoked handlers in the chain

            //CLIENT-SIDE
            processor.closeHandlers(msgContext, processor.getIndex(), 0);
            processor.setIndex(-1);
            //reset remedyActionTaken
            remedyActionTaken = false;
        } else {
            //Close all handlers in the chain

            //CLIENT-SIDE
            processor.closeHandlers(msgContext, logicalHandlers.size() - 1, 0);

        }
    }

    public AbstractFilterTubeImpl copy(TubeCloner cloner) {
        return new ClientLogicalHandlerTube(this, cloner);
    }

    void setUpProcessor() {
        // Take a snapshot, User may change chain after invocation, Same chain
        // should be used for the entire MEP
        logicalHandlers = new ArrayList<LogicalHandler>();
        List<LogicalHandler> logicalSnapShot= ((BindingImpl) binding).getHandlerConfig().getLogicalHandlers();
        if (!logicalSnapShot.isEmpty()) {
            logicalHandlers.addAll(logicalSnapShot);
            if (binding.getSOAPVersion() == null) {
                processor = new XMLHandlerProcessor(this, binding,
                        logicalHandlers);
            } else {
                processor = new SOAPHandlerProcessor(true, this, binding,
                        logicalHandlers);
            }
        }
    }


    MessageUpdatableContext getContext(Packet packet) {
        return new LogicalMessageContextImpl(binding, packet);
    }

    boolean callHandlersOnRequest(MessageUpdatableContext context, boolean isOneWay) {

        boolean handlerResult;
        try {

            //CLIENT-SIDE
            handlerResult = processor.callHandlersRequest(HandlerProcessor.Direction.OUTBOUND, context, !isOneWay);
        } catch (WebServiceException wse) {
            remedyActionTaken = true;
            //no rewrapping
            throw wse;
        } catch (RuntimeException re) {
            remedyActionTaken = true;

            throw new WebServiceException(re);

        }
        if (!handlerResult) {
            remedyActionTaken = true;
        }
        return handlerResult;
    }

    void callHandlersOnResponse(MessageUpdatableContext context, boolean handleFault) {
        try {

            //CLIENT-SIDE
            processor.callHandlersResponse(HandlerProcessor.Direction.INBOUND, context, handleFault);

        } catch (WebServiceException wse) {
            //no rewrapping
            throw wse;
        } catch (RuntimeException re) {

            throw new WebServiceException(re);

        }
    }
}
