/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.message;

import com.oracle.webservices.internal.api.message.ContentType;
import com.oracle.webservices.internal.api.message.PropertySet;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.marshaller.SAX2DOMEx;
import com.sun.xml.internal.ws.addressing.WsaPropertyBag;
import com.sun.xml.internal.ws.addressing.WsaServerTube;
import com.sun.xml.internal.ws.addressing.WsaTubeHelper;
import com.sun.xml.internal.ws.api.Component;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.WSDLOperationMapping;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.server.Adapter;
import com.sun.xml.internal.ws.api.server.TransportBackChannel;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.server.WebServiceContextDelegate;
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.client.*;
import com.sun.xml.internal.ws.developer.JAXWSProperties;
import com.sun.xml.internal.ws.encoding.MtomCodec;
import com.sun.xml.internal.ws.message.RelatesToHeader;
import com.sun.xml.internal.ws.message.StringHeader;
import com.sun.xml.internal.ws.util.DOMUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.wsdl.DispatchException;
import com.sun.xml.internal.ws.wsdl.OperationDispatcher;
import com.sun.xml.internal.ws.resources.AddressingMessages;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.LogicalMessageContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import javax.xml.ws.soap.MTOMFeature;

import java.util.*;
import java.util.logging.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;

/**
 * Represents a container of a {@link Message}.
 *
 * <h2>What is a {@link Packet}?</h2>
 * <p>
 * A packet can be thought of as a frame/envelope/package that wraps
 * a {@link Message}. A packet keeps track of optional metadata (properties)
 * about a {@link Message} that doesn't go across the wire.
 * This roughly corresponds to {@link MessageContext} in the JAX-WS API.
 *
 * <p>
 * Usually a packet contains a {@link Message} in it, but sometimes
 * (such as for a reply of an one-way operation), a packet may
 * float around without a {@link Message} in it.
 *
 *
 * <a name="properties"></a>
 * <h2>Properties</h2>
 * <p>
 * Information frequently used inside the JAX-WS RI
 * is stored in the strongly-typed fields. Other information is stored
 * in terms of a generic {@link Map} (see
 * {@link #invocationProperties}.)
 *
 * <p>
 * Some properties need to be retained between request and response,
 * some don't. For strongly typed fields, this characteristic is
 * statically known for each of them, and propagation happens accordingly.
 * For generic information stored in {@link Map}, {@link #invocationProperties}
 * stores per-invocation scope information (which carries over to
 * the response.)
 *
 * <p>
 * This object is used as the backing store of {@link MessageContext}, and
 * {@link LogicalMessageContext} and {@link SOAPMessageContext} will
 * be delegating to this object for storing/retrieving values.
 *
 *
 * <h3>Relationship to request/response context</h3>
 * <p>
 * {@link BindingProvider#getRequestContext() Request context} is used to
 * seed the initial values of {@link Packet}.
 * Some of those values go to strongly-typed fields, and others go to
 * {@link #invocationProperties}, as they need to be retained in the reply message.
 *
 * <p>
 * Similarly, {@link BindingProvider#getResponseContext() response context}
 * is constructed from {@link Packet} (or rather it's just a view of {@link Packet}.)
 * by using properties from {@link #invocationProperties},
 * modulo properties named explicitly in {@link #getHandlerScopePropertyNames(boolean)}.
 * IOW, properties added to {@link #invocationProperties}
 * are exposed to the response context by default.
 *
 *
 *
 * <h3>TODO</h3>
 * <ol>
 *  <li>this class needs to be cloneable since Message is copiable.
 *  <li>The three live views aren't implemented correctly. It will be
 *      more work to do so, although I'm sure it's possible.
 *  <li>{@link PropertySet.Property} annotation is to make it easy
 *      for {@link MessageContext} to export properties on this object,
 *      but it probably needs some clean up.
 * </ol>
 *
 * @author Kohsuke Kawaguchi
 */
