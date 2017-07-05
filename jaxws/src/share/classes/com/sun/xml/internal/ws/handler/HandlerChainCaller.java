/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.LogicalMessage;
import javax.xml.ws.ProtocolException;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.LogicalHandler;
import javax.xml.ws.handler.LogicalMessageContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import javax.xml.ws.http.HTTPException;
import javax.xml.ws.soap.SOAPFaultException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;

/**
 * The class stores the actual "chain" of handlers that is called
 * during a request or response. On the client side, it is created
 * by a {@link com.sun.xml.internal.ws.binding.BindingImpl} class when a
 * binding provider is created. On the server side, where a Binding
 * object may be passed from an outside source, the handler chain
 * caller may be created by the message dispatcher classes.
 *
 * <p>When created, a java.util.List of Handlers is passed in. This list
 * is sorted into logical and protocol handlers, so the handler order
 * that is returned from getHandlerChain() may be different from the
 * original that was passed in.
 *
 * <p>At runtime, one of the callHandlers() methods is invoked by the
 * soap or xml message dispatchers, passing in a {@link HandlerContext}
 * or {@link XMLHandlerContext} object along with other information
 * about the current message that is required for proper handler flow.
 *
 * <p>Exceptions are logged in many cases here before being rethrown. This
 * is to help primarily with server side handlers.
 *
 * <p>Currently, the handler chain caller checks for a null soap
 * message context to see if the binding in use is XML/HTTP.
 *
 * @see com.sun.xml.internal.ws.binding.BindingImpl
 * @see com.sun.xml.internal.ws.protocol.soap.client.SOAPMessageDispatcher
 * @see com.sun.xml.internal.ws.protocol.soap.server.SOAPMessageDispatcher
 * @see com.sun.xml.internal.ws.protocol.xml.server.XMLMessageDispatcher
 *
 * @author WS Development Team
 */
public class HandlerChainCaller {

