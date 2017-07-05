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
package com.sun.xml.internal.ws.protocol.soap.server;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.pept.presentation.TargetFinder;
import com.sun.xml.internal.ws.pept.presentation.Tie;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.binding.soap.SOAPBindingImpl;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SOAPDecoder;
import com.sun.xml.internal.ws.encoding.soap.SOAPEPTFactory;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.handler.HandlerChainCaller;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.Direction;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.RequestOrResponse;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.handler.SOAPHandlerContext;
import com.sun.xml.internal.ws.model.soap.SOAPRuntimeModel;
import com.sun.xml.internal.ws.server.AppMsgContextImpl;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.server.RuntimeEndpointInfo;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.spi.runtime.Invoker;
import com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegate;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.spi.runtime.WebServiceContext;
import com.sun.xml.internal.ws.util.FastInfosetUtil;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPConnectionUtil;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Binding;
import javax.xml.ws.ProtocolException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.MessageContext.Scope;

import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.SOAPFaultException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.xml.internal.ws.client.BindingProviderProperties.CONTENT_NEGOTIATION_PROPERTY;

public class SOAPMessageDispatcher implements MessageDispatcher {

    private static final String[] contentTypes = {
            "text/xml", "application/soap+xml", "application/xop+xml",
            "application/fastinfoset", "application/soap+fastinfoset" };

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.soapmd");

    public SOAPMessageDispatcher() {
    }

    public void send(MessageInfo messageInfo) {
        // Not required for server
        throw new UnsupportedOperationException();
    }

