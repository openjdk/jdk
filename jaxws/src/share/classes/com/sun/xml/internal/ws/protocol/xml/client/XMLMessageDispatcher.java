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
package com.sun.xml.internal.ws.protocol.xml.client;

import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.*;
import static com.sun.xml.internal.ws.client.BindingProviderProperties.*;
import com.sun.xml.internal.ws.client.dispatch.DispatchContext;
import com.sun.xml.internal.ws.client.dispatch.ResponseImpl;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLEncoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLEncoder;
import com.sun.xml.internal.ws.encoding.soap.internal.MessageInfoBase;
import com.sun.xml.internal.ws.encoding.xml.XMLEncoder;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import com.sun.xml.internal.ws.handler.HandlerChainCaller;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.Direction;
import com.sun.xml.internal.ws.handler.HandlerChainCaller.RequestOrResponse;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.handler.XMLHandlerContext;
import com.sun.xml.internal.ws.handler.HandlerContext;
import com.sun.xml.internal.ws.model.JavaMethod;
import com.sun.xml.internal.ws.pept.ept.EPTFactory;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.spi.runtime.ClientTransportFactory;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.transport.http.client.HttpClientTransportFactory;
import com.sun.xml.internal.ws.util.Base64Util;
import com.sun.xml.internal.ws.util.XMLConnectionUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

import javax.activation.DataSource;
import javax.activation.DataHandler;
import javax.xml.bind.JAXBContext;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.*;
import static javax.xml.ws.BindingProvider.PASSWORD_PROPERTY;
import static javax.xml.ws.BindingProvider.USERNAME_PROPERTY;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.http.HTTPException;
import javax.xml.ws.soap.SOAPBinding;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Client-side XML-based message dispatcher {@link com.sun.xml.internal.ws.pept.protocol.MessageDispatcher}
 *
 * @author WS Development Team
 */
public class XMLMessageDispatcher implements MessageDispatcher {

    protected static final int MAX_THREAD_POOL_SIZE = 3;
    protected static final long AWAIT_TERMINATION_TIME = 10L;

    protected ExecutorService executorService = null;


    /**
     * Default constructor
     */

