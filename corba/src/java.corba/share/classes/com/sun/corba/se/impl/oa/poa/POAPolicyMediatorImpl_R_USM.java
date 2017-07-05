/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set ;
import org.omg.CORBA.SystemException ;

import org.omg.PortableServer.ServantActivator ;
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

import com.sun.corba.se.impl.oa.NullServantImpl ;

import com.sun.corba.se.impl.javax.rmi.CORBA.Util ;

import com.sun.corba.se.spi.oa.OAInvocationInfo ;
import com.sun.corba.se.spi.oa.NullServant ;

/** Implementation of POARequesHandler that provides policy specific
 * operations on the POA.
 */
public class POAPolicyMediatorImpl_R_USM extends POAPolicyMediatorBase_R {
    protected ServantActivator activator ;

    POAPolicyMediatorImpl_R_USM( Policies policies, POAImpl poa )
    {
        // assert policies.retainServants()
        super( policies, poa ) ;
        activator = null ;

        if (!policies.useServantManager())
            throw poa.invocationWrapper().policyMediatorBadPolicyInFactory() ;
    }

    /* This handles a rather subtle bug (4939892).  The problem is that
     * enter will wait on the entry if it is being etherealized.  When the
     * deferred state transition completes, the entry is no longer in the
     * AOM, and so we need to get a new entry, otherwise activator.incarnate
     * will be called twice, once for the old entry, and again when a new
     * entry is created.  This fix also required extending the FSM StateEngine
     * to allow actions to throw exceptions, and adding a new state in the
     * AOMEntry FSM to detect this condition.
     */
    private AOMEntry enterEntry( ActiveObjectMap.Key key )
    {
        AOMEntry result = null ;
        boolean failed ;
        do {
            failed = false ;
            result = activeObjectMap.get(key) ;

            try {
                result.enter() ;
            } catch (Exception exc) {
                failed = true ;
            }
        } while (failed) ;

        return result ;
    }

    protected java.lang.Object internalGetServant( byte[] id,
        String operation ) throws ForwardRequest
    {
        if (poa.getDebug()) {
            ORBUtility.dprint( this,
                "Calling POAPolicyMediatorImpl_R_USM.internalGetServant " +
                "for poa " + poa + " operation=" + operation ) ;
        }

        try {
            ActiveObjectMap.Key key = new ActiveObjectMap.Key( id ) ;
            AOMEntry entry = enterEntry(key) ;
            java.lang.Object servant = activeObjectMap.getServant( entry ) ;
            if (servant != null) {
                if (poa.getDebug()) {
                    ORBUtility.dprint( this,
                        "internalGetServant: servant already activated" ) ;
                }

                return servant ;
            }

            if (activator == null) {
                if (poa.getDebug()) {
                    ORBUtility.dprint( this,
                        "internalGetServant: no servant activator in POA" ) ;
                }

                entry.incarnateFailure() ;
                throw poa.invocationWrapper().poaNoServantManager() ;
            }

            // Drop the POA lock during the incarnate call and
            // re-acquire it afterwards.  The entry state machine
            // prevents more than one thread from executing the
            // incarnate method at a time within the same POA.
            try {
                if (poa.getDebug()) {
                    ORBUtility.dprint( this,
                        "internalGetServant: upcall to incarnate" ) ;
                }

                poa.unlock() ;

                servant = activator.incarnate(id, poa);

                if (servant == null)
                    servant = new NullServantImpl(
                        poa.omgInvocationWrapper().nullServantReturned() ) ;
            } catch (ForwardRequest freq) {
                if (poa.getDebug()) {
                    ORBUtility.dprint( this,
                        "internalGetServant: incarnate threw ForwardRequest" ) ;
                }

                throw freq ;
            } catch (SystemException exc) {
                if (poa.getDebug()) {
                    ORBUtility.dprint( this,
                        "internalGetServant: incarnate threw SystemException " + exc ) ;
                }

                throw exc ;
            } catch (Throwable exc) {
                if (poa.getDebug()) {
                    ORBUtility.dprint( this,
                        "internalGetServant: incarnate threw Throwable " + exc ) ;
                }

                throw poa.invocationWrapper().poaServantActivatorLookupFailed(
                    exc ) ;
            } finally {
                poa.lock() ;

                // servant == null means incarnate threw an exception,
                // while servant instanceof NullServant means incarnate returned a
                // null servant.  Either case is an incarnate failure to the
                // entry state machine.
                if ((servant == null) || (servant instanceof NullServant)) {
                    if (poa.getDebug()) {
                        ORBUtility.dprint( this,
                            "internalGetServant: incarnate failed" ) ;
                    }

                    // XXX Does the AOM leak in this case? Yes,
                    // but the problem is hard to fix.  There may be
                    // a number of threads waiting for the state to change
                    // from INCARN to something else, which is VALID or
                    // INVALID, depending on the incarnate result.
                    // The activeObjectMap.get() call above creates an
                    // ActiveObjectMap.Entry if one does not already exist,
                    // and stores it in the keyToEntry map in the AOM.
                    entry.incarnateFailure() ;
                } else {
                    // here check for unique_id policy, and if the servant
                    // is already registered for a different ID, then throw
                    // OBJ_ADAPTER exception, else activate it. Section 11.3.5.1
                    // 99-10-07.pdf
                    if (isUnique) {
                        // check if the servant already is associated with some id
                        if (activeObjectMap.contains((Servant)servant)) {
                            if (poa.getDebug()) {
                                ORBUtility.dprint( this,
                                    "internalGetServant: servant already assigned to ID" ) ;
                            }

                            entry.incarnateFailure() ;
                            throw poa.invocationWrapper().poaServantNotUnique() ;
                        }
                    }

                    if (poa.getDebug()) {
                        ORBUtility.dprint( this,
                            "internalGetServant: incarnate complete" ) ;
                    }

                    entry.incarnateComplete() ;
                    activateServant(key, entry, (Servant)servant);
                }
            }

            return servant ;
        } finally {
            if (poa.getDebug()) {
                ORBUtility.dprint( this,
                    "Exiting POAPolicyMediatorImpl_R_USM.internalGetServant " +
                    "for poa " + poa ) ;
            }
        }
    }

