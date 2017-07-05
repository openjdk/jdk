/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.oa.poa;

import java.util.Collection ;
import java.util.Set ;
import java.util.HashSet ;
import java.util.Map ;
import java.util.HashMap ;
import java.util.Iterator ;

import org.omg.CORBA.Policy ;
import org.omg.CORBA.SystemException ;

import org.omg.PortableServer.POA ;
import org.omg.PortableServer.Servant ;
import org.omg.PortableServer.POAManager ;
import org.omg.PortableServer.AdapterActivator ;
import org.omg.PortableServer.ServantManager ;
import org.omg.PortableServer.ForwardRequest ;
import org.omg.PortableServer.ThreadPolicy;
import org.omg.PortableServer.LifespanPolicy;
import org.omg.PortableServer.IdUniquenessPolicy;
import org.omg.PortableServer.IdAssignmentPolicy;
import org.omg.PortableServer.ImplicitActivationPolicy;
import org.omg.PortableServer.ServantRetentionPolicy;
import org.omg.PortableServer.RequestProcessingPolicy;
import org.omg.PortableServer.ThreadPolicyValue ;
import org.omg.PortableServer.LifespanPolicyValue ;
import org.omg.PortableServer.IdUniquenessPolicyValue ;
import org.omg.PortableServer.IdAssignmentPolicyValue ;
import org.omg.PortableServer.ImplicitActivationPolicyValue ;
import org.omg.PortableServer.ServantRetentionPolicyValue ;
import org.omg.PortableServer.RequestProcessingPolicyValue ;
import org.omg.PortableServer.POAPackage.AdapterAlreadyExists ;
import org.omg.PortableServer.POAPackage.AdapterNonExistent ;
import org.omg.PortableServer.POAPackage.InvalidPolicy ;
import org.omg.PortableServer.POAPackage.WrongPolicy ;
import org.omg.PortableServer.POAPackage.WrongAdapter ;
import org.omg.PortableServer.POAPackage.NoServant ;
import org.omg.PortableServer.POAPackage.ServantAlreadyActive ;
import org.omg.PortableServer.POAPackage.ObjectAlreadyActive ;
import org.omg.PortableServer.POAPackage.ServantNotActive ;
import org.omg.PortableServer.POAPackage.ObjectNotActive ;

import org.omg.PortableInterceptor.ObjectReferenceFactory ;
import org.omg.PortableInterceptor.ObjectReferenceTemplate ;
import org.omg.PortableInterceptor.NON_EXISTENT ;

import org.omg.IOP.TAG_INTERNET_IOP ;

import com.sun.corba.se.spi.copyobject.CopierManager ;
import com.sun.corba.se.spi.copyobject.ObjectCopier ;
import com.sun.corba.se.spi.copyobject.ObjectCopierFactory ;
import com.sun.corba.se.spi.oa.OADestroyed ;
import com.sun.corba.se.spi.oa.OAInvocationInfo ;
import com.sun.corba.se.spi.oa.ObjectAdapter ;
import com.sun.corba.se.spi.oa.ObjectAdapterBase ;
import com.sun.corba.se.spi.oa.ObjectAdapterFactory ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;
import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.ObjectAdapterId ;
import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.ior.IORFactories ;
import com.sun.corba.se.spi.ior.IORTemplate ;
import com.sun.corba.se.spi.ior.IORTemplateList ;
import com.sun.corba.se.spi.ior.TaggedProfile ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile ;
import com.sun.corba.se.spi.ior.iiop.IIOPAddress ;
import com.sun.corba.se.spi.ior.iiop.IIOPFactories ;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.protocol.ForwardException ;
import com.sun.corba.se.spi.transport.SocketOrChannelAcceptor;

import com.sun.corba.se.impl.ior.POAObjectKeyTemplate ;
import com.sun.corba.se.impl.ior.ObjectAdapterIdArray ;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.concurrent.Sync ;
import com.sun.corba.se.impl.orbutil.concurrent.SyncUtil ;
import com.sun.corba.se.impl.orbutil.concurrent.ReentrantMutex ;
import com.sun.corba.se.impl.orbutil.concurrent.CondVar ;

/**
 * POAImpl is the implementation of the Portable Object Adapter. It
 * contains an implementation of the POA interfaces specified in
 * COBRA 2.3.1 chapter 11 (formal/99-10-07).  This implementation
 * is moving to comply with CORBA 3.0 due to the many clarifications
 * that have been made to the POA semantics since CORBA 2.3.1.
 * Specific comments have been added where 3.0 applies, but note that
 * we do not have the new 3.0 APIs yet.
 */
public class POAImpl extends ObjectAdapterBase implements POA
{
    private boolean debug ;

    /* POA creation takes place in 2 stages: first, the POAImpl constructor is
       called, then the initialize method is called.  This separation is
       needed because an AdapterActivator does not know the POAManager or
       the policies when
       the unknown_adapter method is invoked.  However, the POA must be created
       before the unknown_adapter method is invoked, so that the parent knows
       when concurrent attempts are made to create the same POA.
       Calling the POAImpl constructor results in a new POA in state STATE_START.
       Calling initialize( POAManager, Policies ) results in state STATE_RUN.
       Calling destroy results in STATE_DESTROY, which marks the beginning of
       POA destruction.
    */

