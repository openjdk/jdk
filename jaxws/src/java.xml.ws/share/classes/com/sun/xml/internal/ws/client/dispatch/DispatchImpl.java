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

package com.sun.xml.internal.ws.client.dispatch;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.api.message.AddressingUtils;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.ContainerResolver;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.*;
import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.xml.internal.ws.message.DataHandlerAttachment;
import com.sun.xml.internal.ws.resources.DispatchMessages;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.AsyncHandler;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Response;
import javax.xml.ws.Service;
import javax.xml.ws.Service.Mode;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.SOAPFaultException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The <code>DispatchImpl</code> abstract class provides support
 * for the dynamic invocation of a service endpoint operation using XML
 * constructs, JAXB objects or <code>SOAPMessage</code>. The <code>javax.xml.ws.Service</code>
 * interface acts as a factory for the creation of <code>DispatchImpl</code>
 * instances.
 *
 * @author WS Development Team
 * @version 1.0
 */
public abstract class DispatchImpl<T> extends Stub implements Dispatch<T> {

    private static final Logger LOGGER = Logger.getLogger(DispatchImpl.class.getName());

    final Service.Mode mode;
    final SOAPVersion soapVersion;
    final boolean allowFaultResponseMsg;
    static final long AWAIT_TERMINATION_TIME = 800L;

    /**
     *
     * @param port    dispatch instance is associated with this wsdl port qName
     * @param mode    Service.mode associated with this Dispatch instance - Service.mode.MESSAGE or Service.mode.PAYLOAD
     * @param owner   Service that created the Dispatch
     * @param pipe    Master pipe for the pipeline
     * @param binding Binding of this Dispatch instance, current one of SOAP/HTTP or XML/HTTP
     */
    @Deprecated
    protected DispatchImpl(QName port, Service.Mode mode, WSServiceDelegate owner, Tube pipe, BindingImpl binding, @Nullable WSEndpointReference epr) {
        super(port, owner, pipe, binding, (owner.getWsdlService() != null)? owner.getWsdlService().get(port) : null , owner.getEndpointAddress(port), epr);
        this.mode = mode;
        this.soapVersion = binding.getSOAPVersion();
        this.allowFaultResponseMsg = false;
    }

    /**
     * @param portInfo dispatch instance is associated with this portInfo
     * @param mode     Service.mode associated with this Dispatch instance - Service.mode.MESSAGE or Service.mode.PAYLOAD
     * @param binding  Binding of this Dispatch instance, current one of SOAP/HTTP or XML/HTTP
     */
    protected DispatchImpl(WSPortInfo portInfo, Service.Mode mode, BindingImpl binding, @Nullable WSEndpointReference epr) {
        this(portInfo, mode, binding, epr, false);
    }

    /**
     * @param portInfo dispatch instance is associated with this portInfo
     * @param mode     Service.mode associated with this Dispatch instance - Service.mode.MESSAGE or Service.mode.PAYLOAD
     * @param binding  Binding of this Dispatch instance, current one of SOAP/HTTP or XML/HTTP
     * @param allowFaultResponseMsg A packet containing a SOAP fault message is allowed as the response to a request on this dispatch instance.
     */
    protected DispatchImpl(WSPortInfo portInfo, Service.Mode mode, BindingImpl binding, @Nullable WSEndpointReference epr, boolean allowFaultResponseMsg) {
        this(portInfo, mode, binding, null, epr, allowFaultResponseMsg);
    }

