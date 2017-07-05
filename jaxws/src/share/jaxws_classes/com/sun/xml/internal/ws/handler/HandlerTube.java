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
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.*;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.HandlerConfiguration;

import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.Handler;
import java.util.List;

/**
 * @author WS Development team
 */

public abstract class HandlerTube extends AbstractFilterTubeImpl {
    /**
     * handle hold reference to other Tube for inter-tube communication
     */
    HandlerTube cousinTube;
    protected List<Handler> handlers;
    HandlerProcessor processor;
    boolean remedyActionTaken = false;
    protected final @Nullable WSDLPort port;
    // flag used to decide whether to call close on cousinTube
    boolean requestProcessingSucessful = false;
    private WSBinding binding;
    private HandlerConfiguration hc;

    public HandlerTube(Tube next, WSDLPort port, WSBinding binding) {
        super(next);
        this.port = port;
        this.binding = binding;
    }

    public HandlerTube(Tube next, HandlerTube cousinTube, WSBinding binding) {
        super(next);
        this.cousinTube = cousinTube;
        this.binding = binding;
        if(cousinTube != null) {
            this.port = cousinTube.port;
        } else {
            this.port = null;
        }
    }

    /**
     * Copy constructor for {@link Tube#copy(TubeCloner)}.
     */
    protected HandlerTube(HandlerTube that, TubeCloner cloner) {
        super(that,cloner);
        if(that.cousinTube != null) {
            this.cousinTube = cloner.copy(that.cousinTube);
        }
        this.port = that.port;
        this.binding = that.binding;
    }

    protected WSBinding getBinding() {
        return binding;
    }

    @Override
    public NextAction processRequest(Packet request) {
        setupExchange();
        // This check is done to cover handler returning false in Oneway request
        if (isHandleFalse()) {
            // Cousin HandlerTube returned false during Oneway Request processing.
            // Don't call handlers and dispatch the message.
            remedyActionTaken = true;
            return doInvoke(super.next, request);
        }

        // This is done here instead of the constructor, since User can change
        // the roles and handlerchain after a stub/proxy is created.
        setUpProcessorInternal();

        MessageUpdatableContext context = getContext(request);
        boolean isOneWay = checkOneWay(request);
        try {
            if (!isHandlerChainEmpty()) {
                // Call handlers on Request
                boolean handlerResult = callHandlersOnRequest(context, isOneWay);
                //Update Packet with user modifications
                context.updatePacket();
                // two-way case where no message is sent
                if (!isOneWay && !handlerResult) {
                    return doReturnWith(request);
                }
            }
            requestProcessingSucessful = true;
            // Call next Tube
            return doInvoke(super.next, request);
        } catch (RuntimeException re) {
            if(isOneWay) {
                //Eat the exception, its already logged and close the transportBackChannel
                if(request.transportBackChannel != null ) {
                        request.transportBackChannel.close();
                }
                request.setMessage(null);
                return doReturnWith(request);
            } else
                throw re;
        } finally {
            if(!requestProcessingSucessful) {
                initiateClosing(context.getMessageContext());
            }
        }

    }

    @Override
    public NextAction processResponse(Packet response) {
        setupExchange();
        MessageUpdatableContext context = getContext(response);
        try {
            if (isHandleFalse() || (response.getMessage() == null)) {
                // Cousin HandlerTube returned false during Response processing.
                // or it is oneway request
                // or handler chain is empty
                // Don't call handlers.
                return doReturnWith(response);
            }

            setUpProcessorInternal();

            boolean isFault = isHandleFault(response);
            if (!isHandlerChainEmpty()) {
                // Call handlers on Response
                callHandlersOnResponse(context, isFault);
            }
        } finally {
            initiateClosing(context.getMessageContext());
        }
        //Update Packet with user modifications
        context.updatePacket();

        return doReturnWith(response);

    }

    @Override
    public NextAction processException(Throwable t) {
        try {
            return doThrow(t);
        } finally {
                Packet packet = Fiber.current().getPacket();
            MessageUpdatableContext context = getContext(packet);
            initiateClosing(context.getMessageContext());
            /* TODO revisit: commented this out as the modified packet is no longer used
                    In future if the message is propagated even when an exception
                    occurs, then uncomment context.updatePacket();
            */
            //Update Packet with user modifications
            //context.updatePacket();


        }
    }

    /**
     * Must be overridden by HandlerTube that drives other handler tubes for processing a message.
     * On Client-side: ClientLogicalHandlerTube drives the Handler Processing.
     * On Server-side: In case SOAP Binding, ServerMessageHandlerTube drives the Handler Processing.
     *                 In case XML/HTTP Binding, ServerLogicalHandlerTube drives the Handler Processing.
     *
     *
     * If its a top HandlerTube, should override by calling #close(MessaggeContext);
     *
     */

