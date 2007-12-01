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
package com.sun.xml.internal.ws.protocol.xml.server;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.pept.presentation.TargetFinder;
import com.sun.xml.internal.ws.pept.presentation.Tie;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import com.sun.xml.internal.ws.handler.HandlerChainCaller;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.Direction;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.RequestOrResponse;
import com.sun.xml.internal.ws.handler.XMLHandlerContext;
import com.sun.xml.internal.ws.server.provider.ProviderModel;
import com.sun.xml.internal.ws.server.provider.ProviderPeptTie;
import com.sun.xml.internal.ws.spi.runtime.Invoker;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.ws.Binding;
import javax.xml.ws.handler.MessageContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.xml.internal.ws.server.*;
import com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegate;
import com.sun.xml.internal.ws.spi.runtime.WebServiceContext;
import com.sun.xml.internal.ws.util.XMLConnectionUtil;
import javax.xml.ws.handler.MessageContext.Scope;

import static com.sun.xml.internal.ws.client.BindingProviderProperties.CONTENT_NEGOTIATION_PROPERTY;

/**
 * @author WS Development Team
 *
 */
public abstract class XMLMessageDispatcher implements MessageDispatcher {

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.xmlmd");

    public XMLMessageDispatcher() {
    }

    public void send(MessageInfo messageInfo) {
        // Not required for server
        throw new UnsupportedOperationException();
    }