    /**
     * @param portInfo dispatch instance is associated with this portInfo
     * @param mode     Service.mode associated with this Dispatch instance - Service.mode.MESSAGE or Service.mode.PAYLOAD
     * @param binding  Binding of this Dispatch instance, current one of SOAP/HTTP or XML/HTTP
     * @param pipe    Master pipe for the pipeline
     * @param allowFaultResponseMsg A packet containing a SOAP fault message is allowed as the response to a request on this dispatch instance.
     */
    protected DispatchImpl(WSPortInfo portInfo, Service.Mode mode, BindingImpl binding, Tube pipe, @Nullable WSEndpointReference epr, boolean allowFaultResponseMsg) {
        super(portInfo, binding, pipe, portInfo.getEndpointAddress(), epr);
        this.mode = mode;
        this.soapVersion = binding.getSOAPVersion();
        this.allowFaultResponseMsg = allowFaultResponseMsg;
    }
    /**
     *
     * @param portportInfo dispatch instance is associated with this wsdl port qName
     * @param mode    Service.mode associated with this Dispatch instance - Service.mode.MESSAGE or Service.mode.PAYLOAD
     * @param pipe    Master pipe for the pipeline
     * @param binding Binding of this Dispatch instance, current one of SOAP/HTTP or XML/HTTP
     * @param allowFaultResponseMsg A packet containing a SOAP fault message is allowed as the response to a request on this dispatch instance.
     */
    protected DispatchImpl(WSPortInfo portInfo, Service.Mode mode, Tube pipe, BindingImpl binding, @Nullable WSEndpointReference epr, boolean allowFaultResponseMsg) {
        super(portInfo, binding, pipe, portInfo.getEndpointAddress(), epr);
        this.mode = mode;
        this.soapVersion = binding.getSOAPVersion();
        this.allowFaultResponseMsg = allowFaultResponseMsg;
    }

    /**
     * Abstract method that is implemented by each concrete Dispatch class
     * @param msg  message passed in from the client program on the invocation
     * @return  The Message created returned as the Interface in actuallity a
     *          concrete Message Type
     */
    abstract Packet createPacket(T msg);

    /**
     * Obtains the value to return from the response message.
     */
    abstract T toReturnValue(Packet response);

    public final Response<T> invokeAsync(T param) {
        Container old = ContainerResolver.getDefault().enterContainer(owner.getContainer());
        try {
            if (LOGGER.isLoggable(Level.FINE)) {
              dumpParam(param, "invokeAsync(T)");
            }
            AsyncInvoker invoker = new DispatchAsyncInvoker(param);
            AsyncResponseImpl<T> ft = new AsyncResponseImpl<T>(invoker,null);
            invoker.setReceiver(ft);
            ft.run();
            return ft;
        } finally {
            ContainerResolver.getDefault().exitContainer(old);
        }
    }

    private void dumpParam(T param, String method) {
      if (param instanceof Packet) {
        Packet message = (Packet)param;

        String action;
        String msgId;
        if (LOGGER.isLoggable(Level.FINE)) {
          AddressingVersion av = DispatchImpl.this.getBinding().getAddressingVersion();
          SOAPVersion sv = DispatchImpl.this.getBinding().getSOAPVersion();
          action =
            av != null && message.getMessage() != null ?
              AddressingUtils.getAction(message.getMessage().getHeaders(), av, sv) : null;
          msgId =
            av != null && message.getMessage() != null ?
              AddressingUtils.getMessageID(message.getMessage().getHeaders(), av, sv) : null;
          LOGGER.fine("In DispatchImpl." + method + " for message with action: " + action + " and msg ID: " + msgId + " msg: " + message.getMessage());

          if (message.getMessage() == null) {
            LOGGER.fine("Dispatching null message for action: " + action + " and msg ID: " + msgId);
          }
        }
      }
    }
    public final Future<?> invokeAsync(T param, AsyncHandler<T> asyncHandler) {
        Container old = ContainerResolver.getDefault().enterContainer(owner.getContainer());
        try {
            if (LOGGER.isLoggable(Level.FINE)) {
              dumpParam(param, "invokeAsync(T, AsyncHandler<T>)");
            }
            AsyncInvoker invoker = new DispatchAsyncInvoker(param);
            AsyncResponseImpl<T> ft = new AsyncResponseImpl<T>(invoker,asyncHandler);
            invoker.setReceiver(ft);
            invoker.setNonNullAsyncHandlerGiven(asyncHandler != null);

            ft.run();
            return ft;
        } finally {
            ContainerResolver.getDefault().exitContainer(old);
        }
    }