    protected void initiateClosing(MessageContext mc) {
        // Do nothing

    }

    /**
     * Calls close on previously invoked handlers.
     * Also, Cleans up any state left over in the Tube instance from the current
     * invocation, as Tube instances can be reused after the completion of MEP.
     *
     * On Client, SOAPHandlers are closed first and then LogicalHandlers
     * On Server, LogicalHandlers are closed first and then SOAPHandlers
     */
    final public void close(MessageContext msgContext) {
        //assuming cousinTube is called if requestProcessingSucessful is true
        if (requestProcessingSucessful) {
            if (cousinTube != null) {
                cousinTube.close(msgContext);
            }

        }
        if (processor != null)
            closeHandlers(msgContext);

        // Clean up the exchange for next invocation.
        exchange = null;
        requestProcessingSucessful = false;

    }

    /**
     * On Client, Override by calling #closeClientHandlers(MessageContext mc)
     * On Server, Override by calling #closeServerHandlers(MessageContext mc)
     *      The difference is the order in which they are closed.
     * @param mc
     */
    abstract void closeHandlers(MessageContext mc);

    /**
     * Called by close(MessageContext mc) in a Client Handlertube
     */
    protected void closeClientsideHandlers(MessageContext msgContext) {
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
            processor.closeHandlers(msgContext, handlers.size() - 1, 0);

        }
    }

    /**
     * Called by close(MessageContext mc) in a Server Handlertube
     */
    protected void closeServersideHandlers(MessageContext msgContext) {
        if (processor == null)
            return;
        if (remedyActionTaken) {
            //Close only invoked handlers in the chain

            //SERVER-SIDE
            processor.closeHandlers(msgContext, processor.getIndex(), handlers.size() - 1);
            processor.setIndex(-1);
            //reset remedyActionTaken
            remedyActionTaken = false;
        } else {
            //Close all handlers in the chain

            //SERVER-SIDE
            processor.closeHandlers(msgContext, 0, handlers.size() - 1);

        }
    }

    abstract void callHandlersOnResponse(MessageUpdatableContext context, boolean handleFault);

    abstract boolean callHandlersOnRequest(MessageUpdatableContext context, boolean oneWay);

    private boolean checkOneWay(Packet packet) {
        if (port != null) {
            /* we can determine this value from WSDL */
            return packet.getMessage().isOneWay(port);
        } else {
            /*
              otherwise use this value as an approximation, since this carries
              the application's intention --- whether it was invokeOneway vs invoke,etc.
             */
            return !(packet.expectReply != null && packet.expectReply);
        }
    }

    private void setUpProcessorInternal() {
        HandlerConfiguration hc = ((BindingImpl) binding).getHandlerConfig();
        if (hc != this.hc)
                resetProcessor();
        this.hc = hc;

        setUpProcessor();
    }

    abstract void setUpProcessor();

    protected void resetProcessor() {
        handlers = null;
    }

    final public boolean isHandlerChainEmpty() {
        return handlers.isEmpty();
    }
    abstract MessageUpdatableContext getContext(Packet p);

    private boolean isHandleFault(Packet packet) {
        if (cousinTube != null) {
            return exchange.isHandleFault();
        } else {
            boolean isFault = packet.getMessage().isFault();
            exchange.setHandleFault(isFault);
            return isFault;
        }
    }

    final void setHandleFault() {
        exchange.setHandleFault(true);
    }

    private boolean isHandleFalse() {
        return exchange.isHandleFalse();
    }

    final void setHandleFalse() {
        exchange.setHandleFalse();
    }

    private void setupExchange() {
        if(exchange == null) {
            exchange = new HandlerTubeExchange();
            if(cousinTube != null) {
                cousinTube.exchange = exchange;
            }
        } else {
            if(cousinTube != null) {
                cousinTube.exchange = exchange;
            }

        }
    }
    private HandlerTubeExchange exchange;

    /**
     * This class is used primarily to exchange information or status between
     * LogicalHandlerTube and SOAPHandlerTube
     */
    static final class HandlerTubeExchange {
        private boolean handleFalse;
        private boolean handleFault;

        boolean isHandleFault() {
            return handleFault;
        }

        void setHandleFault(boolean isFault) {
            this.handleFault = isFault;
        }

        public boolean isHandleFalse() {
            return handleFalse;
        }

        void setHandleFalse() {
            this.handleFalse = true;
        }
    }

}