    public XMLMessageDispatcher() {
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
    protected XMLMessage doSend(MessageInfo messageInfo) {
        //change from LogicalEPTFactory to ContactInfoBase - should be changed back when we have things working
        EPTFactory contactInfo = messageInfo.getEPTFactory();
        XMLEncoder encoder = (XMLEncoder) contactInfo.getEncoder(messageInfo);

        boolean handlerResult = true;
        boolean isRequestResponse = (messageInfo.getMEP() == MessageStruct.REQUEST_RESPONSE_MEP);

        DispatchContext dispatchContext = (DispatchContext) messageInfo.getMetaData(BindingProviderProperties.DISPATCH_CONTEXT);

        if (!isHTTPMessageType(dispatchContext)) {
            throw new WebServiceException("Mode not allowed with HTTP Binding. Must use other Service.mode");
        }

        XMLMessage xm = makeXMLMessage(messageInfo);

        try {
            HandlerChainCaller caller = getHandlerChainCaller(messageInfo);
            if (caller.hasHandlers()) {
                XMLHandlerContext handlerContext = new XMLHandlerContext(
                    messageInfo, null, xm);
                updateMessageContext(messageInfo, handlerContext);
                handlerResult = callHandlersOnRequest(handlerContext);
                updateXMLMessage(handlerContext);
                xm = handlerContext.getXMLMessage();
                if (xm == null) {
                    xm = encoder.toXMLMessage(
                        handlerContext.getInternalMessage(), messageInfo);
                }

                // the only case where no message is sent
                if (isRequestResponse && !handlerResult) {
                    return xm;
                }
            }

            // Setting encoder here is necessary for calls to getBindingId()
//            messageInfo.setEncoder(encoder);
            Map<String, Object> context = processMetadata(messageInfo, xm);

            // set the MIME headers on connection headers
            Map<String, List<String>> ch = new HashMap<String, List<String>>();
            for (Iterator iter = xm.getMimeHeaders().getAllHeaders(); iter.hasNext();)
            {
                List<String> h = new ArrayList<String>();
                MimeHeader mh = (MimeHeader) iter.next();

                h.clear();
                h.add(mh.getValue());
                ch.put(mh.getName(), h);
            }

            setConnection(messageInfo, context);
            ((WSConnection) messageInfo.getConnection()).setHeaders(ch);

            if (!isAsync(messageInfo)) {
                WSConnection connection = (WSConnection) messageInfo.getConnection();
                //logRequestMessage(xm, messageInfo);
                XMLConnectionUtil.sendResponse(connection, xm);
            }

            // if handlerResult is false, the receive has already happened
            if (isRequestResponse && handlerResult) {
                receive(messageInfo);
                postReceiveHook(messageInfo);
            }
        } catch (Throwable e) {
            setResponseType(e, messageInfo);
            messageInfo.setResponse(e);
        }
        return xm;
    }

    /**
     * Process and classify the metadata in MIME headers or message context. <String,String> data
     * is copied into MIME headers and the remaining metadata is passed in message context to the
     * transport layer.
     *
     * @param messageInfo
     * @param xm
     */
    protected Map<String, Object> processMetadata(MessageInfo messageInfo, XMLMessage xm) {
        Map<String, Object> messageContext = new HashMap<String, Object>();
        List<String> header = new ArrayList<String>();

        ContextMap properties = (ContextMap)
            messageInfo.getMetaData(JAXWS_CONTEXT_PROPERTY);
        DispatchContext dcontext = (DispatchContext)
            messageInfo.getMetaData(BindingProviderProperties.DISPATCH_CONTEXT);
        if (isHTTPMessageType(dcontext)) {
            setHTTPContext(messageContext, dcontext, properties);
        }

        if (messageInfo.getMEP() == MessageStruct.ONE_WAY_MEP)
            messageContext.put(ONE_WAY_OPERATION, "true");

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
                        xm.getMimeHeaders().addHeader("Authorization", "Basic " + credentials);
                    }
                } else
                if (propName.equals(BindingProvider.SESSION_MAINTAIN_PROPERTY))
                {
                    Object maintainSession = properties.get(BindingProvider.SESSION_MAINTAIN_PROPERTY);
                    if (maintainSession != null && maintainSession.equals(Boolean.TRUE))
                    {
                        Object cookieJar = properties.get(HTTP_COOKIE_JAR);
                        if (cookieJar != null)
                            messageContext.put(HTTP_COOKIE_JAR, cookieJar);
                    }
                } else {
                    messageContext.put(propName, properties.get(propName));
                }
            }
        }

        // Set accept header depending on content negotiation property
        String contentNegotiation = (String)
            messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY);

        String bindingId = getBindingId(messageInfo);

        if (bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING)) {
            xm.getMimeHeaders().addHeader(ACCEPT_PROPERTY,
                contentNegotiation != "none" ? SOAP12_XML_FI_ACCEPT_VALUE : SOAP12_XML_ACCEPT_VALUE);
        } else {
            xm.getMimeHeaders().addHeader(ACCEPT_PROPERTY,
                contentNegotiation != "none" ? XML_FI_ACCEPT_VALUE : XML_ACCEPT_VALUE);
        }

        //setRequestHeaders
        Map<String, List<String>> requestHeaders = (Map)
            properties.get(MessageContext.HTTP_REQUEST_HEADERS);
        //requestHeaders.
        setMimeHeaders(requestHeaders, xm);

        messageContext.put(BINDING_ID_PROPERTY, bindingId);

        // SOAPAction: MIME header
        RuntimeContext runtimeContext = (RuntimeContext) messageInfo.getMetaData(JAXWS_RUNTIME_CONTEXT);
        if (runtimeContext != null) {
            JavaMethod javaMethod = runtimeContext.getModel().getJavaMethod(messageInfo.getMethod());
            if (javaMethod != null) {
                String soapAction = ((com.sun.xml.internal.ws.model.soap.SOAPBinding) javaMethod.getBinding()).getSOAPAction();
                header.clear();
                if (soapAction == null) {
                    xm.getMimeHeaders().addHeader("SOAPAction", "\"\"");
                } else {
                    xm.getMimeHeaders().addHeader("SOAPAction", soapAction);
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
        }
        connection = clientTransportFactory.create(context);
        messageInfo.setConnection(connection);
    }

    protected void setResponseType(Throwable e, MessageInfo messageInfo) {
        //e.printStackTrace();
        if (e instanceof RuntimeException) {
            messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
            if (e instanceof ClientTransportException) {
                Throwable temp = e;
                e = new WebServiceException(temp.getMessage(), temp);
            }
        } else {
            messageInfo.setResponseType(MessageStruct.CHECKED_EXCEPTION_RESPONSE);
        }
        messageInfo.setResponse(e);
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

        XMLMessage xm = getXMLMessage(messageInfo);

        // Content negotiation logic
        String contentNegotiation = (String) messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY);
        // If XML request
        if (contentNegotiation == "pessimistic") {
            try {
                if (xm.isFastInfoset()) {
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
            //logResponseMessage(xm, messageInfo);
        } catch (Exception ex) {
            throw new WebServiceException(ex);
        }

        XMLHandlerContext handlerContext =
            getInboundHandlerContext(messageInfo, xm);

        HandlerChainCaller caller = getHandlerChainCaller(messageInfo);
        if (caller.hasHandlers()) {
            callHandlersOnResponse(handlerContext);
            xm = handlerContext.getXMLMessage();
        }

        //set messageInfo response with appropriate result
        //at same time sets ResponseContext
        setResponse(messageInfo, xm, handlerContext);
    }

    private void setResponse(MessageInfo messageInfo, XMLMessage xm, HandlerContext handlerContext) {

        Map<String, DataHandler> attachments = getAttachments(handlerContext, xm);

        updateResponseContext(messageInfo, (XMLHandlerContext)handlerContext, attachments);

        DispatchContext dispatchContext = (DispatchContext) messageInfo.getMetaData(BindingProviderProperties.DISPATCH_CONTEXT);
        DispatchContext.MessageType msgtype = (DispatchContext.MessageType) dispatchContext.getProperty(DispatchContext.DISPATCH_MESSAGE);
        if (msgtype != null) {
            switch ((DispatchContext.MessageType) msgtype) {
                case HTTP_SOURCE_MESSAGE:
                    messageInfo.setResponse(xm.getSource());
                    break;
                case HTTP_SOURCE_PAYLOAD:
                    messageInfo.setResponse(xm.getSource());
                    break;
                case HTTP_JAXB_PAYLOAD:
                    messageInfo.setResponse(xm.getPayload(getJAXBContext(messageInfo)));
                    break;
                case HTTP_DATASOURCE_MESSAGE:
                    if (xm.getDataSource() != null)
                        messageInfo.setResponse(xm.getDataSource());
                    break;
                default:
                    throw new WebServiceException("Unknown invocation return object ");
            }
        } else {
            //tbd just assume source for now
            throw new WebServiceException("Unknown invocation return object");
        }

    }

    private Map<String, DataHandler> getAttachments(HandlerContext handlerContext, XMLMessage xm) {
        //are there attachments on the MessageContext properties ? Handlers
        Map<String, DataHandler> attmc = (Map<String, DataHandler>)
            handlerContext.getMessageContext().get(MessageContext.INBOUND_MESSAGE_ATTACHMENTS);
        //can we get it from XMLMessage as well?
        Map<String, DataHandler> attxm = xm.getAttachments();

        Map<String, DataHandler> attachments = new HashMap<String, DataHandler>();
        if (attxm != null && attmc != null) {
            attachments.putAll(attxm);
            attachments.putAll(attmc);
        } else if (attxm != null) {
            attachments.putAll(attxm);
        } else if (attmc != null)
            attachments.putAll(attmc);
        else {
            attachments = null; //gc it
            return null;
        }
        return attachments;
    }

    private XMLHandlerContext getInboundHandlerContext(
        MessageInfo messageInfo, XMLMessage xm) {
        XMLHandlerContext handlerContext = (XMLHandlerContext) messageInfo
            .getMetaData(BindingProviderProperties.JAXWS_HANDLER_CONTEXT_PROPERTY);
        if (handlerContext != null) {
            handlerContext.setXMLMessage(xm);
            handlerContext.setInternalMessage(null);
        } else {
            handlerContext = new XMLHandlerContext(messageInfo, null, xm);
        }
        return handlerContext;
    }

    /**
     * Orchestrates the sending of an asynchronous request
     */
    protected void doSendAsync(final MessageInfo messageInfo) {
        try { // should have already been caught
            preSendHook(messageInfo);
            XMLMessage xm = doSend(messageInfo);
            postSendHook(messageInfo);

            //pass a copy of MessageInfo to the future task,so that no conflicts
            //due to threading happens
            Response r = sendAsyncReceive(MessageInfoBase.copy(messageInfo), xm);
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
    protected Response<Object> sendAsyncReceive(final MessageInfo messageInfo, final XMLMessage xm) {

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
                    //logRequestMessage(xm, messageInfo);
                    XMLConnectionUtil.sendResponse(connection, xm);
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

    protected boolean callHandlersOnRequest(XMLHandlerContext handlerContext) {

        HandlerChainCaller caller = getHandlerChainCaller(
            handlerContext.getMessageInfo());
        boolean responseExpected = (handlerContext.getMessageInfo().getMEP() !=
            MessageStruct.ONE_WAY_MEP);
        try {
            return caller.callHandlers(Direction.OUTBOUND,
                RequestOrResponse.REQUEST, handlerContext, responseExpected);
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
    * User's handler can throw a RuntimeExceptions
    * (e.g., a ProtocolException).
    * Need to wrap any RuntimeException (other than WebServiceException) in
    * WebServiceException.
    */
    protected boolean callHandlersOnResponse(XMLHandlerContext handlerContext) {
        HandlerChainCaller caller = getHandlerChainCaller(
            handlerContext.getMessageInfo());
        try {
            return caller.callHandlers(Direction.INBOUND,
                RequestOrResponse.RESPONSE, handlerContext, false);
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
            .get(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY);
        return provider.getBinding();
    }

    protected HandlerChainCaller getHandlerChainCaller(MessageInfo messageInfo) {
        BindingImpl binding = (BindingImpl) getBinding(messageInfo);
        return binding.getHandlerChainCaller();
    }

    protected void updateMessageContext(MessageInfo messageInfo,
                                        XMLHandlerContext context) {

        MessageContext messageContext = context.getMessageContext();
        messageInfo.setMetaData(
            BindingProviderProperties.JAXWS_HANDLER_CONTEXT_PROPERTY, context);
        RequestContext ctxt = (RequestContext) messageInfo
            .getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        Iterator i = ctxt.copy().getPropertyNames();
        while (i.hasNext()) {
            String name = (String) i.next();
            Object value = ctxt.get(name);
            messageContext.put(name, value);
        }
    }

    protected void updateResponseContext(MessageInfo messageInfo,
                                         XMLHandlerContext context, Map<String, DataHandler> attachments) {


        MessageContext messageContext = context.getMessageContext();
        BindingProvider provider = (BindingProvider)
            messageContext.get(JAXWS_CLIENT_HANDLE_PROPERTY);
        ResponseContext responseContext = new ResponseContext(provider);
        for (String name : messageContext.keySet()) {
            MessageContext.Scope scope = messageContext.getScope(name);
            if (MessageContext.Scope.APPLICATION == scope) {
                Object value = messageContext.get(name);
                responseContext.put(name, value);
            }
        }

        //let's update status code
        MessageContext mc =  context.getMessageContext();
        WSConnection con = messageInfo.getConnection();
        Map<String, List<String>> headers = con.getHeaders();

        responseContext.put(MessageContext.HTTP_RESPONSE_HEADERS, headers);
        responseContext.put(MessageContext.HTTP_RESPONSE_CODE, con.getStatus());

        //attachments for ResponseContext
        if (attachments != null)
           responseContext.put(MessageContext.INBOUND_MESSAGE_ATTACHMENTS, attachments);

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
    }

    private void postReceiveHook(MessageInfo messageInfo) {

        if (messageInfo.getMEP() == MessageStruct.ONE_WAY_MEP)
            return;
        Object response = messageInfo.getResponse();
        if (response instanceof StreamSource) {
            InputStream is = ((StreamSource) response).getInputStream();

            Transformer transformer = XmlUtil.newTransformer();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(is.available());
                if (out.size() > 0) {
                    transformer.transform((StreamSource) response,
                        new StreamResult(out));
                    byte[] bytes = out.toByteArray();
                    //could do to string
                    if (new String(bytes).indexOf("HTTPException") > -1)
                        throw new HTTPException(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    else {
                        InputStream bis = new ByteArrayInputStream(bytes);
                        messageInfo.setResponse(new StreamSource(bis));
                    }
                }
            } catch (TransformerException e) {
                throw new WebServiceException(e);
            } catch (IOException e) {
                throw new WebServiceException(e);
            }
        }
        switch (messageInfo.getResponseType()) {
            case MessageStruct.NORMAL_RESPONSE:
                // not sure where this belongs yet - but for now-
                return;
            case MessageStruct.CHECKED_EXCEPTION_RESPONSE:
                if (response instanceof Exception) {
                    throw new HTTPException(HttpURLConnection.HTTP_INTERNAL_ERROR);
                }
                return;
            case MessageStruct.UNCHECKED_EXCEPTION_RESPONSE:
                if (response instanceof ProtocolException) {
                    throw new HTTPException(HttpURLConnection.HTTP_INTERNAL_ERROR);
                } else {
                    WebServiceException jex = null;
                    if (response instanceof Exception) {
                        throw new WebServiceException((Exception) response);
                    }
                    messageInfo.setResponse(response);
                }
                return;
            default:
                messageInfo.setResponse(response);
        }
    }

    private void closeAllHandlers(XMLHandlerContext context) {
        HandlerChainCaller caller = getHandlerChainCaller(
            context.getMessageInfo());
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
        if (encoder instanceof SOAPXMLEncoder)
            return SOAPBinding.SOAP11HTTP_BINDING;
        else if (encoder instanceof SOAP12XMLEncoder)
            return SOAPBinding.SOAP12HTTP_BINDING;
        else
            return HTTPBinding.HTTP_BINDING;
    }

    /**
     * Logs the SOAP request message
     */
    protected void logRequestMessage(XMLMessage request, MessageInfo messageInfo)
        throws IOException, MessagingException, TransformerException {

        OutputStream out = ((WSConnection) messageInfo.getConnection()).getDebug();

        if (out != null) {
            String s = "******************\nRequest\n";
            out.write(s.getBytes());
            for (Iterator iter =
                request.getMimeHeaders().getAllHeaders();
                 iter.hasNext();
                ) {
                MimeHeader header = (MimeHeader) iter.next();
                s = header.getName() + ": " + header.getValue() + "\n";
                out.write(s.getBytes());
            }
            out.flush();
            request.writeTo(out);
            s = "\n";
            out.write(s.getBytes());
            out.flush();
        }
    }

    /**
     * Logs the SOAP response message
     */
    protected void logResponseMessage(XMLMessage response, MessageInfo messageInfo)
        throws IOException, MessagingException, TransformerException {

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

    /*
    * Gets XMLMessage from the connection
    */
    private XMLMessage getXMLMessage(MessageInfo messageInfo) {
        WSConnection con = (WSConnection) messageInfo.getConnection();
        return XMLConnectionUtil.getXMLMessage(con, messageInfo);
    }

    protected JAXBContext getJAXBContext(MessageInfo messageInfo) {
        JAXBContext jc = null;
        RequestContext context = (RequestContext) messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        if (context != null)
            jc = (JAXBContext) context.get(BindingProviderProperties.JAXB_CONTEXT_PROPERTY);

        return jc;
    }

    public void setHTTPContext(Map<String, Object> messageContext, DispatchContext dispatchContext, Map requestContext) {

        if (requestContext.get(MessageContext.HTTP_REQUEST_METHOD) != null)
            messageContext.put(MessageContext.HTTP_REQUEST_METHOD, requestContext.get(MessageContext.HTTP_REQUEST_METHOD));
        if (requestContext.get(MessageContext.HTTP_REQUEST_HEADERS) != null)
            messageContext.put(MessageContext.HTTP_REQUEST_HEADERS, requestContext.get(MessageContext.HTTP_REQUEST_HEADERS));

        //resolve endpoint look for query parameters, pathInfo
        String origEndpoint = (String) requestContext.get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);

        String pathInfo = null;
        String queryString = null;
        if (requestContext.get(MessageContext.PATH_INFO) != null) {
            pathInfo = (String) requestContext.get(MessageContext.PATH_INFO);
        }
        if (requestContext.get(MessageContext.QUERY_STRING) != null) {
            queryString = (String) requestContext.get(MessageContext.QUERY_STRING);
        }

        String resolvedEndpoint = null;
        if (pathInfo != null || queryString != null) {
            pathInfo = checkPath(pathInfo);
            queryString = checkQuery(queryString);
            if (origEndpoint != null) {
                try {
                    URI endpointURI = new URI(origEndpoint);
                    resolvedEndpoint = resolveURI(endpointURI, pathInfo, queryString);
                } catch (URISyntaxException e) {
                    resolvedEndpoint = origEndpoint;
                }
            }

            requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, resolvedEndpoint);
            messageContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, resolvedEndpoint);
        }
    }

    protected String resolveURI(URI endpointURI, String pathInfo, String queryString) {
        String query = null;
        String fragment = null;
        if (queryString != null) {
            URI result = endpointURI.resolve(queryString);
            query = result.getQuery();
            fragment = result.getFragment();
        }
        //String path = (pathInfo != null) ? endpointURI.getPath() + pathInfo : endpointURI.getPath();
        String path = (pathInfo != null) ? pathInfo : endpointURI.getPath();
        try {
            URI temp = new URI(null, null, path, query, fragment);
            return endpointURI.resolve(temp).toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return endpointURI.toString();
    }

    private String checkPath(String path) {
        //does it begin with /
        return (path == null || path.startsWith("/")) ? path : "/" + path;
    }

    private String checkQuery(String query) {
        //does it begin with ?
        return (query == null || query.startsWith("?")) ? query : "?" + query;
    }

    protected boolean isHTTPMessageType(DispatchContext dispatchContext) {

        DispatchContext.MessageType type = (DispatchContext.MessageType)
            dispatchContext.getProperty(DispatchContext.DISPATCH_MESSAGE);

        if ((type == DispatchContext.MessageType.HTTP_DATASOURCE_MESSAGE) ||
            //(type == DispatchContext.MessageType.HTTP_DATASOURCE_PAYLOAD) ||
            (type == DispatchContext.MessageType.HTTP_SOURCE_MESSAGE) ||
            (type == DispatchContext.MessageType.HTTP_SOURCE_PAYLOAD) ||
            //(type == DispatchContext.MessageType.HTTP_JAXB_MESSAGE) ||
            (type == DispatchContext.MessageType.HTTP_JAXB_PAYLOAD))
            return true;

        return false;
    }

    protected XMLMessage makeXMLMessage
        (MessageInfo
            messageInfo) {

        XMLMessage xm = null;

        Class clazz = (Class)
            messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_CLASS);

        Map<String, Object> context = (Map<String, Object>)
            messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        Map<String, DataHandler> attachments = (context != null) ?
            (Map<String, DataHandler> )context.get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS) : null;

        // Determine if Fast Infoset is to be used
        boolean useFastInfoset =
            (messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY) == "optimistic");

        Object object = messageInfo.getData()[0];

        if (clazz != null && clazz.isAssignableFrom(Source.class)) {
            //xm = new XMLMessage((Source) object, useFastInfoset);
            xm = new XMLMessage((Source) object, attachments, useFastInfoset);
        } else if (clazz != null && clazz.isAssignableFrom(DataSource.class)) {
            xm = new XMLMessage((DataSource) object, useFastInfoset);
        } else {
            xm = new XMLMessage(object, getJAXBContext(messageInfo), attachments, useFastInfoset);
            //xm = new XMLMessage(object, getJAXBContext(messageInfo), useFastInfoset);
        }

        return xm;
    }

     private void setMimeHeaders(Map<String, List<String>> requestHeaders, XMLMessage xm) {

        if ((requestHeaders != null) && (!requestHeaders.isEmpty())) {
            Set<Map.Entry<String, List<String>>> headerSet = requestHeaders.entrySet();
            Iterator<Map.Entry<String, List<String>>> iter = headerSet.iterator();
            while (iter.hasNext()) {
                Map.Entry<String,List<String>> entry =iter.next();
                MimeHeaders headers = xm.getMimeHeaders();
                String[] values = entry.getValue().toArray(new String[entry.getValue().size()] );;
                StringBuffer buf = new StringBuffer(250);
                if (values.length > 0)
                   buf.append(values[0]);
                else break;
                for (int i = 1; i < values.length - 1; i++){
                    buf.append(values[i]);
                }

                headers.addHeader(entry.getKey(), buf.toString());
            }
        }
    }

    private XMLMessage updateXMLMessage(XMLHandlerContext context) {
    // Create a new XMLMessage from existing message and OUTBOUND attachments property
        MessageContext msgCtxt = context.getMessageContext();
        Map<String, DataHandler> atts = (Map<String, DataHandler>)msgCtxt.get(
                MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);
        if (atts != null) {
            XMLMessage xmlMessage = context.getXMLMessage();
            if (xmlMessage != null) {
                Map<String, DataHandler> allAtts = xmlMessage.getAttachments();
                if (allAtts != null) {
                    allAtts.putAll(atts);
                } else {
                    allAtts = atts;
                }
                context.setXMLMessage(new XMLMessage(xmlMessage.getSource(), allAtts,
                    xmlMessage.isFastInfoset()));
            } else {
                //can I make a message w/o src
            }

        }
        return context.getXMLMessage();
    }


    class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread daemonThread = new Thread(r);
            daemonThread.setDaemon(Boolean.TRUE);
            return daemonThread;
        }
    }

}
