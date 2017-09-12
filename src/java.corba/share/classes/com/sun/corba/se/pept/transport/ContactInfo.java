/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.pept.transport;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.protocol.ClientRequestDispatcher;
import com.sun.corba.se.pept.transport.ConnectionCache;

/**
 * <p>The <b><em>primary</em></b> PEPt client-side plug-in point and enabler
 * for <b><em>altenate encodings, protocols and transports</em></b>.</p>
 *
 * <p><code>ContactInfo</code> is a <em>factory</em> for client-side
 * artifacts used
 * to construct and send a message (and possibly receive and process a
 * response).</p>
 *
 * @author Harold Carr
 */
public interface ContactInfo
{
    /**
     * The {@link com.sun.corba.se.pept.broker.Broker Broker} associated
     * with an invocation.
     *
     * @return {@link com.sun.corba.se.pept.broker.Broker Broker}
     */
    public Broker getBroker();

    /**
     * The parent
     * {@link com.sun.corba.se.pept.broker.ContactInfoList ContactInfoList}
     * for this <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.broker.ContactInfoList ContactInfoList}
     */
    public ContactInfoList getContactInfoList();

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.protocol.ClientRequestDispatcher
     * ClientRequestDispatcher}
     * used to handle the specific <em>protocol</em> represented by this
     * <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.protocol.ClientRequestDispatcher
     * ClientRequestDispatcher} */
    public ClientRequestDispatcher getClientRequestDispatcher();

    /**
     * Used to determine if a
     * {@link com.sun.corba.se.pept.transport.Connection Connection}
     * will be present in an invocation.
     *
     * For example, it may be
     * <code>false</code> in the case of shared-memory
     * <code>Input/OutputObjects</code>.
     *
     * @return <code>true</code> if a
     * {@link com.sun.corba.se.pept.transport.Connection Connection}
     * will be used for an invocation.
     */
    public boolean isConnectionBased();

    /**
     * Used to determine if the
     * {@link com.sun.corba.se.pept.transport.Connection Connection}
     * used for a request should be cached.
     *
     * If <code>true</code> then PEPt will attempt to reuse an existing
     * {@link com.sun.corba.se.pept.transport.Connection Connection}. If
     * one is not found it will create a new one and cache it for future use.
     *
     *
     * @return <code>true</code> if
     * {@link com.sun.corba.se.pept.transport.Connection Connection}s
     * created by this <code>ContactInfo</code> should be cached.
     */
    public boolean shouldCacheConnection();

    /**
     * PEPt uses separate caches for each type of <code>ContactInfo</code>
     * as given by <code>getConnectionCacheType</code>.
     *
     * @return {@link java.lang.String}
     */
    public String getConnectionCacheType();

    /**
     * Set the
     * {@link com.sun.corba.se.pept.transport.Outbound.ConnectionCache OutboundConnectionCache}
     * to be used by this <code>ContactInfo</code>.
     *
     * PEPt uses separate caches for each type of <code>ContactInfo</code>
     * as given by {@link #getConnectionCacheType}.
     * {@link #setConnectionCache} and {@link #getConnectionCache} support
     * an optimzation to avoid hashing to find that cache.
     *
     * @param connectionCache.
     */
    public void setConnectionCache(OutboundConnectionCache connectionCache);

    /**
     * Get the
     * {@link com.sun.corba.se.pept.transport.Outbound.ConnectionCache OutboundConnectionCache}
     * used by this <code>ContactInfo</code>
     *
     * PEPt uses separate caches for each type of <code>ContactInfo</code>
     * as given by {@link #getConnectionCacheType}.
     * {@link #setConnectionCache} and {@link #getConnectionCache} support
     * an optimzation to avoid hashing to find that cache.
     *
     * @return
     * {@link com.sun.corba.se.pept.transport.ConnectionCache ConnectionCache}
     */
    public OutboundConnectionCache getConnectionCache();

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.transport.Connection Connection}
     * to send and receive messages on the specific <em>transport</em>
     * represented by this <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.transport.Connection Connection}
     */
    public Connection createConnection();

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.protocol.MessageMeidator MessageMediator}
     * to hold internal data for a message to be sent using the specific
     * encoding, protocol, transport combination represented by this
     * <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.protocol.MessageMediator MessageMediator}
     */
    public MessageMediator createMessageMediator(Broker broker,
                                                 ContactInfo contactInfo,
                                                 Connection connection,
                                                 String methodName,
                                                 boolean isOneWay);

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.protocol.MessageMeidator MessageMediator}
     * to hold internal data for a message received using the specific
     * encoding, protocol, transport combination represented by this
     * <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.protocol.MessageMeidator MessageMediator}
     */
    public MessageMediator createMessageMediator(Broker broker,
                                                 Connection connection);

    /**
     * Used to finish creating a
     * {@link com.sun.corba.se.pept.protocol.MessageMeidator MessageMediator}
     * with internal data for a message received using the specific
     * encoding, protocol, transport combination represented by this
     * <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.protocol.MessageMediator MessageMediator}
     */
    public MessageMediator finishCreatingMessageMediator(Broker broker,
                                                         Connection connection,
                                                         MessageMediator messageMediator);

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.encoding.InputObject InputObject}
     * for the specific <em>encoding</em> represented by this
     * <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.encoding.InputObject InputObject}
     */
    public InputObject createInputObject(Broker broker,
                                         MessageMediator messageMediator);

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.encoding.OutputObject OutputObject}
     * for the specific <em>encoding</em> represented by this
     * <code>ContactInfo</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.encoding.OutputObject OutputObject}
     */
    public OutputObject createOutputObject(MessageMediator messageMediator);

    /**
     * Used to lookup artifacts associated with this <code>ContactInfo</code>.
     *
     * @return the hash value.
     */
    public int hashCode();
}

// End of file.
