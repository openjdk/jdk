/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.cosnaming;

// Get CORBA type
import org.omg.CORBA.INITIALIZE;
import org.omg.CORBA.ORB;
import org.omg.CORBA.CompletionStatus;

import org.omg.CORBA.Policy;
import org.omg.CORBA.INTERNAL;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.LifespanPolicyValue;
import org.omg.PortableServer.RequestProcessingPolicyValue;
import org.omg.PortableServer.IdAssignmentPolicyValue;
import org.omg.PortableServer.ServantRetentionPolicyValue;

// Get org.omg.CosNaming types
import org.omg.CosNaming.NamingContext;

// Import transient naming context
import com.sun.corba.se.impl.naming.cosnaming.TransientNamingContext;
import com.sun.corba.se.impl.orbutil.ORBConstants;

import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.logging.NamingSystemException;

/**
 * Class TransientNameService implements a transient name service
 * using TransientNamingContexts and TransientBindingIterators, which
 * implement the org.omg.CosNaming::NamingContext and org.omg.CosNaming::BindingIterator
 * interfaces specfied by the OMG Common Object Services Specification.
 * <p>
 * The TransientNameService creates the initial NamingContext object.
 * @see NamingContextImpl
 * @see BindingIteratorImpl
 * @see TransientNamingContext
 * @see TransientBindingIterator
 */
public class TransientNameService
{
    /**
     * Constructs a new TransientNameService, and creates an initial
     * NamingContext, whose object
     * reference can be obtained by the initialNamingContext method.
     * @param orb The ORB object
     * @exception org.omg.CORBA.INITIALIZE Thrown if
     * the TransientNameService cannot initialize.
     */
    public TransientNameService(com.sun.corba.se.spi.orb.ORB orb )
        throws org.omg.CORBA.INITIALIZE
    {
        // Default constructor uses "NameService" as the key for the Root Naming
        // Context. If default constructor is used then INS's object key for
        // Transient Name Service is "NameService"
        initialize( orb, "NameService" );
    }

    /**
     * Constructs a new TransientNameService, and creates an initial
     * NamingContext, whose object
     * reference can be obtained by the initialNamingContext method.
     * @param orb The ORB object
     * @param serviceName Stringified key used for INS Service registry
     * @exception org.omg.CORBA.INITIALIZE Thrown if
     * the TransientNameService cannot initialize.
     */
    public TransientNameService(com.sun.corba.se.spi.orb.ORB orb,
        String serviceName ) throws org.omg.CORBA.INITIALIZE
    {
        // This constructor gives the flexibility of providing the Object Key
        // for the Root Naming Context that is registered with INS.
        initialize( orb, serviceName );
    }


    /**
     * This method initializes Transient Name Service by associating Root
     * context with POA and registering the root context with INS Object Keymap.
     */
    private void initialize( com.sun.corba.se.spi.orb.ORB orb,
        String nameServiceName )
        throws org.omg.CORBA.INITIALIZE
    {
        NamingSystemException wrapper = NamingSystemException.get( orb,
            CORBALogDomains.NAMING ) ;

        try {
            POA rootPOA = (POA) orb.resolve_initial_references(
                ORBConstants.ROOT_POA_NAME );
            rootPOA.the_POAManager().activate();

            int i = 0;
            Policy[] poaPolicy = new Policy[3];
            poaPolicy[i++] = rootPOA.create_lifespan_policy(
                LifespanPolicyValue.TRANSIENT);
            poaPolicy[i++] = rootPOA.create_id_assignment_policy(
                IdAssignmentPolicyValue.SYSTEM_ID);
            poaPolicy[i++] = rootPOA.create_servant_retention_policy(
                ServantRetentionPolicyValue.RETAIN);

            POA nsPOA = rootPOA.create_POA( "TNameService", null, poaPolicy );
            nsPOA.the_POAManager().activate();

            // Create an initial context
            TransientNamingContext initialContext =
                new TransientNamingContext(orb, null, nsPOA);
            byte[] rootContextId = nsPOA.activate_object( initialContext );
            initialContext.localRoot =
                nsPOA.id_to_reference( rootContextId );
            theInitialNamingContext = initialContext.localRoot;
            orb.register_initial_reference( nameServiceName,
                theInitialNamingContext );
        } catch (org.omg.CORBA.SystemException e) {
            throw wrapper.transNsCannotCreateInitialNcSys( e ) ;
        } catch (Exception e) {
            throw wrapper.transNsCannotCreateInitialNc( e ) ;
        }
    }


    /**
     * Return the initial NamingContext.
     * @return the object reference for the initial NamingContext.
     */
    public org.omg.CORBA.Object initialNamingContext()
    {
        return theInitialNamingContext;
    }


    // The initial naming context for this name service
    private org.omg.CORBA.Object theInitialNamingContext;
}
