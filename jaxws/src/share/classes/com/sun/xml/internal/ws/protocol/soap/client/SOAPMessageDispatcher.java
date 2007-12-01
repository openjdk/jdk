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
package com.sun.xml.internal.ws.protocol.soap.client;

import com.sun.xml.internal.messaging.saaj.soap.MessageImpl;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.AsyncHandlerService;
import com.sun.xml.internal.ws.client.ContextMap;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import static com.sun.xml.internal.ws.client.BindingProviderProperties.*;
import com.sun.xml.internal.ws.client.EndpointIFInvocationHandler;
import com.sun.xml.internal.ws.client.EndpointIFContext;
import com.sun.xml.internal.ws.client.RequestContext;
import com.sun.xml.internal.ws.client.ResponseContext;
import com.sun.xml.internal.ws.client.WSFuture;
import com.sun.xml.internal.ws.client.dispatch.DispatchContext;
import com.sun.xml.internal.ws.client.dispatch.ResponseImpl;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLEncoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLDecoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLEncoder;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.internal.MessageInfoBase;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.handler.HandlerChainCaller;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.Direction;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.RequestOrResponse;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.handler.SOAPHandlerContext;
import com.sun.xml.internal.ws.model.JavaMethod;
import com.sun.xml.internal.ws.model.RuntimeModel;
import com.sun.xml.internal.ws.pept.ept.EPTFactory;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.spi.runtime.ClientTransportFactory;
import com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegate;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.transport.http.client.HttpClientTransportFactory;
import com.sun.xml.internal.ws.util.Base64Util;
import com.sun.xml.internal.ws.util.FastInfosetUtil;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPConnectionUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.wsdl.parser.BindingOperation;
import javax.activation.DataHandler;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.ws.Binding;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.ProtocolException;
import javax.xml.ws.Service;
import javax.xml.ws.Response;
import javax.xml.ws.WebServiceException;
import static javax.xml.ws.BindingProvider.PASSWORD_PROPERTY;
import static javax.xml.ws.BindingProvider.USERNAME_PROPERTY;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.SOAPFaultException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Client-side SOAP protocol-specific {@link com.sun.pept.protocol.MessageDispatcher}
 *
 * @author WS Development Team
 */
public class SOAPMessageDispatcher implements MessageDispatcher {

    protected static final int MAX_THREAD_POOL_SIZE = 3;

    protected static final long AWAIT_TERMINATION_TIME = 10L;

    protected ExecutorService executorService;

    private final static String MUST_UNDERSTAND_FAULT_MESSAGE_STRING = "SOAP must understand error";

    /**
     * Default constructor
     */
    public SOAPMessageDispatcher() {
    }

    /*
     * Invokes doSendAsync method if the message exchange pattern is asynchronous, otherwise
     * invokes doSend method.
     *
     * @see com.sun.pept.protocol.MessageDispatcher#send(com.sun.pept.ept.MessageInfo)
     */
    public void send(MessageInfo messageInfo) {
        if (isAsync(messageInfo)) {
            doSendAsync(messageInfo);
        } else {
            doSend(messageInfo);
        }
    }

