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

package com.sun.corba.se.impl.oa.poa ;

import org.omg.PortableServer.Servant ;

import org.omg.PortableServer.POAPackage.WrongPolicy ;
import org.omg.PortableServer.POAPackage.ServantNotActive ;
import org.omg.PortableServer.POAPackage.ServantAlreadyActive ;
import org.omg.PortableServer.POAPackage.ObjectNotActive ;
import org.omg.PortableServer.POAPackage.ObjectAlreadyActive ;

import com.sun.corba.se.impl.orbutil.concurrent.SyncUtil ;

import com.sun.corba.se.impl.orbutil.ORBUtility ;
import com.sun.corba.se.impl.orbutil.ORBConstants ;

import com.sun.corba.se.impl.javax.rmi.CORBA.Util ;

import com.sun.corba.se.impl.oa.NullServantImpl ;

public abstract class POAPolicyMediatorBase_R extends POAPolicyMediatorBase {
    protected ActiveObjectMap activeObjectMap ;

    POAPolicyMediatorBase_R( Policies policies, POAImpl poa )
    {
        super( policies, poa ) ;

        // assert policies.retainServants() && policies.useActiveObjectMapOnly()
        if (!policies.retainServants())
            throw poa.invocationWrapper().policyMediatorBadPolicyInFactory() ;

        activeObjectMap = ActiveObjectMap.create(poa, !isUnique);
    }

    public void returnServant()
    {
        // NO-OP
    }

    public void clearAOM()
    {
        activeObjectMap.clear() ;
        activeObjectMap = null ;
    }

    protected Servant internalKeyToServant( ActiveObjectMap.Key key )
    {
        AOMEntry entry = activeObjectMap.get(key);
        if (entry == null)
            return null ;

        return activeObjectMap.getServant( entry ) ;
    }

    protected Servant internalIdToServant( byte[] id )
    {
        ActiveObjectMap.Key key = new ActiveObjectMap.Key( id ) ;
        return internalKeyToServant( key ) ;
    }

    protected void activateServant( ActiveObjectMap.Key key, AOMEntry entry, Servant servant )
    {
        setDelegate(servant, key.id );

        if (orb.shutdownDebugFlag) {
            System.out.println("Activating object " + servant +
                " with POA " + poa);
        }

        activeObjectMap.putServant( servant, entry ) ;

        if (Util.isInstanceDefined()) {
            POAManagerImpl pm = (POAManagerImpl)poa.the_POAManager() ;
            POAFactory factory = pm.getFactory() ;
            factory.registerPOAForServant(poa, servant);
        }
    }

    public final void activateObject(byte[] id, Servant servant)
        throws WrongPolicy, ServantAlreadyActive, ObjectAlreadyActive
    {
        if (isUnique && activeObjectMap.contains(servant))
            throw new ServantAlreadyActive();
        ActiveObjectMap.Key key = new ActiveObjectMap.Key( id ) ;

        AOMEntry entry = activeObjectMap.get( key ) ;

        // Check for an ObjectAlreadyActive error
        entry.activateObject() ;
        activateServant( key, entry, servant ) ;
    }

    public Servant deactivateObject( byte[] id )
        throws ObjectNotActive, WrongPolicy
    {
        ActiveObjectMap.Key key = new ActiveObjectMap.Key( id ) ;
        return deactivateObject( key ) ;
    }

    protected void deactivateHelper( ActiveObjectMap.Key key, AOMEntry entry,
        Servant s ) throws ObjectNotActive, WrongPolicy
    {
        // Default does nothing, but the USE_SERVANT_MANAGER case
        // must handle etherealization

        activeObjectMap.remove(key);

        if (Util.isInstanceDefined()) {
            POAManagerImpl pm = (POAManagerImpl)poa.the_POAManager() ;
            POAFactory factory = pm.getFactory() ;
            factory.unregisterPOAForServant(poa, s);
        }
    }

    public Servant deactivateObject( ActiveObjectMap.Key key )
        throws ObjectNotActive, WrongPolicy
    {
        if (orb.poaDebugFlag) {
            ORBUtility.dprint( this,
                "Calling deactivateObject for key " + key ) ;
        }

        try {
            AOMEntry entry = activeObjectMap.get(key);
            if (entry == null)
                throw new ObjectNotActive();

            Servant s = activeObjectMap.getServant( entry ) ;
            if (s == null)
                throw new ObjectNotActive();

            if (orb.poaDebugFlag) {
                System.out.println("Deactivating object " + s + " with POA " + poa);
            }

            deactivateHelper( key, entry, s ) ;

            return s ;
        } finally {
            if (orb.poaDebugFlag) {
                ORBUtility.dprint( this,
                    "Exiting deactivateObject" ) ;
            }
        }
    }

    public byte[] servantToId( Servant servant ) throws ServantNotActive, WrongPolicy
    {
        // XXX needs to handle call from an invocation on this POA

        if (!isUnique && !isImplicit)
            throw new WrongPolicy();

        if (isUnique) {
            ActiveObjectMap.Key key = activeObjectMap.getKey(servant);
            if (key != null)
                return key.id ;
        }

        // assert !isUnique || (servant not in activateObjectMap)

        if (isImplicit)
            try {
                byte[] id = newSystemId() ;
                activateObject( id, servant ) ;
                return id ;
            } catch (ObjectAlreadyActive oaa) {
                // This can't occur here, since id is always brand new.
                throw poa.invocationWrapper().servantToIdOaa( oaa ) ;
            } catch (ServantAlreadyActive s) {
                throw poa.invocationWrapper().servantToIdSaa( s ) ;
            } catch (WrongPolicy w) {
                throw poa.invocationWrapper().servantToIdWp( w ) ;
            }

        throw new ServantNotActive();
    }
}
