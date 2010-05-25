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

import org.omg.CORBA.SystemException;
import org.omg.CORBA.OBJ_ADAPTER ;
import org.omg.CORBA.UNKNOWN ;
import org.omg.CORBA.CompletionStatus ;

import org.omg.CORBA.portable.ServantObject;

import com.sun.corba.se.pept.protocol.ClientRequestDispatcher;

import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcherFactory;
import com.sun.corba.se.spi.protocol.ForwardException ;

import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.oa.OAInvocationInfo ;
import com.sun.corba.se.spi.oa.OADestroyed;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.ior.IOR ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.POASystemException ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class POALocalCRDImpl extends LocalClientRequestDispatcherBase
{
    private ORBUtilSystemException wrapper ;
    private POASystemException poaWrapper ;

    public POALocalCRDImpl( ORB orb, int scid, IOR ior)
    {
        super( (com.sun.corba.se.spi.orb.ORB)orb, scid, ior );
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        poaWrapper = POASystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
    }

    private OAInvocationInfo servantEnter( ObjectAdapter oa ) throws OADestroyed
    {
        oa.enter() ;

        OAInvocationInfo info = oa.makeInvocationInfo( objectId ) ;
        orb.pushInvocationInfo( info ) ;

        return info ;
    }

    private void servantExit( ObjectAdapter oa )
    {
        try {
            oa.returnServant();
        } finally {
            oa.exit() ;
            orb.popInvocationInfo() ;
        }
    }

    // Look up the servant for this request and return it in a
    // ServantObject.  Note that servant_postinvoke is always called
    // by the stub UNLESS this method returns null.  However, in all
    // cases we must be sure that ObjectAdapter.getServant and
    // ObjectAdapter.returnServant calls are paired, as required for
    // Portable Interceptors and Servant Locators in the POA.
    // Thus, this method must call returnServant if it returns null.
    public ServantObject servant_preinvoke(org.omg.CORBA.Object self,
                                           String operation,
                                           Class expectedType)
    {
        ObjectAdapter oa = oaf.find( oaid ) ;
        OAInvocationInfo info = null ;

        try {
            info = servantEnter( oa ) ;
            info.setOperation( operation ) ;
        } catch ( OADestroyed ex ) {
            // Destroyed POAs can be recreated by normal adapter activation.
            // So just reinvoke this method.
            return servant_preinvoke(self, operation, expectedType);
        }

        try {
            try {
                oa.getInvocationServant( info );
                if (!checkForCompatibleServant( info, expectedType ))
                    return null ;
            } catch (Throwable thr) {
                // Cleanup after this call, then throw to allow
                // outer try to handle the exception appropriately.
                servantExit( oa ) ;
                throw thr ;
            }
        } catch ( ForwardException ex ) {
            /* REVISIT
            ClientRequestDispatcher csub = (ClientRequestDispatcher)
                StubAdapter.getDelegate( ex.forward_reference ) ;
            IOR ior = csub.getIOR() ;
            setLocatedIOR( ior ) ;
            */
            RuntimeException runexc = new RuntimeException("deal with this.");
            runexc.initCause( ex ) ;
            throw runexc ;
        } catch ( ThreadDeath ex ) {
            // ThreadDeath on the server side should not cause a client
            // side thread death in the local case.  We want to preserve
            // this behavior for location transparency, so that a ThreadDeath
            // has the same affect in either the local or remote case.
            // The non-colocated case is handled in iiop.ORB.process, which
            // throws the same exception.
            throw wrapper.runtimeexception( ex ) ;
        } catch ( Throwable t ) {
            if (t instanceof SystemException)
                throw (SystemException)t ;

            throw poaWrapper.localServantLookup( t ) ;
        }

        if (!checkForCompatibleServant( info, expectedType )) {
            servantExit( oa ) ;
            return null ;
        }

        return info;
    }

    public void servant_postinvoke(org.omg.CORBA.Object self,
                                   ServantObject servantobj)
    {
        ObjectAdapter oa = orb.peekInvocationInfo().oa() ;
        servantExit( oa ) ;
    }
}

// End of file.