    /**
     * Synchronously invokes a service.
     *
     * See {@link #process(Packet, RequestContext, ResponseContextReceiver)} on
     * why it takes a {@link RequestContext} and {@link ResponseContextReceiver} as a parameter.
     */
    public final T doInvoke(T in, RequestContext rc, ResponseContextReceiver receiver){
        Packet response = null;
        try {
                try {
                    checkNullAllowed(in, rc, binding, mode);

                    Packet message = createPacket(in);
                    message.setState(Packet.State.ClientRequest);
                    resolveEndpointAddress(message, rc);
                    setProperties(message,true);
                    response = process(message,rc,receiver);
                    Message msg = response.getMessage();

        // REVIEW: eliminate allowFaultResponseMsg, but make that behavior default for MessageDispatch, PacketDispatch
                    if(msg != null && msg.isFault() &&
                 !allowFaultResponseMsg) {
                        SOAPFaultBuilder faultBuilder = SOAPFaultBuilder.create(msg);
                        // passing null means there is no checked excpetion we're looking for all
                        // it will get back to us is a protocol exception
                        throw (SOAPFaultException)faultBuilder.createException(null);
                    }
                } catch (JAXBException e) {
                    //TODO: i18nify
                    throw new DeserializationException(DispatchMessages.INVALID_RESPONSE_DESERIALIZATION(),e);
                } catch(WebServiceException e){
                    //it could be a WebServiceException or a ProtocolException
                    throw e;
                } catch(Throwable e){
                    // it could be a RuntimeException resulting due to some internal bug or
                    // its some other exception resulting from user error, wrap it in
                    // WebServiceException
                    throw new WebServiceException(e);
                }

                return toReturnValue(response);
        } finally {
        // REVIEW: Move to AsyncTransportProvider
                if (response != null && response.transportBackChannel != null)
                        response.transportBackChannel.close();
        }
    }

    public final T invoke(T in) {
        Container old = ContainerResolver.getDefault().enterContainer(owner.getContainer());
        try {
            if (LOGGER.isLoggable(Level.FINE)) {
              dumpParam(in, "invoke(T)");
            }

            return doInvoke(in,requestContext,this);
        } finally {
            ContainerResolver.getDefault().exitContainer(old);
        }
    }

    public final void invokeOneWay(T in) {
        Container old = ContainerResolver.getDefault().enterContainer(owner.getContainer());
        try {
            if (LOGGER.isLoggable(Level.FINE)) {
              dumpParam(in, "invokeOneWay(T)");
            }

            try {
                checkNullAllowed(in, requestContext, binding, mode);

                Packet request = createPacket(in);
                request.setState(Packet.State.ClientRequest);
                setProperties(request,false);
                process(request,requestContext,this);
            } catch(WebServiceException e){
                //it could be a WebServiceException or a ProtocolException
                throw e;
            } catch(Throwable e){
                // it could be a RuntimeException resulting due to some internal bug or
                // its some other exception resulting from user error, wrap it in
                // WebServiceException
                throw new WebServiceException(e);
            }
        } finally {
            ContainerResolver.getDefault().exitContainer(old);
        }
    }

    void setProperties(Packet packet, boolean expectReply) {
        packet.expectReply = expectReply;
    }

    static boolean isXMLHttp(@NotNull WSBinding binding) {
        return binding.getBindingId().equals(BindingID.XML_HTTP);
    }

    static boolean isPAYLOADMode(@NotNull Service.Mode mode) {
           return mode == Service.Mode.PAYLOAD;
    }

    static void checkNullAllowed(@Nullable Object in, RequestContext rc, WSBinding binding, Service.Mode mode) {

        if (in != null)
            return;

        //With HTTP Binding a null invocation parameter can not be used
        //with HTTP Request Method == POST
        if (isXMLHttp(binding)){
            if (methodNotOk(rc))
                throw new WebServiceException(DispatchMessages.INVALID_NULLARG_XMLHTTP_REQUEST_METHOD(HTTP_REQUEST_METHOD_POST, HTTP_REQUEST_METHOD_GET));
        } else { //soapBinding
              if (mode == Service.Mode.MESSAGE )
                   throw new WebServiceException(DispatchMessages.INVALID_NULLARG_SOAP_MSGMODE(mode.name(), Service.Mode.PAYLOAD.toString()));
        }
    }

    static boolean methodNotOk(@NotNull RequestContext rc) {
        String requestMethod = (String)rc.get(MessageContext.HTTP_REQUEST_METHOD);
        String request = (requestMethod == null)? HTTP_REQUEST_METHOD_POST: requestMethod;
        // if method == post or put with a null invocation parameter in xml/http binding this is not ok
        return HTTP_REQUEST_METHOD_POST.equalsIgnoreCase(request) || HTTP_REQUEST_METHOD_PUT.equalsIgnoreCase(request);
    }