    // Notes on concurrency.
    // The POA requires careful design for concurrency management to correctly
    // implement the specification and avoid deadlocks.  The order of acquiring
    // locks must respect the following locking hierarchy:
    //
    // 1. Lock POAs before POAManagers
    // 2. Lock a POA before locking its child POA
    //
    // Also note that there are 3 separate conditions on which threads may wait
    // in the POA, as defined by invokeCV, beingDestroyedCV, and
    // adapterActivatorCV.  This means that (for this reason as well as others)
    // we cannot simply use the standard Java synchronized primitive.
    // This implementation uses a modified version of Doug Lea's
    // util.concurrent (version 1.3.0) that supports reentrant
    // mutexes to handle the locking.  This will all be replaced by the new JSR
    // 166 concurrency primitives in J2SE 1.5 and later once the ORB moves to
    // J2SE 1.5.

    // POA state constants
    //
    // Note that ordering is important here: we must have the state defined in
    // this order so that ordered comparison is possible.
    // DO NOT CHANGE THE VALUES OF THE STATE CONSTANTS!!!  In particular, the
    // initialization related states must be lower than STATE_RUN.
    //
    // POA is created in STATE_START
    //
    // Valid state transitions:
    //
    // START to INIT                        after find_POA constructor call
    // START to RUN                         after initialize completes
    // INIT to INIT_DONE                    after initialize completes
    // INIT to DESTROYED                    after failed unknown_adapter
    // INIT_DONE to RUN                     after successful unknown_adapter
    // STATE_RUN to STATE_DESTROYING        after start of destruction
    // STATE_DESTROYING to STATE_DESTROYED  after destruction completes.

    private static final int STATE_START        = 0 ; // constructor complete
    private static final int STATE_INIT         = 1 ; // waiting for adapter activator
    private static final int STATE_INIT_DONE    = 2 ; // adapter activator called create_POA
    private static final int STATE_RUN          = 3 ; // initialized and running
    private static final int STATE_DESTROYING   = 4 ; // being destroyed
    private static final int STATE_DESTROYED    = 5 ; // destruction complete

    private String stateToString()
    {
        switch (state) {
            case STATE_START :
                return "START" ;
            case STATE_INIT :
                return "INIT" ;
            case STATE_INIT_DONE :
                return "INIT_DONE" ;
            case STATE_RUN :
                return "RUN" ;
            case STATE_DESTROYING :
                return "DESTROYING" ;
            case STATE_DESTROYED :
                return "DESTROYED" ;
            default :
                return "UNKNOWN(" + state + ")" ;
        }
    }

    // Current state of the POA
    private int state ;

    // The POA request handler that performs all policy specific operations
    // Note that POAImpl handles all synchronization, so mediator is (mostly)
    // unsynchronized.
    private POAPolicyMediator mediator;

    // Representation of object adapter ID
    private int numLevels;          // counts depth of tree.  Root = 1.
    private ObjectAdapterId poaId ; // the actual object adapter ID for this POA
    private String name;            // the name of this POA

    private POAManagerImpl manager; // This POA's POAManager
    private int uniquePOAId ;       // ID for this POA that is unique relative
                                    // to the POAFactory, which has the same
                                    // lifetime as the ORB.
    private POAImpl parent;         // The POA that created this POA.
    private Map children;           // Map from name to POA of POAs created by
                                    // this POA.

    private AdapterActivator activator;
    private int invocationCount ; // pending invocations on this POA.

    // Data used to control POA concurrency
    // XXX revisit for JSR 166

    // Master lock for all POA synchronization.  See lock and unlock.
    // package private for access by AOMEntry.
    Sync poaMutex ;

    // Wait on this CV for AdapterActivator upcalls to complete
    private CondVar adapterActivatorCV ;

    // Wait on this CV for all active invocations to complete
    private CondVar invokeCV ;

    // Wait on this CV for the destroy method to complete doing its work
    private CondVar beingDestroyedCV ;

    // thread local variable to store a boolean to detect deadlock in
    // POA.destroy().
    protected ThreadLocal isDestroying ;

    // This includes the most important information for debugging
    // POA problems.
    public String toString()
    {
        return "POA[" + poaId.toString() +
            ", uniquePOAId=" + uniquePOAId +
            ", state=" + stateToString() +
            ", invocationCount=" + invocationCount + "]" ;
    }

    // package private for mediator implementations.
    boolean getDebug()
    {
        return debug ;
    }

    // package private for access to servant to POA map
    static POAFactory getPOAFactory( ORB orb )
    {
        return (POAFactory)orb.getRequestDispatcherRegistry().
            getObjectAdapterFactory( ORBConstants.TRANSIENT_SCID ) ;
    }

    // package private so that POAFactory can access it.
    static POAImpl makeRootPOA( ORB orb )
    {
        POAManagerImpl poaManager = new POAManagerImpl( getPOAFactory( orb ),
            orb.getPIHandler() ) ;

        POAImpl result = new POAImpl( ORBConstants.ROOT_POA_NAME,
            null, orb, STATE_START ) ;
        result.initialize( poaManager, Policies.rootPOAPolicies ) ;

        return result ;
    }

    // package private so that POAPolicyMediatorBase can access it.
    int getPOAId()
    {
        return uniquePOAId ;
    }


    // package private so that POAPolicyMediator can access it.
    void lock()
    {
        SyncUtil.acquire( poaMutex ) ;

        if (debug) {
            ORBUtility.dprint( this, "LOCKED poa " + this ) ;
        }
    }

    // package private so that POAPolicyMediator can access it.
    void unlock()
    {
        if (debug) {
            ORBUtility.dprint( this, "UNLOCKED poa " + this ) ;
        }

        poaMutex.release() ;
    }

