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

package com.sun.corba.se.spi.protocol;

import java.util.Set;

import com.sun.corba.se.pept.protocol.ClientRequestDispatcher ;
import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher ;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcherFactory ;

import com.sun.corba.se.spi.oa.ObjectAdapterFactory ;

/**
 * This is a registry of all subcontract ID dependent objects.  This includes:
 * LocalClientRequestDispatcherFactory, ClientRequestDispatcher, ServerRequestDispatcher, and
 * ObjectAdapterFactory.
 * XXX Should the registerXXX methods take an scid or not?  I think we
 * want to do this so that the same instance can be shared across multiple
 * scids (and this is already true for ObjectAdapterFactory and LocalClientRequestDispatcherFactory),
 * but this will require some changes for ClientRequestDispatcher and ServerRequestDispatcher.
 */
public interface RequestDispatcherRegistry {
    // XXX needs javadocs!

    void registerClientRequestDispatcher( ClientRequestDispatcher csc, int scid) ;

    ClientRequestDispatcher getClientRequestDispatcher( int scid ) ;

    void registerLocalClientRequestDispatcherFactory( LocalClientRequestDispatcherFactory csc, int scid) ;

    LocalClientRequestDispatcherFactory getLocalClientRequestDispatcherFactory( int scid ) ;

    void registerServerRequestDispatcher( CorbaServerRequestDispatcher ssc, int scid) ;

    CorbaServerRequestDispatcher getServerRequestDispatcher(int scid) ;

    void registerServerRequestDispatcher( CorbaServerRequestDispatcher ssc, String name ) ;

    CorbaServerRequestDispatcher getServerRequestDispatcher( String name ) ;

    void registerObjectAdapterFactory( ObjectAdapterFactory oaf, int scid) ;

    ObjectAdapterFactory getObjectAdapterFactory( int scid ) ;

    Set getObjectAdapterFactories() ;
}