    /**
     * Orchestrates the sending of a synchronous request
     */
    protected SOAPMessage doSend(MessageInfo messageInfo) {
        //change from LogicalEPTFactory to ContactInfoBase - should be changed back when we have things working
        EPTFactory contactInfo = messageInfo.getEPTFactory();
        SOAPXMLEncoder encoder = (SOAPXMLEncoder) contactInfo.getEncoder(messageInfo);
        SOAPMessage sm = null;
        boolean handlerResult = true;
        boolean isRequestResponse = (messageInfo.getMEP() == MessageStruct.REQUEST_RESPONSE_MEP);

        try {
            if (messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) == Service.Mode.MESSAGE)
            {
                sm = (SOAPMessage) messageInfo.getData()[0];
                // Ensure supplied message is encoded according to conneg
                FastInfosetUtil.ensureCorrectEncoding(messageInfo, sm);
            }

            SOAPHandlerContext handlerContext = null;
            InternalMessage im = encoder.toInternalMessage(messageInfo);

            HandlerChainCaller caller = getHandlerChainCaller(messageInfo);
            if (caller.hasHandlers()) {
                im = preHandlerOutboundHook(sm, im);
                handlerContext = new SOAPHandlerContext(messageInfo, im, sm);

                //this is needed so that attachments are compied from RESPONSE_MESSAGE_ATTACHMEMTN PROPERTY
                handlerContext.getMessageContext().put(
                    MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.TRUE);

                encoder.setAttachmentsMap(messageInfo, im);
                updateMessageContext(messageInfo, handlerContext);

                //now that the MESSAGE_OUTBOUND_PROPERTY is set so populate the attachemnts
                handlerContext.populateAttachmentMap();

                JAXWSAttachmentMarshaller am = MessageInfoUtil.getAttachmentMarshaller(messageInfo);
                boolean isXopped = false;
                //there are handlers so disable Xop encoding if enabled, so that they dont
                // see xop:Include reference
                if ((am != null) && am.isXOPPackage()) {
                    isXopped = am.isXOPPackage();
                    am.setXOPPackage(false);
                }
                handlerResult = callHandlersOnRequest(handlerContext);
                // now put back the old value
                if ((am != null)) {
                    am.setXOPPackage(isXopped);
                }

                sm = handlerContext.getSOAPMessage();
                postHandlerOutboundHook(messageInfo, handlerContext, sm);
                if (sm == null) {
                    sm = encoder.toSOAPMessage(handlerContext.getInternalMessage(), messageInfo);
                }

                // the only case where no message is sent
                if (isRequestResponse && !handlerResult) {
                    SOAPXMLDecoder decoder = (SOAPXMLDecoder) contactInfo.getDecoder(messageInfo);
                    im = decoder.toInternalMessage(sm, messageInfo);
                    decoder.toMessageInfo(im, messageInfo);
                    return sm;
                }
            }

            // Setting encoder here is necessary for calls to getBindingId()
            messageInfo.setEncoder(encoder);

            SystemHandlerDelegate systemHandlerDelegate =
                ((com.sun.xml.internal.ws.spi.runtime.Binding) getBinding(messageInfo)).
                    getSystemHandlerDelegate();
            if (systemHandlerDelegate != null) {
                if (handlerContext == null) {
                    handlerContext = new SOAPHandlerContext(messageInfo, im, sm);
                    //this is needed so that attachments are compied from RESPONSE_MESSAGE_ATTACHMEMTN PROPERTY
                    handlerContext.getMessageContext().put(
                        MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.TRUE);

                    //now that the MESSAGE_OUTBOUND_PROPERTY is set so populate the attachemnts
                    handlerContext.populateAttachmentMap();

                    updateMessageContext(messageInfo, handlerContext);
                }

                encoder.setAttachmentsMap(messageInfo, im);
                //already used im, we can set that to null
                if ((sm != null) && (im != null))
                    handlerContext.setInternalMessage(null);


                handlerContext.getBindingId();
                systemHandlerDelegate.processRequest(
                    handlerContext.getSHDSOAPMessageContext());
                sm = handlerContext.getSOAPMessage();
            }

            if (sm == null){
                sm = encoder.toSOAPMessage(im, messageInfo);
                if (handlerContext == null)
                    handlerContext = new SOAPHandlerContext(messageInfo, im, sm);
                updateMessageContext(messageInfo, handlerContext);
            }


            Map<String, Object> context = processMetadata(messageInfo, sm);

            setConnection(messageInfo, context);

            if (!isAsync(messageInfo)) {
                WSConnection connection = (WSConnection) messageInfo.getConnection();

                logRequestMessage(sm, messageInfo);
                SOAPConnectionUtil.sendResponse(connection, sm);
            }

            if (isRequestResponse) {
                receive(messageInfo);
            } else if (isOneway(messageInfo)) {
                checkReturnStatus(messageInfo);
            }
        } catch (WebServiceException wse) {
            setResponseType(wse, messageInfo);
            messageInfo.setResponse(wse);
        } catch (Throwable e) {
            RuntimeException ex = (RuntimeException) e;
            setResponseType(ex, messageInfo);
            messageInfo.setResponse(ex);
        }
        return sm;
    }


    private boolean isOneway(MessageInfo messageInfo) {
        return messageInfo.getMEP() == MessageStruct.ONE_WAY_MEP ? true : false;
    }

    /**
     * Process and classify the metadata in MIME headers or message context. <String,String> data
     * is copied into MIME headers and the remaining metadata is passed in message context to the
     * transport layer.
     *
     * @param messageInfo
     * @param soapMessage
     */
    protected Map<String, Object> processMetadata(MessageInfo messageInfo, SOAPMessage soapMessage) {
        Map<String, Object> messageContext = new HashMap<String, Object>();
        List<String> header = new ArrayList<String>();

        ContextMap properties = (ContextMap) messageInfo.getMetaData(JAXWS_CONTEXT_PROPERTY);

        if (messageInfo.getMEP() == MessageStruct.ONE_WAY_MEP)
            messageContext.put(ONE_WAY_OPERATION, "true");

        String soapAction = null;
        boolean useSoapAction = false;

        // process the properties
        if (properties != null) {
            for (Iterator names = properties.getPropertyNames(); names.hasNext();)
            {
                String propName = (String) names.next();

                // consume PEPT-specific properties
                if (propName.equals(ClientTransportFactory.class.getName())) {
                    messageContext.put(CLIENT_TRANSPORT_FACTORY, (ClientTransportFactory) properties.get(propName));
                } else if (propName.equals(USERNAME_PROPERTY)) {
                    String credentials = (String) properties.get(USERNAME_PROPERTY);
                    if (credentials != null) {
                        credentials += ":";
                        String password = (String) properties.get(PASSWORD_PROPERTY);
                        if (password != null)
                            credentials += password;

                        try {
                            credentials = Base64Util.encode(credentials.getBytes());
                        } catch (Exception ex) {
                            throw new WebServiceException(ex);
                        }
                        soapMessage.getMimeHeaders().addHeader("Authorization", "Basic " + credentials);
                    }
                } else
                if (propName.equals(BindingProvider.SOAPACTION_USE_PROPERTY)) {
                    useSoapAction = ((Boolean)
                        properties.get(BindingProvider.SOAPACTION_USE_PROPERTY)).booleanValue();
                    if (useSoapAction)
                        soapAction = (String)
                            properties.get(BindingProvider.SOAPACTION_URI_PROPERTY);
                } else {
                    messageContext.put(propName, properties.get(propName));
                }
            }
        }

        // Set accept header depending on content negotiation property
        String contentNegotiation = (String) messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY);

        String bindingId = getBindingId(messageInfo);
        if (bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING) || bindingId.equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING))
        {
            soapMessage.getMimeHeaders().setHeader(ACCEPT_PROPERTY,
                contentNegotiation != "none" ? SOAP12_XML_FI_ACCEPT_VALUE : SOAP12_XML_ACCEPT_VALUE);
        } else {
            soapMessage.getMimeHeaders().setHeader(ACCEPT_PROPERTY,
                contentNegotiation != "none" ? XML_FI_ACCEPT_VALUE : XML_ACCEPT_VALUE);
        }

        messageContext.put(BINDING_ID_PROPERTY, bindingId);

        // SOAPAction: MIME header
        RuntimeContext runtimeContext = (RuntimeContext) messageInfo.getMetaData(JAXWS_RUNTIME_CONTEXT);
        if (runtimeContext != null) {
            JavaMethod javaMethod = runtimeContext.getModel().getJavaMethod(messageInfo.getMethod());
            if (javaMethod != null) {
                soapAction = ((com.sun.xml.internal.ws.model.soap.SOAPBinding) javaMethod.getBinding()).getSOAPAction();
                header.clear();
                if (bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING) || bindingId.equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING))
                {
                    if ((soapAction != null) && (soapAction.length() > 0)) {
                        ((MessageImpl) soapMessage).setAction(soapAction);
                    }
                } else {
                    if (soapAction == null) {
                        soapMessage.getMimeHeaders().addHeader("SOAPAction", "\"\"");
                    } else {
                        soapMessage.getMimeHeaders().addHeader("SOAPAction", "\"" + soapAction + "\"");
                    }
                }
            }
        } else
        if (messageInfo.getMetaData(BindingProviderProperties.DISPATCH_CONTEXT) != null)
        {
            //bug fix 6344358
            header.clear();
            if (bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING) || bindingId.equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING))
            {
                if ((soapAction != null) && (soapAction.length() > 0)) {
                    ((MessageImpl) soapMessage).setAction(soapAction);
                }
            } else {
                if (soapAction == null) {
                    soapMessage.getMimeHeaders().addHeader("SOAPAction", "\"\"");
                } else {
                    soapMessage.getMimeHeaders().addHeader("SOAPAction", "\"" + soapAction + "\"");
                }
            }
        }

        return messageContext;
    }

    protected void setConnection(MessageInfo messageInfo, Map<String, Object> context) {
        ClientTransportFactory clientTransportFactory = (ClientTransportFactory) context.get(CLIENT_TRANSPORT_FACTORY);
        WSConnection connection = null;
        if (clientTransportFactory == null) {
            clientTransportFactory = new HttpClientTransportFactory();
            context.put(CLIENT_TRANSPORT_FACTORY, clientTransportFactory);
        }
        connection = clientTransportFactory.create(context);
        messageInfo.setConnection(connection);
    }

    protected void setResponseType(Throwable e, MessageInfo messageInfo) {
        if (e instanceof RuntimeException) {
            messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
        } else {
            messageInfo.setResponseType(MessageStruct.CHECKED_EXCEPTION_RESPONSE);
        }
        messageInfo.setResponse(e);
    }

    public void checkReturnStatus(MessageInfo messageInfo) {
        WSConnection connection = (WSConnection) messageInfo.getConnection();
        Map<String, List<String>> headers = connection.getHeaders();
        if (connection.getStatus() != 202 && connection.getStatus() != 200) {
            Logger.getAnonymousLogger().log(Level.SEVERE, "HTTP status code for oneway: expected 202 or 200, got " + connection.getStatus());
//            System.out.println("status: "+connection.getStatus());
        }
    }

    /*
    * Orchestrates the receiving of a synchronous response
    *
    * @see com.sun.pept.protocol.MessageDispatcher#receive(com.sun.pept.ept.MessageInfo)
    *
    * todo: exception handling with possible saaj error below
    */
    public void receive(MessageInfo messageInfo) {
        // change from LogicalEPTFactory to ContactInfoBase - should be changed back when we have things working
        EPTFactory contactInfo = messageInfo.getEPTFactory();
        //LogicalEPTFactory contactInfo = (LogicalEPTFactory) messageInfo.getEPTFactory();

        SOAPXMLDecoder decoder = (SOAPXMLDecoder) contactInfo.getDecoder(messageInfo);


        SOAPMessage sm = null;
        try {
            sm = decoder.toSOAPMessage(messageInfo);
        } catch (RuntimeException e) {
            //if there is a transport error HTTP status code and response Headers
            //need to be populated into messageContext and requestContext
            //bug 6373688
            setContexts(messageInfo, sm);
            throw e;
        }
        // Content negotiation logic
        String contentNegotiationType = (String) messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY);
        // If XML request
        if (contentNegotiationType == "pessimistic") {
            try {
                if (((com.sun.xml.internal.messaging.saaj.soap.MessageImpl) sm).isFastInfoset())
                {
                    Map requestContext = (Map) messageInfo.getMetaData(JAXWS_CONTEXT_PROPERTY);
                    // Further requests will be send using FI
                    requestContext.put(CONTENT_NEGOTIATION_PROPERTY, "optimistic");
                }
            }
            catch (ClassCastException e) {
                // Content negotiation fails
            }
        }

        try {
            logResponseMessage(sm, messageInfo);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        SOAPHandlerContext handlerContext = getInboundHandlerContext(messageInfo, sm);
        WSConnection con = (WSConnection) messageInfo.getConnection();
        MessageContextUtil.setHttpStatusCode(handlerContext.getMessageContext(),
            con.getStatus());
        MessageContextUtil.setHttpResponseHeaders(handlerContext.getMessageContext(),
            con.getHeaders());

        //set the handlerContext to RuntimeContext
        RuntimeContext rtContext = MessageInfoUtil.getRuntimeContext(messageInfo);
        if (rtContext != null)
            rtContext.setHandlerContext(handlerContext);

        handlerContext.getMessageContext().put(
            MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.FALSE);
        //set MESSAGE_ATTACHMENTS property
        MessageContext msgCtxt = MessageInfoUtil.getMessageContext(messageInfo);
        if (msgCtxt != null) {
            try {
                //clear the attMap on this messageContext, its from request
                Map<String, DataHandler> attMap = (Map<String, DataHandler>) msgCtxt.get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);
                if (attMap != null)
                    attMap.clear();
                MessageContextUtil.copyInboundMessageAttachments(msgCtxt, sm.getAttachments());
            } catch (SOAPException e) {
                throw new RuntimeException(e);
            }
        }

        SystemHandlerDelegate systemHandlerDelegate =
            ((com.sun.xml.internal.ws.spi.runtime.Binding) getBinding(messageInfo)).
                getSystemHandlerDelegate();
        if (systemHandlerDelegate != null) {
//            handlerContext.getMessageContext().put(
//                MessageContext.MESSAGE_OUTBOUND_PROPERTY, Boolean.FALSE);
            try {
                systemHandlerDelegate.processResponse(handlerContext.getSHDSOAPMessageContext());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try {
            MessageInfoUtil.setHandlerChainCaller(messageInfo,
                getHandlerChainCaller(messageInfo));
            decoder.doMustUnderstandProcessing(sm, messageInfo, handlerContext, false);
        } catch (SOAPException se) { // unusual saaj error
            throw new RuntimeException(se);
        } catch (IOException ie) { // unusual saaj error
            throw new RuntimeException(ie);
        } catch (SOAPFaultException sfe) {
            closeAllHandlers(handlerContext);
            throw sfe;
        }

        HandlerChainCaller caller = getHandlerChainCaller(messageInfo);
        if (caller.hasHandlers()) {
            callHandlersOnResponse(handlerContext);
            postHandlerInboundHook(messageInfo, handlerContext, sm);
        }

        SOAPXMLEncoder encoder = (SOAPXMLEncoder) contactInfo.getEncoder(messageInfo);

        InternalMessage im = handlerContext.getInternalMessage();
        if (messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) == Service.Mode.MESSAGE)
            im = null;
        if (im == null) {
            im = decoder.toInternalMessage(sm, messageInfo);
        } else {
            im = decoder.toInternalMessage(sm, im, messageInfo);
        }
        decoder.toMessageInfo(im, messageInfo);
        updateResponseContext(messageInfo, handlerContext);
        if (messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) == Service.Mode.MESSAGE)
        {
            messageInfo.setResponse(sm);
            postReceiveAndDecodeHook(messageInfo);
        }
    }

    private void setContexts(MessageInfo messageInfo, SOAPMessage sm) {
        WSConnection con = (WSConnection) messageInfo.getConnection();
        SOAPHandlerContext handlerContext = getInboundHandlerContext(messageInfo, sm);
        MessageContextUtil.setHttpStatusCode(handlerContext.getMessageContext(),
            con.getStatus());
        updateResponseContext(messageInfo, handlerContext);
    }


    private SOAPHandlerContext getInboundHandlerContext(MessageInfo messageInfo, SOAPMessage sm) {
        SOAPHandlerContext handlerContext = (SOAPHandlerContext) messageInfo
            .getMetaData(BindingProviderProperties.JAXWS_HANDLER_CONTEXT_PROPERTY);
        if (handlerContext != null) {
            handlerContext.setSOAPMessage(sm);
            handlerContext.setInternalMessage(null);
        } else
            handlerContext = new SOAPHandlerContext(messageInfo, null, sm);
        return handlerContext;
    }

    /**
     * Orchestrates the sending of an asynchronous request
     */
    protected void doSendAsync(final MessageInfo messageInfo) {
        try { // should have already been caught
            preSendHook(messageInfo);
            SOAPMessage sm = doSend(messageInfo);
            postSendHook(messageInfo);

            //pass a copy of MessageInfo to the future task,so that no conflicts
            //due to threading happens
            Response r = sendAsyncReceive(MessageInfoBase.copy(messageInfo), sm);
            if (executorService == null) {
                executorService =
                    Executors.newFixedThreadPool(MAX_THREAD_POOL_SIZE, new DaemonThreadFactory());
            }

            AsyncHandlerService service = (AsyncHandlerService) messageInfo
                .getMetaData(BindingProviderProperties.JAXWS_CLIENT_ASYNC_HANDLER);
            WSFuture wsfuture = null;
            if (service != null) {
                wsfuture = service.setupAsyncCallback(r);
                ((ResponseImpl) r).setUID(service.getUID());
                ((ResponseImpl) r).setHandlerService(service);
            }

            executorService.execute((FutureTask) r);
            if (service == null)
                messageInfo.setResponse(r);
            else
                messageInfo.setResponse(wsfuture);
        } catch (Throwable e) {
            messageInfo.setResponse(e);
        }
    }

    /**
     * Orchestrates the receiving of an asynchronous response
     */
    protected Response<Object> sendAsyncReceive(final MessageInfo messageInfo, final SOAPMessage sm) {

        final AsyncHandlerService handler = (AsyncHandlerService) messageInfo
            .getMetaData(BindingProviderProperties.JAXWS_CLIENT_ASYNC_HANDLER);
        final boolean callback = (messageInfo.getMEP() == MessageStruct.ASYNC_CALLBACK_MEP) ? true
            : false;
        if (callback && (handler == null))
            throw new WebServiceException("Asynchronous callback invocation, but no handler - AsyncHandler required");

        final Response r = new ResponseImpl<Object>(new Callable<Object>() {

            public Object call() throws Exception {
                // get connection and do http.invoke()
                try {
                    final WSConnection connection = (WSConnection) messageInfo.getConnection();
                    logRequestMessage(sm, messageInfo);
                    SOAPConnectionUtil.sendResponse(connection, sm);
                } catch (Throwable t) {
                    messageInfo.setResponse(t);
                    messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
                }
                // receive response
                preReceiveHook(messageInfo);
                try {
                    receive(messageInfo);
                } catch (Exception ex) {
                    messageInfo.setResponse(ex);
                }
                postReceiveHook(messageInfo);

                if (messageInfo.getResponse() instanceof Exception)
                    throw (Exception) messageInfo.getResponse();
                return messageInfo.getResponse();
            }
        });
        messageInfo.setMetaData(JAXWS_CLIENT_ASYNC_RESPONSE_CONTEXT, r);
        return r;
    }


    protected boolean callHandlersOnRequest(SOAPHandlerContext handlerContext) {
        try {
            HandlerChainCaller caller = getHandlerChainCaller(handlerContext.getMessageInfo());
            boolean responseExpected = (handlerContext.getMessageInfo().getMEP() != MessageStruct.ONE_WAY_MEP);
            return caller.callHandlers(Direction.OUTBOUND, RequestOrResponse.REQUEST, handlerContext,
                responseExpected);
        } catch (ProtocolException pe) {
            if (MessageContextUtil.ignoreFaultInMessage(
                handlerContext.getMessageContext())) {
                // Ignore fault in this case and use exception.
                throw pe;
            } else
                return false;
        } catch (WebServiceException wse) {
            throw wse;
        } catch (RuntimeException re) {
            // handlers are expected to be able to throw RE
            throw new WebServiceException(re);
        }
    }

    /*
     * User's handler can throw RuntimeExceptions
     * (e.g., a ProtocolException).
     * Need to wrap any RuntimeException (other than WebServiceException) in
     * WebServiceException.
     */
    protected boolean callHandlersOnResponse(SOAPHandlerContext handlerContext) {
        HandlerChainCaller caller =
            getHandlerChainCaller(handlerContext.getMessageInfo());
        try {
            int httpResponseCode = (Integer) handlerContext.getMessageContext().get(
                    MessageContext.HTTP_RESPONSE_CODE);
            if(httpResponseCode != 200 && httpResponseCode != 202) {
                //Check if it is a fault message
                SOAPMessage sm = handlerContext.getSOAPMessage();
                if((sm != null) && sm.getSOAPBody().hasFault())
                    return caller.callHandleFaultOnClient(handlerContext);
            }

            return caller.callHandlers(Direction.INBOUND,
                RequestOrResponse.RESPONSE, handlerContext, false);
        } catch (SOAPException se) {
            throw new WebServiceException(se);
        } catch (WebServiceException wse) {
            throw wse;
        } catch (RuntimeException re) {
            // handlers are expected to be able to throw RE
            throw new WebServiceException(re);
        }
    }

    protected Binding getBinding(MessageInfo messageInfo) {
        ContextMap context = (ContextMap) ((MessageInfoBase) messageInfo)
            .getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        BindingProvider provider = (BindingProvider) context
            .get(JAXWS_CLIENT_HANDLE_PROPERTY);
        return provider.getBinding();
    }

    protected HandlerChainCaller getHandlerChainCaller(MessageInfo messageInfo) {
        BindingImpl binding = (BindingImpl) getBinding(messageInfo);
        return binding.getHandlerChainCaller();
    }

    private void updateSOAPMessage(Object value, SOAPMessage sm) {
        try {
            if (value instanceof Source) {
                SOAPBody sb = sm.getSOAPPart().getEnvelope().getBody();
                sb.removeContents();
                XmlUtil.newTransformer().transform((Source) value, new DOMResult(sb));
                sm.saveChanges();
            }
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }


    protected void updateMessageContext(MessageInfo messageInfo, SOAPHandlerContext context) {
        SOAPMessageContext messageContext = context.getSOAPMessageContext();
        messageInfo.setMetaData(BindingProviderProperties.JAXWS_HANDLER_CONTEXT_PROPERTY, context);
        RequestContext ctxt = (RequestContext) messageInfo
            .getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        Iterator i = ctxt.copy().getPropertyNames();
        while (i.hasNext()) {
            String name = (String) i.next();
            Object value = ctxt.get(name);
            messageContext.put(name, value);
        }

        BindingProvider provider = (BindingProvider) context.getMessageContext()
            .get(JAXWS_CLIENT_HANDLE_PROPERTY);

        QName portTypeQName = null;
        if (provider != null) {
            if (Proxy.isProxyClass(provider.getClass())) {

                EndpointIFInvocationHandler invocationHandler = (EndpointIFInvocationHandler) Proxy.getInvocationHandler(provider);
                EndpointIFContext endpointContext = invocationHandler.getEndpointContext();
                portTypeQName = invocationHandler.getWSDLPortTypeQName();

                messageContext.put(MessageContext.WSDL_SERVICE, invocationHandler.getServiceQName());
                messageContext.put(MessageContext.WSDL_PORT, endpointContext.getPortName());

                context.setBindingId(endpointContext.getBindingID().toString());

                RuntimeContext rtContext = (RuntimeContext) messageInfo.getMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);
                if (rtContext != null) {   //should never be
                    RuntimeModel model = rtContext.getModel();
                    JavaMethod javaMethod = model.getJavaMethod(messageInfo.getMethod());
                    String opname = javaMethod.getOperationName();
                    if (portTypeQName != null) {
                        String tns = portTypeQName.getNamespaceURI();
                        messageContext.put(MessageContext.WSDL_OPERATION, new QName(tns,opname));
                    }
                }
                //set handlerContext
                rtContext.setHandlerContext(context);
            }
        }

        //now get value for ContentNegotiation
        Object prop = messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY);
        if (prop != null) {
            messageContext.put(CONTENT_NEGOTIATION_PROPERTY, prop);
        }
    }

    protected void updateResponseContext(MessageInfo messageInfo,
                                         SOAPHandlerContext context) {
        SOAPMessageContext messageContext = context.getSOAPMessageContext();
        RequestContext rc = (RequestContext) messageInfo.getMetaData(JAXWS_CONTEXT_PROPERTY);
        BindingProvider provider = (BindingProvider) rc.get(JAXWS_CLIENT_HANDLE_PROPERTY);
        ResponseContext responseContext = new ResponseContext(provider);
        for (String name : messageContext.keySet()) {
            MessageContext.Scope scope = messageContext.getScope(name);
            if (MessageContext.Scope.APPLICATION == scope) {
                Object value = messageContext.get(name);
                responseContext.put(name, value);
            }
        }
        ResponseImpl asyncResponse = (ResponseImpl) messageInfo.getMetaData(
            JAXWS_CLIENT_ASYNC_RESPONSE_CONTEXT);
        if (asyncResponse != null) {
            asyncResponse.setResponseContext(responseContext.copy());
        } else {
            messageInfo.setMetaData(JAXWS_RESPONSE_CONTEXT_PROPERTY,
                responseContext.copy());
        }
    }

    /**
     * @return true if message exchange pattern indicates asynchronous, otherwise returns false
     */
    protected boolean isAsync(MessageInfo messageInfo) {
        if ((messageInfo.getMEP() == MessageStruct.ASYNC_POLL_MEP)
            || (messageInfo.getMEP() == MessageStruct.ASYNC_CALLBACK_MEP)) {
            return true;
        }
        return false;
    }

    private void preSendHook(MessageInfo messageInfo) {
    }

    private void preReceiveHook(MessageInfo messageInfo) {
    }

    private void postSendHook(MessageInfo messageInfo) {
        if (messageInfo.getResponseType() != MessageStruct.NORMAL_RESPONSE) {
            postReceiveHook(messageInfo);
            throw (WebServiceException) messageInfo.getResponse();
        }
    }

    private void postReceiveAndDecodeHook(MessageInfo messageInfo) {
        DispatchContext dispatchContext = (DispatchContext) messageInfo
            .getMetaData(BindingProviderProperties.DISPATCH_CONTEXT);
        if ((messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) == Service.Mode.MESSAGE)
            && (dispatchContext.getProperty(DispatchContext.DISPATCH_MESSAGE) == DispatchContext.MessageType.SOURCE_MESSAGE))
        {
            Object response = messageInfo.getResponse();
            if (response instanceof SOAPMessage) {
                SOAPPart part = ((SOAPMessage) response).getSOAPPart();
                try {
                    messageInfo.setResponse(part.getContent());
                } catch (SOAPException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void postReceiveHook(MessageInfo messageInfo) {
        // postReceiveHook exaimines the result for an exception
        // or SOAPFaultInfo - it will set appropriate
        // asynchronous exceptions
        Object response = messageInfo.getResponse();
        switch (messageInfo.getResponseType()) {
            case MessageStruct.NORMAL_RESPONSE:
                // not sure where this belongs yet - but for now-
                return;
            case MessageStruct.CHECKED_EXCEPTION_RESPONSE:
                if (response instanceof SOAPFaultInfo) {
                    SOAPFaultInfo soapFaultInfo = (SOAPFaultInfo) response;
                    JAXBException jbe = null;
                    if (soapFaultInfo.getString().contains("javax.xml.bind")) {
                        jbe = new JAXBException(soapFaultInfo.getString());
                        // do I need to put this in a jaxws exception
                    }
                    SOAPFaultException sfe = new SOAPFaultException(soapFaultInfo.getSOAPFault());
                    if (jbe != null)
                        sfe.initCause(jbe);
                    messageInfo.setResponse((SOAPFaultException) sfe);
                }
                return;
            case MessageStruct.UNCHECKED_EXCEPTION_RESPONSE:
                if (response instanceof SOAPFaultException) {
                    messageInfo.setResponse((SOAPFaultException) response);
                } else {

                    if (response instanceof Exception) {
                        RuntimeException jex = (RuntimeException) response;
                        messageInfo.setResponse(jex);
                    }
                }
                return;
            default:
                messageInfo.setResponse(response);
        }
    }

    private InternalMessage preHandlerOutboundHook(SOAPMessage sm, InternalMessage im) {
        if ((sm != null) && (im != null))
            im = null;
        return im;
    }

    private void postHandlerOutboundHook(MessageInfo messageInfo, SOAPHandlerContext handlerContext, SOAPMessage sm) {
        if (messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) == Service.Mode.MESSAGE)
        {
            InternalMessage im = handlerContext.getInternalMessage();
            if (im != null) {
                Object value = im.getBody().getValue();
                updateSOAPMessage(value, sm);
                im = null;
            } else
                try {
                    sm.saveChanges();
                } catch (SOAPException e) {
                    throw new RuntimeException(e);
                }
        }
    }

    private void postHandlerInboundHook(MessageInfo messageInfo, SOAPHandlerContext handlerContext, SOAPMessage sm) {
        if ((messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) == Service.Mode.MESSAGE) ||
            (messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) == Service.Mode.PAYLOAD))
        {
            InternalMessage im = handlerContext.getInternalMessage();
            if (im != null) {
                Object value = im.getBody().getValue();
                updateSOAPMessage(value, sm);
                im = null;
            } else
                try {
                    sm.saveChanges();
                } catch (SOAPException e) {
                    throw new RuntimeException(e);
                }
        }
    }


    private void closeAllHandlers(SOAPHandlerContext context) {
        HandlerChainCaller caller = getHandlerChainCaller(context.getMessageInfo());
        if (caller != null && caller.hasHandlers()) {
            caller.forceCloseHandlersOnClient(context);
        }
    }

    /**
     * This method is used to create the appropriate SOAPMessage (1.1 or 1.2 using SAAJ api).
     *
     * @return the BindingId associated with messageInfo
     */
    protected String getBindingId(MessageInfo messageInfo) {
        SOAPEncoder encoder = (SOAPEncoder) messageInfo.getEncoder();
        if (encoder instanceof SOAP12XMLEncoder)
            return SOAPBinding.SOAP12HTTP_BINDING;
        else
            return SOAPBinding.SOAP11HTTP_BINDING;
    }

    /**
     * Logs the SOAP request message
     */
    protected void logRequestMessage(SOAPMessage soapMessage, MessageInfo messageInfo)
        throws IOException, SOAPException {

        OutputStream out = ((WSConnection) messageInfo.getConnection()).getDebug();

        if (out != null) {
            String s = "******************\nRequest\n";
            out.write(s.getBytes());
            for (Iterator iter =
                soapMessage.getMimeHeaders().getAllHeaders();
                 iter.hasNext();
                ) {
                MimeHeader header = (MimeHeader) iter.next();
                s = header.getName() + ": " + header.getValue() + "\n";
                out.write(s.getBytes());
            }
            out.flush();
            soapMessage.writeTo(out);
            s = "\n";
            out.write(s.getBytes());
            out.flush();
        }
    }

    /**
     * Logs the SOAP response message
     */
    protected void logResponseMessage(SOAPMessage response, MessageInfo messageInfo)
        throws IOException, SOAPException {

        OutputStream out = ((WSConnection) messageInfo.getConnection()).getDebug();
        if (out != null) {
            String s = "Response\n";
            out.write(s.getBytes());
            s =
                "Http Status Code: "
                    + ((WSConnection) messageInfo.getConnection()).getStatus()
                    + "\n\n";
            out.write(s.getBytes());
            for (Iterator iter =
                response.getMimeHeaders().getAllHeaders();
                 iter.hasNext();
                ) {
                MimeHeader header = (MimeHeader) iter.next();
                s = header.getName() + ": " + header.getValue() + "\n";
                out.write(s.getBytes());
            }
            out.flush();
            response.writeTo(out);
            s = "******************\n\n";
            out.write(s.getBytes());
        }
    }

    class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread daemonThread = new Thread(r);
            daemonThread.setDaemon(Boolean.TRUE);
            return daemonThread;
        }
    }
}
