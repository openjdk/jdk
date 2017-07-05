/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package com.sun.corba.se.impl.protocol;

import org.omg.CORBA.portable.ServantObject ;

import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcherFactory ;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.oa.OAInvocationInfo ;
import com.sun.corba.se.spi.oa.OADestroyed ;

import com.sun.corba.se.spi.ior.IOR;

import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.logging.POASystemException ;

public class FullServantCacheLocalCRDImpl extends ServantCacheLocalCRDBase
{
    public FullServantCacheLocalCRDImpl( ORB orb, int scid, IOR ior )
    {
        super( (com.sun.corba.se.spi.orb.ORB)orb, scid, ior ) ;
    }

    public ServantObject servant_preinvoke( org.omg.CORBA.Object self,
        String operation, Class expectedType )
    {
        OAInvocationInfo cachedInfo = getCachedInfo() ;
        if (!checkForCompatibleServant( cachedInfo, expectedType ))
            return null ;

        // Note that info is shared across multiple threads
        // using the same subcontract, each of which may
        // have its own operation.  Therefore we need to clone it.
        OAInvocationInfo info = new OAInvocationInfo( cachedInfo, operation ) ;
        orb.pushInvocationInfo( info ) ;

        try {
            info.oa().enter() ;
        } catch (OADestroyed pdes) {
            throw wrapper.preinvokePoaDestroyed( pdes ) ;
        }

        return info ;
    }

    public void servant_postinvoke(org.omg.CORBA.Object self,
                                   ServantObject servantobj)
    {
        OAInvocationInfo cachedInfo = getCachedInfo() ;
        cachedInfo.oa().exit() ;
        orb.popInvocationInfo() ;
    }
}