    public static void checkValidSOAPMessageDispatch(WSBinding binding, Service.Mode mode) {
        // Dispatch<SOAPMessage> is only valid for soap binding and in Service.Mode.MESSAGE
        if (DispatchImpl.isXMLHttp(binding))
            throw new WebServiceException(DispatchMessages.INVALID_SOAPMESSAGE_DISPATCH_BINDING(HTTPBinding.HTTP_BINDING, SOAPBinding.SOAP11HTTP_BINDING + " or " + SOAPBinding.SOAP12HTTP_BINDING));
        if (DispatchImpl.isPAYLOADMode(mode))
            throw new WebServiceException(DispatchMessages.INVALID_SOAPMESSAGE_DISPATCH_MSGMODE(mode.name(), Service.Mode.MESSAGE.toString()));
    }

    public static void checkValidDataSourceDispatch(WSBinding binding, Service.Mode mode) {
        // Dispatch<DataSource> is only valid with xml/http binding and in Service.Mode.MESSAGE
        if (!DispatchImpl.isXMLHttp(binding))
            throw new WebServiceException(DispatchMessages.INVALID_DATASOURCE_DISPATCH_BINDING("SOAP/HTTP", HTTPBinding.HTTP_BINDING));
        if (DispatchImpl.isPAYLOADMode(mode))
            throw new WebServiceException(DispatchMessages.INVALID_DATASOURCE_DISPATCH_MSGMODE(mode.name(), Service.Mode.MESSAGE.toString()));
    }

    public final @NotNull QName getPortName() {
        return portname;
    }

    void resolveEndpointAddress(@NotNull final Packet message, @NotNull final RequestContext requestContext) {
        final boolean p = message.packetTakesPriorityOverRequestContext;

        //resolve endpoint look for query parameters, pathInfo
        String endpoint;
        if (p && message.endpointAddress != null) {
            endpoint = message.endpointAddress.toString();
        } else {
            endpoint = (String) requestContext.get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);
        }
        // This is existing before packetTakesPriorityOverRequestContext so leaving in place.
        if (endpoint == null) {
            if (message.endpointAddress == null) throw new WebServiceException(DispatchMessages.INVALID_NULLARG_URI());
            endpoint = message.endpointAddress.toString();
        }

        String pathInfo = null;
        String queryString = null;
        if (p && message.invocationProperties.get(MessageContext.PATH_INFO) != null) {
            pathInfo = (String) message.invocationProperties.get(MessageContext.PATH_INFO);
        } else if (requestContext.get(MessageContext.PATH_INFO) != null) {
            pathInfo = (String) requestContext.get(MessageContext.PATH_INFO);
        }

        if (p && message.invocationProperties.get(MessageContext.QUERY_STRING) != null) {
            queryString = (String) message.invocationProperties.get(MessageContext.QUERY_STRING);
        } else if (requestContext.get(MessageContext.QUERY_STRING) != null) {
            queryString = (String) requestContext.get(MessageContext.QUERY_STRING);
        }

