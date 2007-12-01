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

package com.sun.corba.se.impl.oa.poa ;

import java.util.Enumeration ;

import org.omg.PortableServer.Servant ;
import org.omg.PortableServer.ServantManager ;
import org.omg.PortableServer.ForwardRequest ;
import org.omg.PortableServer.POAPackage.WrongPolicy ;
import org.omg.PortableServer.POAPackage.ObjectNotActive ;
import org.omg.PortableServer.POAPackage.ServantNotActive ;
import org.omg.PortableServer.POAPackage.ObjectAlreadyActive ;
import org.omg.PortableServer.POAPackage.ServantAlreadyActive ;
import org.omg.PortableServer.POAPackage.NoServant ;

import com.sun.corba.se.impl.orbutil.concurrent.SyncUtil ;
import com.sun.corba.se.impl.orbutil.ORBUtility ;
import com.sun.corba.se.impl.orbutil.ORBConstants ;

/** Implementation of POARequesHandler that provides policy specific
 * operations on the POA.
 */
public class POAPolicyMediatorImpl_R_UDS extends POAPolicyMediatorBase_R {
    private Servant defaultServant ;

    POAPolicyMediatorImpl_R_UDS( Policies policies, POAImpl poa )
    {
        // assert policies.retainServants()
        super( policies, poa ) ;
        defaultServant = null ;

        // policies.useDefaultServant()
        if (!policies.useDefaultServant())
            throw poa.invocationWrapper().policyMediatorBadPolicyInFactory() ;
    }

    protected java.lang.Object internalGetServant( byte[] id,
        String operation ) throws ForwardRequest
    {
        Servant servant = internalIdToServant( id ) ;
        if (servant == null)
            servant = defaultServant ;

        if (servant == null)
            throw poa.invocationWrapper().poaNoDefaultServant() ;

        return servant ;
    }

    public void etherealizeAll()
    {
        // NO-OP
    }

    public ServantManager getServantManager() throws WrongPolicy
    {
        throw new WrongPolicy();
    }

    public void setServantManager( ServantManager servantManager ) throws WrongPolicy
    {
        throw new WrongPolicy();
    }

    public Servant getDefaultServant() throws NoServant, WrongPolicy
    {
        if (defaultServant == null)
            throw new NoServant();
        else
            return defaultServant;
    }

    public void setDefaultServant( Servant servant ) throws WrongPolicy
    {
        defaultServant = servant;
        setDelegate(defaultServant, "DefaultServant".getBytes());
    }

    public Servant idToServant( byte[] id )
        throws WrongPolicy, ObjectNotActive
    {
        ActiveObjectMap.Key key = new ActiveObjectMap.Key( id ) ;
        Servant s = internalKeyToServant(key);

        if (s == null)
            if (defaultServant != null)
                s = defaultServant;

        if (s == null)
            throw new ObjectNotActive() ;

        return s;
    }
}