    // package private so that DelegateImpl can access it.
    Policies getPolicies()
    {
        return mediator.getPolicies() ;
    }

    // Note that the parent POA must be locked when this constructor is called.
    private POAImpl( String name, POAImpl parent, ORB orb, int initialState )
    {
        super( orb ) ;

        debug = orb.poaDebugFlag ;

        if (debug) {
            ORBUtility.dprint( this, "Creating POA with name=" + name +
                " parent=" + parent ) ;
        }

        this.state     = initialState ;
        this.name      = name ;
        this.parent    = parent;
        children = new HashMap();
        activator = null ;

        // This was done in initialize, but I moved it here
        // to get better searchability when tracing.
        uniquePOAId = getPOAFactory( orb ).newPOAId() ;

        if (parent == null) {
            // This is the root POA, which counts as 1 level
            numLevels = 1 ;
        } else {
            // My level is one more than that of my parent
            numLevels = parent.numLevels + 1 ;

            parent.children.put(name, this);
        }

        // Get an array of all of the POA names in order to
        // create the poaid.
        String[] names = new String[ numLevels ] ;
        POAImpl poaImpl = this ;
        int ctr = numLevels - 1 ;
        while (poaImpl != null) {
            names[ctr--] = poaImpl.name ;
            poaImpl = poaImpl.parent ;
        }

        poaId = new ObjectAdapterIdArray( names ) ;

        invocationCount = 0;

        poaMutex = new ReentrantMutex( orb.poaConcurrencyDebugFlag ) ;

        adapterActivatorCV = new CondVar( poaMutex,
            orb.poaConcurrencyDebugFlag ) ;
        invokeCV           = new CondVar( poaMutex,
            orb.poaConcurrencyDebugFlag ) ;
        beingDestroyedCV   = new CondVar( poaMutex,
            orb.poaConcurrencyDebugFlag ) ;

        isDestroying = new ThreadLocal () {
            protected java.lang.Object initialValue() {
                return Boolean.FALSE;
            }
        };
    }

    // The POA lock must be held when this method is called.
    private void initialize( POAManagerImpl manager, Policies policies )
    {
        if (debug) {
            ORBUtility.dprint( this, "Initializing poa " + this +
                " with POAManager=" + manager + " policies=" + policies ) ;
        }

        this.manager = manager;
        manager.addPOA(this);

        mediator = POAPolicyMediatorFactory.create( policies, this ) ;

        // Construct the object key template
        int serverid = mediator.getServerId() ;
        int scid = mediator.getScid() ;
        String orbId = getORB().getORBData().getORBId();

        ObjectKeyTemplate oktemp = new POAObjectKeyTemplate( getORB(),
            scid, serverid, orbId, poaId ) ;

        if (debug) {
            ORBUtility.dprint( this, "Initializing poa: oktemp=" + oktemp ) ;
        }

        // Note that parent == null iff this is the root POA.
        // This was used to avoid executing interceptors on the RootPOA.
        // That is no longer necessary.
        boolean objectAdapterCreated = true; // parent != null ;

        // XXX extract codebase from policies and pass into initializeTemplate
        // after the codebase policy change is finalized.
        initializeTemplate( oktemp, objectAdapterCreated,
                            policies,
                            null, // codebase
                            null, // manager id
                            oktemp.getObjectAdapterId()
                            ) ;

        if (state == STATE_START)
            state = STATE_RUN ;
        else if (state == STATE_INIT)
            state = STATE_INIT_DONE ;
        else
            throw lifecycleWrapper().illegalPoaStateTrans() ;
    }

    // The poaMutex must be held when this method is called
    private boolean waitUntilRunning()
    {
        if (debug) {
            ORBUtility.dprint( this,
                "Calling waitUntilRunning on poa " + this ) ;
        }

        while (state < STATE_RUN) {
            try {
                adapterActivatorCV.await() ;
            } catch (InterruptedException exc) {
                // NO-OP
            }
        }

        if (debug) {
            ORBUtility.dprint( this,
                "Exiting waitUntilRunning on poa " + this ) ;
        }

        // Note that a POA could be destroyed while in STATE_INIT due to a
        // failure in the AdapterActivator upcall.
        return (state == STATE_RUN) ;
    }

