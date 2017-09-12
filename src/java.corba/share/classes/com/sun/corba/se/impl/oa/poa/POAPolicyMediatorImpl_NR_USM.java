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

package com.sun.corba.se.impl.oa.poa ;

import java.util.Enumeration ;

import org.omg.PortableServer.POA ;
import org.omg.PortableServer.Servant ;
import org.omg.PortableServer.ServantManager ;
import org.omg.PortableServer.ServantLocator ;
import org.omg.PortableServer.ForwardRequest ;
import org.omg.PortableServer.POAPackage.NoServant ;
import org.omg.PortableServer.POAPackage.WrongPolicy ;
import org.omg.PortableServer.POAPackage.ObjectNotActive ;
import org.omg.PortableServer.POAPackage.ServantNotActive ;
import org.omg.PortableServer.POAPackage.ObjectAlreadyActive ;
import org.omg.PortableServer.POAPackage.ServantAlreadyActive ;
import org.omg.PortableServer.ServantLocatorPackage.CookieHolder ;

import com.sun.corba.se.impl.orbutil.concurrent.SyncUtil ;
import com.sun.corba.se.impl.orbutil.ORBUtility ;
import com.sun.corba.se.impl.orbutil.ORBConstants ;

import com.sun.corba.se.spi.oa.OAInvocationInfo ;
import com.sun.corba.se.impl.oa.NullServantImpl ;

/** Implementation of POARequesHandler that provides policy specific
 * operations on the POA.
 */
public class POAPolicyMediatorImpl_NR_USM extends POAPolicyMediatorBase {
    private ServantLocator locator ;

    POAPolicyMediatorImpl_NR_USM( Policies policies, POAImpl poa )
    {
        super( policies, poa ) ;

        // assert !policies.retainServants() && policies.useServantManager()
        if (policies.retainServants())
            throw poa.invocationWrapper().policyMediatorBadPolicyInFactory() ;

        if (!policies.useServantManager())
            throw poa.invocationWrapper().policyMediatorBadPolicyInFactory() ;

        locator = null ;
    }

    protected java.lang.Object internalGetServant( byte[] id,
        String operation ) throws ForwardRequest
    {
        if (locator == null)
            throw poa.invocationWrapper().poaNoServantManager() ;

        CookieHolder cookieHolder = orb.peekInvocationInfo().getCookieHolder() ;

        // Try - finally is J2EE requirement.
        java.lang.Object servant;
        try{
            poa.unlock() ;

            servant = locator.preinvoke(id, poa, operation, cookieHolder);
            if (servant == null)
                servant = new NullServantImpl( poa.omgInvocationWrapper().nullServantReturned() ) ;
            else
                setDelegate( (Servant)servant, id);
        } finally {
            poa.lock() ;
        }

        return servant;
    }

    public void returnServant()
    {
        OAInvocationInfo info = orb.peekInvocationInfo();
        if (locator == null)
            return;

        try {
            poa.unlock() ;
            locator.postinvoke(info.id(), (POA)(info.oa()),
                info.getOperation(), info.getCookieHolder().value,
                (Servant)(info.getServantContainer()) );
        } finally {
            poa.lock() ;
        }
    }

    public void etherealizeAll()
    {
        // NO-OP
    }

    public void clearAOM()
    {
        // NO-OP
    }

    public ServantManager getServantManager() throws WrongPolicy
    {
        return locator ;
    }

    public void setServantManager( ServantManager servantManager ) throws WrongPolicy
    {
        if (locator != null)
            throw poa.invocationWrapper().servantManagerAlreadySet() ;

        if (servantManager instanceof ServantLocator)
            locator = (ServantLocator)servantManager;
        else
            throw poa.invocationWrapper().servantManagerBadType() ;
    }

    public Servant getDefaultServant() throws NoServant, WrongPolicy
    {
        throw new WrongPolicy();
    }

    public void setDefaultServant( Servant servant ) throws WrongPolicy
    {
        throw new WrongPolicy();
    }

    public final void activateObject(byte[] id, Servant servant)
        throws WrongPolicy, ServantAlreadyActive, ObjectAlreadyActive
    {
        throw new WrongPolicy();
    }

    public Servant deactivateObject( byte[] id ) throws ObjectNotActive, WrongPolicy
    {
        throw new WrongPolicy();
    }

    public byte[] servantToId( Servant servant ) throws ServantNotActive, WrongPolicy
    {
        throw new WrongPolicy();
    }

    public Servant idToServant( byte[] id )
        throws WrongPolicy, ObjectNotActive
    {
        throw new WrongPolicy();
    }
}
