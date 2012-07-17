/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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
 */
public interface RequestDispatcherRegistry {

    /** Register a ClientRequestDispatcher for a particular subcontract ID.
     * The subcontract ID appears in the ObjectKey of an object reference, and is used
     * to control how a remote method invocation is processed by the ORB for a
     * particular kind of object reference.
     */
    void registerClientRequestDispatcher( ClientRequestDispatcher csc, int scid) ;

    /** Get the ClientRequestDispatcher for subcontract ID scid.
     */
    ClientRequestDispatcher getClientRequestDispatcher( int scid ) ;

    /** Register a LocalClientRequestDispatcher for a particular subcontract ID.
     * The subcontract ID appears in the ObjectKey of an object reference, and is used
     * to control how a particular kind of colocated request is processed.
     */
    void registerLocalClientRequestDispatcherFactory( LocalClientRequestDispatcherFactory csc, int scid) ;

    /** Get the LocalClientRequestDispatcher for subcontract ID scid.
     */
    LocalClientRequestDispatcherFactory getLocalClientRequestDispatcherFactory( int scid ) ;

    /** Register a CorbaServerRequestDispatcher for a particular subcontract ID.
     * The subcontract ID appears in the ObjectKey of an object reference, and is used
     * to control how a particular kind of request is processed when received by the ORB.
     */
    void registerServerRequestDispatcher( CorbaServerRequestDispatcher ssc, int scid) ;

    /** Get the CorbaServerRequestDispatcher for subcontract ID scid.
     */
    CorbaServerRequestDispatcher getServerRequestDispatcher(int scid) ;

    /** Register a CorbaServerRequestDispatcher for handling an explicit object key name.
     * This is used for non-standard invocations such as INS and the bootstrap name service.
     */
    void registerServerRequestDispatcher( CorbaServerRequestDispatcher ssc, String name ) ;

    /** Get the CorbaServerRequestDispatcher for a particular object key.
     */
    CorbaServerRequestDispatcher getServerRequestDispatcher( String name ) ;

    /** Register an ObjectAdapterFactory for a particular subcontract ID.
     * This controls how Object references are created and managed.
     */
    void registerObjectAdapterFactory( ObjectAdapterFactory oaf, int scid) ;

    /** Get the ObjectAdapterFactory for a particular subcontract ID scid.
     */
    ObjectAdapterFactory getObjectAdapterFactory( int scid ) ;

    /** Return the set of all ObjectAdapterFactory instances that are registered.
     */
    Set<ObjectAdapterFactory> getObjectAdapterFactories();
}
