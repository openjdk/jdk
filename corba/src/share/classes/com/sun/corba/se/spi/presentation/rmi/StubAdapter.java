/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.presentation.rmi ;

import javax.rmi.CORBA.Tie ;

import org.omg.CORBA.portable.Delegate ;
import org.omg.CORBA.portable.ObjectImpl ;
import org.omg.CORBA.portable.OutputStream ;

import org.omg.PortableServer.POA ;
import org.omg.PortableServer.POAManager ;
import org.omg.PortableServer.Servant ;

import org.omg.PortableServer.POAPackage.WrongPolicy ;
import org.omg.PortableServer.POAPackage.ServantNotActive ;
import org.omg.PortableServer.POAManagerPackage.AdapterInactive ;

import org.omg.CORBA.ORB ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

// XXX Getting rid of this requires introducing an ObjectAdapterManager abstraction
// as an interface into the OA framework.
import com.sun.corba.se.impl.oa.poa.POAManagerImpl ;

/** Provide access to stub delegate and type id information
 * independent of the stub type.  This class exists because
 * ObjectImpl does not have an interface for the 3 delegate and
 * type id methods, so a DynamicStub has a different type.
 * We cannot simply change ObjectImpl as it is a standard API.
 * We also cannot change the code generation of Stubs, as that
 * is also standard.  Hence I am left with this ugly class.
 */
public abstract class StubAdapter
{
    private StubAdapter() {}

    private static ORBUtilSystemException wrapper =
        ORBUtilSystemException.get( CORBALogDomains.RPC_PRESENTATION ) ;

    public static boolean isStubClass( Class cls )
    {
        return (ObjectImpl.class.isAssignableFrom( cls )) ||
            (DynamicStub.class.isAssignableFrom( cls )) ;
    }

    public static boolean isStub( Object stub )
    {
        return (stub instanceof DynamicStub) ||
            (stub instanceof ObjectImpl) ;
    }

    public static void setDelegate( Object stub, Delegate delegate )
    {
        if (stub instanceof DynamicStub)
            ((DynamicStub)stub).setDelegate( delegate ) ;
        else if (stub instanceof ObjectImpl)
            ((ObjectImpl)stub)._set_delegate( delegate ) ;
        else
            throw wrapper.setDelegateRequiresStub() ;
    }

    /** Use implicit activation to get an object reference for the servant.
     */
    public static org.omg.CORBA.Object activateServant( Servant servant )
    {
        POA poa = servant._default_POA() ;
        org.omg.CORBA.Object ref = null ;

        try {
            ref = poa.servant_to_reference( servant ) ;
        } catch (ServantNotActive sna) {
            throw wrapper.getDelegateServantNotActive( sna ) ;
        } catch (WrongPolicy wp) {
            throw wrapper.getDelegateWrongPolicy( wp ) ;
        }

        // Make sure that the POAManager is activated if no other
        // POAManager state management has taken place.
        POAManager mgr = poa.the_POAManager() ;
        if (mgr instanceof POAManagerImpl) {
            POAManagerImpl mgrImpl = (POAManagerImpl)mgr ;
            mgrImpl.implicitActivation() ;
        }

        return ref ;
    }

    /** Given any Tie, return the corresponding object refernce, activating
     * the Servant if necessary.
     */
    public static org.omg.CORBA.Object activateTie( Tie tie )
    {
        /** Any implementation of Tie should be either a Servant or an ObjectImpl,
         * depending on which style of code generation is used.  rmic -iiop by
         * default results in an ObjectImpl-based Tie, while rmic -iiop -poa
         * results in a Servant-based Tie.  Dynamic RMI-IIOP also uses Servant-based
         * Ties (see impl.presentation.rmi.ReflectiveTie).
         */
        if (tie instanceof ObjectImpl) {
            return tie.thisObject() ;
        } else if (tie instanceof Servant) {
            Servant servant = (Servant)tie ;
            return activateServant( servant ) ;
        } else {
            throw wrapper.badActivateTieCall() ;
        }
    }


    /** This also gets the delegate from a Servant by
     * using Servant._this_object()
     */
    public static Delegate getDelegate( Object stub )
    {
        if (stub instanceof DynamicStub)
            return ((DynamicStub)stub).getDelegate() ;
        else if (stub instanceof ObjectImpl)
            return ((ObjectImpl)stub)._get_delegate() ;
        else if (stub instanceof Tie) {
            Tie tie = (Tie)stub ;
            org.omg.CORBA.Object ref = activateTie( tie ) ;
            return getDelegate( ref ) ;
        } else
            throw wrapper.getDelegateRequiresStub() ;
    }

    public static ORB getORB( Object stub )
    {
        if (stub instanceof DynamicStub)
            return ((DynamicStub)stub).getORB() ;
        else if (stub instanceof ObjectImpl)
            return (ORB)((ObjectImpl)stub)._orb() ;
        else
            throw wrapper.getOrbRequiresStub() ;
    }

    public static String[] getTypeIds( Object stub )
    {
        if (stub instanceof DynamicStub)
            return ((DynamicStub)stub).getTypeIds() ;
        else if (stub instanceof ObjectImpl)
            return ((ObjectImpl)stub)._ids() ;
        else
            throw wrapper.getTypeIdsRequiresStub() ;
    }

    public static void connect( Object stub,
        ORB orb ) throws java.rmi.RemoteException
    {
        if (stub instanceof DynamicStub)
            ((DynamicStub)stub).connect(
                (com.sun.corba.se.spi.orb.ORB)orb ) ;
        else if (stub instanceof javax.rmi.CORBA.Stub)
            ((javax.rmi.CORBA.Stub)stub).connect( orb ) ;
        else if (stub instanceof ObjectImpl)
            orb.connect( (org.omg.CORBA.Object)stub ) ;
        else
            throw wrapper.connectRequiresStub() ;
    }

    public static boolean isLocal( Object stub )
    {
        if (stub instanceof DynamicStub)
            return ((DynamicStub)stub).isLocal() ;
        else if (stub instanceof ObjectImpl)
            return ((ObjectImpl)stub)._is_local() ;
        else
            throw wrapper.isLocalRequiresStub() ;
    }

    public static OutputStream request( Object stub,
        String operation, boolean responseExpected )
    {
        if (stub instanceof DynamicStub)
            return ((DynamicStub)stub).request( operation,
                responseExpected ) ;
        else if (stub instanceof ObjectImpl)
            return ((ObjectImpl)stub)._request( operation,
                responseExpected ) ;
        else
            throw wrapper.requestRequiresStub() ;
    }
}
