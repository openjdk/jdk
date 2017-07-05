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

import org.omg.CORBA.BAD_OPERATION ;
import org.omg.CORBA.INTERNAL ;
import org.omg.CORBA.SystemException ;
import org.omg.CORBA.CompletionStatus ;

import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher;
import com.sun.corba.se.spi.protocol.ForwardException;

// XXX This should be in the SPI
import com.sun.corba.se.impl.protocol.LocalClientRequestDispatcherBase;

import com.sun.corba.se.spi.oa.OAInvocationInfo;
import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.oa.OADestroyed;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.ior.IOR ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.POASystemException;

public abstract class ServantCacheLocalCRDBase extends LocalClientRequestDispatcherBase
{

    private OAInvocationInfo cachedInfo ;
    protected POASystemException wrapper ;

    protected ServantCacheLocalCRDBase( ORB orb, int scid, IOR ior )
    {
        super( orb, scid, ior ) ;
        wrapper = POASystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
    }

    protected synchronized OAInvocationInfo getCachedInfo()
    {
        if (!servantIsLocal)
            throw wrapper.servantMustBeLocal() ;

        if (cachedInfo == null) {
            ObjectAdapter oa = oaf.find( oaid ) ;
            cachedInfo = oa.makeInvocationInfo( objectId ) ;

            // InvocationInfo must be pushed before calling getInvocationServant
            orb.pushInvocationInfo( cachedInfo ) ;

            try {
                oa.enter( );
                oa.getInvocationServant( cachedInfo ) ;
            } catch (ForwardException freq) {
                throw wrapper.illegalForwardRequest( freq ) ;
            } catch( OADestroyed oades ) {
                // This is an error since no user of this implementation
                // should ever throw this exception
                throw wrapper.adapterDestroyed( oades ) ;
            } finally {
                oa.returnServant( );
                oa.exit( );
                orb.popInvocationInfo() ;
            }
        }

        return cachedInfo ;
    }
}

// End of File
