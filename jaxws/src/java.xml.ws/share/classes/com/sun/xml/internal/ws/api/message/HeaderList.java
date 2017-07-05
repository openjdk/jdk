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

package com.sun.xml.internal.ws.api.message;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.binding.SOAPBindingImpl;
import com.sun.xml.internal.ws.protocol.soap.ClientMUTube;
import com.sun.xml.internal.ws.protocol.soap.ServerMUTube;
import java.util.Arrays;

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
 *  <li>Use one of the {@code getXXX} methods that take a
 *      boolean {@code markAsUnderstood} parameter.
 *      Most often, a {@link Pipe} knows it's going to understand a header
 *      as long as it's present, so this is the easiest and thus the preferred way.
 *
 *      For example, if JAX-WSA looks for &lt;wsa:To>, then it can set
 *      {@code markAsUnderstand} to true, to do the obtaining of a header
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
 * understood but {@link Header#isIgnorable(SOAPVersion, java.util.Set)} is false, a bad thing
 * will happen. The actual implementation of the checking is more complicated,
 * for that see {@link ClientMUTube}/{@link ServerMUTube}.
 *
 * @see Message#getHeaders()
 */
public class HeaderList extends ArrayList<Header> implements MessageHeaders {

    private static final long serialVersionUID = -6358045781349627237L;
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

    private SOAPVersion soapVersion;

    /**
     * This method is deprecated - instead use this one:
     * public HeaderList(SOAPVersion)
     * Creates an empty {@link HeaderList}.
     */
    @Deprecated
    public HeaderList() {
    }

    /**
     * Creates an empty {@link HeaderList} with the given soap version
     * @param soapVersion
     */
    public HeaderList(SOAPVersion soapVersion) {
        this.soapVersion = soapVersion;
    }

    /**
     * Copy constructor.
     */
    public HeaderList(HeaderList that) {
        super(that);
        this.understoodBits = that.understoodBits;
        if (that.moreUnderstoodBits != null) {
            this.moreUnderstoodBits = (BitSet) that.moreUnderstoodBits.clone();
        }
    }

    public HeaderList(MessageHeaders that) {
        super(that.asList());
        if (that instanceof HeaderList) {
            HeaderList hThat = (HeaderList) that;
            this.understoodBits = hThat.understoodBits;
            if (hThat.moreUnderstoodBits != null) {
                this.moreUnderstoodBits = (BitSet) hThat.moreUnderstoodBits.clone();
            }
        } else {
            Set<QName> understood = that.getUnderstoodHeaders();
            if (understood != null) {
                for (QName qname : understood) {
                    understood(qname);
                }
            }
        }
    }

    /**
     * The total number of headers.
     */
    @Override
    public int size() {
        return super.size();
    }

    @Override
    public boolean hasHeaders() {
        return !isEmpty();
    }

    /**
     * Adds all the headers.
     * @deprecated throws UnsupportedOperationException from some HeaderList implementations - better iterate over items one by one
     */
    @Deprecated
    public void addAll(Header... headers) {
        addAll(Arrays.asList(headers));
    }

    /**
     * Gets the {@link Header} at the specified index.
     *
     * <p>
     * This method does not mark the returned {@link Header} as understood.
     *
     * @see #understood(int)
     */
    @Override
    public Header get(int index) {
        return super.get(index);
    }

    /**
     * Marks the {@link Header} at the specified index as
     * <a href="#MU">"understood"</a>.
     */
    public void understood(int index) {
        // check that index is in range
        if (index >= size()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        if (index < 32) {
            understoodBits |= 1 << index;
        } else {
            if (moreUnderstoodBits == null) {
                moreUnderstoodBits = new BitSet();
            }
            moreUnderstoodBits.set(index - 32);
        }
    }

    /**
     * Returns true if a {@link Header} at the given index
     * was <a href="#MU">"understood"</a>.
     */
    public boolean isUnderstood(int index) {
        // check that index is in range
        if (index >= size()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        if (index < 32) {
            return understoodBits == (understoodBits | (1 << index));
        } else {
            if (moreUnderstoodBits == null) {
                return false;
            }
            return moreUnderstoodBits.get(index - 32);
        }
    }

    /**
     * Marks the specified {@link Header} as <a href="#MU">"understood"</a>.
     *
     * @deprecated
     * By the definition of {@link ArrayList}, this operation requires
     * O(n) search of the array, and thus inherently inefficient.
     *
     * Because of this, if you are developing a {@link Pipe} for
     * a performance sensitive environment, do not use this method.
     *
     * @throws IllegalArgumentException
     *      if the given header is not {@link #contains(Object) contained}
     *      in this header.
     */
    @Override
    public void understood(@NotNull Header header) {
        int sz = size();
        for (int i = 0; i < sz; i++) {
            if (get(i) == header) {
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
    @Override
    public @Nullable Header get(@NotNull String nsUri, @NotNull String localName, boolean markAsUnderstood) {
        int len = size();
        for (int i = 0; i < len; i++) {
            Header h = get(i);
            if (h.getLocalPart().equals(localName) && h.getNamespaceURI().equals(nsUri)) {
                if (markAsUnderstood) {
                    understood(i);
                }
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
        return get(nsUri, localName, true);
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
    @Override
    public @Nullable Header get(@NotNull QName name, boolean markAsUnderstood) {
        return get(name.getNamespaceURI(), name.getLocalPart(), markAsUnderstood);
    }

    /**
     * @deprecated
     *      Use {@link #get(QName)}
     */
    public
    @Nullable
    Header get(@NotNull QName name) {
        return get(name, true);
    }

    /**
     * @deprecated
     *      Use {@link #getHeaders(String, String, boolean)}
     */
    public Iterator<Header> getHeaders(final String nsUri, final String localName) {
        return getHeaders(nsUri, localName, true);
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
    public
    @NotNull
    @Override
    Iterator<Header> getHeaders(@NotNull final String nsUri, @NotNull final String localName, final boolean markAsUnderstood) {
        return new Iterator<Header>() {

            int idx = 0;
            Header next;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    fetch();
                }
                return next != null;
            }

            @Override
            public Header next() {
                if (next == null) {
                    fetch();
                    if (next == null) {
                        throw new NoSuchElementException();
                    }
                }

                if (markAsUnderstood) {
                    assert get(idx - 1) == next;
                    understood(idx - 1);
                }

                Header r = next;
                next = null;
                return r;
            }

            private void fetch() {
                while (idx < size()) {
                    Header h = get(idx++);
                    if (h.getLocalPart().equals(localName) && h.getNamespaceURI().equals(nsUri)) {
                        next = h;
                        break;
                    }
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * @see #getHeaders(String, String, boolean)
     */
    public
    @NotNull
    @Override
    Iterator<Header> getHeaders(@NotNull QName headerName, final boolean markAsUnderstood) {
        return getHeaders(headerName.getNamespaceURI(), headerName.getLocalPart(), markAsUnderstood);
    }

    /**
     * @deprecated
     *      use {@link #getHeaders(String, boolean)}.
     */
    public
    @NotNull
    Iterator<Header> getHeaders(@NotNull final String nsUri) {
        return getHeaders(nsUri, true);
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
    public
    @NotNull
    @Override
    Iterator<Header> getHeaders(@NotNull final String nsUri, final boolean markAsUnderstood) {
        return new Iterator<Header>() {

            int idx = 0;
            Header next;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    fetch();
                }
                return next != null;
            }

            @Override
            public Header next() {
                if (next == null) {
                    fetch();
                    if (next == null) {
                        throw new NoSuchElementException();
                    }
                }

                if (markAsUnderstood) {
                    assert get(idx - 1) == next;
                    understood(idx - 1);
                }

                Header r = next;
                next = null;
                return r;
            }

            private void fetch() {
                while (idx < size()) {
                    Header h = get(idx++);
                    if (h.getNamespaceURI().equals(nsUri)) {
                        next = h;
                        break;
                    }
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Returns the value of WS-Addressing {@code To} header. The {@code version}
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate {@code To} headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either {@code av} or {@code sv} is null.
     * @return Value of WS-Addressing To header, anonymous URI if no header is present
     */
    public String getTo(AddressingVersion av, SOAPVersion sv) {
        return AddressingUtils.getTo(this, av, sv);
    }

    /**
     * Returns the value of WS-Addressing {@code Action} header. The {@code version}
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate {@code Action} headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either {@code av} or {@code sv} is null.
     * @return Value of WS-Addressing Action header, null if no header is present
     */
    public String getAction(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        return AddressingUtils.getAction(this, av, sv);
    }

    /**
     * Returns the value of WS-Addressing {@code ReplyTo} header. The {@code version}
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate {@code ReplyTo} headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either {@code av} or {@code sv} is null.
     * @return Value of WS-Addressing ReplyTo header, null if no header is present
     */
    public WSEndpointReference getReplyTo(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        return AddressingUtils.getReplyTo(this, av, sv);
    }

    /**
     * Returns the value of WS-Addressing {@code FaultTo} header. The {@code version}
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate {@code FaultTo} headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws IllegalArgumentException if either {@code av} or {@code sv} is null.
     * @return Value of WS-Addressing FaultTo header, null if no header is present
     */
    public WSEndpointReference getFaultTo(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        return AddressingUtils.getFaultTo(this, av, sv);
    }

    /**
     * Returns the value of WS-Addressing {@code MessageID} header. The {@code version}
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate {@code MessageID} headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws WebServiceException if either {@code av} or {@code sv} is null.
     * @return Value of WS-Addressing MessageID header, null if no header is present
     */
    public String getMessageID(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        return AddressingUtils.getMessageID(this, av, sv);
    }

    /**
     * Returns the value of WS-Addressing {@code RelatesTo} header. The {@code version}
     * identifies the WS-Addressing version and the header returned is targeted at
     * the current implicit role. Caches the value for subsequent invocation.
     * Duplicate {@code RelatesTo} headers are detected earlier.
     *
     * @param av WS-Addressing version
     * @param sv SOAP version
     * @throws WebServiceException if either {@code av} or {@code sv} is null.
     * @return Value of WS-Addressing RelatesTo header, null if no header is present
     */
    public String getRelatesTo(@NotNull AddressingVersion av, @NotNull SOAPVersion sv) {
        return AddressingUtils.getRelatesTo(this, av, sv);
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
     * @param mustUnderstand to indicate if the addressing headers are set with mustUnderstand attribute
     */
    public void fillRequestAddressingHeaders(Packet packet, AddressingVersion av, SOAPVersion sv, boolean oneway, String action, boolean mustUnderstand) {
        AddressingUtils.fillRequestAddressingHeaders(this, packet, av, sv, oneway, action, mustUnderstand);
    }

    public void fillRequestAddressingHeaders(Packet packet, AddressingVersion av, SOAPVersion sv, boolean oneway, String action) {
        AddressingUtils.fillRequestAddressingHeaders(this, packet, av, sv, oneway, action);
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
        AddressingUtils.fillRequestAddressingHeaders(this, wsdlPort, binding, packet);
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
    @Override
    public boolean add(Header header) {
        return super.add(header);
    }

    /**
     * Removes the first {@link Header} of the specified name.
     * @param nsUri namespace URI of the header to remove
     * @param localName local part of the FQN of the header to remove
     *
     * @return null if not found.
     */
    public
    @Nullable
    @Override
    Header remove(@NotNull String nsUri, @NotNull String localName) {
        int len = size();
        for (int i = 0; i < len; i++) {
            Header h = get(i);
            if (h.getLocalPart().equals(localName) && h.getNamespaceURI().equals(nsUri)) {
                return remove(i);
            }
        }
        return null;
    }

    /**
     * Replaces an existing {@link Header} or adds a new {@link Header}.
     *
     * <p>
     * Order doesn't matter in headers, so this method
     * does not make any guarantee as to where the new header
     * is inserted.
     *
     * @return
     *      always true. Don't use the return value.
     */
    @Override
    public boolean addOrReplace(Header header) {
        for (int i=0; i < size(); i++) {
          Header hdr = get(i);
          if (hdr.getNamespaceURI().equals(header.getNamespaceURI()) &&
              hdr.getLocalPart().equals(header.getLocalPart())) {
            // Put the new header in the old position. Call super versions
            // internally to avoid UnsupportedOperationException
            removeInternal(i);
            addInternal(i, header);
            return true;
          }
        }
        return add(header);
    }

    @Override
    public void replace(Header old, Header header) {
        for (int i=0; i < size(); i++) {
            Header hdr = get(i);
            if (hdr.getNamespaceURI().equals(header.getNamespaceURI()) &&
                hdr.getLocalPart().equals(header.getLocalPart())) {
              // Put the new header in the old position. Call super versions
              // internally to avoid UnsupportedOperationException
              removeInternal(i);
              addInternal(i, header);
              return;
            }
          }

          throw new IllegalArgumentException();
    }

    protected void addInternal(int index, Header header) {
        super.add(index, header);
    }

    protected Header removeInternal(int index) {
        return super.remove(index);
    }

    /**
     * Removes the first {@link Header} of the specified name.
     *
     * @param name fully qualified name of the header to remove
     *
     * @return null if not found.
     */
    public
    @Nullable
    @Override
    Header remove(@NotNull QName name) {
        return remove(name.getNamespaceURI(), name.getLocalPart());
    }

    /**
     * Removes the first {@link Header} of the specified name.
     *
     * @param index index of the header to remove
     *
     * @return removed header
     */
    @Override
    public Header remove(int index) {
        removeUnderstoodBit(index);
        return super.remove(index);
    }

    /**
     * Removes the "understood" bit for header on the position specified by {@code index} parameter
     * from the set of understood header bits.
     *
     * @param index position of the bit to remove
     */
    private void removeUnderstoodBit(int index) {
        assert index < size();

        if (index < 32) {
            /**
             * Let
             *   R be the bit to be removed
             *   M be a more significant "upper" bit than bit R
             *   L be a less significant "lower" bit than bit R
             *
             * Then following 3 lines of code produce these results:
             *
             *   old understoodBits = MMMMMMMMMMMMRLLLLLLLLLLLLLLLLLLL
             *
             *   shiftedUpperBits   = 0MMMMMMMMMMMM0000000000000000000
             *
             *   lowerBits          = 0000000000000LLLLLLLLLLLLLLLLLLL
             *
             *   new understoodBits = 0MMMMMMMMMMMMLLLLLLLLLLLLLLLLLLL
             *
             * The R bit is removed and all the upper bits are shifted right (unsigned)
             */
            int shiftedUpperBits = understoodBits >>> -31 + index << index;
            int lowerBits = understoodBits << -index >>> 31 - index >>> 1;
            understoodBits = shiftedUpperBits | lowerBits;

            if (moreUnderstoodBits != null && moreUnderstoodBits.cardinality() > 0) {
                if (moreUnderstoodBits.get(0)) {
                    understoodBits |= 0x80000000;
                }

                moreUnderstoodBits.clear(0);
                for (int i = moreUnderstoodBits.nextSetBit(1); i > 0; i = moreUnderstoodBits.nextSetBit(i + 1)) {
                    moreUnderstoodBits.set(i - 1);
                    moreUnderstoodBits.clear(i);
                }
            }
        } else if (moreUnderstoodBits != null && moreUnderstoodBits.cardinality() > 0) {
            index -= 32;
            moreUnderstoodBits.clear(index);
            for (int i = moreUnderstoodBits.nextSetBit(index); i >= 1; i = moreUnderstoodBits.nextSetBit(i + 1)) {
                moreUnderstoodBits.set(i - 1);
                moreUnderstoodBits.clear(i);
            }
        }

        // remove bit set if the new size will be < 33 => we fit all bits into int
        if (size() - 1 <= 33 && moreUnderstoodBits != null) {
            moreUnderstoodBits = null;
        }
    }

    /**
     * Removes a single instance of the specified element from this
     * header list, if it is present.  More formally,
     * removes a header {@code h} such that
     * {@code (o==null ? h==null : o.equals(h))},
     * if the header list contains one or more such
     * headers.  Returns {@code true} if the list contained the
     * specified element (or equivalently, if the list changed as a
     * result of the call).<p>
     *
     * @param o element to be removed from this list, if present.
     * @return {@code true} if the list contained the specified element.
     * @see #remove(javax.xml.namespace.QName)
     */
    @Override
    public boolean remove(Object o) {
        if (o != null) {
            for (int index = 0; index < this.size(); index++) {
                if (o.equals(this.get(index))) {
                    remove(index);
                    return true;
                }
            }
        }

        return false;
    }

    public Header remove(Header h) {
        if (remove((Object) h)) {
            return h;
        } else {
            return null;
        }
    }

    /**
     * Creates a copy.
     *
     * This handles null {@link HeaderList} correctly.
     *
     * @param original
     *      Can be null, in which case null will be returned.
     */
    public static HeaderList copy(MessageHeaders original) {
        if (original == null) {
            return null;
        } else {
            return new HeaderList(original);
        }
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
        return copy((MessageHeaders) original);
    }

    public void readResponseAddressingHeaders(WSDLPort wsdlPort, WSBinding binding) {
        // read Action
//        String wsaAction = getAction(binding.getAddressingVersion(), binding.getSOAPVersion());
        // TODO: validate client-inbound Action
    }

    @Override
    public void understood(QName name) {
       get(name, true);
    }

    @Override
    public void understood(String nsUri, String localName) {
        get(nsUri, localName, true);
    }

    @Override
    public Set<QName> getUnderstoodHeaders() {
        Set<QName> understoodHdrs = new HashSet<QName>();
        for (int i = 0; i < size(); i++) {
            if (isUnderstood(i)) {
                Header header = get(i);
                understoodHdrs.add(new QName(header.getNamespaceURI(), header.getLocalPart()));
            }
        }
        return understoodHdrs;
//        throw new UnsupportedOperationException("getUnderstoodHeaders() is not implemented by HeaderList");
    }

    @Override
    public boolean isUnderstood(Header header) {
        return isUnderstood(header.getNamespaceURI(), header.getLocalPart());
    }

    @Override
    public boolean isUnderstood(String nsUri, String localName) {
        for (int i = 0; i < size(); i++) {
            Header h = get(i);
            if (h.getLocalPart().equals(localName) && h.getNamespaceURI().equals(nsUri)) {
                return isUnderstood(i);
            }
        }
        return false;
    }

    @Override
    public boolean isUnderstood(QName name) {
        return isUnderstood(name.getNamespaceURI(), name.getLocalPart());
    }

    @Override
    public Set<QName> getNotUnderstoodHeaders(Set<String> roles, Set<QName> knownHeaders, WSBinding binding) {
        Set<QName> notUnderstoodHeaders = null;
        if (roles == null) {
            roles = new HashSet<String>();
        }
        SOAPVersion effectiveSoapVersion = getEffectiveSOAPVersion(binding);
        roles.add(effectiveSoapVersion.implicitRole);
        for (int i = 0; i < size(); i++) {
            if (!isUnderstood(i)) {
                Header header = get(i);
                if (!header.isIgnorable(effectiveSoapVersion, roles)) {
                    QName qName = new QName(header.getNamespaceURI(), header.getLocalPart());
                    if (binding == null) {
                        //if binding is null, no further checks needed...we already
                        //know this header is not understood from the isUnderstood
                        //check above
                        if (notUnderstoodHeaders == null) {
                            notUnderstoodHeaders = new HashSet<QName>();
                        }
                        notUnderstoodHeaders.add(qName);
                    } else {
                        // if the binding is not null, see if the binding can understand it
                        if (binding instanceof SOAPBindingImpl && !((SOAPBindingImpl) binding).understandsHeader(qName)) {
                            if (!knownHeaders.contains(qName)) {
                                //logger.info("Element not understood=" + qName);
                                if (notUnderstoodHeaders == null) {
                                    notUnderstoodHeaders = new HashSet<QName>();
                                }
                                notUnderstoodHeaders.add(qName);
                            }
                        }
                    }
                }
            }
        }
        return notUnderstoodHeaders;
    }

    private SOAPVersion getEffectiveSOAPVersion(WSBinding binding) {
        SOAPVersion mySOAPVersion = (soapVersion != null) ? soapVersion : binding.getSOAPVersion();
        if (mySOAPVersion == null) {
            mySOAPVersion = SOAPVersion.SOAP_11;
        }
        return mySOAPVersion;
    }

    public void setSoapVersion(SOAPVersion soapVersion) {
       this.soapVersion = soapVersion;
    }

    @Override
    public Iterator<Header> getHeaders() {
        return iterator();
    }

    @Override
    public List<Header> asList() {
        return this;
    }
}