        if (pathInfo != null || queryString != null) {
            pathInfo = checkPath(pathInfo);
            queryString = checkQuery(queryString);
            if (endpoint != null) {
                try {
                    final URI endpointURI = new URI(endpoint);
                    endpoint = resolveURI(endpointURI, pathInfo, queryString);
                } catch (URISyntaxException e) {
                    throw new WebServiceException(DispatchMessages.INVALID_URI(endpoint));
                }
            }
        }
        // These two lines used to be inside the above if.  It is outside so:
        // - in cases where there is no setting of address on a Packet before invocation or no pathInfo/queryString
        //   this will just put back what it found in the requestContext - basically a noop.
        // - but when info is in the Packet this will update so it will get used later.
        // Remember - we are operating on a copied RequestContext at this point - not the sticky one in the Stub.
        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
        // This is not necessary because a later step will copy the resolvedEndpoint put above into message.
        //message.endpointAddress = EndpointAddress.create(endpoint);
    }

    protected @NotNull String resolveURI(@NotNull URI endpointURI, @Nullable String pathInfo, @Nullable String queryString) {
        String query = null;
        String fragment = null;
        if (queryString != null) {
            final URI result;
            try {
                URI tp = new URI(null, null, endpointURI.getPath(), queryString, null);
                result = endpointURI.resolve(tp);
            } catch (URISyntaxException e) {
                throw new WebServiceException(DispatchMessages.INVALID_QUERY_STRING(queryString));
            }
            query = result.getQuery();
            fragment = result.getFragment();
        }

        final String path = (pathInfo != null) ? pathInfo : endpointURI.getPath();
        try {
            //final URI temp = new URI(null, null, path, query, fragment);
            //return endpointURI.resolve(temp).toURL().toExternalForm();
            // Using the following HACK instead of the above to avoid double encoding of
            // the query. Application's QUERY_STRING is encoded using URLEncoder.encode().
            // If we use that query in URI's constructor, it is encoded again.
            // URLEncoder's encoding is not the same as URI's encoding of the query.
            // See {@link URL}
            StringBuilder spec = new StringBuilder();
            if (path != null) {
                spec.append(path);
            }
            if (query != null) {
                spec.append("?");
                spec.append(query);
            }
            if (fragment != null) {
                spec.append("#");
                spec.append(fragment);
            }
            return new URL(endpointURI.toURL(), spec.toString()).toExternalForm();
       } catch (MalformedURLException e) {
            throw new WebServiceException(DispatchMessages.INVALID_URI_RESOLUTION(path));
        }
    }

    private static String checkPath(@Nullable String path) {
        //does it begin with /
        return (path == null || path.startsWith("/")) ? path : "/" + path;
    }

    private static String checkQuery(@Nullable String query) {
        if (query == null) return null;

        if (query.indexOf('?') == 0)
           throw new WebServiceException(DispatchMessages.INVALID_QUERY_LEADING_CHAR(query));
        return query;
    }


    protected AttachmentSet setOutboundAttachments() {
        HashMap<String, DataHandler> attachments = (HashMap<String, DataHandler>)
                getRequestContext().get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);

        if (attachments != null) {
            List<Attachment> alist = new ArrayList();
            for (Map.Entry<String, DataHandler> att : attachments.entrySet()) {
                DataHandlerAttachment dha = new DataHandlerAttachment(att.getKey(), att.getValue());
                alist.add(dha);
            }
            return new AttachmentSetImpl(alist);
        }
        return new AttachmentSetImpl();
    }

   /* private void getInboundAttachments(Message msg) {
        AttachmentSet attachments = msg.getAttachments();
        if (!attachments.isEmpty()) {
            Map<String, DataHandler> in = new HashMap<String, DataHandler>();
            for (Attachment attachment : attachments)
                in.put(attachment.getContentId(), attachment.asDataHandler());
            getResponseContext().put(MessageContext.INBOUND_MESSAGE_ATTACHMENTS, in);
        }

    }
    */


    /**
     * Calls {@link DispatchImpl#doInvoke(Object,RequestContext,ResponseContextReceiver)}.
     */
    private class Invoker implements Callable {
        private final T param;
        // snapshot the context now. this is necessary to avoid concurrency issue,
        // and is required by the spec
        private final RequestContext rc = requestContext.copy();

        /**
         * Because of the object instantiation order,
         * we can't take this as a constructor parameter.
         */
        private ResponseContextReceiver receiver;

        Invoker(T param) {
            this.param = param;
        }

        public T call() throws Exception {
            if (LOGGER.isLoggable(Level.FINE)) {
              dumpParam(param, "call()");
            }
            return doInvoke(param,rc,receiver);
        }

        void setReceiver(ResponseContextReceiver receiver) {
            this.receiver = receiver;
        }
    }

    /**
     *
     */
    private class DispatchAsyncInvoker extends AsyncInvoker {
        private final T param;
        // snapshot the context now. this is necessary to avoid concurrency issue,
        // and is required by the spec
        private final RequestContext rc = requestContext.copy();

        DispatchAsyncInvoker(T param) {
            this.param = param;
        }

        public void do_run () {
            checkNullAllowed(param, rc, binding, mode);
            final Packet message = createPacket(param);
            message.setState(Packet.State.ClientRequest);
            message.nonNullAsyncHandlerGiven = this.nonNullAsyncHandlerGiven;
            resolveEndpointAddress(message, rc);
            setProperties(message,true);

            String action = null;
            String msgId = null;
            if (LOGGER.isLoggable(Level.FINE)) {
              AddressingVersion av = DispatchImpl.this.getBinding().getAddressingVersion();
              SOAPVersion sv = DispatchImpl.this.getBinding().getSOAPVersion();
              action =
                av != null && message.getMessage() != null ?
                  AddressingUtils.getAction(message.getMessage().getHeaders(), av, sv) : null;
              msgId =
                av != null&& message.getMessage() != null ?
                  AddressingUtils.getMessageID(message.getMessage().getHeaders(), av, sv) : null;
              LOGGER.fine("In DispatchAsyncInvoker.do_run for async message with action: " + action + " and msg ID: " + msgId);
            }

            final String actionUse = action;
            final String msgIdUse = msgId;

            Fiber.CompletionCallback callback = new Fiber.CompletionCallback() {
                public void onCompletion(@NotNull Packet response) {

                    if (LOGGER.isLoggable(Level.FINE)) {
                      LOGGER.fine("Done with processAsync in DispatchAsyncInvoker.do_run, and setting response for async message with action: " + actionUse + " and msg ID: " + msgIdUse);
                    }

                    Message msg = response.getMessage();

                    if (LOGGER.isLoggable(Level.FINE)) {
                      LOGGER.fine("Done with processAsync in DispatchAsyncInvoker.do_run, and setting response for async message with action: " + actionUse + " and msg ID: " + msgIdUse + " msg: " + msg);
                    }

                    try {
                        if(msg != null && msg.isFault() &&
                           !allowFaultResponseMsg) {
                            SOAPFaultBuilder faultBuilder = SOAPFaultBuilder.create(msg);
                            // passing null means there is no checked excpetion we're looking for all
                            // it will get back to us is a protocol exception
                            throw (SOAPFaultException)faultBuilder.createException(null);
                        }
                        responseImpl.setResponseContext(new ResponseContext(response));
                        responseImpl.set(toReturnValue(response), null);
                    } catch (JAXBException e) {
                        //TODO: i18nify
                        responseImpl.set(null, new DeserializationException(DispatchMessages.INVALID_RESPONSE_DESERIALIZATION(),e));
                    } catch(WebServiceException e){
                        //it could be a WebServiceException or a ProtocolException
                        responseImpl.set(null, e);
                    } catch(Throwable e){
                        // It could be any RuntimeException resulting due to some internal bug.
                        // or its some other exception resulting from user error, wrap it in
                        // WebServiceException
                        responseImpl.set(null, new WebServiceException(e));
                    }
                }
                public void onCompletion(@NotNull Throwable error) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                      LOGGER.fine("Done with processAsync in DispatchAsyncInvoker.do_run, and setting response for async message with action: " + actionUse + " and msg ID: " + msgIdUse + " Throwable: " + error.toString());
                    }
                    if (error instanceof WebServiceException) {
                        responseImpl.set(null, error);

                    } else {
                        //its RuntimeException or some other exception resulting from user error, wrap it in
                        // WebServiceException
                        responseImpl.set(null, new WebServiceException(error));
                    }
                }
            };
            processAsync(responseImpl,message,rc, callback);
        }
    }

    public void setOutboundHeaders(Object... headers) {
        throw new UnsupportedOperationException();
    }

    static final String HTTP_REQUEST_METHOD_GET="GET";
    static final String HTTP_REQUEST_METHOD_POST="POST";
    static final String HTTP_REQUEST_METHOD_PUT="PUT";

    @Deprecated
    public static Dispatch<Source> createSourceDispatch(QName port, Mode mode, WSServiceDelegate owner, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
        if(isXMLHttp(binding))
            return new RESTSourceDispatch(port,mode,owner,pipe,binding,epr);
        else
            return new SOAPSourceDispatch(port,mode,owner,pipe,binding,epr);
    }

    public static Dispatch<Source> createSourceDispatch(WSPortInfo portInfo, Mode mode, BindingImpl binding, WSEndpointReference epr) {
        if (isXMLHttp(binding))
            return new RESTSourceDispatch(portInfo, mode, binding, epr);
        else
            return new SOAPSourceDispatch(portInfo, mode, binding, epr);
    }
}