    public void returnServant()
    {
        OAInvocationInfo info = orb.peekInvocationInfo();
        byte[] id = info.id() ;
        ActiveObjectMap.Key key = new ActiveObjectMap.Key( id ) ;
        AOMEntry entry = activeObjectMap.get( key ) ;
        entry.exit() ;
    }

    public void etherealizeAll()
    {
        if (activator != null)  {
            Set keySet = activeObjectMap.keySet() ;

            // Copy the elements in the set to an array to avoid
            // changes in the set due to concurrent modification
            ActiveObjectMap.Key[] keys =
                (ActiveObjectMap.Key[])keySet.toArray(
                    new ActiveObjectMap.Key[ keySet.size() ] ) ;

            for (int ctr=0; ctr<keySet.size(); ctr++) {
                ActiveObjectMap.Key key = keys[ctr] ;
                AOMEntry entry = activeObjectMap.get( key ) ;
                Servant servant = activeObjectMap.getServant( entry ) ;
                if (servant != null) {
                    boolean remainingActivations =
                        activeObjectMap.hasMultipleIDs(entry) ;

                    // Here we etherealize in the thread that called this
                    // method, rather than etherealizing in a new thread
                    // as in the deactivate case.  We still inform the
                    // entry state machine so that only one thread at a
                    // time can call the etherealize method.
                    entry.startEtherealize( null ) ;
                    try {
                        poa.unlock() ;
                        try {
                            activator.etherealize(key.id, poa, servant, true,
                                remainingActivations);
                        } catch (Exception exc) {
                            // ignore all exceptions
                        }
                    } finally {
                        poa.lock() ;
                        entry.etherealizeComplete() ;
                    }
                }
            }
        }
    }

    public ServantManager getServantManager() throws WrongPolicy
    {
        return activator;
    }

    public void setServantManager(
        ServantManager servantManager ) throws WrongPolicy
    {
        if (activator != null)
            throw poa.invocationWrapper().servantManagerAlreadySet() ;

        if (servantManager instanceof ServantActivator)
            activator = (ServantActivator)servantManager;
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

    class Etherealizer extends sun.misc.ManagedLocalsThread {
        private POAPolicyMediatorImpl_R_USM mediator ;
        private ActiveObjectMap.Key key ;
        private AOMEntry entry ;
        private Servant servant ;
        private boolean debug ;


        public Etherealizer( POAPolicyMediatorImpl_R_USM mediator,
            ActiveObjectMap.Key key, AOMEntry entry, Servant servant,
            boolean debug )
        {
            this.mediator = mediator ;
            this.key = key ;
            this.entry = entry;
            this.servant = servant;
            this.debug = debug ;
        }

        public void run() {
            if (debug) {
                ORBUtility.dprint( this, "Calling Etherealizer.run on key " +
                    key ) ;
            }

            try {
                try {
                    mediator.activator.etherealize( key.id, mediator.poa, servant,
                        false, mediator.activeObjectMap.hasMultipleIDs( entry ) );
                } catch (Exception exc) {
                    // ignore all exceptions
                }

                try {
                    mediator.poa.lock() ;

                    entry.etherealizeComplete() ;
                    mediator.activeObjectMap.remove( key ) ;

                    POAManagerImpl pm = (POAManagerImpl)mediator.poa.the_POAManager() ;
                    POAFactory factory = pm.getFactory() ;
                    factory.unregisterPOAForServant( mediator.poa, servant);
                } finally {
                    mediator.poa.unlock() ;
                }
            } finally {
                if (debug) {
                    ORBUtility.dprint( this, "Exiting Etherealizer.run" ) ;
                }
            }
        }
    }

    public void deactivateHelper( ActiveObjectMap.Key key, AOMEntry entry,
        Servant servant ) throws ObjectNotActive, WrongPolicy
    {
        if (activator == null)
            throw poa.invocationWrapper().poaNoServantManager() ;

        Etherealizer eth = new Etherealizer( this, key, entry, servant, poa.getDebug() ) ;
        entry.startEtherealize( eth ) ;
    }

    public Servant idToServant( byte[] id )
        throws WrongPolicy, ObjectNotActive
    {
        ActiveObjectMap.Key key = new ActiveObjectMap.Key( id ) ;
        AOMEntry entry = activeObjectMap.get(key);

        Servant servant = activeObjectMap.getServant( entry ) ;
        if (servant != null)
            return servant ;
        else
            throw new ObjectNotActive() ;
    }
}