    public static final String HANDLER_CHAIN_CALLER = "handler_chain_caller";
    public static final String IGNORE_FAULT_PROPERTY =
        "ignore fault in message";

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".handler");

    // need request or response for Handle interface
    public enum RequestOrResponse { REQUEST, RESPONSE }
    public enum Direction { OUTBOUND, INBOUND }

    private Set<QName> understoodHeaders;
    private List<Handler> handlers; // may be logical/soap mixed

    private List<LogicalHandler> logicalHandlers;
    private List<SOAPHandler> soapHandlers;

    private Set<String> roles;

    /**
     * The handlers that are passed in will be sorted into
     * logical and soap handlers. During this sorting, the
     * understood headers are also obtained from any soap
     * handlers.
     *
     * @param chain A list of handler objects, which can
     * be protocol or logical handlers.
     */
    public HandlerChainCaller(List<Handler> chain) {
        if (chain == null) { // should only happen in testing
            chain = new ArrayList<Handler>();
        }
        handlers = chain;
        logicalHandlers = new ArrayList<LogicalHandler>();
        soapHandlers = new ArrayList<SOAPHandler>();
        understoodHeaders = new HashSet<QName>();
        sortHandlers();
    }

    /**
     * This list may be different than the chain that is passed
     * in since the logical and protocol handlers must be separated.
     *
     * @return The list of handlers, sorted by logical and then protocol.
     */
    public List<Handler> getHandlerChain() {
        return handlers;
    }

    public boolean hasHandlers() {
        return (handlers.size() != 0);
    }

    /**
     * These are set by the SOAPBindingImpl when it creates the
     * HandlerChainCaller or when new roles are set on the binding.
     *
     * @param roles A set of roles strings.
     */
    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    /**
     * Returns the roles that were passed in by the binding
     * in the case of soap binding.
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Returns the headers understood by the handlers. This set
     * is created when the handler chain caller is instantiated and
     * the handlers are sorted. The set is comprised of headers
     * returned from SOAPHandler.getHeaders() method calls.
     *
     * @return The set of all headers that the handlers declare
     * that they understand.
     */
    public Set<QName> getUnderstoodHeaders() {
        return understoodHeaders;
    }

    /**
     * This method separates the logical and protocol handlers. When
     * this method returns, the original "handlers" List has been
     * resorted.
     */
    private void sortHandlers() {
        for (Handler handler : handlers) {
            if (LogicalHandler.class.isAssignableFrom(handler.getClass())) {
                logicalHandlers.add((LogicalHandler) handler);
            } else if (SOAPHandler.class.isAssignableFrom(handler.getClass())) {
                soapHandlers.add((SOAPHandler) handler);
                Set<QName> headers = ((SOAPHandler) handler).getHeaders();
                if (headers != null) {
                    understoodHeaders.addAll(headers);
                }
            } else if (Handler.class.isAssignableFrom(handler.getClass())) {
                throw new HandlerException(
                    "cannot.extend.handler.directly",
                    handler.getClass().toString());
            } else {
                throw new HandlerException("handler.not.valid.type",
                    handler.getClass().toString());
            }
        }
        handlers.clear();
        handlers.addAll(logicalHandlers);
        handlers.addAll(soapHandlers);
    }

    /**
     * Replace the message in the given message context with a
     * fault message. If the context already contains a fault
     * message, then return without changing it.
     * Also sets the HTTP_RESPONSE_CODE in the context on Server-side.
     */
    private void insertFaultMessage(ContextHolder holder,
        ProtocolException exception) {
        try {
            SOAPMessageContext context = holder.getSMC();
            if (context == null) { // non-soap case
                LogicalMessageContext lmc = holder.getLMC();
                LogicalMessage msg = lmc.getMessage();
                if (msg != null) {
                    msg.setPayload(null);
                }
                //Set Status Code only if it is on server
                if((Boolean)lmc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY)){
                    if (exception instanceof HTTPException) {
                        lmc.put(MessageContext.HTTP_RESPONSE_CODE,((HTTPException)exception).getStatusCode());
                    } else {
                        lmc.put(MessageContext.HTTP_RESPONSE_CODE,WSConnection.INTERNAL_ERR);
                    }
                }
                return;
            }
            //Set Status Code only if it is on server
            if((Boolean)context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY)){
                context.put(MessageContext.HTTP_RESPONSE_CODE,WSConnection.INTERNAL_ERR);
            }
            SOAPMessage message = context.getMessage();
            SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();
            SOAPBody body = envelope.getBody();
            if (body.hasFault()) {
                return;
            }
            if (envelope.getHeader() != null) {
                envelope.getHeader().detachNode();
            }

            body.removeContents();
            SOAPFault fault = body.addFault();
            String envelopeNamespace = envelope.getNamespaceURI();

            if (exception instanceof SOAPFaultException) {
                SOAPFaultException sfe = (SOAPFaultException) exception;
                SOAPFault userFault = sfe.getFault();

                QName faultCode = userFault.getFaultCodeAsQName();
                if (faultCode == null) {
                    faultCode = determineFaultCode(context);
                }
                fault.setFaultCode(faultCode);

                String faultString = userFault.getFaultString();
                if (faultString == null) {
                    if (sfe.getMessage() != null) {
                        faultString = sfe.getMessage();
                    } else {
                        faultString = sfe.toString();
                    }
                }
                fault.setFaultString(faultString);

                String faultActor = userFault.getFaultActor();
                if (faultActor == null) {
                    faultActor = "";
                }
                fault.setFaultActor(faultActor);

                if (userFault.getDetail() != null) {
                    fault.addChildElement(userFault.getDetail());
                }
            } else {
                fault.setFaultCode(determineFaultCode(context));
                if (exception.getMessage() != null) {
                    fault.setFaultString(exception.getMessage());
                } else {
                    fault.setFaultString(exception.toString());
                }
            }
        } catch (Exception e) {
            // severe since this is from runtime and not handler
            logger.log(Level.SEVERE,
                "exception while creating fault message in handler chain", e);
            throw new RuntimeException(e);
        }
    }



    /**
     * <p>The expectation of the rest of the code is that,
     * if a ProtocolException is thrown from the handler chain,
     * the message contents reflect the protocol exception.
     * However, if a new ProtocolException is thrown from
     * the handleFault method, then the fault should be
     * ignored and the new exception should be dispatched.
     *
     * <p>This method simply sets a property that is checked
     * by the client and server code when a ProtocolException
     * is caught. The property can be checked with
     * {@link MessageContextUtil#ignoreFaultInMessage}
     */
    private void addIgnoreFaultProperty(ContextHolder holder) {
        LogicalMessageContext context = holder.getLMC();
        context.put(IGNORE_FAULT_PROPERTY, Boolean.TRUE);
    }

    /**
     * <p>Figure out if the fault code local part is client,
     * server, sender, receiver, etc. This is called by
     * insertFaultMessage.
     *
     * <p>This method should only be called when there is a ProtocolException
     * during request. Reverse the Message direction first,
     * So this method can use the MESSAGE_OUTBOUND_PROPERTY
     * to determine whether it is being called on the client
     * or the server side. If this changes in the spec, then
     * something else will need to be passed to the method
     * to determine whether the fault code is client or server.
     *
     * <p>For determining soap version, start checking with the
     * latest version and default to soap 1.1.
     */
    private QName determineFaultCode(SOAPMessageContext context)
        throws SOAPException {

        SOAPEnvelope envelope =
            context.getMessage().getSOAPPart().getEnvelope();
        String uri = envelope.getNamespaceURI();

        // client case
        if (!(Boolean) context.get(
            MessageContext.MESSAGE_OUTBOUND_PROPERTY)) {
            if (uri.equals(SOAP12NamespaceConstants.ENVELOPE)) {
                return SOAP12Constants.FAULT_CODE_CLIENT;
            }
            return SOAPConstants.FAULT_CODE_CLIENT;
        }

        //server case
        if (uri.equals(SOAP12NamespaceConstants.ENVELOPE)) {
            return SOAP12Constants.FAULT_CODE_SERVER;
        }
        return SOAPConstants.FAULT_CODE_SERVER;
    }

    /**
     * <p>Method used to call handlers with a HandlerContext that
     * may contain logical and protocol handlers. This is the
     * main entry point for calling the handlers in the case
     * of SOAP binding. Before calling the handlers, the
     * handler chain caller will set the outbound property and
     * the roles on the message context.
     *
     * <p>Besides the context object passed in, the other information
     * is used to control handler execution and closing. See the
     * handler section of the spec for the rules concering handlers
     * returning false, throwing exceptions, etc.
     *
     * @param direction Inbound or outbound.
     * @param messageType Request or response.
     * @param context A soap handler context containing the message.
     * @param responseExpected A boolean indicating whether or not
     * a response is expected to the current message (should be false
     * for responses or one-way requests).
     *
     * @return True in the normal case, false if a handler
     * returned false. This normally means that the runtime
     * should reverse direction if called during a request.
     */
    public boolean callHandlers(Direction direction,
        RequestOrResponse messageType,
        SOAPHandlerContext context,
        boolean responseExpected) {

        return internalCallHandlers(direction, messageType,
            new ContextHolder(context), responseExpected);
    }

    /**
     * Method used to call handlers with a HandlerContext that
     * may contain logical handlers only. This is the
     * main entry point for calling the handlers in the case
     * of http binding. Before calling the handlers, the
     * handler chain caller will set the outbound property on
     * the message context.
     *
     * <p>Besides the context object passed in, the other information
     * is used to control handler execution and closing. See the
     * handler section of the spec for the rules concering handlers
     * returning false, throwing exceptions, etc.
     *
     * @param direction Inbound or outbound.
     * @param messageType Request or response.
     * @param context A soap handler context containing the message.
     * @param responseExpected A boolean indicating whether or not
     * a response is expected to the current message (should be false
     * for responses or one-way requests).
     *
     * @return True in the normal case, false if a handler
     * returned false. This normally means that the runtime
     * should reverse direction if called during a request.
     */
    public boolean callHandlers(Direction direction,
        RequestOrResponse messageType,
        XMLHandlerContext context,
        boolean responseExpected) {

        return internalCallHandlers(direction, messageType,
            new ContextHolder(context), responseExpected);
    }

    /**
     * Main runtime method, called internally by the callHandlers()
     * methods that may be called with HandlerContext or
     * XMLHandlerContext objects.
     *
     * The boolean passed in is whether or not a response is required
     * for the current message. See section 5.3.2. (todo: this section
     * is going to change).
     *
     * The callLogicalHandlers and callProtocolHandlers methods will
     * take care of execution once called and return true or false or
     * throw an exception.
     */
    private boolean internalCallHandlers(Direction direction,
        RequestOrResponse messageType,
        ContextHolder ch,
        boolean responseExpected) {

        // set outbound property
        ch.getLMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,
            (direction == Direction.OUTBOUND));

        // if there is as soap message context, set roles
        if (ch.getSMC() != null) {
            ((SOAPMessageContextImpl) ch.getSMC()).setRoles(getRoles());
        }

        // call handlers
        if (direction == Direction.OUTBOUND) {
            if (callLogicalHandlers(ch, direction, messageType,
                    responseExpected) == false) {
                return false;
            }
            if (callProtocolHandlers(ch, direction, messageType,
                    responseExpected) == false) {
                return false;
            }
        } else {
            if (callProtocolHandlers(ch, direction, messageType,
                    responseExpected) == false) {
                return false;
            }
            if (callLogicalHandlers(ch, direction, messageType,
                    responseExpected) == false) {
                return false;
            }
        }

        /*
         * Close if MEP finished. Server code responsible for closing
         * handlers if it determines that an incoming request is a
         * one way message.
         */
                if (!responseExpected) {
            if (messageType == RequestOrResponse.REQUEST) {
                if (direction == Direction.INBOUND) {
                    closeHandlersServer(ch);
                } else {
                    closeHandlersClient(ch);
                }
            } else {
                if (direction == Direction.INBOUND) {
                    closeHandlersClient(ch);
                } else {
                    closeHandlersServer(ch);
                }
            }
        }
        return true;
    }

    /**
     * This method called by the server when an endpoint has thrown
     * an exception. This method calls handleFault on the handlers
     * and closes them. Because this method is called only during
     * a response after the endpoint has been reached, all of the
     * handlers have been called during the request and so all are
     * closed.
     */
    public boolean callHandleFault(SOAPHandlerContext context) {
        ContextHolder ch = new ContextHolder(context);
        ch.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, true);
        ((SOAPMessageContextImpl) ch.getSMC()).setRoles(getRoles());

        int i = 0; // counter for logical handlers
        int j = 0; // counter for protocol handlers
        try {
            while (i < logicalHandlers.size()) {
                if (logicalHandlers.get(i).handleFault(ch.getLMC()) == false) {
                    return false;
                }
                i++;
            }
            while (j < soapHandlers.size()) {
                if (soapHandlers.get(j).handleFault(ch.getSMC()) == false) {
                    return false;
                }
                j++;
            }
        } catch (RuntimeException re) {
            logger.log(Level.FINER, "exception in handler chain", re);
            throw re;
        } finally {
            closeHandlersServer(ch); // this is always called on server side
        }
        return true;
    }

    /**
     * This method called by the client when it sees a SOAPFault message.
     * This method calls handleFault on the handlers and closes them. Because
     * this method is called only during a response, all of the handlers have
     * been called during the request and so all are closed.
     */
    public boolean callHandleFaultOnClient(SOAPHandlerContext context) {
        ContextHolder ch = new ContextHolder(context);
        ch.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, false);
        ((SOAPMessageContextImpl) ch.getSMC()).setRoles(getRoles());

        try {
            for (int i=soapHandlers.size()-1; i>=0; i--) {
                if (soapHandlers.get(i).handleFault(ch.getSMC()) == false) {
                    return false;
                }
            }
            for (int i=logicalHandlers.size()-1; i>=0; i--) {
                if (logicalHandlers.get(i).handleFault(ch.getLMC()) == false) {
                    return false;
                }
            }
        } catch (RuntimeException re) {
            logger.log(Level.FINER, "exception in handler chain", re);
            throw re;
        } finally {
            closeHandlersClient(ch);
        }
        return true;
    }


    /**
     * Called from the main callHandlers() method.
     * Logical message context updated before this method is called.
     */
    private boolean callLogicalHandlers(ContextHolder holder,
        Direction direction, RequestOrResponse type, boolean responseExpected) {

        if (direction == Direction.OUTBOUND) {
            int i = 0;
            try {
                while (i < logicalHandlers.size()) {
                    if (logicalHandlers.get(i).
                        handleMessage(holder.getLMC()) == false) {
                        if (responseExpected) {
                            // reverse and call handle message
                            holder.getLMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,false);
                            callLogicalHandleMessage(holder, i-1, 0);
                        }
                        if (type == RequestOrResponse.RESPONSE) {
                            closeHandlersServer(holder);
                        } else {
                            closeLogicalHandlers(holder, i, 0);
                        }
                        return false;
                    }
                    i++;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER, "exception in handler chain", re);
                if (responseExpected && re instanceof ProtocolException) {
                    // reverse direction and handle fault
                    holder.getLMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,false);
                    insertFaultMessage(holder, (ProtocolException) re);
                    if (i>0) {
                        try {
                            callLogicalHandleFault(holder, i-1, 0);
                        } catch (ProtocolException re1) {
                            addIgnoreFaultProperty(holder);
                            re = re1;
                        } catch (RuntimeException re2) {
                            re = re2;
                        }
                    }
                }
                if (type == RequestOrResponse.RESPONSE) {
                    closeHandlersServer(holder);
                } else {
                    closeLogicalHandlers(holder, i, 0);
                }
                throw re;
            }
        } else { // inbound case, H(x) -> H(x-1) -> ... H(1) -> H(0)
            int i = logicalHandlers.size()-1;
            try {
                while (i >= 0) {
                    if (logicalHandlers.get(i).
                        handleMessage(holder.getLMC()) == false) {

                        if (responseExpected) {
                            // reverse and call handle message/response
                            holder.getLMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,true);
                            callLogicalHandleMessage(holder, i+1,
                                logicalHandlers.size()-1);
                            callProtocolHandleMessage(holder, 0,
                                soapHandlers.size()-1);
                        }
                        if (type == RequestOrResponse.RESPONSE) {
                            closeHandlersClient(holder);
                        } else {
                            closeLogicalHandlers(holder, i,
                                logicalHandlers.size()-1);
                            closeProtocolHandlers(holder, 0,
                                soapHandlers.size()-1);
                        }
                        return false;
                    }
                    i--;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER, "exception in handler chain", re);
                if (responseExpected && re instanceof ProtocolException) {
                    // reverse direction and handle fault
                    holder.getLMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,true);
                    insertFaultMessage(holder, (ProtocolException) re);

                    try {
                        // if i==size-1, no more logical handlers to call
                        if (i == logicalHandlers.size()-1 ||
                            callLogicalHandleFault(holder, i+1,
                                logicalHandlers.size()-1)) {
                            callProtocolHandleFault(holder, 0,
                                soapHandlers.size()-1);
                        }
                    } catch (ProtocolException re1) {
                        addIgnoreFaultProperty(holder);
                        re = re1;
                    } catch (RuntimeException re2) {
                        re = re2;
                    }
                }
                if (type == RequestOrResponse.RESPONSE) {
                    closeHandlersClient(holder);
                } else {
                    closeLogicalHandlers(holder, i, logicalHandlers.size()-1);
                    closeProtocolHandlers(holder, 0, soapHandlers.size()-1);
                }
                throw re;
            }
        }

        return true;
    }

    /**
     * Called from the main callHandlers() method.
     * SOAP message context updated before this method is called.
     */
    private boolean callProtocolHandlers(ContextHolder holder,
        Direction direction, RequestOrResponse type, boolean responseExpected) {

        if (direction == Direction.OUTBOUND) {
            int i = 0;
            try {
                while (i<soapHandlers.size()) {
                    if (soapHandlers.get(i).
                        handleMessage(holder.getSMC()) == false) {

                        if (responseExpected) {
                            // reverse and call handle message/response
                            holder.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,false);
                            if (i>0) {
                                callProtocolHandleMessage(holder, i-1, 0);
                            }
                            callLogicalHandleMessage(holder,
                                logicalHandlers.size()-1, 0);
                        }
                        if (type == RequestOrResponse.RESPONSE) {
                            closeHandlersServer(holder);
                        } else {
                            closeProtocolHandlers(holder, i, 0);
                            closeLogicalHandlers(holder,
                                logicalHandlers.size()-1 , 0);
                        }
                        return false;
                    }
                    i++;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER, "exception in handler chain", re);
                if (responseExpected && re instanceof ProtocolException) {
                    // reverse direction and handle fault
                    holder.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,false);
                    insertFaultMessage(holder, (ProtocolException) re);
                    try {
                        if (i == 0 || // still on first handler
                            callProtocolHandleFault(holder, i-1, 0)) {
                            callLogicalHandleFault(holder,
                                logicalHandlers.size()-1, 0);
                        }
                    } catch (ProtocolException re1) {
                        addIgnoreFaultProperty(holder);
                        re = re1;
                    } catch (RuntimeException re2) {
                        re = re2;
                    }
                }
                if (type == RequestOrResponse.RESPONSE) {
                    closeHandlersServer(holder);
                } else {
                    closeProtocolHandlers(holder, i, 0);
                    closeLogicalHandlers(holder, logicalHandlers.size()-1, 0);
                }
                throw re;
            }
        } else { // inbound case, H(x) -> H(x-1) -> ... H(1) -> H(0)
            int i = soapHandlers.size()-1;
            try {
                while (i >= 0) {
                    if (soapHandlers.get(i).
                        handleMessage(holder.getSMC()) == false) {

                        // reverse and call handle message/response
                        holder.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,true);
                        if (responseExpected && i != soapHandlers.size()-1) {
                            callProtocolHandleMessage(holder, i+1,
                                soapHandlers.size()-1);
                        }
                        if (type == RequestOrResponse.RESPONSE) {
                            closeHandlersClient(holder);
                        } else {
                            closeProtocolHandlers(holder, i,
                                soapHandlers.size()-1);
                        }
                        return false;
                    }
                    i--;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER, "exception in handler chain", re);
                if (responseExpected && re instanceof ProtocolException) {
                    // reverse direction and handle fault
                    holder.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY,true);
                    insertFaultMessage(holder, (ProtocolException) re);
                    try {
                        if (i < soapHandlers.size()-1) {
                            callProtocolHandleFault(holder, i+1,
                                soapHandlers.size()-1);
                        }
                    } catch (ProtocolException re1) {
                        addIgnoreFaultProperty(holder);
                        re = re1;
                    } catch (RuntimeException re2) {
                        re = re2;
                    }
                }
                if (type == RequestOrResponse.RESPONSE) {
                    closeHandlersClient(holder);
                } else {
                    closeProtocolHandlers(holder, i, soapHandlers.size()-1);
                }
                throw re;
            }
        }
        return true;
    }

    /**
     * Method called for abnormal processing (for instance, as the
     * result of a handler returning false during normal processing).
     * Start and end indices are inclusive.
     */
    private void callLogicalHandleMessage(ContextHolder holder,
            int start, int end) {

        if (logicalHandlers.isEmpty() ||
            start == -1 ||
            start == logicalHandlers.size()) {
            return;
        }
        callGenericHandleMessage(logicalHandlers,holder.getLMC(),start,end);

    }

    /**
     * Method called for abnormal processing (for instance, as the
     * result of a handler returning false during normal processing).
     * Start and end indices are inclusive.
     */
    private void callProtocolHandleMessage(ContextHolder holder,
        int start, int end) {

        if (soapHandlers.isEmpty()) {
            return;
        }
        callGenericHandleMessage(soapHandlers,holder.getSMC(),start,end);
    }

    /**
     * Utility method for calling handleMessage during abnormal processing(for
     * instance, as the result of a handler returning false during normal
     * processing). Start and end indices are inclusive.
     */

    private <C extends MessageContext> void callGenericHandleMessage(List<? extends Handler> handlerList,
        C context, int start, int end) {
        if (handlerList.isEmpty()) {
            return ;
        }
        int i = start;
        if (start > end) {
            try {
                while (i >= end) {
                    if (handlerList.get(i).handleMessage(context) == false)
                        return;
                    i--;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER,
                    "exception in handler chain", re);
                throw re;
            }
        } else {
            try {
                while (i <= end) {
                    if (handlerList.get(i).handleMessage(context) == false)
                        return ;
                    i++;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER,
                    "exception in handler chain", re);
                throw re;
            }
        }
        return;
    }

    /*
     * Calls handleFault on the logical handlers. Indices are
     * inclusive. Exceptions get passed up the chain, and an
     * exception or return of 'false' ends processing.
     */
    private boolean callLogicalHandleFault(ContextHolder holder,
            int start, int end) {

        return callGenericHandleFault(logicalHandlers,
            holder.getLMC(), start, end);
    }

    /**
     * Calls handleFault on the protocol handlers. Indices are
     * inclusive. Exceptions get passed up the chain, and an
     * exception or return of 'false' ends processing.
     */
    private boolean callProtocolHandleFault(ContextHolder holder,
        int start, int end) {

        return callGenericHandleFault(soapHandlers,
            holder.getSMC(), start, end);
    }

    /*
     * Used by callLogicalHandleFault and callProtocolHandleFault.
     */
    private <C extends MessageContext> boolean callGenericHandleFault(List<? extends Handler> handlerList,
        C context, int start, int end) {

        if (handlerList.isEmpty()) {
            return true;
        }
        int i = start;
        if (start > end) {
            try {
                while (i >= end) {
                    if (handlerList.get(i).
                            handleFault(context) == false) {

                        return false;
                    }
                    i--;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER,
                    "exception in handler chain", re);
                throw re;
            }
        } else {
            try {
                while (i <= end) {
                    if (handlerList.get(i).
                        handleFault(context) == false) {

                        return false;
                    }
                    i++;
                }
            } catch (RuntimeException re) {
                logger.log(Level.FINER,
                    "exception in handler chain", re);
                throw re;
            }
        }
        return true;
    }

    /**
     * Method that closes protocol handlers and then
     * logical handlers.
     */
    private void closeHandlersClient(ContextHolder holder) {
        closeProtocolHandlers(holder, soapHandlers.size()-1, 0);
        closeLogicalHandlers(holder, logicalHandlers.size()-1, 0);
    }

    /**
     * Method that closes logical handlers and then
     * protocol handlers.
     */
    private void closeHandlersServer(ContextHolder holder) {
        closeLogicalHandlers(holder, 0, logicalHandlers.size()-1);
        closeProtocolHandlers(holder, 0, soapHandlers.size()-1);
    }

    /**
     * This version is called by the server code once it determines
     * that an incoming message is a one-way request.
     */
    public void forceCloseHandlersOnServer(SOAPHandlerContext context) {
        ContextHolder ch = new ContextHolder(context);
        // only called after an inbound request
        ch.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, false);
        ((SOAPMessageContextImpl) ch.getSMC()).setRoles(getRoles());
        closeHandlersServer(ch);
    }

    /**
     * It is called by the client when an MU fault occurs since the handlerchain
     * never gets invoked. The direction is an inbound message.
     */
    public void forceCloseHandlersOnClient(SOAPHandlerContext context) {
        ContextHolder ch = new ContextHolder(context);

        // only called after an inbound request
        ch.getSMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, false);
        ((SOAPMessageContextImpl) ch.getSMC()).setRoles(getRoles());
        closeHandlersClient(ch);
    }

    /**
     * Version of forceCloseHandlers(HandlerContext) that is used
     * by XML binding.
     */
    public void forceCloseHandlersOnServer(XMLHandlerContext context) {
        ContextHolder ch = new ContextHolder(context);
        // only called after an inbound request
        ch.getLMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, false);
        closeHandlersServer(ch);
    }

    /**
     * Version of forceCloseHandlers(HandlerContext) that is used
     * by XML binding.
     */
    public void forceCloseHandlersOnClient(XMLHandlerContext context) {
        ContextHolder ch = new ContextHolder(context);
        // only called after an inbound request
        ch.getLMC().put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, false);
        closeHandlersClient(ch);
    }

    private void closeProtocolHandlers(ContextHolder holder,
        int start, int end) {

        closeGenericHandlers(soapHandlers, holder.getSMC(), start, end);
    }

    private void closeLogicalHandlers(ContextHolder holder,
        int start, int end) {

        closeGenericHandlers(logicalHandlers, holder.getLMC(), start, end);
    }

    /**
     * Calls close on the handlers from the starting
     * index through the ending index (inclusive). Made indices
     * inclusive to allow both directions more easily.
     */
    private void closeGenericHandlers(List<? extends Handler> handlerList,
        MessageContext context, int start, int end) {

        if (handlerList.isEmpty()) {
            return;
        }
        if (start > end) {
            for (int i=start; i>=end; i--) {
                try {
                    handlerList.get(i).close(context);
                } catch (RuntimeException re) {
                    logger.log(Level.INFO,
                        "Exception ignored during close", re);
                }
            }
        } else {
            for (int i=start; i<=end; i++) {
                try {
                    handlerList.get(i).close(context);
                } catch (RuntimeException re) {
                    logger.log(Level.INFO,
                        "Exception ignored during close", re);
                }
            }
        }
    }

    /**
     * Used to hold the context objects that are used to get
     * and set the current message.
     *
     * If a HandlerContext is passed in, both logical and soap
     * handlers are used. If XMLHandlerContext is passed in,
     * only logical handlers are assumed to be present.
     */
    static class ContextHolder {

        boolean logicalOnly;
        SOAPHandlerContext context;
        XMLHandlerContext xmlContext;

        ContextHolder(SOAPHandlerContext context) {
            this.context = context;
            logicalOnly = false;
        }

        ContextHolder(XMLHandlerContext xmlContext) {
            this.xmlContext = xmlContext;
            logicalOnly = true;
        }

        LogicalMessageContext getLMC() {
            return (logicalOnly ? xmlContext.getLogicalMessageContext() :
                context.getLogicalMessageContext());
        }

        SOAPMessageContext getSMC() {
            return (logicalOnly ? null : context.getSOAPMessageContext());
        }
    }

}
