/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;

import com.sun.corba.se.pept.transport.Acceptor;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.pept.transport.ConnectionCache;
import com.sun.corba.se.pept.transport.Selector;

/**
 * @author Harold Carr
 */
public interface TransportManager
{
    public ByteBufferPool getByteBufferPool(int id);

    public OutboundConnectionCache getOutboundConnectionCache(
        ContactInfo contactInfo);

    public Collection getOutboundConnectionCaches();

    public InboundConnectionCache getInboundConnectionCache(Acceptor acceptor);

    public Collection getInboundConnectionCaches();

    public Selector getSelector(int id);

    public void registerAcceptor(Acceptor acceptor);

    public Collection getAcceptors();

    public void unregisterAcceptor(Acceptor acceptor);

    public void close();
}

// End of file.
