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
package com.sun.xml.internal.ws.api.message;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.marshaller.SAX2DOMEx;
import com.sun.xml.internal.ws.addressing.WsaTubeHelper;
import com.sun.xml.internal.ws.addressing.model.InvalidAddressingHeaderException;
import com.sun.xml.internal.ws.api.DistributedPropertySet;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.PropertySet;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.server.TransportBackChannel;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.server.WebServiceContextDelegate;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.client.ContentNegotiation;
import com.sun.xml.internal.ws.client.HandlerConfiguration;
import com.sun.xml.internal.ws.client.ResponseContext;
import com.sun.xml.internal.ws.developer.JAXWSProperties;
import com.sun.xml.internal.ws.message.RelatesToHeader;
import com.sun.xml.internal.ws.message.StringHeader;
import com.sun.xml.internal.ws.util.DOMUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.soap.SOAPMessage;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.LogicalMessageContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *  <li>{@link Property} annotation is to make it easy
 *      for {@link MessageContext} to export properties on this object,
 *      but it probably needs some clean up.
 * </ol>
 *
 * @author Kohsuke Kawaguchi
 */
public final class Packet extends DistributedPropertySet {

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
    }

    /**
     * Creates an empty {@link Packet} that doesn't have any {@link Message}.
     */
    public Packet() {
        this.invocationProperties = new HashMap<String,Object>();
    }

    /**
     * Used by {@link #createResponse(Message)}.
     */
    private Packet(Packet that) {
        that.copySatelliteInto(this);
        this.handlerConfig = that.handlerConfig;
        this.invocationProperties = that.invocationProperties;
        this.handlerScopePropertyNames = that.handlerScopePropertyNames;
        this.contentNegotiation = that.contentNegotiation;
        this.wasTransportSecure = that.wasTransportSecure;
        this.endpointAddress = that.endpointAddress;
        // copy other properties that need to be copied. is there any?
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
        if (copyMessage) {
            copy.message = this.message.copy();
        }

        return copy;
    }

    private Message message;

    /**
     * Gets the last {@link Message} set through {@link #setMessage(Message)}.
     *
     * @return
     *      may null. See the class javadoc for when it's null.
     */
    public Message getMessage() {
        return message;
    }

    /**
     * Sets a {@link Message} to this packet.
     *
     * @param message
     *      Can be null.
     */
    public void setMessage(Message message) {
        this.message = message;
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
     *
     */
    public static final String INBOUND_TRANSPORT_HEADERS = "com.sun.xml.internal.ws.api.message.packet.inbound.transport.headers";

    /**
     * Outbound transport headers are captured in a transport neutral way.
     *
     * <p>
     * Transports may choose to ignore certain headers that interfere with
     * its correct operation, such as
     * <tt>Content-Type</tt> and <tt>Content-Length</tt>.
     */
    public static final String OUTBOUND_TRANSPORT_HEADERS = "com.sun.xml.internal.ws.api.message.packet.outbound.transport.headers";


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
        if(endpointAddress==null)
            return null;
        else
            return endpointAddress.toString();
    }

    public void setEndPointAddressString(String s) {
        if(s==null)
            this.endpointAddress = null;
        else
            this.endpointAddress = EndpointAddress.create(s);
    }

    /**
     * The value of {@link ContentNegotiation#PROPERTY}
     * property.
     *
     * This property is used only on the client side.
     */
    public ContentNegotiation contentNegotiation;

    @Property(ContentNegotiation.PROPERTY)
    public String getContentNegotiationString() {
        return (contentNegotiation != null) ? contentNegotiation.toString() : null;
    }

    public void setContentNegotiationString(String s) {
        if(s==null)
            contentNegotiation = null;
        else {
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
    public @NotNull List<Element> getReferenceParameters() {
        List<Element> refParams =  new ArrayList<Element>();
        HeaderList hl = message.getHeaders();
        for(Header h :hl) {
            String attr = h.getAttribute(AddressingVersion.W3C.nsUri,"IsReferenceParameter");
            if(attr!=null && (attr.equals("true") || attr.equals("1"))) {
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
     * @deprecated
     *      This method is for exposing header list through {@link PropertySet#get(Object)},
     *      for user applications, and should never be invoked directly from within the JAX-WS RI.
     */
    @Property(JAXWSProperties.INBOUND_HEADER_LIST_PROPERTY)
    /*package*/ HeaderList getHeaderList() {
        if(message==null)   return null;
        return message.getHeaders();
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
     *
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
    public @Nullable TransportBackChannel transportBackChannel;

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
     * If this field is null, the transport may choose to send <tt>""</tt>
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
     *
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
     * When this property is {@link Boolean#TRUE}, it means that
     * the pipeline does not expect a reply from a server (and therefore
     * the correlator should not block for a reply message
     * -- if such a reply does arrive, it can be just ignored.)
     *
     * <p>
     * When this property is {@link Boolean#FALSE}, it means that
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
    public final Map<String,Object> invocationProperties;

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
    public final Set<String> getHandlerScopePropertyNames( boolean readOnly ) {
        Set<String> o = this.handlerScopePropertyNames;
        if(o==null) {
            if(readOnly)
                return Collections.emptySet();
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
    public final Set<String> getApplicationScopePropertyNames( boolean readOnly ) {
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
        return response;
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

        AddressingVersion av = binding.getAddressingVersion();
        // populate WS-A headers only if WS-A is enabled
        if (av == null)
            return r;
        //populate WS-A headers only if the request has addressing headers
        String inputAction = this.getMessage().getHeaders().getAction(av, binding.getSOAPVersion());
        if (inputAction == null) {
            return r;
        }
        // if one-way, then dont populate any WS-A headers
        if (responseMessage == null || (wsdlPort != null && message.isOneWay(wsdlPort)))
            return r;

        // otherwise populate WS-Addressing headers
        populateAddressingHeaders(binding, r, wsdlPort,seiModel);
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

        // populate WS-A headers only if WS-A is enabled
        if (addressingVersion == null)
            return responsePacket;
        //populate WS-A headers only if the request has addressing headers
        String inputAction = this.getMessage().getHeaders().getAction(addressingVersion, soapVersion);
        if (inputAction == null) {
            return responsePacket;
        }

        populateAddressingHeaders(responsePacket, addressingVersion, soapVersion, action);
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

    private void populateAddressingHeaders(Packet responsePacket, AddressingVersion av, SOAPVersion sv, String action) {
        // populate WS-A headers only if WS-A is enabled
        if (av == null) return;

        // if one-way, then dont populate any WS-A headers
        if (responsePacket.getMessage() == null)
            return;

        HeaderList hl = responsePacket.getMessage().getHeaders();

        // wsa:To
        WSEndpointReference replyTo;
        try {
        replyTo = message.getHeaders().getReplyTo(av, sv);
        if (replyTo != null)
            hl.add(new StringHeader(av.toTag, replyTo.getAddress()));
        } catch (InvalidAddressingHeaderException e) {
            replyTo = null;
        }

        // wsa:Action, add if the message doesn't already contain it,
        // generally true for SEI case where there is SEIModel or WSDLModel
        //           false for Provider with no wsdl, Expects User to set the coresponding header on the Message.
        if(responsePacket.getMessage().getHeaders().getAction(av,sv) == null) {
            //wsa:Action header is not set in the message, so use the wsa:Action  passed as the parameter.
            hl.add(new StringHeader(av.actionTag, action));
        }

        // wsa:MessageID
        hl.add(new StringHeader(av.messageIDTag, responsePacket.getMessage().getID(av, sv)));

        // wsa:RelatesTo
        String mid = getMessage().getHeaders().getMessageID(av,sv);
        if (mid != null)
            hl.add(new RelatesToHeader(av.relatesToTag, mid));

        // populate reference parameters
        WSEndpointReference refpEPR;
        if (responsePacket.getMessage().isFault()) {
            // choose FaultTo
            refpEPR = message.getHeaders().getFaultTo(av, sv);

            // if FaultTo is null, then use ReplyTo
            if (refpEPR == null)
                refpEPR = replyTo;
        } else {
            // choose ReplyTo
            refpEPR = replyTo;
        }
        if (refpEPR != null) {
            refpEPR.addReferenceParameters(hl);
        }
    }

    private void populateAddressingHeaders(WSBinding binding, Packet responsePacket, WSDLPort wsdlPort, SEIModel seiModel) {
        AddressingVersion addressingVersion = binding.getAddressingVersion();

        if (addressingVersion == null)  return;

        WsaTubeHelper wsaHelper = addressingVersion.getWsaHelper(wsdlPort,seiModel, binding);
        String action = responsePacket.message.isFault() ?
                wsaHelper.getFaultAction(this, responsePacket) :
                wsaHelper.getOutputAction(this);

        populateAddressingHeaders(responsePacket, addressingVersion, binding.getSOAPVersion(), action);
    }

    // completes TypedMap
    private static final PropertyMap model;

    static {
        model = parse(Packet.class);
    }

    protected PropertyMap getPropertyMap() {
        return model;
    }
}
