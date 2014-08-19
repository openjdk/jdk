/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.legacy.connection;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.omg.CORBA.INITIALIZE;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.CompletionStatus;

import com.sun.corba.se.pept.transport.Acceptor;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.corba.se.pept.transport.Selector;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate;
import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.transport.CorbaTransportManager;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketEndPointInfo;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketManager;
import com.sun.corba.se.spi.transport.SocketOrChannelAcceptor;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.encoding.EncapsOutputStream;
import com.sun.corba.se.impl.legacy.connection.SocketFactoryAcceptorImpl;
import com.sun.corba.se.impl.legacy.connection.USLPort;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.transport.SocketOrChannelAcceptorImpl;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;

public class LegacyServerSocketManagerImpl
    implements
        LegacyServerSocketManager
{
    protected ORB orb;
    private ORBUtilSystemException wrapper ;

    public LegacyServerSocketManagerImpl(ORB orb)
    {
        this.orb = orb;
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_TRANSPORT ) ;
    }

    ////////////////////////////////////////////////////
    //
    // LegacyServerSocketManager
    //

    // Only used in ServerManagerImpl.
    public int legacyGetTransientServerPort(String type)
    {
        return legacyGetServerPort(type, false);
    }

    // Only used by POAPolicyMediatorBase.
    public synchronized int legacyGetPersistentServerPort(String socketType)
    {
        if (orb.getORBData().getServerIsORBActivated()) {
            // this server is activated by orbd
            return legacyGetServerPort(socketType, true);
        } else if (orb.getORBData().getPersistentPortInitialized()) {
            // this is a user-activated server
            return orb.getORBData().getPersistentServerPort();
        } else {
            throw wrapper.persistentServerportNotSet(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    // Only used by PI IORInfoImpl.
    public synchronized int legacyGetTransientOrPersistentServerPort(
        String socketType)
    {
            return legacyGetServerPort(socketType,
                                       orb.getORBData()
                                       .getServerIsORBActivated());
    }

    // Used in RepositoryImpl, ServerManagerImpl, POAImpl,
    // POAPolicyMediatorBase, TOAImpl.
    // To get either default or bootnaming endpoint.
    public synchronized LegacyServerSocketEndPointInfo legacyGetEndpoint(
        String name)
    {
        Iterator iterator = getAcceptorIterator();
        while (iterator.hasNext()) {
            LegacyServerSocketEndPointInfo endPoint = cast(iterator.next());
            if (endPoint != null && name.equals(endPoint.getName())) {
                return endPoint;
            }
        }
        throw new INTERNAL("No acceptor for: " + name);
    }

    // Check to see if the given port is equal to any of the ORB Server Ports.
    // XXX Does this need to change for the multi-homed case?
    // Used in IIOPProfileImpl, ORBImpl.
    public boolean legacyIsLocalServerPort(int port)
    {
        Iterator iterator = getAcceptorIterator();
        while (iterator.hasNext()) {
            LegacyServerSocketEndPointInfo endPoint = cast(iterator.next());
            if (endPoint != null && endPoint.getPort() == port) {
                return true;
            }
        }
        return false;
    }

    ////////////////////////////////////////////////////
    //
    // Implementation.
    //

    private int legacyGetServerPort (String socketType, boolean isPersistent)
    {
        Iterator endpoints = getAcceptorIterator();
        while (endpoints.hasNext()) {
            LegacyServerSocketEndPointInfo ep = cast(endpoints.next());
            if (ep != null && ep.getType().equals(socketType)) {
                if (isPersistent) {
                    return ep.getLocatorPort();
                } else {
                    return ep.getPort();
                }
            }
        }
        return -1;
    }

    private Iterator getAcceptorIterator()
    {
        Collection acceptors =
            orb.getCorbaTransportManager().getAcceptors(null, null);
        if (acceptors != null) {
            return acceptors.iterator();
        }

        throw wrapper.getServerPortCalledBeforeEndpointsInitialized() ;
    }

    private LegacyServerSocketEndPointInfo cast(Object o)
    {
        if (o instanceof LegacyServerSocketEndPointInfo) {
            return (LegacyServerSocketEndPointInfo) o;
        }
        return null;
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint("LegacyServerSocketManagerImpl", msg);
    }
}

// End of file.
