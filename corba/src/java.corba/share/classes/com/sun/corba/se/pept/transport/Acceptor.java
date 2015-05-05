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
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.EventHandler;

/**
 * <p>The <b><em>primary</em></b> PEPt server-side plug-in point and enabler
 * for <b><em>altenate encodings, protocols and transports</em></b>.</p>
 *
 * <p><code>Acceptor</code> is a <em>factory</em> for client-side
 * artifacts used to receive a message (and possibly send a response).</p>
 *
 * @author Harold Carr
 */
public interface Acceptor
{
    /**
     * Used to initialize an <code>Acceptor</code>.
     *
     * For example, initialization may mean to create a
     * {@link java.nio.channels.ServerSocketChannel ServerSocketChannel}.
     *
     * Note: this must be prepared to be be called multiple times.
     *
     * @return <code>true</code> when it performs initializatin
     * actions (typically the first call.
     */
    public boolean initialize();

    /**
     * Used to determine if an <code>Acceptor</code> has been initialized.
     *
     * @return <code>true</code> if the <code>Acceptor</code> has been
     * initialized.
     */
    public boolean initialized();

    /**
     * PEPt uses separate caches for each type of <code>Acceptor</code>
     * as given by <code>getConnectionCacheType</code>.
     *
     * @return {@link java.lang.String}
     */
    public String getConnectionCacheType();

    /**
     * Set the
     * {@link com.sun.corba.se.pept.transport.InboundConnectionCache InboundConnectionCache}
     * to be used by this <code>Acceptor</code>.
     *
     * PEPt uses separate caches for each type of <code>Acceptor</code>
     * as given by {@link #getConnectionCacheType}.
     * {@link #setConnectionCache} and {@link #getConnectionCache} support
     * an optimzation to avoid hashing to find that cache.
     *
     * @param connectionCache.
     */
    public void setConnectionCache(InboundConnectionCache connectionCache);

    /**
     * Get the
     * {@link com.sun.corba.se.pept.transport.InboundConnectionCache InboundConnectionCache}
     * used by this <code>Acceptor</code>
     *
     * PEPt uses separate caches for each type of <code>Acceptor</code>
     * as given by {@link #getConnectionCacheType}.
     * {@link #setConnectionCache} and {@link #getConnectionCache} support
     * an optimzation to avoid hashing to find that cache.
     *
     * @return
     * {@link com.sun.corba.se.pept.transport.ConnectionCache ConnectionCache}
     */
    public InboundConnectionCache getConnectionCache();

    /**
     * Used to determine if the <code>Acceptor</code> should register
     * with
     * {@link com.sun.corba.se.pept.transport.Selector Selector}
     * to handle accept events.
     *
     * For example, this may be <em>false</em> in the case of Solaris Doors
     * which do not actively listen.
     *
     * @return <code>true</code> if the <code>Acceptor</code> should be
     * registered with
     * {@link com.sun.corba.se.pept.transport.Selector Selector}
     */
    public boolean shouldRegisterAcceptEvent();

    /**
     * Accept a connection request.
     *
     * This is called either when the selector gets an accept event
     * for this <code>Acceptor</code> or by a
     * {@link com.sun.corba.se.pept.transport.ListenerThread ListenerThread}.
     *
     * It results in a
     * {@link com.sun.corba.se.pept.transport.Connection Connection}
     * being created.
     */
    public void accept();

    /**
     * Close the <code>Acceptor</code>.
     */
    public void close();

    /**
     * Get the
     * {@link com.sun.corba.se.pept.transport.EventHandler EventHandler}
     * associated with this <code>Acceptor</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.transport.EventHandler EventHandler}
     */
    public EventHandler getEventHandler();

    //
    // Factory methods
    //

    // REVISIT: Identical to ContactInfo method.  Refactor into base interface.

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.protocol.MessageMeidator MessageMediator}
     * to hold internal data for a message received using the specific
     * encoding, protocol, transport combination represented by this
     * <code>Acceptor</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.protocol.MessageMeidator MessageMediator}
     */
    public MessageMediator createMessageMediator(Broker xbroker,
                                                 Connection xconnection);

    // REVISIT: Identical to ContactInfo method.  Refactor into base interface.

    /**
     * Used to finish creating a
     * {@link com.sun.corba.se.pept.protocol.MessageMeidator MessageMediator}
     * to with internal data for a message received using the specific
     * encoding, protocol, transport combination represented by this
     * <code>Acceptor</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.protocol.MessageMediator MessageMediator}
     */

    public MessageMediator finishCreatingMessageMediator(Broker broker,
                                                         Connection xconnection,
                                                         MessageMediator messageMediator);

    /**
     * Used to get a
     * {@link com.sun.corba.se.pept.encoding.InputObject InputObject}
     * for the specific <em>encoding</em> represented by this
     * <code>Acceptor</code>.
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
     * <code>Acceptor</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.encoding.OutputObject OutputObject}
     */
    public OutputObject createOutputObject(Broker broker,
                                           MessageMediator messageMediator);

    //
    // Usage dictates implementation equals and hashCode.
    //
}

// End of file.