    // TODO: need to work the exception logic
    public void receive(MessageInfo messageInfo) {
        XMLMessage xmlMessage = null;
        try {
            xmlMessage = getXMLMessage(messageInfo);
        } catch(Exception e) {
            sendResponseError(messageInfo, e);
            return;
        }
        // Set it before response is sent on transport. If transport creates
        // any exception, this can be used not to send again
        boolean sent = false;
        try {

            // If FI is accepted by client, set property to optimistic
            if (xmlMessage.acceptFastInfoset()) {
                messageInfo.setMetaData(CONTENT_NEGOTIATION_PROPERTY, "optimistic");
            }

            XMLHandlerContext context = new XMLHandlerContext(messageInfo, null,
                xmlMessage);
            updateHandlerContext(messageInfo, context);


            SystemHandlerDelegate shd = getSystemHandlerDelegate(messageInfo);
            XmlInvoker implementor = new XmlInvoker(messageInfo, xmlMessage,
                context, shd);
            try {
                if (shd == null) {
                    // Invokes request handler chain, endpoint, response handler chain
                    implementor.invoke();
                } else {
                    context.setInvoker(implementor);
                    if (shd.processRequest(context.getSHDXMLMessageContext())) {
                        implementor.invoke();
                        context.getMessageContext().put(
                            MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.TRUE);
                        shd.processResponse(context.getSHDXMLMessageContext());
                    }
                }
            } finally {
                sent = implementor.isSent();    // response is sent or not
            }
            if (!isOneway(messageInfo)) {
                sent = true;
                sendResponse(messageInfo, context);
            } else if (!sent) {
                // Oneway and request handler chain reversed the execution direction
                sent = true;
                sendResponseOneway(messageInfo);
            }
        } catch(Exception e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, e.getMessage(), e);
            if (!sent) {
                sendResponseError(messageInfo, e);
            }
        }
        assert sent;            // Make sure response is sent
    }

    protected abstract void toMessageInfo(MessageInfo messageInfo, XMLHandlerContext context);

    /*
     * Gets XMLMessage from the connection
     */
    private XMLMessage getXMLMessage(MessageInfo messageInfo) {
        WSConnection con = (WSConnection)messageInfo.getConnection();
        return XMLConnectionUtil.getXMLMessage(con, messageInfo);
    }

    /**
     * Invokes the endpoint
     *
     * In this case, Oneway is known only after invoking the endpoint. For other
     * endpoints, the HTTP response code is sent before invoking the endpoint.
     * This is taken care here after invoking the endpoint.
     */
    private void invokeEndpoint(MessageInfo messageInfo, XMLHandlerContext hc) {
        TargetFinder targetFinder =
            messageInfo.getEPTFactory().getTargetFinder(messageInfo);
        Tie tie = targetFinder.findTarget(messageInfo);
        tie._invoke(messageInfo);
    }

    protected XMLMessage getResponse(MessageInfo messageInfo, XMLHandlerContext context) {
        setResponseInContext(messageInfo, context);
        try {
            HandlerChainCaller handlerCaller =
                getCallerFromMessageInfo(messageInfo);
            if (handlerCaller != null && handlerCaller.hasHandlers()) {
                int messageType = messageInfo.getResponseType();
                if (messageType == MessageInfo.CHECKED_EXCEPTION_RESPONSE ||
                    messageType == MessageInfo.UNCHECKED_EXCEPTION_RESPONSE) {

                    callHandleFault(handlerCaller, context);
                } else {
                    callHandlersOnResponse(handlerCaller, context);
                }
            }
        } catch(Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            boolean useFastInfoset =
                messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY) == "optimistic";
            XMLMessage xmlMessage = new XMLMessage(e, useFastInfoset);
            context.setXMLMessage(xmlMessage);
        }
        // Create a new XMLMessage from existing message and OUTBOUND attachments property
        MessageContext msgCtxt = context.getMessageContext();
        Map<String, DataHandler> atts = (Map<String, DataHandler>)msgCtxt.get(
                MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);
        if (atts != null) {
            XMLMessage xmlMessage = context.getXMLMessage();
            Map<String, DataHandler> allAtts = xmlMessage.getAttachments();
            if (allAtts != null) {
                allAtts.putAll(atts);
            } else {
                allAtts = atts;
            }
            xmlMessage = new XMLMessage(xmlMessage.getSource(), allAtts,
                    xmlMessage.isFastInfoset());
            context.setXMLMessage(xmlMessage);
        }
        return context.getXMLMessage();
    }

    /*
     * MessageInfo contains the endpoint invocation results. The information
     * is converted to XMLMessage and is set in HandlerContext
     */
    protected abstract void setResponseInContext(MessageInfo messageInfo,
            XMLHandlerContext context);

    /**
     * Sends XMLMessage response on the connection
     */
    private void sendResponse(MessageInfo messageInfo, XMLHandlerContext ctxt)
    throws IOException {
        XMLMessage xmlMessage = ctxt.getXMLMessage();
        MessageContext msgCtxt = ctxt.getMessageContext();
        WSConnection con = messageInfo.getConnection();

        // See if MessageContext.HTTP_STATUS_CODE is present
        Integer status = MessageContextUtil.getHttpStatusCode(msgCtxt);
        int statusCode = (status == null) ? xmlMessage.getStatus() : status;


        Map<String, List<String>> headers = new HashMap<String, List<String>>();

        // put all headers from MessageContext.HTTP_RESPONSE_HEADERS
        Map<String, List<String>> ctxtHdrs = MessageContextUtil.getHttpResponseHeaders(msgCtxt);
        if (ctxtHdrs != null) {
            headers.putAll(ctxtHdrs);
        }
        // put all headers from XMLMessage
        MimeHeaders mhs = xmlMessage.getMimeHeaders();
        Iterator i = mhs.getAllHeaders();
        while (i.hasNext()) {
            MimeHeader mh = (MimeHeader) i.next();
            String name = mh.getName();
            List<String> values = headers.get(name);
            if (values == null) {
                values = new ArrayList<String>();
                headers.put(name, values);
            }
            values.add(mh.getValue());
        }

        // Set HTTP status code
        con.setStatus(statusCode);
        // put response headers on the connection
        con.setHeaders(headers);
        // Write contents on the connection
        xmlMessage.writeTo(con.getOutput());
        con.closeOutput();
    }

    protected void sendResponseOneway(MessageInfo messageInfo) {
        WSConnection con = (WSConnection)messageInfo.getConnection();
        XMLConnectionUtil.sendResponseOneway(con);
    }

    private void sendResponseError(MessageInfo messageInfo, Exception e) {
        logger.log(Level.SEVERE, e.getMessage(), e);
        WSConnection con = (WSConnection)messageInfo.getConnection();
        XMLConnectionUtil.sendResponseError(con, messageInfo);
    }



    /**
     * Calls inbound handlers. It also calls outbound handlers incase flow is
     * reversed. If the handler throws a ProtocolException, SOAP message is
     * already set in the context. Otherwise, it creates InternalMessage,
     * and that is used to create SOAPMessage.
     *
     * returns whether to invoke endpoint or not.
     */
    private boolean callHandlersOnRequest(MessageInfo messageInfo,
        XMLHandlerContext context, boolean responseExpected) {

        boolean skipEndpoint = false;
        HandlerChainCaller handlerCaller =
            getCallerFromMessageInfo(messageInfo);

        if (handlerCaller != null && handlerCaller.hasHandlers()) {
            skipEndpoint = !handlerCaller.callHandlers(Direction.INBOUND,
                RequestOrResponse.REQUEST, context, responseExpected);
        }
        return skipEndpoint;
    }

    /**
     * Use this to find out if handlers are there in the execution path or not
     *
     * @return true if there are handlers in execution path
     *         falst otherwise
     */
    private boolean hasHandlers(MessageInfo messageInfo) {
        HandlerChainCaller handlerCaller =
            getCallerFromMessageInfo(messageInfo);
        return (handlerCaller != null && handlerCaller.hasHandlers()) ? true : false;
    }

    private HandlerChainCaller getCallerFromMessageInfo(MessageInfo info) {
        HandlerChainCaller caller = MessageInfoUtil.getHandlerChainCaller(info);
        if (caller == null) {
            RuntimeContext context = (RuntimeContext)
                info.getMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);
            Binding binding = context.getRuntimeEndpointInfo().getBinding();
            caller = new HandlerChainCaller(binding.getHandlerChain());
            MessageInfoUtil.setHandlerChainCaller(info, caller);
        }
        return caller;
    }

    protected boolean callHandlersOnResponse(HandlerChainCaller caller,
        XMLHandlerContext context) {

        return caller.callHandlers(Direction.OUTBOUND,
            RequestOrResponse.RESPONSE, context, false);
    }

    /*
     * Used when the endpoint throws an exception. HandleFault is called
     * on the server handlers rather than handleMessage.
     */
    protected boolean  callHandleFault(HandlerChainCaller caller, XMLHandlerContext context) {
        /*
        return caller.callHandleFault(context);
         */
        return false;
    }

    /*
     * Server does not know if a message is one-way until after
     * the handler chain has finished processing the request. If
     * it is a one-way message, have the handler chain caller
     * call close on the handlers.
     */
    private void closeHandlers(MessageInfo info, XMLHandlerContext context) {
        HandlerChainCaller handlerCaller = getCallerFromMessageInfo(info);
        if (handlerCaller != null && handlerCaller.hasHandlers()) {
            handlerCaller.forceCloseHandlersOnServer(context);
        }
    }

    private static boolean isFailure(MessageInfo messageInfo) {
        return (messageInfo.getResponseType() == MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
    }

    public static boolean isOneway(MessageInfo messageInfo) {
        return (messageInfo.getMEP() == MessageStruct.ONE_WAY_MEP);
    }

    /*
     * Sets the WebServiceContext with correct MessageContext which contains
     * APPLICATION scope properties
     */
    protected void updateWebServiceContext(MessageInfo messageInfo, XMLHandlerContext hc) {
        RuntimeEndpointInfo endpointInfo =
            MessageInfoUtil.getRuntimeContext(messageInfo).getRuntimeEndpointInfo();
        WebServiceContext wsContext = endpointInfo.getWebServiceContext();
        if (wsContext != null) {
            AppMsgContextImpl appCtxt = new AppMsgContextImpl(hc.getMessageContext());
            wsContext.setMessageContext(appCtxt);
        }
    }

    /**
     * copy from message info to handler context
     */
    private void updateHandlerContext(MessageInfo messageInfo,
            XMLHandlerContext context) {

        RuntimeEndpointInfo endpointInfo =
            MessageInfoUtil.getRuntimeContext(messageInfo).getRuntimeEndpointInfo();
        WebServiceContext wsContext = endpointInfo.getWebServiceContext();
        if (wsContext != null) {
            context.setMessageContext(wsContext.getMessageContext());
        }
    }

    private SystemHandlerDelegate getSystemHandlerDelegate(MessageInfo mi) {
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(mi);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        return endpointInfo.getBinding().getSystemHandlerDelegate();
    }

    /**
     * This breaks the XMLMessage into source and attachments. A new XMLMessage
     * is created with source and is set in XMLHandlerContext. The attachments
     * are set in MessageContext as INBOUND attachments.
     */
    private void setInboundAttachments(MessageInfo messageInfo, XMLHandlerContext context) {
        if (hasHandlers(messageInfo)) {
            XMLMessage xmlMessage = context.getXMLMessage();
            Map<String, DataHandler> atts = xmlMessage.getAttachments();
            if (atts != null) {
                MessageContext msgCtxt = context.getMessageContext();
                msgCtxt.put(MessageContext.INBOUND_MESSAGE_ATTACHMENTS, atts);
                msgCtxt.setScope(MessageContext.INBOUND_MESSAGE_ATTACHMENTS, MessageContext.Scope.APPLICATION);
                xmlMessage = new XMLMessage(xmlMessage.getSource(), xmlMessage.isFastInfoset());
                context.setXMLMessage(xmlMessage);
            }
        }
    }

    /**
     * This creates a new XMLMessage from existing source and inbound attachments.
     * inbound attachements cannot be there if there are no handlers.
     * If the endpoint is Provider<Source>, it doesn't create a new message.
     */
    private void processInboundAttachments(MessageInfo messageInfo, XMLHandlerContext context) {
        if (hasHandlers(messageInfo)) {
            RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
            RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
            ProviderModel model = endpointInfo.getProviderModel();
            boolean isSource = model.isSource();
            if (!isSource) {
                XMLMessage xmlMessage = context.getXMLMessage();
                MessageContext msgCtxt = context.getMessageContext();
                Map<String, DataHandler> atts = (Map<String, DataHandler>)
                    msgCtxt.get(MessageContext.INBOUND_MESSAGE_ATTACHMENTS);
                xmlMessage = new XMLMessage(xmlMessage.getSource(), atts, xmlMessage.isFastInfoset());
                context.setXMLMessage(xmlMessage);
            }
        }
    }

    /**
     * Invokes request handler chain, endpoint and response handler chain.
     * Separated as a separate class, so that SHD can call this in doPriv()
     * block.
     */
    private class XmlInvoker implements Invoker {

        MessageInfo messageInfo;
        XMLMessage xmlMessage;
        XMLHandlerContext context;
        SystemHandlerDelegate shd;
        boolean sent;

        XmlInvoker(MessageInfo messageInfo, XMLMessage xmlMessage,
                XMLHandlerContext context, SystemHandlerDelegate shd) {
            this.messageInfo = messageInfo;
            this.xmlMessage = xmlMessage;
            this.context = context;
            this.shd = shd;
        }

        public void invoke() throws Exception {
            boolean skipEndpoint = false;

            // Sets INBOUND_MESSAGE_ATTACHMENTS in MessageContext
            setInboundAttachments(messageInfo, context);
            // Call inbound handlers. It also calls outbound handlers incase of
            // reversal of flow.
            skipEndpoint = callHandlersOnRequest(messageInfo, context, true);

            if (!skipEndpoint) {
                // new XMLMessage is created using INBOUND_MESSAGE_ATTACHMENTS
                // in MessageContext
                processInboundAttachments(messageInfo, context);
                toMessageInfo(messageInfo, context);
                if (!isFailure(messageInfo)) {
                    if (shd != null) {
                        shd.preInvokeEndpointHook(context.getSHDXMLMessageContext());
                    }
                    updateWebServiceContext(messageInfo, context);
                    invokeEndpoint(messageInfo, context);
                     // For Provider endpoints Oneway is known only after executing endpoint
                    if (!sent && isOneway(messageInfo)) {
                        sent = true;
                        sendResponseOneway(messageInfo);
                    }
                }

                if (isOneway(messageInfo)) {
                    if (isFailure(messageInfo)) {
                        // Just log the error. Not much to do
                    }
                } else {
                    updateHandlerContext(messageInfo, context);
                    xmlMessage = getResponse(messageInfo, context);
                    context.setXMLMessage(xmlMessage);
                }
            }
        }

        /**
         * Gets the dispatch method in the endpoint for the payload's QName
         *
         * @return dispatch method
         */
        public Method getMethod(QName name) {
            return ProviderPeptTie.invoke_Method;
        }

        /*
         * Is the message sent on transport. Happens when the operation is oneway
         *
         * @return true if the message is sent
         *        false otherwise
         */
        public boolean isSent() {
            return sent;
        }
    }
}