    // This method checks that the AdapterActivator finished the
    // initialization of a POA activated in find_POA.  This is
    // determined by checking the state of the POA.  If the state is
    // STATE_INIT, the AdapterActivator did not complete the
    // inialization.  In this case, we destroy the POA that was
    // partially created and return false.  Otherwise, we return true.
    // In any case, we must wake up all threads waiting for the adapter
    // activator, either to continue their invocations, or to return
    // errors to their client.
    //
    // The poaMutex must NOT be held when this method is called.
    private boolean destroyIfNotInitDone()
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling destroyIfNotInitDone on poa " + this ) ;
            }

            boolean success = (state == STATE_INIT_DONE) ;

            if (success)
                state = STATE_RUN ;
            else {
                // Don't just use destroy, because the check for
                // deadlock is too general, and can prevent this from
                // functioning properly.
                DestroyThread destroyer = new DestroyThread( false, debug );
                destroyer.doIt( this, true ) ;
            }

            return success ;
        } finally {
            adapterActivatorCV.broadcast() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Exiting destroyIfNotInitDone on poa " + this ) ;
            }

            unlock() ;
        }
    }

    private byte[] internalReferenceToId(
        org.omg.CORBA.Object reference ) throws WrongAdapter
    {
        IOR ior = ORBUtility.getIOR( reference ) ;
        IORTemplateList thisTemplate = ior.getIORTemplates() ;

        ObjectReferenceFactory orf = getCurrentFactory() ;
        IORTemplateList poaTemplate =
            IORFactories.getIORTemplateList( orf ) ;

        if (!poaTemplate.isEquivalent( thisTemplate ))
            throw new WrongAdapter();

        // Extract the ObjectId from the first TaggedProfile in the IOR.
        // If ior was created in this POA, the same ID was used for
        // every profile through the profile templates in the currentFactory,
        // so we will get the same result from any profile.
        Iterator iter = ior.iterator() ;
        if (!iter.hasNext())
            throw iorWrapper().noProfilesInIor() ;
        TaggedProfile prof = (TaggedProfile)(iter.next()) ;
        ObjectId oid = prof.getObjectId() ;

        return oid.getId();
    }

    // Converted from anonymous class to local class
    // so that we can call performDestroy() directly.
    static class DestroyThread extends sun.misc.ManagedLocalsThread {
        private boolean wait ;
        private boolean etherealize ;
        private boolean debug ;
        private POAImpl thePoa ;

        public DestroyThread( boolean etherealize, boolean debug )
        {
            this.etherealize = etherealize ;
            this.debug = debug ;
        }

        public void doIt( POAImpl thePoa, boolean wait )
        {
            if (debug) {
                ORBUtility.dprint( this,
                    "Calling DestroyThread.doIt(thePOA=" + thePoa +
                    " wait=" + wait + " etherealize=" + etherealize ) ;
            }

            this.thePoa = thePoa ;
            this.wait = wait ;

            if (wait) {
                run() ;
            } else {
                // Catch exceptions since setDaemon can cause a
                // security exception to be thrown under netscape
                // in the Applet mode
                try { setDaemon(true); } catch (Exception e) {}
                start() ;
            }
        }

        public void run()
        {
            Set destroyedPOATemplates = new HashSet() ;

            performDestroy( thePoa, destroyedPOATemplates );

            Iterator iter = destroyedPOATemplates.iterator() ;
            ObjectReferenceTemplate[] orts = new ObjectReferenceTemplate[
                destroyedPOATemplates.size() ] ;
            int index = 0 ;
            while (iter.hasNext())
                orts[ index++ ] = (ObjectReferenceTemplate)iter.next();

            thePoa.getORB().getPIHandler().adapterStateChanged( orts,
                NON_EXISTENT.value ) ;
        }

        // Returns true if destruction must be completed, false
        // if not, which means that another thread is already
        // destroying poa.
        private boolean prepareForDestruction( POAImpl poa,
            Set destroyedPOATemplates )
        {
            POAImpl[] childPoas = null ;

            // Note that we do not synchronize on this, since this is
            // the PerformDestroy instance, not the POA.
            try {
                poa.lock() ;

                if (debug) {
                    ORBUtility.dprint( this,
                        "Calling performDestroy on poa " + poa ) ;
                }

                if (poa.state <= STATE_RUN) {
                    poa.state = STATE_DESTROYING ;
                } else {
                    // destroy may be called multiple times, and each call
                    // is allowed to proceed with its own setting of the wait
                    // flag, but the etherealize value is used from the first
                    // call to destroy.  Also all children should be destroyed
                    // before the parent POA.  If the poa is already destroyed,
                    // we can just return.  If the poa has started destruction,
                    // but not completed, and wait is true, we need to wait
                    // until destruction is complete, then just return.
                    if (wait)
                        while (poa.state != STATE_DESTROYED) {
                            try {
                                poa.beingDestroyedCV.await() ;
                            } catch (InterruptedException exc) {
                                // NO-OP
                            }
                        }

                    return false ;
                }

                poa.isDestroying.set(Boolean.TRUE);

                // Make a copy since we can't hold the lock while destroying
                // the children, and an iterator is not deletion-safe.
                childPoas = (POAImpl[])poa.children.values().toArray(
                    new POAImpl[0] );
            } finally {
                poa.unlock() ;
            }

            // We are not holding the POA mutex here to avoid holding it
            // while destroying the POA's children, since this may involve
            // upcalls to etherealize methods.

            for (int ctr=0; ctr<childPoas.length; ctr++ ) {
                performDestroy( childPoas[ctr], destroyedPOATemplates ) ;
            }

            return true ;
        }

        public void performDestroy( POAImpl poa, Set destroyedPOATemplates )
        {
            if (!prepareForDestruction( poa, destroyedPOATemplates ))
                return ;

            // NOTE: If we are here, poa is in STATE_DESTROYING state. All
            // other state checks are taken care of in prepareForDestruction.
            // No other threads may either be starting new invocations
            // by calling enter or starting to destroy poa.  There may
            // still be pending invocations.

            POAImpl parent = poa.parent ;
            boolean isRoot = parent == null ;

            try {
                // Note that we must lock the parent before the child.
                // The parent lock is required (if poa is not the root)
                // to safely remove poa from parent's children Map.
                if (!isRoot)
                    parent.lock() ;

                try {
                    poa.lock() ;

                    completeDestruction( poa, parent,
                        destroyedPOATemplates ) ;
                } finally {
                    poa.unlock() ;

                    if (isRoot)
                        // We have just destroyed the root POA, so we need to
                        // make sure that the next call to
                        // resolve_initial_reference( "RootPOA" )
                        // will recreate a valid root POA.
                        poa.manager.getFactory().registerRootPOA() ;
                }
            } finally {
                if (!isRoot) {
                    parent.unlock() ;
                    poa.parent = null ;
                }
            }
        }

        private void completeDestruction( POAImpl poa, POAImpl parent,
            Set destroyedPOATemplates )
        {
            if (debug) {
                ORBUtility.dprint( this,
                    "Calling completeDestruction on poa " + poa ) ;
            }

            try {
                while (poa.invocationCount != 0) {
                    try {
                        poa.invokeCV.await() ;
                    } catch (InterruptedException ex) {
                        // NO-OP
                    }
                }

                if (poa.mediator != null) {
                    if (etherealize)
                        poa.mediator.etherealizeAll();

                    poa.mediator.clearAOM() ;
                }

                if (poa.manager != null)
                    poa.manager.removePOA(poa);

                if (parent != null)
                    parent.children.remove( poa.name ) ;

                destroyedPOATemplates.add( poa.getAdapterTemplate() ) ;
            } catch (Throwable thr) {
                if (thr instanceof ThreadDeath)
                    throw (ThreadDeath)thr ;

                poa.lifecycleWrapper().unexpectedException( thr, poa.toString() ) ;
            } finally {
                poa.state = STATE_DESTROYED ;
                poa.beingDestroyedCV.broadcast();
                poa.isDestroying.set(Boolean.FALSE);

                if (debug) {
                    ORBUtility.dprint( this,
                        "Exiting completeDestruction on poa " + poa ) ;
                }
            }
        }
    }

    void etherealizeAll()
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling etheralizeAll on poa " + this ) ;
            }

            mediator.etherealizeAll() ;
        } finally {
            if (debug) {
                ORBUtility.dprint( this,
                    "Exiting etheralizeAll on poa " + this ) ;
            }

            unlock() ;
        }
    }

 //*******************************************************************
 // Public POA API
 //*******************************************************************

    /**
     * <code>create_POA</code>
     * <b>Section 3.3.8.2</b>
     */
    public POA create_POA(String name, POAManager
        theManager, Policy[] policies) throws AdapterAlreadyExists,
        InvalidPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling create_POA(name=" + name +
                    " theManager=" + theManager + " policies=" + policies +
                    ") on poa " + this ) ;
            }

            // We cannot create children of a POA that is (being) destroyed.
            // This has been added to the CORBA 3.0 spec.
            if (state > STATE_RUN)
                throw omgLifecycleWrapper().createPoaDestroy() ;

            POAImpl poa = (POAImpl)(children.get(name)) ;

            if (poa == null) {
                poa = new POAImpl( name, this, getORB(), STATE_START ) ;
            }

            try {
                poa.lock() ;

                if (debug) {
                    ORBUtility.dprint( this,
                        "Calling create_POA: new poa is " + poa ) ;
                }

                if ((poa.state != STATE_START) && (poa.state != STATE_INIT))
                    throw new AdapterAlreadyExists();

                POAManagerImpl newManager = (POAManagerImpl)theManager ;
                if (newManager == null)
                    newManager = new POAManagerImpl( manager.getFactory(),
                        manager.getPIHandler() );

                int defaultCopierId =
                    getORB().getCopierManager().getDefaultId() ;
                Policies POAPolicies =
                    new Policies( policies, defaultCopierId ) ;

                poa.initialize( newManager, POAPolicies ) ;

                return poa;
            } finally {
                poa.unlock() ;
            }
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>find_POA</code>
     * <b>Section 3.3.8.3</b>
     */
    public POA find_POA(String name, boolean activate)
        throws AdapterNonExistent
    {
        POAImpl found = null ;
        AdapterActivator act = null ;

        lock() ;

        if (debug) {
            ORBUtility.dprint( this, "Calling find_POA(name=" + name +
                " activate=" + activate + ") on poa " + this ) ;
        }

        found = (POAImpl) children.get(name);

        if (found != null) {
            if (debug) {
                ORBUtility.dprint( this,
                    "Calling find_POA: found poa " + found ) ;
            }

            try {
                found.lock() ;

                // Do not hold the parent POA lock while
                // waiting for child to complete initialization.
                unlock() ;

                // Make sure that the child has completed its initialization,
                // if it was created by an AdapterActivator, otherwise throw
                // a standard TRANSIENT exception with minor code 4 (see
                // CORBA 3.0 11.3.9.3, in reference to unknown_adapter)
                if (!found.waitUntilRunning())
                    throw omgLifecycleWrapper().poaDestroyed() ;

                // Note that found may be in state DESTROYING or DESTROYED at
                // this point.  That's OK, since destruction could start at
                // any time.
            } finally {
                found.unlock() ;
            }
        } else {
            try {
                if (debug) {
                    ORBUtility.dprint( this,
                        "Calling find_POA: no poa found" ) ;
                }

                if (activate && (activator != null)) {
                    // Create a child, but don't initialize it.  The newly
                    // created POA will be in state STATE_START, which will
                    // cause other calls to find_POA that are creating the same
                    // POA to block on the waitUntilRunning call above.
                    // Initialization must be completed by a call to create_POA
                    // inside the unknown_adapter upcall.  Note that
                    // this.poaMutex must be held here so that this.children
                    // can be safely updated.  The state is set to STATE_INIT
                    // so that initialize can make the correct state transition
                    // when create_POA is called inside the AdapterActivator.
                    // This avoids activating the new POA too soon
                    // by transitioning to STATE_RUN after unknown_adapter
                    // returns.
                    found = new POAImpl( name, this, getORB(), STATE_INIT ) ;

                    if (debug) {
                        ORBUtility.dprint( this,
                            "Calling find_POA: created poa " + found ) ;
                    }

                    act = activator ;
                } else {
                    throw new AdapterNonExistent();
                }
            } finally {
                unlock() ;
            }
        }

        // assert (found != null)
        // assert not holding this.poaMutex OR found.poaMutex

        // We must not hold either this.poaMutex or found.poaMutex here while
        // waiting for intialization of found to complete to prevent possible
        // deadlocks.

        if (act != null) {
            boolean status = false ;
            boolean adapterResult = false ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling find_POA: calling AdapterActivator"  ) ;
            }

            try {
                // Prevent more than one thread at a time from executing in act
                // in case act is shared between multiple POAs.
                synchronized (act) {
                    status = act.unknown_adapter(this, name);
                }
            } catch (SystemException exc) {
                throw omgLifecycleWrapper().adapterActivatorException( exc,
                    name, poaId.toString() ) ;
            } catch (Throwable thr) {
                // ignore most non-system exceptions, but log them for
                // diagnostic purposes.
                lifecycleWrapper().unexpectedException( thr, this.toString() ) ;

                if (thr instanceof ThreadDeath)
                    throw (ThreadDeath)thr ;
            } finally {
                // At this point, we have completed adapter activation.
                // Whether this was successful or not, we must call
                // destroyIfNotInitDone so that calls to enter() and create_POA()
                // that are waiting can execute again.  Failing to do this
                // will cause the system to hang in complex tests.
                adapterResult = found.destroyIfNotInitDone() ;
            }

            if (status) {
                if (!adapterResult)
                    throw omgLifecycleWrapper().adapterActivatorException( name,
                        poaId.toString() ) ;
            } else {
                if (debug) {
                    ORBUtility.dprint( this,
                        "Calling find_POA: AdapterActivator returned false"  ) ;
                }

                // OMG Issue 3740 is resolved to throw AdapterNonExistent if
                // unknown_adapter() returns false.
                throw new AdapterNonExistent();
            }
        }

        return found;
    }

    /**
     * <code>destroy</code>
     * <b>Section 3.3.8.4</b>
     */
    public void destroy(boolean etherealize, boolean wait_for_completion)
    {
        // This is to avoid deadlock
        if (wait_for_completion && getORB().isDuringDispatch()) {
            throw lifecycleWrapper().destroyDeadlock() ;
        }

        DestroyThread destroyer = new DestroyThread( etherealize, debug );
        destroyer.doIt( this, wait_for_completion ) ;
    }

    /**
     * <code>create_thread_policy</code>
     * <b>Section 3.3.8.5</b>
     */
    public ThreadPolicy create_thread_policy(
        ThreadPolicyValue value)
    {
        return new ThreadPolicyImpl(value);
    }

    /**
     * <code>create_lifespan_policy</code>
     * <b>Section 3.3.8.5</b>
     */
    public LifespanPolicy create_lifespan_policy(
        LifespanPolicyValue value)
    {
        return new LifespanPolicyImpl(value);
    }

    /**
     * <code>create_id_uniqueness_policy</code>
     * <b>Section 3.3.8.5</b>
     */
    public IdUniquenessPolicy create_id_uniqueness_policy(
        IdUniquenessPolicyValue value)
    {
        return new IdUniquenessPolicyImpl(value);
    }

    /**
     * <code>create_id_assignment_policy</code>
     * <b>Section 3.3.8.5</b>
     */
    public IdAssignmentPolicy create_id_assignment_policy(
        IdAssignmentPolicyValue value)
    {
        return new IdAssignmentPolicyImpl(value);
    }

    /**
     * <code>create_implicit_activation_policy</code>
     * <b>Section 3.3.8.5</b>
     */
    public ImplicitActivationPolicy create_implicit_activation_policy(
        ImplicitActivationPolicyValue value)
    {
        return new ImplicitActivationPolicyImpl(value);
    }

    /**
     * <code>create_servant_retention_policy</code>
     * <b>Section 3.3.8.5</b>
     */
    public ServantRetentionPolicy create_servant_retention_policy(
        ServantRetentionPolicyValue value)
    {
        return new ServantRetentionPolicyImpl(value);
    }

    /**
     * <code>create_request_processing_policy</code>
     * <b>Section 3.3.8.5</b>
     */
    public RequestProcessingPolicy create_request_processing_policy(
        RequestProcessingPolicyValue value)
    {
        return new RequestProcessingPolicyImpl(value);
    }

    /**
     * <code>the_name</code>
     * <b>Section 3.3.8.6</b>
     */
    public String the_name()
    {
        try {
            lock() ;

            return name;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>the_parent</code>
     * <b>Section 3.3.8.7</b>
     */
    public POA the_parent()
    {
        try {
            lock() ;

            return parent;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>the_children</code>
     */
    public org.omg.PortableServer.POA[] the_children()
    {
        try {
            lock() ;

            Collection coll = children.values() ;
            int size = coll.size() ;
            POA[] result = new POA[ size ] ;
            int index = 0 ;
            Iterator iter = coll.iterator() ;
            while (iter.hasNext()) {
                POA poa = (POA)(iter.next()) ;
                result[ index++ ] = poa ;
            }

            return result ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>the_POAManager</code>
     * <b>Section 3.3.8.8</b>
     */
    public POAManager the_POAManager()
    {
        try {
            lock() ;

            return manager;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>the_activator</code>
     * <b>Section 3.3.8.9</b>
     */
    public AdapterActivator the_activator()
    {
        try {
            lock() ;

            return activator;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>the_activator</code>
     * <b>Section 3.3.8.9</b>
     */
    public void the_activator(AdapterActivator activator)
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling the_activator on poa " +
                    this + " activator=" + activator ) ;
            }

            this.activator = activator;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>get_servant_manager</code>
     * <b>Section 3.3.8.10</b>
     */
    public ServantManager get_servant_manager() throws WrongPolicy
    {
        try {
            lock() ;

            return mediator.getServantManager() ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>set_servant_manager</code>
     * <b>Section 3.3.8.10</b>
     */
    public void set_servant_manager(ServantManager servantManager)
        throws WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling set_servant_manager on poa " +
                    this + " servantManager=" + servantManager ) ;
            }

            mediator.setServantManager( servantManager ) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>get_servant</code>
     * <b>Section 3.3.8.12</b>
     */
    public Servant get_servant() throws NoServant, WrongPolicy
    {
        try {
            lock() ;

            return mediator.getDefaultServant() ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>set_servant</code>
     * <b>Section 3.3.8.13</b>
     */
    public void set_servant(Servant defaultServant)
        throws WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling set_servant on poa " +
                    this + " defaultServant=" + defaultServant ) ;
            }

            mediator.setDefaultServant( defaultServant ) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>activate_object</code>
     * <b>Section 3.3.8.14</b>
     */
    public byte[] activate_object(Servant servant)
        throws ServantAlreadyActive, WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling activate_object on poa " + this +
                    " (servant=" + servant + ")" ) ;
            }

            // Allocate a new system-generated object-id.
            // This will throw WrongPolicy if not SYSTEM_ID
            // policy.
            byte[] id = mediator.newSystemId();

            try {
                mediator.activateObject( id, servant ) ;
            } catch (ObjectAlreadyActive oaa) {
                // This exception can not occur in this case,
                // since id is always brand new.
                //
            }

            return id ;
        } finally {
            if (debug) {
                ORBUtility.dprint( this,
                    "Exiting activate_object on poa " + this ) ;
            }

            unlock() ;
        }
    }

    /**
     * <code>activate_object_with_id</code>
     * <b>Section 3.3.8.15</b>
     */
    public void activate_object_with_id(byte[] id,
                                                     Servant servant)
        throws ObjectAlreadyActive, ServantAlreadyActive, WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling activate_object_with_id on poa " + this +
                    " (servant=" + servant + " id=" + id + ")" ) ;
            }

            // Clone the id to avoid possible errors due to aliasing
            // (e.g. the client passes the id in and then changes it later).
            byte[] idClone = (byte[])(id.clone()) ;

            mediator.activateObject( idClone, servant ) ;
        } finally {
            if (debug) {
                ORBUtility.dprint( this,
                    "Exiting activate_object_with_id on poa " + this ) ;
            }

            unlock() ;
        }
    }

    /**
     * <code>deactivate_object</code>
     * <b>3.3.8.16</b>
     */
    public void deactivate_object(byte[] id)
        throws ObjectNotActive, WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling deactivate_object on poa " + this +
                    " (id=" + id + ")" ) ;
            }

            mediator.deactivateObject( id ) ;
        } finally {
            if (debug) {
                ORBUtility.dprint( this,
                    "Exiting deactivate_object on poa " + this ) ;
            }

            unlock() ;
        }
    }

    /**
     * <code>create_reference</code>
     * <b>3.3.8.17</b>
     */
    public org.omg.CORBA.Object create_reference(String repId)
        throws WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling create_reference(repId=" +
                    repId + ") on poa " + this ) ;
            }

            return makeObject( repId, mediator.newSystemId()) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>create_reference_with_id</code>
     * <b>3.3.8.18</b>
     */
    public org.omg.CORBA.Object
        create_reference_with_id(byte[] oid, String repId)
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling create_reference_with_id(oid=" +
                    oid + " repId=" + repId + ") on poa " + this ) ;
            }

            // Clone the id to avoid possible errors due to aliasing
            // (e.g. the client passes the id in and then changes it later).
            byte[] idClone = (byte[])(oid.clone()) ;

            return makeObject( repId, idClone ) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>servant_to_id</code>
     * <b>3.3.8.19</b>
     */
    public byte[] servant_to_id(Servant servant)
        throws ServantNotActive, WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling servant_to_id(servant=" +
                    servant + ") on poa " + this ) ;
            }

            return mediator.servantToId( servant ) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>servant_to_reference</code>
     * <b>3.3.8.20</b>
     */
    public org.omg.CORBA.Object servant_to_reference(Servant servant)
        throws ServantNotActive, WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling servant_to_reference(servant=" +
                    servant + ") on poa " + this ) ;
            }

            byte[] oid = mediator.servantToId(servant);
            String repId = servant._all_interfaces( this, oid )[0] ;
            return create_reference_with_id(oid, repId);
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>reference_to_servant</code>
     * <b>3.3.8.21</b>
     */
    public Servant reference_to_servant(org.omg.CORBA.Object reference)
        throws ObjectNotActive, WrongPolicy, WrongAdapter
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling reference_to_servant(reference=" +
                    reference + ") on poa " + this ) ;
            }

            if ( state >= STATE_DESTROYING ) {
                throw lifecycleWrapper().adapterDestroyed() ;
            }

            // reference_to_id should throw WrongAdapter
            // if the objref was not created by this POA
            byte [] id = internalReferenceToId(reference);

            return mediator.idToServant( id ) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>reference_to_id</code>
     * <b>3.3.8.22</b>
     */
    public byte[] reference_to_id(org.omg.CORBA.Object reference)
        throws WrongAdapter, WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling reference_to_id(reference=" +
                    reference + ") on poa " + this ) ;
            }

            if( state >= STATE_DESTROYING ) {
                throw lifecycleWrapper().adapterDestroyed() ;
            }

            return internalReferenceToId( reference ) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>id_to_servant</code>
     * <b>3.3.8.23</b>
     */
    public Servant id_to_servant(byte[] id)
        throws ObjectNotActive, WrongPolicy
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling id_to_servant(id=" +
                    id + ") on poa " + this ) ;
            }

            if( state >= STATE_DESTROYING ) {
                throw lifecycleWrapper().adapterDestroyed() ;
            }
            return mediator.idToServant( id ) ;
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>id_to_reference</code>
     * <b>3.3.8.24</b>
     */
    public org.omg.CORBA.Object id_to_reference(byte[] id)
        throws ObjectNotActive, WrongPolicy

    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling id_to_reference(id=" +
                    id + ") on poa " + this ) ;
            }

            if( state >= STATE_DESTROYING ) {
                throw lifecycleWrapper().adapterDestroyed() ;
            }

            Servant s = mediator.idToServant( id ) ;
            String repId = s._all_interfaces( this, id )[0] ;
            return makeObject(repId, id );
        } finally {
            unlock() ;
        }
    }

    /**
     * <code>id</code>
     * <b>11.3.8.26 in ptc/00-08-06</b>
     */
    public byte[] id()
    {
        try {
            lock() ;

            return getAdapterId() ;
        } finally {
            unlock() ;
        }
    }

    //***************************************************************
    //Implementation of ObjectAdapter interface
    //***************************************************************

    public Policy getEffectivePolicy( int type )
    {
        return mediator.getPolicies().get_effective_policy( type ) ;
    }

    public int getManagerId()
    {
        return manager.getManagerId() ;
    }

    public short getState()
    {
        return manager.getORTState() ;
    }

    public String[] getInterfaces( java.lang.Object servant, byte[] objectId )
    {
        Servant serv = (Servant)servant ;
        return serv._all_interfaces( this, objectId ) ;
    }

    protected ObjectCopierFactory getObjectCopierFactory()
    {
        int copierId = mediator.getPolicies().getCopierId() ;
        CopierManager cm = getORB().getCopierManager() ;
        return cm.getObjectCopierFactory( copierId ) ;
    }

    public void enter() throws OADestroyed
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling enter on poa " + this ) ;
            }

            // Avoid deadlock if this is the thread that is processing the
            // POA.destroy because this is the only thread that can notify
            // waiters on beingDestroyedCV.  This can happen if an
            // etherealize upcall invokes a method on a colocated object
            // served by this POA.
            while ((state == STATE_DESTROYING) &&
                (isDestroying.get() == Boolean.FALSE)) {
                try {
                    beingDestroyedCV.await();
                } catch (InterruptedException ex) {
                    // NO-OP
                }
            }

            if (!waitUntilRunning())
                throw new OADestroyed() ;

            invocationCount++;
        } finally {
            if (debug) {
                ORBUtility.dprint( this, "Exiting enter on poa " + this ) ;
            }

            unlock() ;
        }

        manager.enter();
    }

    public void exit()
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this, "Calling exit on poa " + this ) ;
            }

            invocationCount--;

            if ((invocationCount == 0) && (state == STATE_DESTROYING)) {
                invokeCV.broadcast();
            }
        } finally {
            if (debug) {
                ORBUtility.dprint( this, "Exiting exit on poa " + this ) ;
            }

            unlock() ;
        }

        manager.exit();
    }

    public void getInvocationServant( OAInvocationInfo info )
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling getInvocationServant on poa " + this ) ;
            }

            java.lang.Object servant = null ;

            try {
                servant = mediator.getInvocationServant( info.id(),
                    info.getOperation() );
            } catch (ForwardRequest freq) {
                throw new ForwardException( getORB(), freq.forward_reference ) ;
            }

            info.setServant( servant ) ;
        } finally {
            if (debug) {
                ORBUtility.dprint( this,
                    "Exiting getInvocationServant on poa " + this ) ;
            }

            unlock() ;
        }
    }

    public org.omg.CORBA.Object getLocalServant( byte[] objectId )
    {
        return null ;
    }

    /** Called from the subcontract to let this POA cleanup after an
     *  invocation. Note: If getServant was called, then returnServant
     *  MUST be called, even in the case of exceptions.  This may be
     *  called multiple times for a single request.
     */
    public void returnServant()
    {
        try {
            lock() ;

            if (debug) {
                ORBUtility.dprint( this,
                    "Calling returnServant on poa " + this  ) ;
            }

            mediator.returnServant();
        } catch (Throwable thr) {
            if (debug) {
                ORBUtility.dprint( this,
                    "Exception " + thr + " in returnServant on poa " + this  ) ;
            }

            if (thr instanceof Error)
                throw (Error)thr ;
            else if (thr instanceof RuntimeException)
                throw (RuntimeException)thr ;

        } finally {
            if (debug) {
                ORBUtility.dprint( this,
                    "Exiting returnServant on poa " + this  ) ;
            }

            unlock() ;
        }
    }
}
