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
import com.sun.xml.internal.ws.addressing.WsaTubeHelper;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.OneWayFeature;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.message.RelatesToHeader;
import com.sun.xml.internal.ws.message.StringHeader;
import com.sun.xml.internal.ws.protocol.soap.ClientMUTube;
import com.sun.xml.internal.ws.protocol.soap.ServerMUTube;
import com.sun.xml.internal.ws.resources.AddressingMessages;
import com.sun.xml.internal.ws.resources.ClientMessages;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A list of {@link Header}s on a {@link Message}.
 *
 * <p>
 * This list can be modified to add headers
 * from outside a {@link Message}, this is necessary
 * since intermediate processing layers often need to
 * put additional headers.
 *
 * <p>
 * Following the SOAP convention, the order among headers
 * are not significant. However, {@link Codec}s are
 * expected to preserve the order of headers in the input
 * message as much as possible.
 *
 *
 * <a name="MU"></a>
 * <h3>MustUnderstand Processing</h3>
 * <p>
 * To perform SOAP mustUnderstang processing correctly, we need to keep
 * track of headers that are understood and headers that are not.
 * This is a collaborative process among {@link Pipe}s, thus it's something
 * a {@link Pipe} author needs to keep in mind.
 *
 * <p>
 * Specifically, when a {@link Pipe} sees a header and processes it
 * (that is, if it did enough computing with the header to claim that
 * the header is understood), then it should mark the corresponding
 * header as "understood". For example, when a pipe that handles JAX-WSA
 * examins the &lt;wsa:To> header, it can claim that it understood the header.
 * But for example, if a pipe that does the signature verification checks
 * &lt;wsa:To> for a signature, that would not be considered as "understood".
 *
 * <p>
 * There are two ways to mark a header as understood:
 *
 * <ol>
 *  <li>Use one of the <tt>getXXX</tt> methods that take a
 *      boolean <tt>markAsUnderstood</tt> parameter.
 *      Most often, a {@link Pipe} knows it's going to understand a header
 *      as long as it's present, so this is the easiest and thus the preferred way.
 *
 *      For example, if JAX-WSA looks for &lt;wsa:To>, then it can set
 *      <tt>markAsUnderstand</tt> to true, to do the obtaining of a header
 *      and marking at the same time.
 *
 *  <li>Call {@link #understood(int)}.
 *      If under a rare circumstance, a pipe cannot determine whether
 *      it can understand it or not when you are fetching a header, then
 *      you can use this method afterward to mark it as understood.
 * </ol>
 *
 * <p>
 * Intuitively speaking, at the end of the day, if a header is not
 * understood but {@link Header#isIgnorable(SOAPVersion, Set)} is false, a bad thing
 * will happen. The actual implementation of the checking is more complicated,
 * for that see {@link ClientMUTube}/{@link ServerMUTube}.
 *
 * @see Message#getHeaders()
 */
public final class HeaderList extends ArrayList<Header> {

    /**
     * Bit set to keep track of which headers are understood.
     * <p>
     * The first 32 headers use this field, and the rest will use
     * {@link #moreUnderstoodBits}. The expectation is that
     * most of the time a SOAP message will only have up to 32 headers,
     * so we can avoid allocating separate objects for {@link BitSet}.
     */
    private int understoodBits;
    /**
     * If there are more than 32 headers, we use this {@link BitSet}
     * to keep track of whether those headers are understood.
     * Lazily allocated.
     */
    private BitSet moreUnderstoodBits = null;

    private String to = null;
    private String action = null;
    private WSEndpointReference replyTo = null;
    private WSEndpointReference faultTo = null;
    private String messageId;


    /**
     * Creates an empty {@link HeaderList}.
     */
    public HeaderList() {
    }

    /**
     * Copy constructor.
     */
    public HeaderList(HeaderList that) {
        super(that);
        this.understoodBits = that.understoodBits;
        if(that.moreUnderstoodBits!=null)
            this.moreUnderstoodBits = (BitSet)that.moreUnderstoodBits.clone();
        this.to = that.to;
        this.action = that.action;
        this.replyTo = that.replyTo;
        this.faultTo = that.faultTo;
        this.messageId = that.messageId;
    }

    /**
     * The number of total headers.
     */
    public int size() {
        return super.size();
    }

    /**
     * Adds all the headers.
     */
    public void addAll(Header... headers) {
        for (Header header : headers)
            add(header);
    }

    /**
     * Gets the {@link Header} at the specified index.
     *
     * <p>
     * This method does not mark the returned {@link Header} as understood.
     *
     * @see #understood(int)
     */
    public Header get(int index) {
        return super.get(index);
    }

    /**
     * Marks the {@link Header} at the specified index as
     * <a href="#MU">"understood"</a>.
     */
    public void understood(int index) {
        assert index<size();    // check that index is in range
        if(index<32)
            understoodBits |= 1<<index;
        else {
            if(moreUnderstoodBits==null)
                moreUnderstoodBits = new BitSet();
            moreUnderstoodBits.set(index-32);
        }
    }

    /**
     * Returns true if a {@link Header} at the given index
     * was <a href="#MU">"understood"</a>.
     */
    public boolean isUnderstood(int index) {
        assert index<size();    // check that index is in range
        if(index<32)
            return understoodBits == (understoodBits|(1<<index));
        else {
            if(moreUnderstoodBits==null)
                return false;
            return moreUnderstoodBits.get(index-32);
        }
    }

    /**
     * Marks the specified {@link Header} as <a href="#MU">"understood"</a>.
     *
     * @deprecated
     * By the deifnition of {@link ArrayList}, this operation requires
     * O(n) search of the array, and thus inherently inefficient.
     *
     * Because of this, if you are developing a {@link Pipe} for
     * a performance sensitive environment, do not use this method.
     *
     * @throws IllegalArgumentException
     *      if the given header is not {@link #contains(Object) contained}
     *      in this header.
     */
    public void understood(@NotNull Header header) {
        int sz = size();
        for( int i=0; i<sz; i++ ) {
            if(get(i)==header) {
                understood(i);
                return;
            }
        }
        throw new IllegalArgumentException();
    }

    /**
     * Gets the first {@link Header} of the specified name.
     *
     * @param markAsUnderstood
     *      If this parameter is true, the returned header will
     *      be marked as <a href="#MU">"understood"</a>.
     * @return null if not found.
     */
    public @Nullable Header get(@NotNull String nsUri, @NotNull String localName, boolean markAsUnderstood) {
        int len = size();
        for( int i=0; i<len; i++ ) {
            Header h = get(i);
            if(h.getLocalPart().equals(localName) && h.getNamespaceURI().equals(nsUri)) {
                if(markAsUnderstood)
                    understood(i);
                return h;
            }
        }
        return null;
    }

    /**
     * @deprecated
     *      Use {@link #get(String, String, boolean)}
     */
    public Header get(String nsUri, String localName) {
        return get(nsUri,localName,true);
    }

    /**
     * Gets the first {@link Header} of the specified name.
     *
     * @param markAsUnderstood
     *      If this parameter is true, the returned header will
     *      be marked as <a href="#MU">"understood"</a>.
     * @return null
     *      if not found.
     */
    public @Nullable Header get(@NotNull QName name, boolean markAsUnderstood) {
        return get(name.getNamespaceURI(),name.getLocalPart(),markAsUnderstood);
    }

    /**
     * @deprecated
     *      Use {@link #get(QName)}
     */
    public @Nullable Header get(@NotNull QName name) {
        return get(name,true);
    }

    /**
     * @deprecated
     *      Use {@link #getHeaders(String, String, boolean)}
     */
    public Iterator<Header> getHeaders(final String nsUri, final String localName) {
        return getHeaders(nsUri,localName,true);
    }

    /**
     * Gets all the {@link Header}s of the specified name,
     * including duplicates (if any.)
     *
     * @param markAsUnderstood
     *      If this parameter is true, the returned headers will
     *      be marked as <a href="#MU">"understood"</a> when they are returned
     *      from {@link Iterator#next()}.
     * @return empty iterator if not found.
     */
    public @NotNull Iterator<Header> getHeaders(@NotNull final String nsUri, @NotNull final String localName, final boolean markAsUnderstood) {
        return new Iterator<Header>() {
            int idx = 0;
            Header next;
            public boolean hasNext() {
                if(next==null)
                    fetch();
                return next!=null;
            }

            public Header next() {
                if(next==null) {
                    fetch();
                    if(next==null)
                        throw new NoSuchElementException();
                }

                if(markAsUnderstood) {
                    assert get(idx-1)==next;
                    understood(idx-1);
                }

                Header r = next;
                next = null;
                return r;
            }

            private void fetch() {
                while(idx<size()) {
                    Header h = get(idx++);
                    if(h.getLocalPart().equals(localName) && h.getNamespaceURI().equals(nsUri)) {
                        next = h;
                        break;
                    }
                }
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * @see #getHeaders(String, String, boolean)
     */
    public @NotNull Iterator<Header> getHeaders(@NotNull QName headerName, final boolean markAsUnderstood) {
        return getHeaders(headerName.getNamespaceURI(),headerName.getLocalPart(),markAsUnderstood);
    }

    /**
     * @deprecated
     *      use {@link #getHeaders(String, boolean)}.
     */
    public @NotNull Iterator<Header> getHeaders(@NotNull final String nsUri) {
        return getHeaders(nsUri,true);
    }

    /**
     * Gets an iteration of headers {@link Header} in the specified namespace,
     * including duplicates (if any.)
     *
     * @param markAsUnderstood
     *      If this parameter is true, the returned headers will
     *      be marked as <a href="#MU">"understood"</a> when they are returned
     *      from {@link Iterator#next()}.
     * @return
     *      empty iterator if not found.
     */
    public @NotNull Iterator<Header> getHeaders(@NotNull final String nsUri, final boolean markAsUnderstood) {
        return new Iterator<Header>() {
            int idx = 0;
            Header next;
            public boolean hasNext() {
                if(next==null)
                    fetch();
                return next!=null;
            }

            public Header next() {
                if(next==null) {
                    fetch();
                    if(next==null)
                        throw new NoSuchElementException();
                }

                if(markAsUnderstood) {
                    assert get(idx-1)==next;
                    understood(idx-1);
                }

                Header r = next;
                next = null;
                return r;
            }

            private void fetch() {
                while(idx<size()) {
                    Header h = get(idx++);
                    if(h.getNamespaceURI().equals(nsUri)) {
                        next = h;
                        break;
                    }
                }
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Gets the first {@link Header} of the specified name targeted at the
     * current implicit role.
     *
     * @param name name of the header
     * @param markUnderstood
     *      If this parameter is true, the returned headers will
     *      be marked as <a href="#MU">"understood"</a> when they are returned
     *      from {@link Iterator#next()}.
     * @return null if header not found
     */
    private Header getFirstHeader(QName name, boolean markUnderstood, SOAPVersion sv) {
        if (sv == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_SOAP_VERSION());

        Iterator<Header> iter = getHeaders(name.getNamespaceURI(), name.getLocalPart(), markUnderstood);
        while (iter.hasNext()) {
            Header h = iter.next();
            if (h.getRole(sv).equals(sv.implicitRole))
                return h;
        }

        return null;
    }

    /**
     * Returns the value of WS-Addressing <code>To</code> header. The <code>version</code>
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate <code>To</code> headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either <code>av</code> or <code>sv</code> is null.
     * @return Value of WS-Addressing To header, anonymous URI if no header is present
     */
    public String getTo(AddressingVersion av, SOAPVersion sv) {
        if (to != null)
            return to;
        if (av == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_ADDRESSING_VERSION());

        Header h = getFirstHeader(av.toTag, true, sv);
        if (h != null) {
            to = h.getStringContent();
        } else {
            to = av.anonymousUri;
        }

        return to;
    }

    /**
     * Returns the value of WS-Addressing <code>Action</code> header. The <code>version</code>
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate <code>Action</code> headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either <code>av</code> or <code>sv</code> is null.
     * @return Value of WS-Addressing Action header, null if no header is present
     */
    public String getAction(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        if (action!= null)
            return action;
        if (av == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_ADDRESSING_VERSION());

        Header h = getFirstHeader(av.actionTag, true, sv);
        if (h != null) {
            action = h.getStringContent();
        }

        return action;
    }

    /**
     * Returns the value of WS-Addressing <code>ReplyTo</code> header. The <code>version</code>
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate <code>ReplyTo</code> headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either <code>av</code> or <code>sv</code> is null.
     * @return Value of WS-Addressing ReplyTo header, null if no header is present
     */
    public WSEndpointReference getReplyTo(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        if (replyTo!=null)
            return replyTo;
        if (av == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_ADDRESSING_VERSION());

        Header h = getFirstHeader(av.replyToTag, true, sv);
        if (h != null) {
            try {
                replyTo = h.readAsEPR(av);
            } catch (XMLStreamException e) {
                throw new WebServiceException(AddressingMessages.REPLY_TO_CANNOT_PARSE(), e);
            }
        } else {
            replyTo = av.anonymousEpr;
        }

        return replyTo;
    }

    /**
     * Returns the value of WS-Addressing <code>FaultTo</code> header. The <code>version</code>
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate <code>FaultTo</code> headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either <code>av</code> or <code>sv</code> is null.
     * @return Value of WS-Addressing FaultTo header, null if no header is present
     */
    public WSEndpointReference getFaultTo(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        if (faultTo != null)
            return faultTo;

        if (av == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_ADDRESSING_VERSION());

        Header h = getFirstHeader(av.faultToTag, true, sv);
        if (h != null) {
            try {
                faultTo = h.readAsEPR(av);
            } catch (XMLStreamException e) {
                throw new WebServiceException(AddressingMessages.FAULT_TO_CANNOT_PARSE(), e);
            }
        }

        return faultTo;
    }

    /**
     * Returns the value of WS-Addressing <code>MessageID</code> header. The <code>version</code>
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate <code>MessageID</code> headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws WebServiceException if either <code>av</code> or <code>sv</code> is null.
     * @return Value of WS-Addressing MessageID header, null if no header is present
     */
    public String getMessageID(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        if (messageId != null)
            return messageId;

        if (av == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_ADDRESSING_VERSION());

        Header h = getFirstHeader(av.messageIDTag, true, sv);
        if (h != null) {
            messageId = h.getStringContent();
        }

        return messageId;
    }

    /**
     * Creates a set of outbound WS-Addressing headers on the client with the
     * specified Action Message Addressing Property value.
     * <p><p>
     * This method needs to be invoked right after such a Message is
     * created which is error prone but so far only MEX, RM and JAX-WS
     * creates a request so this ugliness is acceptable. This method is also used
     * to create protocol messages that are not associated with any {@link WSBinding}
     * and {@link WSDLPort}.
     *
     * @param packet request packet
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @param oneway Indicates if the message exchange pattern is oneway
     * @param action Action Message Addressing Property value
     */
    public void fillRequestAddressingHeaders(Packet packet, AddressingVersion av, SOAPVersion sv, boolean oneway, String action) {
        fillCommonAddressingHeaders(packet, av, sv, action);

        // wsa:ReplyTo
        // null or "true" is equivalent to request/response MEP
        if (!oneway) {
            WSEndpointReference epr = av.anonymousEpr;
            add(epr.createHeader(av.replyToTag));

            // wsa:MessageID
            Header h = new StringHeader(av.messageIDTag, packet.getMessage().getID(av, sv));
            add(h);
        }
    }

    /**
     * Creates a set of outbound WS-Addressing headers on the client with the
     * default Action Message Addressing Property value.
     * <p><p>
     * This method needs to be invoked right after such a Message is
     * created which is error prone but so far only MEX, RM and JAX-WS
     * creates a request so this ugliness is acceptable. If more components
     * are identified using this, then we may revisit this.
     * <p><p>
     * This method is used if default Action Message Addressing Property is to
     * be used. See
     * {@link #fillRequestAddressingHeaders(Packet, com.sun.xml.internal.ws.api.addressing.AddressingVersion, com.sun.xml.internal.ws.api.SOAPVersion, boolean, String)}
     * if non-default Action is to be used, for example when creating a protocol message not
     * associated with {@link WSBinding} and {@link WSDLPort}.
     * This method uses SOAPAction as the Action unless set expplicitly in the wsdl.
     * @param wsdlPort request WSDL port
     * @param binding request WSBinding
     * @param packet request packet
     */
    public void fillRequestAddressingHeaders(WSDLPort wsdlPort, @NotNull WSBinding binding, Packet packet) {
        if (binding == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_BINDING());

        AddressingVersion addressingVersion = binding.getAddressingVersion();
        //seiModel is passed as null as it is not needed.
        WsaTubeHelper wsaHelper = addressingVersion.getWsaHelper(wsdlPort, null, binding);

        // wsa:Action
        String action = wsaHelper.getEffectiveInputAction(packet);
        if (action == null || action.equals("")) {
            throw new WebServiceException(ClientMessages.INVALID_SOAP_ACTION());
        }
        boolean oneway = !packet.expectReply;
        if (wsdlPort != null) {
            // if WSDL has <wsaw:Anonymous>prohibited</wsaw:Anonymous>, then throw an error
            // as anonymous ReplyTo MUST NOT be added in that case. BindingProvider need to
            // disable AddressingFeature and MemberSubmissionAddressingFeature and hand-craft
            // the SOAP message with non-anonymous ReplyTo/FaultTo.
            if (!oneway && packet.getMessage() != null && packet.getMessage().getOperation(wsdlPort) != null && packet.getMessage().getOperation(wsdlPort).getAnonymous() == WSDLBoundOperation.ANONYMOUS.prohibited)
            {
                throw new WebServiceException(AddressingMessages.WSAW_ANONYMOUS_PROHIBITED());
            }
        }
        if (!binding.isFeatureEnabled(OneWayFeature.class)) {
            // standard oneway
            fillRequestAddressingHeaders(packet, addressingVersion, binding.getSOAPVersion(), oneway, action);
        } else {
            // custom oneway
            fillRequestAddressingHeaders(packet, addressingVersion, binding.getSOAPVersion(), binding.getFeature(OneWayFeature.class), action);
        }
    }

    private void fillRequestAddressingHeaders(@NotNull Packet packet, @NotNull AddressingVersion av, @NotNull SOAPVersion sv, @NotNull OneWayFeature of, @NotNull String action) {
        fillCommonAddressingHeaders(packet, av, sv, action);

        // wsa:ReplyTo
        if (of.getReplyTo() != null) {
            add(of.getReplyTo().createHeader(av.replyToTag));

            // add wsa:MessageID only for non-null ReplyTo
            Header h = new StringHeader(av.messageIDTag, packet.getMessage().getID(av, sv));
            add(h);
        }

        // wsa:From
        if (of.getFrom() != null) {
            add(of.getFrom().createHeader(av.fromTag));
        }

        // wsa:RelatesTo
        if (of.getRelatesToID() != null) {
            add(new RelatesToHeader(av.relatesToTag, of.getRelatesToID()));
        }
    }

    /**
     * Creates wsa:To, wsa:Action and wsa:MessageID header on the client
     *
     * @param packet request packet
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @param action Action Message Addressing Property value
     * @throws IllegalArgumentException if any of the parameters is null.
     */
    private void fillCommonAddressingHeaders(Packet packet, @NotNull AddressingVersion av, @NotNull SOAPVersion sv, @NotNull String action) {
        if (packet == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_PACKET());

        if (av == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_ADDRESSING_VERSION());

        if (sv == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_SOAP_VERSION());

        if (action == null)
            throw new IllegalArgumentException(AddressingMessages.NULL_ACTION());

        // wsa:To
        StringHeader h = new StringHeader(av.toTag, packet.endpointAddress.toString());
        add(h);

        // wsa:Action
        packet.soapAction = action;
        h = new StringHeader(av.actionTag, action);
        add(h);
    }

    /**
     * Adds a new {@link Header}.
     *
     * <p>
     * Order doesn't matter in headers, so this method
     * does not make any guarantee as to where the new header
     * is inserted.
     *
     * @return
     *      always true. Don't use the return value.
     */
    public boolean add(Header header) {
        return super.add(header);
    }

    /**
     * @deprecated
     *      {@link HeaderList} is monotonic and you can't remove anything.
     */
    // to allow this, we need to implement the resizing of understoodBits
    public Header remove(int index) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      {@link HeaderList} is monotonic and you can't remove anything.
     */
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      {@link HeaderList} is monotonic and you can't remove anything.
     */
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      {@link HeaderList} is monotonic and you can't remove anything.
     */
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a copy.
     *
     * This handles null {@link HeaderList} correctly.
     *
     * @param original
     *      Can be null, in which case null will be returned.
     */
    public static HeaderList copy(HeaderList original) {
        if(original==null)
            return null;
        else
            return new HeaderList(original);
    }

    public void readResponseAddressingHeaders(WSDLPort wsdlPort, WSBinding binding) {
        // read Action
        String action = getAction(binding.getAddressingVersion(), binding.getSOAPVersion());
        // TODO: validate client-inbound Action
    }
}