    public void receive(MessageInfo messageInfo) {
        // Checks the Content-Type to send unsupported media error
        try {
            checkContentType(messageInfo);
        } catch(ServerRtException e) {
            SOAPConnectionUtil.sendKnownError(messageInfo,
                    WSConnection.UNSUPPORTED_MEDIA);
            return;
        }

        // Gets request stream from WSConnection and creates SOAP message
        SOAPMessage soapMessage = null;
        try {
            soapMessage = getSOAPMessage(messageInfo);
        } catch(Exception e) {
            sendResponseError(messageInfo, e);
            return;
        }

        // Set it before response is sent on transport. If transport creates
        // any exception, this can be used not to send again
        boolean sent = false;
        try {

            // Content negotiation logic
            try {
                // If FI is accepted by client, set property to optimistic
                if (((com.sun.xml.internal.messaging.saaj.soap.MessageImpl) soapMessage).acceptFastInfoset()) {
                    messageInfo.setMetaData(CONTENT_NEGOTIATION_PROPERTY, "optimistic");
                }
            }
            catch (ClassCastException e) {
                // Content negotiation fails
            }

            // context holds MessageInfo, InternalMessage, SOAPMessage
            SOAPHandlerContext context = new SOAPHandlerContext(messageInfo, null,
                soapMessage);
            // WebServiceContext's MessageContext is set into HandlerContext
            updateHandlerContext(messageInfo, context);
            context.getMessageContext().put(
                    MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.FALSE);
            //set MESSAGE_ATTACHMENTS property
            MessageContext msgCtxt = MessageInfoUtil.getMessageContext(messageInfo);
            if (msgCtxt != null) {
                MessageContextUtil.copyInboundMessageAttachments(msgCtxt, soapMessage.getAttachments());
            }
            SystemHandlerDelegate shd = getSystemHandlerDelegate(messageInfo);
            SoapInvoker implementor = new SoapInvoker(messageInfo, soapMessage,
                context, shd);
            try {
                if (shd == null) {
                    // Invokes request handler chain, endpoint, response handler chain
                    implementor.invoke();
                } else {
                    context.setInvoker(implementor);
                    if (shd.processRequest(context.getSHDSOAPMessageContext())) {
                        implementor.invoke();
                        context.getMessageContext().put(
                            MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.TRUE);
                        shd.processResponse(context.getSHDSOAPMessageContext());
                    }
                }
            } finally {
                sent = implementor.isSent();    // response is sent or not
            }
            if (!isOneway(messageInfo)) {
                makeSOAPMessage(messageInfo, context);
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

    /*
     * This decodes the SOAPMessage into InternalMessage. Then InternalMessage
     * is converted to java method and parameters and populates them into
     * MessageInfo.
     *
     */
    protected void toMessageInfo(MessageInfo messageInfo, SOAPHandlerContext context) {
        InternalMessage internalMessage = context.getInternalMessage();
        try {
            SOAPMessage soapMessage = context.getSOAPMessage();
            if (internalMessage == null) {
                // Bind headers, body from SOAPMessage
                SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
                SOAPDecoder decoder = eptf.getSOAPDecoder();
                internalMessage = decoder.toInternalMessage(soapMessage, messageInfo);
            } else {
                // Bind headers from SOAPMessage
                SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
                SOAPDecoder decoder = eptf.getSOAPDecoder();
                internalMessage = decoder.toInternalMessage(soapMessage, internalMessage, messageInfo);
            }
            //setup JAXWSAttachmentMarshaller for outgoing attachments
            SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
            SOAPEncoder encoder = eptf.getSOAPEncoder();
            encoder.setAttachmentsMap(messageInfo, internalMessage);

        } catch(Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
            messageInfo.setResponse(e);
        }
        // InternalMessage to MessageInfo
        if (!isFailure(messageInfo)) {
            SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
            eptf.getInternalEncoder().toMessageInfo(internalMessage, messageInfo);
            Binding binding = MessageInfoUtil.getRuntimeContext(messageInfo).getRuntimeEndpointInfo().getBinding();
            String bindingId = (binding != null)?((SOAPBindingImpl)binding).getBindingId():SOAPBinding.SOAP11HTTP_BINDING;

            if (messageInfo.getMethod() == null) {
                messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
                SOAPFaultInfo faultInfo = new SOAPFaultInfo(
                            "Cannot find dispatch method",
                            SOAPConstants.FAULT_CODE_SERVER,
                            null, null, bindingId);
                messageInfo.setResponse(faultInfo);
            }
        }
    }

    /*
     * Creates SOAPMessage from the connection's request stream
     */
    private SOAPMessage getSOAPMessage(MessageInfo messageInfo) {
        WSConnection con = (WSConnection)messageInfo.getConnection();
        return SOAPConnectionUtil.getSOAPMessage(con, messageInfo, null);
    }

    /*
     * Checks against known Content-Type headers
     */
    private void checkContentType(MessageInfo mi) {
        WSConnection con = (WSConnection)mi.getConnection();
        Map<String, List<String>> headers = con.getHeaders();
        List<String> cts = headers.get("Content-Type");
        if (cts != null && cts.size() > 0) {
            String ct = cts.get(0);
            for(String contentType : contentTypes) {
                if (ct.indexOf(contentType) != -1) {
                    return;
                }
            }
        }
        throw new ServerRtException("Incorrect Content-Type="+cts);
    }

    /*
     * Sets the WebServiceContext with correct MessageContext which contains
     * APPLICATION scope properties
     */
    protected void updateWebServiceContext(MessageInfo messageInfo, SOAPHandlerContext hc) {
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
        //rtCtxt.setHandlerContext(hc);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        WebServiceContext wsContext = endpointInfo.getWebServiceContext();
        hc.getMessageContext().put(CONTENT_NEGOTIATION_PROPERTY,
                messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY));
        hc.getMessageContext().setScope(CONTENT_NEGOTIATION_PROPERTY, Scope.APPLICATION);
        if (wsContext != null) {
            AppMsgContextImpl appCtxt = new AppMsgContextImpl(hc.getMessageContext());
            wsContext.setMessageContext(appCtxt);
        }
    }

    /*
     * Invokes the endpoint. For Provider endpoints, whether the operation is
     * oneway or not known only after the endpoint is invoked. ProviderSOAPMD
     * overrides this and sends the response message on transport for oneway
     * operations.
     *
     * @return true if response is sent on transport
     */
    protected void invokeEndpoint(MessageInfo messageInfo, SOAPHandlerContext hc) {
        TargetFinder targetFinder =
            messageInfo.getEPTFactory().getTargetFinder(messageInfo);
        Tie tie = targetFinder.findTarget(messageInfo);
        tie._invoke(messageInfo);
    }

    /**
     * Converts java method parameters, and return value to InternalMessage.
     * It calls response handlers. At the end, the context has either
     * InternalMessage or SOAPMessage
     *
     */
    protected void getResponse(MessageInfo messageInfo, SOAPHandlerContext context) {
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
                    //there are handlers so disable Xop encoding if enabled, so that they dont
                    // see xop:Include reference
                    JAXWSAttachmentMarshaller am = MessageInfoUtil.getAttachmentMarshaller(messageInfo);
                    boolean isXopped = false;
                    if((am != null) && am.isXOPPackage()){
                        isXopped = am.isXOPPackage();
                        am.setXOPPackage(false);
                    }
                    callHandlersOnResponse(handlerCaller, context);
                    // now put back the old value
                    if((am != null)){
                        am.setXOPPackage(isXopped);
                    }
                }
            }
        } catch(Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            createInternalMessageForException(messageInfo, e, context);
        }
    }

    /*
     *
     *
     *
     */
    private boolean createInternalMessageForException(MessageInfo messageInfo,
            Exception e, SOAPHandlerContext context) {
        boolean soap12 = false;
        RuntimeEndpointInfo rei = MessageInfoUtil.getRuntimeContext(
            messageInfo).getRuntimeEndpointInfo();
        String id = ((SOAPBindingImpl)rei.getBinding()).getBindingId();
        InternalMessage internalMessage = null;
        if (id.equals(SOAPBinding.SOAP11HTTP_BINDING)) {
            internalMessage = SOAPRuntimeModel.createFaultInBody(
                e, null, null, null);
        } else if (id.equals(SOAPBinding.SOAP12HTTP_BINDING)) {
            internalMessage = SOAPRuntimeModel.createSOAP12FaultInBody(
                e, null, null, null, null);
            soap12 = true;
        }
        context.setInternalMessage(internalMessage);
        context.setSOAPMessage(null);
        return soap12;
    }

    private void makeSOAPMessage(MessageInfo messageInfo, SOAPHandlerContext context) {
        InternalMessage internalMessage = context.getInternalMessage();
        if (internalMessage != null) {
            SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
            SOAPEncoder encoder = eptf.getSOAPEncoder();
            SOAPMessage soapMesage = encoder.toSOAPMessage(internalMessage, messageInfo);
            context.setSOAPMessage(soapMesage);
            context.setInternalMessage(null);
        }
    }

    /*
     * MessageInfo contains the endpoint invocation results. The information
     * is converted to InternalMessage or SOAPMessage and set in HandlerContext
     */
    protected void setResponseInContext(MessageInfo messageInfo,
            SOAPHandlerContext context) {
        // MessageInfo to InternalMessage
        SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
        InternalMessage internalMessage = (InternalMessage)eptf.getInternalEncoder().toInternalMessage(
                messageInfo);
        // set handler context
        context.setInternalMessage(internalMessage);
        context.setSOAPMessage(null);
    }

    /*
     * Sends SOAPMessage response on the connection
     */
    private void sendResponse(MessageInfo messageInfo, SOAPHandlerContext ctxt) {
        SOAPMessage soapMessage = ctxt.getSOAPMessage();
        WSConnection con = (WSConnection)messageInfo.getConnection();
        Integer status = MessageContextUtil.getHttpStatusCode(ctxt.getMessageContext());
        int statusCode = (status == null) ? WSConnection.OK : status;
        SOAPConnectionUtil.setStatus(con, statusCode);
        SOAPConnectionUtil.sendResponse(con, soapMessage);
    }

    protected void sendResponseOneway(MessageInfo messageInfo) {
        SOAPConnectionUtil.sendResponseOneway(messageInfo);
    }

    private void sendResponseError(MessageInfo messageInfo, Exception e) {
        e.printStackTrace();
        WSConnection con = (WSConnection)messageInfo.getConnection();
        Binding binding = MessageInfoUtil.getRuntimeContext(messageInfo).getRuntimeEndpointInfo().getBinding();
        String bindingId = ((SOAPBindingImpl)binding).getBindingId();
        SOAPConnectionUtil.sendResponseError(con, bindingId);
    }

    /*
     * Calls inbound handlers. It also calls outbound handlers incase flow is
     * reversed. If the handler throws a ProtocolException, SOAP message is
     * already set in the context. Otherwise, it creates InternalMessage,
     * and that is used to create SOAPMessage.
     *
     * returns whether to invoke endpoint or not.
     */
    private boolean callHandlersOnRequest(MessageInfo messageInfo,
        SOAPHandlerContext context, boolean responseExpected) {

        boolean skipEndpoint = false;
        HandlerChainCaller handlerCaller =
            getCallerFromMessageInfo(messageInfo);

        if (handlerCaller != null && handlerCaller.hasHandlers()) {
            try {
                skipEndpoint = !handlerCaller.callHandlers(Direction.INBOUND,
                    RequestOrResponse.REQUEST, context, responseExpected);
            } catch(ProtocolException pe) {
                skipEndpoint = true;
                if (MessageContextUtil.ignoreFaultInMessage(
                    context.getMessageContext())) {
                    // don't use the fault, use the exception
                    createInternalMessageForException(messageInfo, pe, context);
                }
            } catch(RuntimeException re) {
                skipEndpoint = true;
                createInternalMessageForException(messageInfo, re, context);
            }
        }
        return skipEndpoint;
    }

    private HandlerChainCaller getCallerFromMessageInfo(MessageInfo info) {
        RuntimeContext context = (RuntimeContext) info.getMetaData(
                BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);
        BindingImpl binding = (BindingImpl)context.getRuntimeEndpointInfo().getBinding();
        if (binding.hasHandlers()) {
            HandlerChainCaller caller = binding.getHandlerChainCaller();
            MessageInfoUtil.setHandlerChainCaller(info, caller);
            return caller;
        }
        return null;
    }

    /**
     *
     * Invokes response handler chain
     */
    protected boolean callHandlersOnResponse(HandlerChainCaller caller,
        SOAPHandlerContext context) {

        return caller.callHandlers(Direction.OUTBOUND,
            RequestOrResponse.RESPONSE, context, false);
    }

    /**
     * Used when the endpoint throws an exception. HandleFault is called
     * on the server handlers rather than handleMessage.
     */
    protected boolean  callHandleFault(HandlerChainCaller caller, SOAPHandlerContext context) {
        return caller.callHandleFault(context);
    }

    /**
     * Server does not know if a message is one-way until after
     * the handler chain has finished processing the request. If
     * it is a one-way message, have the handler chain caller
     * call close on the handlers.
     */
    private void closeHandlers(MessageInfo info, SOAPHandlerContext context) {
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


    /**
     * Sets MessageContext into HandlerContext and sets HandlerContext in
     * RuntimeContext
     */
    private void updateHandlerContext(MessageInfo messageInfo,
            SOAPHandlerContext context) {
        MessageInfoUtil.getRuntimeContext(messageInfo).setHandlerContext(context);
        RuntimeEndpointInfo endpointInfo =
            MessageInfoUtil.getRuntimeContext(messageInfo).getRuntimeEndpointInfo();
        context.setBindingId(((BindingImpl)endpointInfo.getBinding()).getActualBindingId());
        WebServiceContext wsContext = endpointInfo.getWebServiceContext();
        if (wsContext != null) {
            context.setMessageContext(wsContext.getMessageContext());
        }
    }

    /**
     * Gets SystemHandlerDelegate from endpoint's Binding
     */
    private SystemHandlerDelegate getSystemHandlerDelegate(MessageInfo mi) {
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(mi);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        return endpointInfo.getBinding().getSystemHandlerDelegate();
    }

    /**
     * Invokes request handler chain, endpoint and response handler chain.
     * Separated as a separate class, so that SHD can call this in doPriv()
     * block.
     */
    private class SoapInvoker implements Invoker {

        MessageInfo messageInfo;
        SOAPMessage soapMessage;
        SOAPHandlerContext context;
        boolean skipEndpoint;
        SystemHandlerDelegate shd;
        boolean sent;

        SoapInvoker(MessageInfo messageInfo, SOAPMessage soapMessage,
                SOAPHandlerContext context, SystemHandlerDelegate shd) {
            this.messageInfo = messageInfo;
            this.soapMessage = soapMessage;
            this.context = context;
            this.shd = shd;
        }

        public void invoke() throws Exception {
            boolean peekOneWay = false;
            if (!skipEndpoint) {
                try {
                    SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
                    SOAPDecoder decoder = eptf.getSOAPDecoder();

                    // add handler chain caller to message info
                    getCallerFromMessageInfo(messageInfo);
                    peekOneWay = decoder.doMustUnderstandProcessing(soapMessage,
                            messageInfo, context, true);
                    context.setMethod(messageInfo.getMethod());
                } catch (SOAPFaultException e) {
                    skipEndpoint = true;
                    boolean soap12 = createInternalMessageForException(messageInfo, e, context);
                    SOAPRuntimeModel.addHeaders(context.getInternalMessage(),
                            messageInfo);
                }
            }

            // Call inbound handlers. It also calls outbound handlers incase of
            // reversal of flow.
            if (!skipEndpoint) {
                skipEndpoint = callHandlersOnRequest(
                    messageInfo, context, !peekOneWay);
            }

            if (skipEndpoint) {
                soapMessage = context.getSOAPMessage();
                if (soapMessage == null) {
                    InternalMessage internalMessage = context.getInternalMessage();
                    SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
                    SOAPEncoder encoder = eptf.getSOAPEncoder();
                    soapMessage = encoder.toSOAPMessage(internalMessage, messageInfo);
                }

                // Ensure message is encoded according to conneg
                FastInfosetUtil.ensureCorrectEncoding(messageInfo, soapMessage);

                context.setSOAPMessage(soapMessage);
                context.setInternalMessage(null);
            } else {
                toMessageInfo(messageInfo, context);

                if (isOneway(messageInfo)) {
                    sent = true;
                    sendResponseOneway(messageInfo);
                    if (!peekOneWay) { // handler chain didn't already clos
                        closeHandlers(messageInfo, context);
                    }
                }

                if (!isFailure(messageInfo)) {
                    if (shd != null) {
                        shd.preInvokeEndpointHook(context.getSHDSOAPMessageContext());
                    }
                    updateWebServiceContext(messageInfo, context);
                    invokeEndpoint(messageInfo, context);
                    // For Provider endpoints Oneway is known only after executing endpoint
                    if (!sent && isOneway(messageInfo)) {
                        sent = true;
                        sendResponseOneway(messageInfo);
                    }
                    context.getMessageContext().put(
                        MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.TRUE);
                }

                if (isOneway(messageInfo)) {
                    if (isFailure(messageInfo)) {
                        // Just log the error. Not much to do
                    }
                } else {
                    getResponse(messageInfo, context);
                }
            }
        }

        /**
         * Gets the dispatch method in the endpoint for the payload's QName
         *
         * @return dispatch method
         */
        public Method getMethod(QName name) {
            RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
            return rtCtxt.getDispatchMethod(name, messageInfo);
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
