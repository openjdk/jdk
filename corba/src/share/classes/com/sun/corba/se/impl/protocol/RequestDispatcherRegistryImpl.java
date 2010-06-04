/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.protocol;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import com.sun.corba.se.pept.protocol.ClientRequestDispatcher ;

import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher ;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcherFactory ;
import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher ;
import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry ;

import com.sun.corba.se.spi.oa.ObjectAdapterFactory ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.impl.orbutil.ORBConstants ;
import com.sun.corba.se.impl.orbutil.DenseIntMapImpl ;

/**
 * This is a registry of all subcontract ID dependent objects.  This includes:
 * LocalClientRequestDispatcherFactory, ClientRequestDispatcher, ServerSubcontract, and
 * ObjectAdapterFactory.
 */
public class RequestDispatcherRegistryImpl implements RequestDispatcherRegistry {
    private ORB orb;

    protected int defaultId;                    // The default subcontract ID to use if
                                                // there is no more specific ID available.
                                                // This happens when invoking a foreign IOR.
    private DenseIntMapImpl SDRegistry ;        // ServerRequestDispatcher registry
    private DenseIntMapImpl CSRegistry ;        // ClientRequestDispatcher registry
    private DenseIntMapImpl OAFRegistry ;       // ObjectAdapterFactory registry
    private DenseIntMapImpl LCSFRegistry ;      // LocalClientRequestDispatcherFactory registry
    private Set objectAdapterFactories ;        // Set of all ObjectAdapterFactory instances
    private Set objectAdapterFactoriesView ;    // Read-only view of oaf instances
    private Map stringToServerSubcontract ;     // Map from obect key string to
                                                // ServerSubcontract
                                                // for special bootstrap IORs

    public RequestDispatcherRegistryImpl(ORB orb, int defaultId )
    {
        this.orb = orb;
        this.defaultId = defaultId;
        SDRegistry = new DenseIntMapImpl() ;
        CSRegistry = new DenseIntMapImpl() ;
        OAFRegistry = new DenseIntMapImpl() ;
        LCSFRegistry = new DenseIntMapImpl() ;
        objectAdapterFactories = new HashSet() ;
        objectAdapterFactoriesView = Collections.unmodifiableSet( objectAdapterFactories ) ;
        stringToServerSubcontract = new HashMap() ;
    }

    public synchronized void registerClientRequestDispatcher(
        ClientRequestDispatcher csc, int scid)
    {
        CSRegistry.set( scid, csc ) ;
    }

    public synchronized void registerLocalClientRequestDispatcherFactory(
        LocalClientRequestDispatcherFactory csc, int scid)
    {
        LCSFRegistry.set( scid, csc ) ;
    }

    public synchronized void registerServerRequestDispatcher(
        CorbaServerRequestDispatcher ssc, int scid)
    {
        SDRegistry.set( scid, ssc ) ;
    }

    public synchronized void registerServerRequestDispatcher(
        CorbaServerRequestDispatcher scc, String name )
    {
        stringToServerSubcontract.put( name, scc ) ;
    }

    public synchronized void registerObjectAdapterFactory(
        ObjectAdapterFactory oaf, int scid)
    {
        objectAdapterFactories.add( oaf ) ;
        OAFRegistry.set( scid, oaf ) ;
    }

    // **************************************************
    // Methods to find the subcontract side subcontract
    // **************************************************

    // Note that both forms of getServerRequestDispatcher need to return
    // the default server delegate if no other match is found.
    // This is essential to proper handling of errors for
    // malformed requests.  In particular, a bad MAGIC will
    // result in a lookup in the named key table (stringToServerSubcontract),
    // which must return a valid ServerRequestDispatcher.  A bad subcontract ID
    // will similarly need to return the default ServerRequestDispatcher.

    public CorbaServerRequestDispatcher getServerRequestDispatcher(int scid)
    {
        CorbaServerRequestDispatcher sdel =
            (CorbaServerRequestDispatcher)(SDRegistry.get(scid)) ;
        if ( sdel == null )
            sdel = (CorbaServerRequestDispatcher)(SDRegistry.get(defaultId)) ;

        return sdel;
    }

    public CorbaServerRequestDispatcher getServerRequestDispatcher( String name )
    {
        CorbaServerRequestDispatcher sdel =
            (CorbaServerRequestDispatcher)stringToServerSubcontract.get( name ) ;

        if ( sdel == null )
            sdel = (CorbaServerRequestDispatcher)(SDRegistry.get(defaultId)) ;

        return sdel;
    }

    public LocalClientRequestDispatcherFactory getLocalClientRequestDispatcherFactory(
        int scid )
    {
        LocalClientRequestDispatcherFactory factory =
            (LocalClientRequestDispatcherFactory)(LCSFRegistry.get(scid)) ;
        if (factory == null) {
            factory = (LocalClientRequestDispatcherFactory)(LCSFRegistry.get(defaultId)) ;
        }

        return factory ;
    }

    public ClientRequestDispatcher getClientRequestDispatcher( int scid )
    {
        ClientRequestDispatcher subcontract =
            (ClientRequestDispatcher)(CSRegistry.get(scid)) ;
        if (subcontract == null) {
            subcontract = (ClientRequestDispatcher)(CSRegistry.get(defaultId)) ;
        }

        return subcontract ;
    }

    public ObjectAdapterFactory getObjectAdapterFactory( int scid )
    {
        ObjectAdapterFactory oaf =
            (ObjectAdapterFactory)(OAFRegistry.get(scid)) ;
        if ( oaf == null )
            oaf = (ObjectAdapterFactory)(OAFRegistry.get(defaultId)) ;

        return oaf;
    }

    public Set getObjectAdapterFactories()
    {
        return objectAdapterFactoriesView ;
    }
}