public final class Packet
        // Packet must continue to extend/implement deprecated interfaces until downstream
        // usage is updated.
    extends com.oracle.webservices.internal.api.message.BaseDistributedPropertySet
    implements com.oracle.webservices.internal.api.message.MessageContext, MessageMetadata {

    /**
     * Creates a {@link Packet} that wraps a given {@link Message}.
     *
     * <p>
     * This method should be only used to create a fresh {@link Packet}.
     * To create a {@link Packet} for a reply, use {@link #createResponse(Message)}.
     *
     * @param request
     *      The request {@link Message}. Can be null.
     */
    public Packet(Message request) {
        this();
        this.message = request;
        if (message != null) message.setMessageMedadata(this);
    }

    /**
     * Creates an empty {@link Packet} that doesn't have any {@link Message}.
     */
    public Packet() {
        this.invocationProperties = new HashMap<String, Object>();
    }

    /**
     * Used by {@link #createResponse(Message)} and {@link #copy(boolean)}.
     */
    private Packet(Packet that) {
        relatePackets(that, true);
        this.invocationProperties = that.invocationProperties;
    }

    /**
     * Creates a copy of this {@link Packet}.
     *
     * @param copyMessage determines whether the {@link Message} from the original {@link Packet} should be copied as
     *        well, or not. If the value is {@code false}, the {@link Message} in the copy of the {@link Packet} is {@code null}.
     * @return copy of the original packet
     */
    public Packet copy(boolean copyMessage) {
        // the copy constructor is originally designed for creating a response packet,
        // but so far the implementation is usable for this purpose as well, so calling the copy constructor
        // to avoid code dupliation.
        Packet copy = new Packet(this);
        if (copyMessage && this.message != null) {
            copy.message = this.message.copy();
        }
        if (copy.message != null) copy.message.setMessageMedadata(copy);
        return copy;
    }

    private Message message;

    /**
     * Gets the last {@link Message} set through {@link #setMessage(Message)}.
     *
     * @return may null. See the class javadoc for when it's null.
     */
    public Message getMessage() {
        if (message != null && !(message instanceof MessageWrapper)) {
            message = new MessageWrapper(this, message);
        }
        return  message;
    }

    public Message getInternalMessage() {
        return (message instanceof MessageWrapper)? ((MessageWrapper)message).delegate : message;
    }

    public WSBinding getBinding() {
        if (endpoint != null) {
            return endpoint.getBinding();
        }
        if (proxy != null) {
            return (WSBinding) proxy.getBinding();
        }
        return null;
    }
    /**
     * Sets a {@link Message} to this packet.
     *
     * @param message Can be null.
     */
    public void setMessage(Message message) {
        this.message = message;
        if (message != null) this.message.setMessageMedadata(this);
    }

    // ALL NEW PACKETS SHOULD HAVE THIS AS false.
    // SETTING TO true MUST BE DONE EXPLICITLY,
    // NOT VIA COPYING/RELATING PACKETS.
    public  boolean isProtocolMessage() {
        return message != null && message.isProtocolMessage();
    }
    public void  setIsProtocolMessage() {
        assert message != null;
        message.setIsProtocolMessage();
    }

    private String    userStateId;
    public  String getUserStateId() {
        return userStateId;
    }
    public  void   setUserStateId(final String x) {
        assert x != null && x.length() <= 256;
        userStateId = x;
    }

    private WSDLOperationMapping wsdlOperationMapping = null;

    private QName wsdlOperation;

    /**
     * Returns the QName of the wsdl operation associated with this packet.
     * <p/>
     * Information such as Payload QName, wsa:Action header, SOAPAction HTTP header are used depending on the features
     * enabled on the particular port.
     *
     * @return null if there is no WSDL model or
     *         runtime cannot uniquely identify the wsdl operation from the information in the packet.
     */
    @Property(MessageContext.WSDL_OPERATION)
    public final
    @Nullable
    QName getWSDLOperation() {
        if (wsdlOperation != null) return wsdlOperation;
        if ( wsdlOperationMapping == null)  wsdlOperationMapping = getWSDLOperationMapping();
        if ( wsdlOperationMapping != null ) wsdlOperation = wsdlOperationMapping.getOperationName();
        return wsdlOperation;
    }

    public WSDLOperationMapping getWSDLOperationMapping() {
        if (wsdlOperationMapping != null) return wsdlOperationMapping;
        OperationDispatcher opDispatcher = null;
        if (endpoint != null) {
            opDispatcher = endpoint.getOperationDispatcher();
        } else if (proxy != null) {
            opDispatcher = ((Stub) proxy).getOperationDispatcher();
        }
        //OpDispatcher is null when there is no WSDLModel
        if (opDispatcher != null) {
            try {
                wsdlOperationMapping = opDispatcher.getWSDLOperationMapping(this);
            } catch (DispatchException e) {
                //Ignore, this might be a protocol message which may not have a wsdl operation
                //LOGGER.info("Cannot resolve wsdl operation that this Packet is targeted for.");
            }
        }
        return wsdlOperationMapping;
    }

    /**
     * Set the wsdl operation to avoid lookup from other data.
     * This is useful in SEI based clients, where the WSDL operation can be known
     * from the associated {@link JavaMethod}
     *
     * @param wsdlOp QName
     */
    public void setWSDLOperation(QName wsdlOp) {
        this.wsdlOperation = wsdlOp;
    }

    /**
     * True if this message came from a transport (IOW inbound),
     * and in paricular from a "secure" transport. A transport
     * needs to set this flag appropriately.
     *
     * <p>
     * This is a requirement from the security team.
     */
    // TODO: expose this as a property
    public boolean wasTransportSecure;

    /**
     * Inbound transport headers are captured in a transport neutral way.
     * Transports are expected to fill this data after creating a Packet.
     * <p>
     * {@link SOAPMessage#getMimeHeaders()} would return these headers.
     */
    public static final String INBOUND_TRANSPORT_HEADERS = "com.sun.xml.internal.ws.api.message.packet.inbound.transport.headers";

    /**
     * Outbound transport headers are captured in a transport neutral way.
     *
     * <p>
     * Transports may choose to ignore certain headers that interfere with
     * its correct operation, such as
     * {@code Content-Type} and {@code Content-Length}.
     */
    public static final String OUTBOUND_TRANSPORT_HEADERS = "com.sun.xml.internal.ws.api.message.packet.outbound.transport.headers";

    /**
     *
     */
    public static final String HA_INFO = "com.sun.xml.internal.ws.api.message.packet.hainfo";


    /**
     * This property holds the snapshot of HandlerConfiguration
     * at the time of invocation.
     * This property is used by MUPipe and HandlerPipe implementations.
     */
    @Property(BindingProviderProperties.JAXWS_HANDLER_CONFIG)
    public HandlerConfiguration handlerConfig;

    /**
     * If a message originates from a proxy stub that implements
     * a port interface, this field is set to point to that object.
     *
     * TODO: who's using this property?
     */
    @Property(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY)
    public BindingProvider proxy;

    /**
     * Determines if the governing {@link Adapter} or {@link com.sun.xml.internal.ws.api.pipe.Fiber.CompletionCallback}
     * will handle delivering response messages targeted at non-anonymous endpoint
     * addresses.  Prior to the introduction of this flag
     * the {@link WsaServerTube} would deliver non-anonymous responses.
     */
    public boolean isAdapterDeliversNonAnonymousResponse;

    /**
     * During invocation of a client Stub or Dispatch a Packet is
     * created then the Stub's RequestContext is copied into the
     * Packet.  On certain internal cases the Packet is created
     * *before* the invocation.  In those cases we want the contents
     * of the Packet to take precedence when ever any key/value pairs
     * collide : if the Packet contains a value for a key use it,
     * otherwise copy as usual from Stub.
     */
    public boolean packetTakesPriorityOverRequestContext = false;

    /**
     * The endpoint address to which this message is sent to.
     *
     * <p>
     * The JAX-WS spec allows this to be changed for each message,
     * so it's designed to be a property.
     *
     * <p>
     * Must not be null for a request message on the client. Otherwise
     * it's null.
     */
    public EndpointAddress endpointAddress;

    /**
     * @deprecated
     *      The programatic acccess should be done via
     *      {@link #endpointAddress}. This is for JAX-WS client applications
     *      that access this property via {@link BindingProvider#ENDPOINT_ADDRESS_PROPERTY}.
     */
    @Property(BindingProvider.ENDPOINT_ADDRESS_PROPERTY)
    public String getEndPointAddressString() {
        if (endpointAddress == null) {
            return null;
        } else {
            return endpointAddress.toString();
        }
    }

    public void setEndPointAddressString(String s) {
        if (s == null) {
            this.endpointAddress = null;
        } else {
            this.endpointAddress = EndpointAddress.create(s);
        }
    }

    /**
     * The value of {@link ContentNegotiation#PROPERTY}
     * property.
     * <p/>
     * This property is used only on the client side.
     */
    public ContentNegotiation contentNegotiation;

    @Property(ContentNegotiation.PROPERTY)
    public String getContentNegotiationString() {
        return (contentNegotiation != null) ? contentNegotiation.toString() : null;
    }

    public void setContentNegotiationString(String s) {
        if (s == null) {
            contentNegotiation = null;
        } else {
            try {
                contentNegotiation = ContentNegotiation.valueOf(s);
            } catch (IllegalArgumentException e) {
                // If the value is not recognized default to none
                contentNegotiation = ContentNegotiation.none;
            }
        }
    }

    /**
     * Gives a list of Reference Parameters in the Message
     * <p>
     * Headers which have attribute wsa:IsReferenceParameter="true"
     * This is not cached as one may reset the Message.
     *<p>
     */
    @Property(MessageContext.REFERENCE_PARAMETERS)
    public
    @NotNull
    List<Element> getReferenceParameters() {
        Message msg = getMessage();
        List<Element> refParams = new ArrayList<Element>();
        if (msg == null) {
            return refParams;
        }
        MessageHeaders hl = msg.getHeaders();
        for (Header h : hl.asList()) {
            String attr = h.getAttribute(AddressingVersion.W3C.nsUri, "IsReferenceParameter");
            if (attr != null && (attr.equals("true") || attr.equals("1"))) {
                Document d = DOMUtil.createDom();
                SAX2DOMEx s2d = new SAX2DOMEx(d);
                try {
                    h.writeTo(s2d, XmlUtil.DRACONIAN_ERROR_HANDLER);
                    refParams.add((Element) d.getLastChild());
                } catch (SAXException e) {
                    throw new WebServiceException(e);
                }
                /*
                DOMResult result = new DOMResult(d);
                XMLDOMWriterImpl domwriter = new XMLDOMWriterImpl(result);
                try {
                    h.writeTo(domwriter);
                    refParams.add((Element) result.getNode().getLastChild());
                } catch (XMLStreamException e) {
                    throw new WebServiceException(e);
                }
                */
            }
        }
        return refParams;
    }

    /**
     *      This method is for exposing header list through {@link PropertySet#get(Object)},
     *      for user applications, and should never be invoked directly from within the JAX-WS RI.
     */
    @Property(JAXWSProperties.INBOUND_HEADER_LIST_PROPERTY)
    /*package*/ MessageHeaders getHeaderList() {
        Message msg = getMessage();
        if (msg == null) {
            return null;
        }
        return msg.getHeaders();
    }

    /**
     * The list of MIME types that are acceptable to a receiver
     * of an outbound message.
     *
     * This property is used only on the server side.
     *
     * <p>The representation shall be that specified by the HTTP Accept
     * request-header field.
     *
     * <p>The list of content types will be obtained from the transport
     * meta-data of a inbound message in a request/response message exchange.
     * Hence this property will be set by the service-side transport pipe.
     */
    public String acceptableMimeTypes;

    /**
     * When non-null, this object is consulted to
     * implement {@link WebServiceContext} methods
     * exposed to the user application.
     *
     * Used only on the server side.
     *
     * <p>
     * This property is set from the parameter
     * of {@link WSEndpoint.PipeHead#process}.
     */
    public WebServiceContextDelegate webServiceContextDelegate;

    /**
     * Used only on the server side so that the transport
     * can close the connection early.
     *
     * <p>
     * This field can be null. While a message is being processed,
     * this field can be set explicitly to null, to prevent
     * future pipes from closing a transport (see {@link #keepTransportBackChannelOpen()})
     *
     * <p>
     * This property is set from the parameter
     * of {@link WSEndpoint.PipeHead#process}.
     */
    public
    @Nullable
    TransportBackChannel transportBackChannel;

    /**
     * Keeps the transport back channel open (by seeting {@link #transportBackChannel} to null.)
     *
     * @return
     *      The previous value of {@link #transportBackChannel}.
     */
    public TransportBackChannel keepTransportBackChannelOpen() {
        TransportBackChannel r = transportBackChannel;
        transportBackChannel = null;
        return r;
    }

    /**
      * The governing owner of this packet.  On the service-side this is the {@link Adapter} and on the client it is the {@link Stub}.
      *
      */
     public Component component;

    /**
     * The governing {@link WSEndpoint} in which this message is floating.
     *
     * <p>
     * This property is set if and only if this is on the server side.
     */
    @Property(JAXWSProperties.WSENDPOINT)
    public WSEndpoint endpoint;

    /**
     * The value of the SOAPAction header associated with the message.
     *
     * <p>
     * For outgoing messages, the transport may sends out this value.
     * If this field is null, the transport may choose to send {@code ""}
     * (quoted empty string.)
     *
     * For incoming messages, the transport will set this field.
     * If the incoming message did not contain the SOAPAction header,
     * the transport sets this field to null.
     *
     * <p>
     * If the value is non-null, it must be always in the quoted form.
     * The value can be null.
     *
     * <p>
     * Note that the way the transport sends this value out depends on
     * transport and SOAP version.
     * <p/>
     * For HTTP transport and SOAP 1.1, BP requires that SOAPAction
     * header is present (See {@BP R2744} and {@BP R2745}.) For SOAP 1.2,
     * this is moved to the parameter of the "application/soap+xml".
     */
    @Property(BindingProvider.SOAPACTION_URI_PROPERTY)
    public String soapAction;

    /**
     * A hint indicating that whether a transport should expect
     * a reply back from the server.
     *
     * <p>
     * This property is used on the client-side for
     * outbound messages, so that a pipeline
     * can communicate to the terminal (or intermediate) {@link Tube}s
     * about this knowledge.
     *
     * <p>
     * This property <b>MUST NOT</b> be used by 2-way transports
     * that have the transport back channel. Those transports
     * must always check a reply coming through the transport back
     * channel regardless of this value, and act accordingly.
     * (This is because the expectation of the client and
     * that of the server can be different, for example because
     * of a bug in user's configuration.)
     *
     * <p>
     * This property is for one-way transports, and more
     * specifically for the coordinator that correlates sent requests
     * and incoming replies, to decide whether to block
     * until a response is received.
     *
     * <p>
     * Also note that this property is related to
     * {@link WSDLOperation#isOneWay()} but not the same thing.
     * In fact in general, they are completely orthogonal.
     *
     * For example, the calling application can choose to invoke
     * {@link Dispatch#invoke(Object)} or {@link Dispatch#invokeOneWay(Object)}
     * with an operation (which determines the value of this property),
     * regardless of whether WSDL actually says it's one way or not.
     * So these two booleans can take any combinations.
     *
     *
     * <p>
     * When this property is {@link Boolean#FALSE}, it means that
     * the pipeline does not expect a reply from a server (and therefore
     * the correlator should not block for a reply message
     * -- if such a reply does arrive, it can be just ignored.)
     *
     * <p>
     * When this property is {@link Boolean#TRUE}, it means that
     * the pipeline expects a reply from a server (and therefore
     * the correlator should block to see if a reply message is received,
     *
     * <p>
     * This property is always set to {@link Boolean#TRUE} or
     * {@link Boolean#FALSE} when used on the request message
     * on the client side.
     * No other {@link Boolean} instances are allowed.
     * <p>
     *
     * In all other situations, this property is null.
     *
     */
    @Property(BindingProviderProperties.ONE_WAY_OPERATION)
    public Boolean expectReply;


    /**
     * This property will be removed in a near future.
     *
     * <p>
     * A part of what this flag represented moved to
     * {@link #expectReply} and the other part was moved
     * to {@link Message#isOneWay(WSDLPort)}. Please update
     * your code soon, or risk breaking your build!!
     */
    @Deprecated
    public Boolean isOneWay;

    /**
     * Indicates whether is invoking a synchronous pattern. If true, no
     * async client programming model (e.g. AsyncResponse or AsyncHandler)
     * were used to make the request that created this packet.
     */
    public Boolean isSynchronousMEP;

    /**
     * Indicates whether a non-null AsyncHandler was given at the point of
     * making the request that created this packet. This flag can be used
     * by Tube implementations to decide how to react when isSynchronousMEP
     * is false. If true, the client gave a non-null AsyncHandler instance
     * at the point of request, and will be expecting a response on that
     * handler when this request has been processed.
     */
    public Boolean nonNullAsyncHandlerGiven;

    /**
     * USE-CASE:
     * WS-AT is enabled, but there is no WSDL available.
     * If Packet.isRequestReplyMEP() is Boolean.TRUE then WS-AT should
     * add the TX context.
     *
     * This value is exposed to users via facades at higher abstraction layers.
     * The user should NEVER use Packet directly.
     * This value should ONLY be set by users.
     */
    private Boolean isRequestReplyMEP;
    public Boolean isRequestReplyMEP() { return isRequestReplyMEP; }
    public void setRequestReplyMEP(final Boolean x) { isRequestReplyMEP = x; }

    /**
     * Lazily created set of handler-scope property names.
     *
     * <p>
     * We expect that this is only used when handlers are present
     * and they explicitly set some handler-scope values.
     *
     * @see #getHandlerScopePropertyNames(boolean)
     */
    private Set<String> handlerScopePropertyNames;

    /**
     * Bag to capture properties that are available for the whole
     * message invocation (namely on both requests and responses.)
     *
     * <p>
     * These properties are copied from a request to a response.
     * This is where we keep properties that are set by handlers.
     *
     * <p>
     * See <a href="#properties">class javadoc</a> for more discussion.
     *
     * @see #getHandlerScopePropertyNames(boolean)
     */
    public final Map<String, Object> invocationProperties;

    /**
     * Gets a {@link Set} that stores handler-scope properties.
     *
     * <p>
     * These properties will not be exposed to the response context.
     * Consequently, if a {@link Tube} wishes to hide a property
     * to {@link ResponseContext}, it needs to add the property name
     * to this set.
     *
     * @param readOnly
     *      Return true if the caller only intends to read the value of this set.
     *      Internally, the {@link Set} is allocated lazily, and this flag helps
     *      optimizing the strategy.
     *
     * @return
     *      always non-null, possibly empty set that stores property names.
     */
    public final Set<String> getHandlerScopePropertyNames(boolean readOnly) {
        Set<String> o = this.handlerScopePropertyNames;
        if (o == null) {
            if (readOnly) {
                return Collections.emptySet();
            }
            o = new HashSet<String>();
            this.handlerScopePropertyNames = o;
        }
        return o;
    }

    /**
     * This method no longer works.
     *
     * @deprecated
     *      Use {@link #getHandlerScopePropertyNames(boolean)}.
     *      To be removed once Tango components are updated.
     */
    public final Set<String> getApplicationScopePropertyNames(boolean readOnly) {
        assert false;
        return new HashSet<String>();
    }

    /**
     * Creates a response {@link Packet} from a request packet ({@code this}).
     *
     * <p>
     * When a {@link Packet} for a reply is created, some properties need to be
     * copied over from a request to a response, and this method handles it correctly.
     *
     * @deprecated
     *      Use createClientResponse(Message) for client side and
     *      createServerResponse(Message, String) for server side response
     *      creation.
     *
     * @param msg
     *      The {@link Message} that represents a reply. Can be null.
     */
    @Deprecated
    public Packet createResponse(Message msg) {
        Packet response = new Packet(this);
        response.setMessage(msg);
        return response;
    }

    /**
     * Creates a response {@link Packet} from a request packet ({@code this}).
     *
     * <p>
     * When a {@link Packet} for a reply is created, some properties need to be
     * copied over from a request to a response, and this method handles it correctly.
     *
     * @param msg
     *      The {@link Message} that represents a reply. Can be null.
     */
    public Packet createClientResponse(Message msg) {
        Packet response = new Packet(this);
        response.setMessage(msg);
        finishCreateRelateClientResponse(response);
        return response;
    }

    /**
     * For use cases that start with an existing Packet.
     */
    public Packet relateClientResponse(final Packet response) {
        response.relatePackets(this, true);
        finishCreateRelateClientResponse(response);
        return response;
    }

    private void finishCreateRelateClientResponse(final Packet response) {
        response.soapAction = null; // de-initializing
        response.setState(State.ClientResponse);
    }

    /**
     * Creates a server-side response {@link Packet} from a request
     * packet ({@code this}). If WS-Addressing is enabled, a default Action
     * Message Addressing Property is obtained using <code>wsdlPort</code> {@link WSDLPort}
     * and <code>binding</code> {@link WSBinding}.
     * <p><p>
     * This method should be called to create application response messages
     * since they are associated with a {@link WSBinding} and {@link WSDLPort}.
     * For creating protocol messages that require a non-default Action, use
     * {@link #createServerResponse(Message, com.sun.xml.internal.ws.api.addressing.AddressingVersion, com.sun.xml.internal.ws.api.SOAPVersion, String)}.
     *
     * @param responseMessage The {@link Message} that represents a reply. Can be null.
     * @param wsdlPort The response WSDL port.
     * @param binding The response Binding. Cannot be null.
     * @return response packet
     */
    public Packet createServerResponse(@Nullable Message responseMessage, @Nullable WSDLPort wsdlPort, @Nullable SEIModel seiModel, @NotNull WSBinding binding) {
        Packet r = createClientResponse(responseMessage);
        return relateServerResponse(r, wsdlPort, seiModel, binding);
    }

    /**
     * Copy all properties from ({@code this}) packet into a input {@link Packet}
     * @param response packet
     */
    public void copyPropertiesTo(@Nullable Packet response){
        relatePackets(response, false);
    }


    /**
     * A common method to make members related between input packet and this packet
     *
     * @param packet
     * @param isCopy 'true' means copying all properties from input packet;
     *               'false' means copying all properties from this packet to input packet.
     */
    private void relatePackets(@Nullable Packet packet, boolean isCopy)
    {
        Packet request;
            Packet response;

        if (!isCopy) { //is relate
          request = this;
          response = packet;

          // processing specific properties
          response.soapAction = null;
          response.invocationProperties.putAll(request.invocationProperties);
          if (this.getState().equals(State.ServerRequest)) {
              response.setState(State.ServerResponse);
          }
        } else { //is copy constructor
          request = packet;
          response = this;

          // processing specific properties
          response.soapAction = request.soapAction;
          response.setState(request.getState());
        }

        request.copySatelliteInto(response);
        response.isAdapterDeliversNonAnonymousResponse = request.isAdapterDeliversNonAnonymousResponse;
        response.handlerConfig = request.handlerConfig;
        response.handlerScopePropertyNames = request.handlerScopePropertyNames;
        response.contentNegotiation = request.contentNegotiation;
        response.wasTransportSecure = request.wasTransportSecure;
        response.transportBackChannel = request.transportBackChannel;
        response.endpointAddress = request.endpointAddress;
        response.wsdlOperation = request.wsdlOperation;
        response.wsdlOperationMapping = request.wsdlOperationMapping;
        response.acceptableMimeTypes = request.acceptableMimeTypes;
        response.endpoint = request.endpoint;
        response.proxy = request.proxy;
        response.webServiceContextDelegate = request.webServiceContextDelegate;
        response.expectReply = request.expectReply;
        response.component = request.component;
        response.mtomAcceptable = request.mtomAcceptable;
        response.mtomRequest = request.mtomRequest;
        response.userStateId = request.userStateId;
        // copy other properties that need to be copied. is there any?
    }


    public Packet relateServerResponse(@Nullable Packet r, @Nullable WSDLPort wsdlPort, @Nullable SEIModel seiModel, @NotNull WSBinding binding) {
        relatePackets(r, false);
        r.setState(State.ServerResponse);
        AddressingVersion av = binding.getAddressingVersion();
        // populate WS-A headers only if WS-A is enabled
        if (av == null) {
            return r;
        }

        if (getMessage() == null) {
            return r;
        }

        //populate WS-A headers only if the request has addressing headers
        String inputAction = AddressingUtils.getAction(getMessage().getHeaders(), av, binding.getSOAPVersion());
        if (inputAction == null) {
            return r;
        }
        // if one-way, then dont populate any WS-A headers
        if (r.getMessage() == null || (wsdlPort != null && getMessage().isOneWay(wsdlPort))) {
            return r;
        }

        // otherwise populate WS-Addressing headers
        populateAddressingHeaders(binding, r, wsdlPort, seiModel);
        return r;
    }

    /**
     * Creates a server-side response {@link Packet} from a request
     * packet ({@code this}). If WS-Addressing is enabled, <code>action</code>
     * is used as Action Message Addressing Property.
     * <p><p>
     * This method should be called only for creating protocol response messages
     * that require a particular value of Action since they are not associated
     * with a {@link WSBinding} and {@link WSDLPort} but do know the {@link AddressingVersion}
     * and {@link SOAPVersion}.
     *
     * @param responseMessage The {@link Message} that represents a reply. Can be null.
     * @param addressingVersion The WS-Addressing version of the response message.
     * @param soapVersion The SOAP version of the response message.
     * @param action The response Action Message Addressing Property value.
     * @return response packet
     */
    public Packet createServerResponse(@Nullable Message responseMessage, @NotNull AddressingVersion addressingVersion, @NotNull SOAPVersion soapVersion, @NotNull String action) {
        Packet responsePacket = createClientResponse(responseMessage);
        responsePacket.setState(State.ServerResponse);
        // populate WS-A headers only if WS-A is enabled
        if (addressingVersion == null) {
            return responsePacket;
        }
        //populate WS-A headers only if the request has addressing headers
        String inputAction = AddressingUtils.getAction(this.getMessage().getHeaders(), addressingVersion, soapVersion);
        if (inputAction == null) {
            return responsePacket;
        }

        populateAddressingHeaders(responsePacket, addressingVersion, soapVersion, action, false);
        return responsePacket;
    }

    /**
     * Overwrites the {@link Message} of the response packet ({@code this}) by the given {@link Message}.
     * Unlike {@link #setMessage(Message)}, fill in the addressing headers correctly, and this process
     * requires the access to the request packet.
     *
     * <p>
     * This method is useful when the caller needs to swap a response message completely to a new one.
     *
     * @see #createServerResponse(Message, AddressingVersion, SOAPVersion, String)
     */
    public void setResponseMessage(@NotNull Packet request, @Nullable Message responseMessage, @NotNull AddressingVersion addressingVersion, @NotNull SOAPVersion soapVersion, @NotNull String action) {
        Packet temp = request.createServerResponse(responseMessage, addressingVersion, soapVersion, action);
        setMessage(temp.getMessage());
    }

    private void populateAddressingHeaders(Packet responsePacket, AddressingVersion av, SOAPVersion sv, String action, boolean mustUnderstand) {
        // populate WS-A headers only if WS-A is enabled
        if (av == null) return;

        // if one-way, then dont populate any WS-A headers
        if (responsePacket.getMessage() == null)
            return;

        MessageHeaders hl = responsePacket.getMessage().getHeaders();

        WsaPropertyBag wpb = getSatellite(WsaPropertyBag.class);
        Message msg = getMessage();
        // wsa:To
        WSEndpointReference replyTo = null;
        Header replyToFromRequestMsg = AddressingUtils.getFirstHeader(msg.getHeaders(), av.replyToTag, true, sv);
        Header replyToFromResponseMsg = hl.get(av.toTag, false);
        boolean replaceToTag = true;
        try{
            if (replyToFromRequestMsg != null){
                replyTo = replyToFromRequestMsg.readAsEPR(av);
            }
            if (replyToFromResponseMsg != null && replyTo == null) {
                replaceToTag = false;
            }
        } catch (XMLStreamException e) {
            throw new WebServiceException(AddressingMessages.REPLY_TO_CANNOT_PARSE(), e);
        }
        if (replyTo == null) {
              replyTo = AddressingUtils.getReplyTo(msg.getHeaders(), av, sv);
        }

        // wsa:Action, add if the message doesn't already contain it,
        // generally true for SEI case where there is SEIModel or WSDLModel
        //           false for Provider with no wsdl, Expects User to set the coresponding header on the Message.
        if (AddressingUtils.getAction(responsePacket.getMessage().getHeaders(), av, sv) == null) {
            //wsa:Action header is not set in the message, so use the wsa:Action  passed as the parameter.
            hl.add(new StringHeader(av.actionTag, action, sv, mustUnderstand));
        }

        // wsa:MessageID
        if (responsePacket.getMessage().getHeaders().get(av.messageIDTag, false) == null) {
            // if header doesn't exist, method getID creates a new random id
            String newID = Message.generateMessageID();
            hl.add(new StringHeader(av.messageIDTag, newID));
        }

        // wsa:RelatesTo
        String mid = null;
        if (wpb != null) {
            mid = wpb.getMessageID();
        }
        if (mid == null) {
            mid = AddressingUtils.getMessageID(msg.getHeaders(), av, sv);
        }
        if (mid != null) {
            hl.addOrReplace(new RelatesToHeader(av.relatesToTag, mid));
        }


        // populate reference parameters
        WSEndpointReference refpEPR = null;
        if (responsePacket.getMessage().isFault()) {
            // choose FaultTo
            if (wpb != null) {
                refpEPR = wpb.getFaultToFromRequest();
            }
            if (refpEPR == null) {
                refpEPR = AddressingUtils.getFaultTo(msg.getHeaders(), av, sv);
            }
            // if FaultTo is null, then use ReplyTo
            if (refpEPR == null) {
                refpEPR = replyTo;
            }
        } else {
            // choose ReplyTo
            refpEPR = replyTo;
        }
        if (replaceToTag && refpEPR != null) {
            hl.addOrReplace(new StringHeader(av.toTag, refpEPR.getAddress()));
            refpEPR.addReferenceParametersToList(hl);
        }
    }

    private void populateAddressingHeaders(WSBinding binding, Packet responsePacket, WSDLPort wsdlPort, SEIModel seiModel) {
        AddressingVersion addressingVersion = binding.getAddressingVersion();

        if (addressingVersion == null) {
            return;
        }

        WsaTubeHelper wsaHelper = addressingVersion.getWsaHelper(wsdlPort, seiModel, binding);
        String action = responsePacket.getMessage().isFault() ?
                wsaHelper.getFaultAction(this, responsePacket) :
                wsaHelper.getOutputAction(this);
        if (action == null) {
            LOGGER.info("WSA headers are not added as value for wsa:Action cannot be resolved for this message");
            return;
        }
        populateAddressingHeaders(responsePacket, addressingVersion, binding.getSOAPVersion(), action, AddressingVersion.isRequired(binding));
    }

    public String toShortString() {
      return super.toString();
    }

    // For use only in a debugger
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      buf.append(super.toString());
      String content;
        try {
            Message msg = getMessage();
        if (msg != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        XMLStreamWriter xmlWriter = XMLStreamWriterFactory.create(baos, "UTF-8");
                        msg.copy().writeTo(xmlWriter);
                        xmlWriter.flush();
                        xmlWriter.close();
                        baos.flush();
                        XMLStreamWriterFactory.recycle(xmlWriter);

                        byte[] bytes = baos.toByteArray();
                        //message = Messages.create(XMLStreamReaderFactory.create(null, new ByteArrayInputStream(bytes), "UTF-8", true));
                        content = new String(bytes, "UTF-8");
                } else {
                    content = "<none>";
        }
        } catch (Throwable t) {
                throw new WebServiceException(t);
        }
      buf.append(" Content: ").append(content);
      return buf.toString();
    }

    // completes TypedMap
    private static final PropertyMap model;

    static {
        model = parse(Packet.class);
    }

    @Override
    protected PropertyMap getPropertyMap() {
        return model;
    }

    public Map<String, Object> asMapIncludingInvocationProperties() {
        final Map<String, Object> asMap = asMap();
        return new AbstractMap<String, Object>() {
            @Override
            public Object get(Object key) {
                Object o = asMap.get(key);
                if (o != null)
                    return o;

                return invocationProperties.get(key);
            }

            @Override
            public int size() {
                return asMap.size() + invocationProperties.size();
            }

            @Override
            public boolean containsKey(Object key) {
                if (asMap.containsKey(key))
                    return true;
                return invocationProperties.containsKey(key);
            }

            @Override
            public Set<Entry<String, Object>> entrySet() {
                final Set<Entry<String, Object>> asMapEntries = asMap.entrySet();
                final Set<Entry<String, Object>> ipEntries = invocationProperties.entrySet();

                return new AbstractSet<Entry<String, Object>>() {
                    @Override
                    public Iterator<Entry<String, Object>> iterator() {
                        final Iterator<Entry<String, Object>> asMapIt = asMapEntries.iterator();
                        final Iterator<Entry<String, Object>> ipIt = ipEntries.iterator();

                        return new Iterator<Entry<String, Object>>() {
                            @Override
                            public boolean hasNext() {
                                return asMapIt.hasNext() || ipIt.hasNext();
                            }

                            @Override
                            public java.util.Map.Entry<String, Object> next() {
                                if (asMapIt.hasNext())
                                    return asMapIt.next();
                                return ipIt.next();
                            }

                            @Override
                            public void remove() {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return asMap.size() + invocationProperties.size();
                    }
                };
            }

            @Override
            public Object put(String key, Object value) {
                if (supports(key))
                    return asMap.put(key, value);

                return invocationProperties.put(key, value);
            }

            @Override
            public void clear() {
                asMap.clear();
                invocationProperties.clear();
            }

            @Override
            public Object remove(Object key) {
                if (supports(key))
                    return asMap.remove(key);

                return invocationProperties.remove(key);
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(Packet.class.getName());

    @Override
    public SOAPMessage getSOAPMessage() throws SOAPException {
        return getAsSOAPMessage();
    }

    //TODO replace the message to a SAAJMEssage issue - JRFSAAJMessage or SAAJMessage?
    @Override
    public SOAPMessage getAsSOAPMessage() throws SOAPException {
        Message msg = this.getMessage();
        if (msg == null)
            return null;
        if (msg instanceof MessageWritable)
            ((MessageWritable) msg).setMTOMConfiguration(mtomFeature);
        return msg.readAsSOAPMessage(this, this.getState().isInbound());
    }

    public
    Codec codec = null;
    public Codec getCodec() {
        if (codec != null) {
            return codec;
        }
        if (endpoint != null) {
            codec = endpoint.createCodec();
        }
        WSBinding wsb = getBinding();
        if (wsb != null) {
            codec = wsb.getBindingId().createEncoder(wsb);
        }
        return codec;
    }

    @Override
    public com.oracle.webservices.internal.api.message.ContentType writeTo( OutputStream out ) throws IOException {
        Message msg = getInternalMessage();
        if (msg instanceof MessageWritable) {
            ((MessageWritable) msg).setMTOMConfiguration(mtomFeature);
            return ((MessageWritable)msg).writeTo(out);
        }
        return getCodec().encode(this, out);
    }

    public com.oracle.webservices.internal.api.message.ContentType writeTo( WritableByteChannel buffer ) {
        return getCodec().encode(this, buffer);
    }

    /**
     * This content type may be set by one of the following ways:
     * (1) By the codec as a result of decoding an incoming message
     * (2) Cached by a codec after encoding the message
     * (3) By a caller of Codec.decode(InputStream, String contentType, Packet)
     */
    private ContentType contentType;

    /**
     * If the request's Content-Type is multipart/related; type=application/xop+xml, then this set to to true
     *
     * Used on server-side, for encoding the repsonse.
     */
    private Boolean mtomRequest;

    /**
     * Based on request's Accept header this is set.
     * Currently only set if MTOMFeature is enabled.
     *
     * Should be used on server-side, for encoding the response.
     */
    private Boolean mtomAcceptable;

    private MTOMFeature mtomFeature;

    public Boolean getMtomRequest() {
        return mtomRequest;
    }

    public void setMtomRequest(Boolean mtomRequest) {
        this.mtomRequest = mtomRequest;
    }

    public Boolean getMtomAcceptable() {
        return mtomAcceptable;
    }

    Boolean checkMtomAcceptable;
    public void checkMtomAcceptable() {
        if (checkMtomAcceptable == null) {
            if (acceptableMimeTypes == null || isFastInfosetDisabled) {
                checkMtomAcceptable = false;
            } else {
                checkMtomAcceptable = (acceptableMimeTypes.indexOf(MtomCodec.XOP_XML_MIME_TYPE) != -1);
//                StringTokenizer st = new StringTokenizer(acceptableMimeTypes, ",");
//                while (st.hasMoreTokens()) {
//                    final String token = st.nextToken().trim();
//                    if (token.toLowerCase().contains(MtomCodec.XOP_XML_MIME_TYPE)) {
//                        mtomAcceptable = true;
//                    }
//                }
//                if (mtomAcceptable == null) mtomAcceptable = false;
            }
        }
        mtomAcceptable = checkMtomAcceptable;
    }

    private Boolean fastInfosetAcceptable;

    public Boolean getFastInfosetAcceptable(String fiMimeType) {
        if (fastInfosetAcceptable == null) {
            if (acceptableMimeTypes == null || isFastInfosetDisabled) {
                fastInfosetAcceptable = false;
            } else {
                fastInfosetAcceptable = (acceptableMimeTypes.indexOf(fiMimeType) != -1);
            }
//        if (accept == null || isFastInfosetDisabled) return false;
//
//        StringTokenizer st = new StringTokenizer(accept, ",");
//        while (st.hasMoreTokens()) {
//            final String token = st.nextToken().trim();
//            if (token.equalsIgnoreCase(fiMimeType)) {
//                return true;
//            }
//        }
//        return false;
        }
        return fastInfosetAcceptable;
    }


    public void setMtomFeature(MTOMFeature mtomFeature) {
        this.mtomFeature = mtomFeature;
    }

    public MTOMFeature getMtomFeature() {
        //If we have a binding, use that in preference to an explicitly
        //set MTOMFeature
        WSBinding binding = getBinding();
        if (binding != null) {
            return binding.getFeature(MTOMFeature.class);
        }
        return mtomFeature;
    }

    @Override
    public com.oracle.webservices.internal.api.message.ContentType getContentType() {
        if (contentType == null) {
            contentType = getInternalContentType();
        }
        if (contentType == null) {
            contentType = getCodec().getStaticContentType(this);
        }
        if (contentType == null) {
            //TODO write to buffer
        }
        return contentType;
    }

    public ContentType getInternalContentType() {
        Message msg = getInternalMessage();
        if (msg instanceof MessageWritable) {
            MessageWritable mw = (MessageWritable) msg;

            //bug 18121499 fix
            mw.setMTOMConfiguration(mtomFeature);

            return mw.getContentType();
        }
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public enum Status {
        Request, Response, Unknown;
        public boolean isRequest()  { return Request.equals(this); }
        public boolean isResponse() { return Response.equals(this); }
    }

    public enum State {
        ServerRequest(true), ClientRequest(false), ServerResponse(false), ClientResponse(true);
        private boolean inbound;
        State(boolean inbound) {
            this.inbound = inbound;
        }
        public boolean isInbound() {
            return inbound;
        }
    }

//    private Status status = Status.Unknown;

    //Default state is ServerRequest - some custom adapters may not set the value of state
    //upon server request - all other code paths should set it
    private State state = State.ServerRequest;

//    public Status getStatus() { return status; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public boolean shouldUseMtom() {
        if (getState().isInbound()) {
            return isMtomContentType();
        } else {
            return shouldUseMtomOutbound();
        }
    }

    private boolean shouldUseMtomOutbound() {
        //Use the getter to make sure all the logic is executed correctly
        MTOMFeature myMtomFeature = getMtomFeature();
        if(myMtomFeature != null && myMtomFeature.isEnabled()) {
                //If the content type is set already on this outbound Packet,
                //(e.g.) through Codec.decode(InputStream, String contentType, Packet)
                //and it is a non-mtom content type, then don't use mtom to encode it
                ContentType curContentType = getInternalContentType();
                if (curContentType != null && !isMtomContentType(curContentType)) {
                        return false;
                }
            //On client, always use XOP encoding if MTOM is enabled
            //On Server, mtomAcceptable and mtomRequest will be set - use XOP encoding
            //if either request is XOP encoded (mtomRequest) or
            //client accepts XOP encoding (mtomAcceptable)
            if (getMtomAcceptable() == null && getMtomRequest() == null) {
                return true;
            } else {
                if (getMtomAcceptable() != null &&  getMtomAcceptable() && getState().equals(State.ServerResponse)) {
                    return true;
                }
                if (getMtomRequest() != null && getMtomRequest() && getState().equals(State.ServerResponse)) {
                    return true;
                }
                if (getMtomRequest() != null && getMtomRequest() && getState().equals(State.ClientRequest)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMtomContentType() {
        return (getInternalContentType() != null && isMtomContentType(getInternalContentType()));
    }

    private boolean isMtomContentType(ContentType cType) {
                return cType.getContentType().contains("application/xop+xml");
        }

        /**
     * @deprecated
     */
    public void addSatellite(@NotNull com.sun.xml.internal.ws.api.PropertySet satellite) {
        super.addSatellite(satellite);
    }

    /**
     * @deprecated
     */
    public void addSatellite(@NotNull Class keyClass, @NotNull com.sun.xml.internal.ws.api.PropertySet satellite) {
        super.addSatellite(keyClass, satellite);
    }

    /**
     * @deprecated
     */
    public void copySatelliteInto(@NotNull com.sun.xml.internal.ws.api.DistributedPropertySet r) {
        super.copySatelliteInto(r);
    }

    /**
     * @deprecated
     */
    public void removeSatellite(com.sun.xml.internal.ws.api.PropertySet satellite) {
        super.removeSatellite(satellite);
    }

    /**
     * This is propogated from SOAPBindingCodec and will affect isMtomAcceptable and isFastInfosetAcceptable
     */
    private boolean isFastInfosetDisabled;

    public void setFastInfosetDisabled(boolean b) {
        isFastInfosetDisabled = b;
    }
}
