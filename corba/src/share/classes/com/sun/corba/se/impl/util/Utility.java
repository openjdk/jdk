/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.util;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.BAD_INV_ORDER;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.ORB;
import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Principal;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.BoxedValueHelper;
import org.omg.CORBA.portable.ValueFactory;
import org.omg.CORBA.portable.Streamable;
import org.omg.CORBA.portable.Delegate;


import java.util.Hashtable;
import java.util.NoSuchElementException;

import java.rmi.Remote;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteStub;

import javax.rmi.PortableRemoteObject;
import javax.rmi.CORBA.Stub;
import javax.rmi.CORBA.Tie;
import javax.rmi.CORBA.Util;

import java.io.Serializable;
import java.io.File;
import java.io.FileInputStream;

import org.omg.PortableServer.POA;

import com.sun.org.omg.SendingContext.CodeBase;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.spi.presentation.rmi.PresentationManager;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter ;

import com.sun.corba.se.impl.logging.UtilSystemException ;
import com.sun.corba.se.impl.logging.OMGSystemException ;

/**
 *  Handy class full of static functions.
 */
public final class Utility {

    public static final String STUB_PREFIX = "_";
    public static final String RMI_STUB_SUFFIX = "_Stub";
    public static final String DYNAMIC_STUB_SUFFIX = "_DynamicStub" ;
    public static final String IDL_STUB_SUFFIX = "Stub";
    public static final String TIE_SUFIX = "_Tie";
    private static IdentityHashtable tieCache = new IdentityHashtable();
    private static IdentityHashtable tieToStubCache = new IdentityHashtable();
    private static IdentityHashtable stubToTieCache = new IdentityHashtable();
    private static Object CACHE_MISS = new Object();
    private static UtilSystemException wrapper = UtilSystemException.get(
        CORBALogDomains.UTIL ) ;
    private static OMGSystemException omgWrapper = OMGSystemException.get(
        CORBALogDomains.UTIL ) ;

    /**
     * Ensure that stubs, ties, and implementation objects
     * are 'connected' to the runtime. Converts implementation
     * objects to a type suitable for sending on the wire.
     * @param obj the object to connect.
     * @param orb the ORB to connect to if obj is exported to IIOP.
     * @param convertToStub true if implementation types should be
     * converted to Stubs rather than just org.omg.CORBA.Object.
     * @return the connected object.
     * @exception NoSuchObjectException if obj is an implementation
     * which has not been exported.
     */
    public static Object autoConnect(Object obj, ORB orb, boolean convertToStub)
    {
        if (obj == null) {
            return obj;
        }

        if (StubAdapter.isStub(obj)) {
            try {
                StubAdapter.getDelegate(obj) ;
            } catch (BAD_OPERATION okay) {
                try {
                    StubAdapter.connect( obj, orb ) ;
                } catch (RemoteException e) {
                    // The stub could not be connected because it
                    // has an invalid IOR...
                    throw wrapper.objectNotConnected( e,
                        obj.getClass().getName() ) ;
                }
            }

            return obj;
        }

        if (obj instanceof Remote) {
            Remote remoteObj = (Remote)obj;
            Tie theTie = Util.getTie(remoteObj);
            if (theTie != null) {
                try {
                    theTie.orb();
                } catch (SystemException okay) {
                    theTie.orb(orb);
                }

                if (convertToStub) {
                    Object result = loadStub(theTie,null,null,true);
                    if (result != null) {
                        return result;
                    } else {
                        throw wrapper.couldNotLoadStub(obj.getClass().getName());
                    }
                } else {
                    return StubAdapter.activateTie( theTie );
                }
            } else {
                // This is an implementation object which has not been
                // exported to IIOP OR is a JRMP stub or implementation
                // object which cannot be marshalled into an ORB stream...
                throw wrapper.objectNotExported( obj.getClass().getName() ) ;
            }
        }

        // Didn't need to do anything, just return the input...

        return obj;
    }

    /*
     * Get a new instance of an RMI-IIOP Tie for the
     * given server object.
     */
    public static Tie loadTie(Remote obj) {
        Tie result = null;
        Class objClass = obj.getClass();

        // Have we tried to find this guy before?

        synchronized (tieCache) {

            Object it = tieCache.get(obj);

            if (it == null) {

                // No, so try it...

                try {

                    // First try the classname...

                    result = loadTie(objClass);

                    // If we don't have a valid tie at this point,
                    // walk up the parent chain until we either
                    // load a tie or encounter PortableRemoteObject
                    // or java.lang.Object...

                    while (result == null &&
                           (objClass = objClass.getSuperclass()) != null &&
                           objClass != PortableRemoteObject.class &&
                           objClass != Object.class) {

                        result = loadTie(objClass);
                    }
                } catch (Exception ex) {
                    wrapper.loadTieFailed( ex, objClass.getName() ) ;
                }

                // Did we get it?

                if (result == null) {

                    // Nope, so cache that fact...

                    tieCache.put(obj,CACHE_MISS);

                } else {

                    // Yes, so cache it...

                    tieCache.put(obj,result);
                }
            } else {

                // Yes, return a new instance or fail again if
                // it was a miss last time...

                if (it != CACHE_MISS) {
                    try {
                        result = (Tie) it.getClass().newInstance();
                    } catch (Exception e) {
                    }
                }
            }
        }

        return result;
    }

    /*
     * Load an RMI-IIOP Tie
     */
    private static Tie loadTie(Class theClass)
    {
        return com.sun.corba.se.spi.orb.ORB.getStubFactoryFactory().
            getTie( theClass ) ;
    }

    /*
     * Clear the stub/tie caches. Intended for use by
     * test code.
     */
    public static void clearCaches() {
        synchronized (tieToStubCache) {
            tieToStubCache.clear();
        }
        synchronized (tieCache) {
            tieCache.clear();
        }
        synchronized (stubToTieCache) {
            stubToTieCache.clear();
        }
    }

    /*
     * Load a class and check that it is assignable to a given type.
     * @param className the class name.
     * @param remoteCodebase the codebase to use. May be null.
     * @param loader the class loader of last resort. May be null.
     * @param expectedType the expected type. May be null.
     * @return the loaded class.
     */
    static Class loadClassOfType(String className, String remoteCodebase,
        ClassLoader loader, Class expectedType,
        ClassLoader expectedTypeClassLoader) throws ClassNotFoundException
    {
        Class loadedClass = null;

        try {
            //Sequence finding of the stubs according to spec
            try{
                //If-else is put here for speed up of J2EE.
                //According to the OMG spec, the if clause is not dead code.
                //It can occur if some compiler has allowed generation
                //into org.omg.stub hierarchy for non-offending
                //classes. This will encourage people to
                //produce non-offending class stubs in their own hierarchy.
                if (!PackagePrefixChecker.hasOffendingPrefix(
                    PackagePrefixChecker.withoutPackagePrefix(className))){
                    loadedClass = Util.loadClass(
                        PackagePrefixChecker.withoutPackagePrefix(className),
                        remoteCodebase,
                        loader);
                } else {
                    loadedClass = Util.loadClass(className, remoteCodebase,
                        loader);
                }
            } catch (ClassNotFoundException cnfe) {
                loadedClass = Util.loadClass(className, remoteCodebase,
                    loader);
            }
            if (expectedType == null)
                return loadedClass;
        } catch (ClassNotFoundException cnfe) {
            if (expectedType == null)
                throw cnfe;
        }

        // If no class was loaded, or if the loaded class is not of the
        // correct type, make a further attempt to load the correct class
        // using the classloader of the expected type.
        // _REVISIT_ Is this step necessary, or should the Util,loadClass
        // algorithm always produce a valid class if the setup is correct?
        // Does the OMG standard algorithm need to be changed to include
        // this step?
        if (loadedClass == null || !expectedType.isAssignableFrom(loadedClass)){
            if (expectedType.getClassLoader() != expectedTypeClassLoader)
                throw new IllegalArgumentException(
                    "expectedTypeClassLoader not class loader of "  +
                    "expected Type.");

            if (expectedTypeClassLoader != null)
                loadedClass = expectedTypeClassLoader.loadClass(className);
            else {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null)
                    cl = ClassLoader.getSystemClassLoader();

                loadedClass = cl.loadClass(className);
            }
        }

        return loadedClass;
    }

    /*
     * Load a class and check that it is compatible with a given type.
     * @param className the class name.
     * @param remoteCodebase the codebase to use. May be null.
     * @param loadingContext the loading context. May be null.
     * @param relatedType the related type. May be null.
     * @return the loaded class.
     */
    public static Class loadClassForClass (String className,
                                           String remoteCodebase,
                                           ClassLoader loader,
                                           Class relatedType,
                                           ClassLoader relatedTypeClassLoader)
        throws ClassNotFoundException
    {
        if (relatedType == null)
            return Util.loadClass(className, remoteCodebase, loader);

        Class loadedClass = null;
        try {
            loadedClass = Util.loadClass(className, remoteCodebase, loader);
        } catch (ClassNotFoundException cnfe) {
            if (relatedType.getClassLoader() == null)
                throw cnfe;
        }

        // If no class was not loaded, or if the loaded class is not of the
        // correct type, make a further attempt to load the correct class
        // using the classloader of the related type.
        // _REVISIT_ Is this step necessary, or should the Util,loadClass
        // algorithm always produce a valid class if the setup is correct?
        // Does the OMG standard algorithm need to be changed to include
        // this step?
        if (loadedClass == null ||
            (loadedClass.getClassLoader() != null &&
             loadedClass.getClassLoader().loadClass(relatedType.getName()) !=
                 relatedType))
        {
            if (relatedType.getClassLoader() != relatedTypeClassLoader)
                throw new IllegalArgumentException(
                    "relatedTypeClassLoader not class loader of relatedType.");

            if (relatedTypeClassLoader != null)
                loadedClass = relatedTypeClassLoader.loadClass(className);
        }

        return loadedClass;
    }

    /**
     * Get the helper for an IDLValue
     *
     * Throws MARSHAL exception if no helper found.
     */
    public static BoxedValueHelper getHelper(Class clazz, String codebase,
        String repId)
    {
        String className = null;
        if (clazz != null) {
            className = clazz.getName();
            if (codebase == null)
                codebase = Util.getCodebase(clazz);
        } else {
            if (repId != null)
                className = RepositoryId.cache.getId(repId).getClassName();
            if (className == null) // no repId or unrecognized repId
                throw wrapper.unableLocateValueHelper(
                    CompletionStatus.COMPLETED_MAYBE);
        }

        try {
            ClassLoader clazzLoader =
                (clazz == null ? null : clazz.getClassLoader());
            Class helperClass =
                loadClassForClass(className+"Helper", codebase, clazzLoader,
                clazz, clazzLoader);
            return (BoxedValueHelper)helperClass.newInstance();

        } catch (ClassNotFoundException cnfe) {
            throw wrapper.unableLocateValueHelper( CompletionStatus.COMPLETED_MAYBE,
                cnfe );
        } catch (IllegalAccessException iae) {
            throw wrapper.unableLocateValueHelper( CompletionStatus.COMPLETED_MAYBE,
                iae );
        } catch (InstantiationException ie) {
            throw wrapper.unableLocateValueHelper( CompletionStatus.COMPLETED_MAYBE,
                ie );
        } catch (ClassCastException cce) {
            throw wrapper.unableLocateValueHelper( CompletionStatus.COMPLETED_MAYBE,
                cce );
        }
    }

    /**
     * Get the factory for an IDLValue
     *
     * Throws MARSHAL exception if no factory found.
     */
    public static ValueFactory getFactory(Class clazz, String codebase,
                               ORB orb, String repId)
    {
        ValueFactory factory = null;
        if ((orb != null) && (repId != null)) {
            try {
                factory = ((org.omg.CORBA_2_3.ORB)orb).lookup_value_factory(
                    repId);
            } catch (org.omg.CORBA.BAD_PARAM ex) {
                // Try other way
            }
        }

        String className = null;
        if (clazz != null) {
            className = clazz.getName();
            if (codebase == null)
                codebase = Util.getCodebase(clazz);
        } else {
            if (repId != null)
                className = RepositoryId.cache.getId(repId).getClassName();
            if (className == null) // no repId or unrecognized repId
                throw omgWrapper.unableLocateValueFactory(
                    CompletionStatus.COMPLETED_MAYBE);
        }

        // if earlier search found a non-default factory, or the same default
        // factory that loadClassForClass would return, bale out now...
        if (factory != null &&
            (!factory.getClass().getName().equals(className+"DefaultFactory") ||
             (clazz == null && codebase == null)))
            return factory;

        try {
            ClassLoader clazzLoader =
                (clazz == null ? null : clazz.getClassLoader());
            Class factoryClass =
                loadClassForClass(className+"DefaultFactory", codebase,
                clazzLoader, clazz, clazzLoader);
            return (ValueFactory)factoryClass.newInstance();

        } catch (ClassNotFoundException cnfe) {
            throw omgWrapper.unableLocateValueFactory(
                CompletionStatus.COMPLETED_MAYBE, cnfe);
        } catch (IllegalAccessException iae) {
            throw omgWrapper.unableLocateValueFactory(
                CompletionStatus.COMPLETED_MAYBE, iae);
        } catch (InstantiationException ie) {
            throw omgWrapper.unableLocateValueFactory(
                CompletionStatus.COMPLETED_MAYBE, ie);
        } catch (ClassCastException cce) {
            throw omgWrapper.unableLocateValueFactory(
                CompletionStatus.COMPLETED_MAYBE, cce);
        }
    }

    /*
     * Load an RMI-IIOP Stub given a Tie.
     * @param tie the tie.
     * @param stubClass the stub class. May be null.
     * @param remoteCodebase the codebase to use. May be null.
     * @param onlyMostDerived if true, will fail if cannot load a stub for the
     * first repID in the tie. If false, will walk all repIDs.
     * @return the stub or null if not found.
     */

    public static Remote loadStub(Tie tie,
                                  PresentationManager.StubFactory stubFactory,
                                  String remoteCodebase,
                                  boolean onlyMostDerived)
    {
        StubEntry entry = null;

        // Do we already have it cached?
        synchronized (tieToStubCache) {
            Object cached = tieToStubCache.get(tie);
            if (cached == null) {
                // No, so go try to load it...
                entry = loadStubAndUpdateCache(
                        tie, stubFactory, remoteCodebase, onlyMostDerived);
            } else {
                // Yes, is it a stub?  If not, it was a miss last
                // time, so return null again...
                if (cached != CACHE_MISS) {
                    // It's a stub.
                    entry = (StubEntry) cached;

                    // Does the cached stub meet the requirements
                    // of the caller? If the caller does not require
                    // the most derived stub and does not require
                    // a specific stub type, we don't have to check
                    // any further because the cached type is good
                    // enough...
                    if (!entry.mostDerived && onlyMostDerived) {
                        // We must reload because we do not have
                        // the most derived cached already...
                        // The stubFactory arg must be null here
                        // to force onlyMostDerived=true to work
                        // correctly.
                        entry = loadStubAndUpdateCache(tie,null,
                            remoteCodebase,true);
                    } else if (stubFactory != null &&
                        !StubAdapter.getTypeIds(entry.stub)[0].equals(
                            stubFactory.getTypeIds()[0]) )
                    {
                        // We do not have exactly the right stub. First, try to
                        // upgrade the cached stub by forcing it to the most
                        // derived stub...
                        entry = loadStubAndUpdateCache(tie,null,
                            remoteCodebase,true);

                        // If that failed, try again with the exact type
                        // we need...
                        if (entry == null) {
                            entry = loadStubAndUpdateCache(tie,stubFactory,
                                    remoteCodebase,onlyMostDerived);
                        }
                    } else {
                        // Use the cached stub. Is the delegate set?
                        try {
                            Delegate stubDel = StubAdapter.getDelegate(
                                entry.stub ) ;
                        } catch (Exception e2) {
                            // No, so set it if we can...
                            try {
                                Delegate del = StubAdapter.getDelegate(
                                    tie ) ;
                                StubAdapter.setDelegate( entry.stub,
                                    del ) ;
                            } catch (Exception e) {}
                        }
                    }
                }
            }
        }

        if (entry != null) {
            return (Remote)entry.stub;
        } else {
            return null;
        }
    }

    /*
     * Load an RMI-IIOP Stub given a Tie, but do not look in the cache.
     * This method must be called with the lock held for tieToStubCache.
     * @param tie the tie.
     * @param stubFactory the stub factory. May be null.
     * @param remoteCodebase the codebase to use. May be null.
     * @param onlyMostDerived if true, will fail if cannot load a stub for the
     * first repID in the tie. If false, will walk all repIDs.
     * @return the StubEntry or null if not found.
     */
    private static StubEntry loadStubAndUpdateCache (
        Tie tie, PresentationManager.StubFactory  stubFactory,
        String remoteCodebase, boolean onlyMostDerived)
    {
        org.omg.CORBA.Object stub = null;
        StubEntry entry = null;
        boolean tieIsStub = StubAdapter.isStub( tie ) ;

        if (stubFactory != null) {
            try {
                stub = stubFactory.makeStub();
            } catch (Throwable e) {
                wrapper.stubFactoryCouldNotMakeStub( e ) ;
                if (e instanceof ThreadDeath) {
                    throw (ThreadDeath) e;
                }
            }
        } else {
            String[] ids = null;
            if (tieIsStub) {
                ids = StubAdapter.getTypeIds( tie ) ;
            } else {
                // This will throw an exception if the tie
                // is not a Servant.  XXX Handle this better?
                ids = ((org.omg.PortableServer.Servant)tie).
                      _all_interfaces( null, null );
            }

            if (remoteCodebase == null) {
                remoteCodebase = Util.getCodebase(tie.getClass());
            }

            if (ids.length == 0) {
                stub = new org.omg.stub.java.rmi._Remote_Stub();
            } else {
                // Now walk all the RepIDs till we find a stub or fail...
                for (int i = 0; i < ids.length; i++) {
                    if (ids[i].length() == 0) {
                        stub = new org.omg.stub.java.rmi._Remote_Stub();
                        break;
                    }

                    try {
                        PresentationManager.StubFactoryFactory stubFactoryFactory =
                            com.sun.corba.se.spi.orb.ORB.getStubFactoryFactory();
                        RepositoryId rid = RepositoryId.cache.getId( ids[i] ) ;
                        String className = rid.getClassName() ;
                        boolean isIDLInterface = rid.isIDLType() ;
                        stubFactory = stubFactoryFactory.createStubFactory(
                            className, isIDLInterface, remoteCodebase, null,
                            tie.getClass().getClassLoader() ) ;
                        stub = stubFactory.makeStub();
                        break;
                    } catch (Exception e) {
                        wrapper.errorInMakeStubFromRepositoryId( e ) ;
                    }

                    if (onlyMostDerived)
                        break;
                }
            }
        }

        if (stub == null) {
            // Stub == null, so cache the miss...
            tieToStubCache.put(tie,CACHE_MISS);
        } else {
            if (tieIsStub) {
                try {
                    Delegate del = StubAdapter.getDelegate( tie ) ;
                    StubAdapter.setDelegate( stub, del ) ;
                } catch( Exception e1 ) {
                    // The tie does not have a delegate set, so stash
                    // this tie away using the stub as a key so that
                    // later, when the stub is connected, we can find
                    // and connect the tie as well...

                    synchronized (stubToTieCache) {
                        stubToTieCache.put(stub,tie);
                    }
                }
            } else {
                // Tie extends Servant
                try {
                    Delegate delegate = StubAdapter.getDelegate( tie ) ;
                    StubAdapter.setDelegate( stub, delegate ) ;
                } catch( org.omg.CORBA.BAD_INV_ORDER bad) {
                    synchronized (stubToTieCache) {
                        stubToTieCache.put(stub,tie);
                    }
                } catch( Exception e ) {
                    // Exception is caught because of any of the
                    // following reasons
                    // 1) POA is not associated with the TIE
                    // 2) POA Policies for the tie-associated POA
                    //    does not support _this_object() call.
                    throw wrapper.noPoa( e ) ;
                }
            }
            // Update the cache...
            entry = new StubEntry(stub,onlyMostDerived);
            tieToStubCache.put(tie,entry);
        }

        return entry;
    }

    /*
     * If we loadStub(Tie,...) stashed away a tie which was
     * not connected, remove it from the cache and return
     * it.
     */
    public static Tie getAndForgetTie (org.omg.CORBA.Object stub) {
        synchronized (stubToTieCache) {
            return (Tie) stubToTieCache.remove(stub);
        }
    }

    /*
     * Remove any cached Stub for the given tie.
     */
    public static void purgeStubForTie (Tie tie) {
        StubEntry entry;
        synchronized (tieToStubCache) {
            entry = (StubEntry)tieToStubCache.remove(tie);
        }
        if (entry != null) {
            synchronized (stubToTieCache) {
                stubToTieCache.remove(entry.stub);
            }
        }
    }

    /*
     * Remove cached tie/servant pair.
     */
    public static void purgeTieAndServant (Tie tie) {
        synchronized (tieCache) {
            Object target = tie.getTarget();
            if (target != null)
                tieCache.remove(target);
        }
    }

    /*
     * Convert a RepId to a stubName...
     */
    public static String stubNameFromRepID (String repID) {

        // Convert the typeid to a RepositoryId instance, get
        // the className and mangle it as needed...

        RepositoryId id = RepositoryId.cache.getId(repID);
        String className = id.getClassName();

        if (id.isIDLType()) {
            className = idlStubName(className);
        } else {
            className = stubName(className);
        }
        return className;
    }


    /*
     * Load an RMI-IIOP Stub.  This is used in PortableRemoteObject.narrow.
     */
    public static Remote loadStub (org.omg.CORBA.Object narrowFrom,
                                   Class narrowTo)
    {
        Remote result = null;

        try {
            // Get the codebase from the delegate to use when loading
            // the new stub, if possible...
            String codebase = null;
            try {
                // We can't assume that narrowFrom is a CORBA_2_3 stub, yet
                // it may have a 2_3 Delegate that provides a codebase.  Swallow
                // the ClassCastException otherwise.
                Delegate delegate = StubAdapter.getDelegate( narrowFrom ) ;
                codebase = ((org.omg.CORBA_2_3.portable.Delegate)delegate).
                    get_codebase(narrowFrom);

            } catch (ClassCastException e) {
                wrapper.classCastExceptionInLoadStub( e ) ;
            }

            PresentationManager.StubFactoryFactory sff =
                com.sun.corba.se.spi.orb.ORB.getStubFactoryFactory() ;
            PresentationManager.StubFactory sf = sff.createStubFactory(
                narrowTo.getName(), false, codebase, narrowTo,
                narrowTo.getClassLoader() ) ;
            result = (Remote)sf.makeStub() ;
            StubAdapter.setDelegate( result,
                StubAdapter.getDelegate( narrowFrom ) ) ;
        } catch (Exception err) {
            wrapper.exceptionInLoadStub( err ) ;
        }

        return result;
    }

    /*
     * Load an RMI-IIOP Stub class.  This is used in the
     * StaticStubFactoryFactory code.
     */
    public static Class loadStubClass(String repID,
                                      String remoteCodebase,
                                      Class expectedType)
        throws ClassNotFoundException
    {
        // Get the repID and check for "" special case.
        // We should never be called with it (See CDRInputStream
        // and the loadStub() method)...

        if (repID.length() == 0) {
            throw new ClassNotFoundException();
        }

        // Get the stubname from the repID and load
        // the class. If we have a valid 'sender', fall
        // back to using its codebase if we need to...
        String className = Utility.stubNameFromRepID(repID);
        ClassLoader expectedTypeClassLoader = (expectedType == null ? null :
            expectedType.getClassLoader());

        try {
              return loadClassOfType(className,
                                       remoteCodebase,
                                       expectedTypeClassLoader,
                                       expectedType,
                                       expectedTypeClassLoader);
        } catch (ClassNotFoundException e) {
            return loadClassOfType(PackagePrefixChecker.packagePrefix() + className,
                                   remoteCodebase,
                                   expectedTypeClassLoader,
                                   expectedType,
                                   expectedTypeClassLoader);
        }
    }

    /**
     * Create an RMI stub name.
     */
    public static String stubName (String className)
    {
        return stubName( className, false ) ;
    }

    public static String dynamicStubName( String className )
    {
        return stubName( className, true ) ;
    }

    private static String stubName( String className,
        boolean isDynamic )
    {
        String name = stubNameForCompiler( className, isDynamic ) ;
        if (PackagePrefixChecker.hasOffendingPrefix( name ))
            name = PackagePrefixChecker.packagePrefix() + name ;
        return name ;
    }

    public static String stubNameForCompiler (String className)
    {
        return stubNameForCompiler( className, false ) ;
    }

    private static String stubNameForCompiler( String className,
        boolean isDynamic )
    {
        int index = className.indexOf('$');
        if (index < 0) {
            index = className.lastIndexOf('.');
        }

        String suffix = isDynamic ? DYNAMIC_STUB_SUFFIX :
            RMI_STUB_SUFFIX ;

        if (index > 0) {
            return className.substring(0,index+1) + STUB_PREFIX +
                className.substring(index+1) + suffix;
        } else {
            return STUB_PREFIX + className + suffix;
        }
    }

    /**
     * Create an RMI tie name.
     */
    public static String tieName (String className)
    {
        return
            PackagePrefixChecker.hasOffendingPrefix(tieNameForCompiler(className)) ?
            PackagePrefixChecker.packagePrefix() + tieNameForCompiler(className) :
            tieNameForCompiler(className);
    }

    public static String tieNameForCompiler (String className)
    {
        int index = className.indexOf('$');
        if (index < 0) {
            index = className.lastIndexOf('.');
        }
        if (index > 0) {
            return className.substring(0,index+1) +
                STUB_PREFIX +
                className.substring(index+1) +
                TIE_SUFIX;
        } else {
            return STUB_PREFIX +
                className +
                TIE_SUFIX;
        }
    }

    /**
     * Throws the CORBA equivalent of a java.io.NotSerializableException
     */
    public static void throwNotSerializableForCorba(String className) {
        throw omgWrapper.notSerializable( CompletionStatus.COMPLETED_MAYBE,
            className ) ;
    }

    /**
     * Create an IDL stub name.
     */
    public static String idlStubName(String className)
    {
        String result = null;
        int index = className.lastIndexOf('.');
        if (index > 0) {
            result = className.substring(0,index+1) +
                STUB_PREFIX +
                className.substring(index+1) +
                IDL_STUB_SUFFIX;
        } else {
            result = STUB_PREFIX +
                className +
                IDL_STUB_SUFFIX;
        }
        return result;
    }

    public static void printStackTrace()
    {
        Throwable thr = new Throwable( "Printing stack trace:" ) ;
        thr.fillInStackTrace() ;
        thr.printStackTrace() ;
    }

    /**
     * Read an object reference from the input stream and narrow
     * it to the desired type.
     * @param in the stream to read from.
     * @throws ClassCastException if narrowFrom cannot be cast to narrowTo.
     */
    public static Object readObjectAndNarrow(InputStream in,
                                             Class narrowTo)
        throws ClassCastException
    {
        Object result = in.read_Object();
        if (result != null)
            return PortableRemoteObject.narrow(result, narrowTo);
        else
            return null;
    }

    /**
     * Read an abstract interface type from the input stream and narrow
     * it to the desired type.
     * @param in the stream to read from.
     * @throws ClassCastException if narrowFrom cannot be cast to narrowTo.
     */
    public static Object readAbstractAndNarrow(
        org.omg.CORBA_2_3.portable.InputStream in, Class narrowTo)
        throws ClassCastException
    {
        Object result = in.read_abstract_interface();
        if (result != null)
            return PortableRemoteObject.narrow(result, narrowTo);
        else
            return null;
    }


    /** Converts an Ascii Character into Hexadecimal digit
     */
    static int hexOf( char x )
    {
        int val;

        val = x - '0';
        if (val >=0 && val <= 9)
            return val;

        val = (x - 'a') + 10;
        if (val >= 10 && val <= 15)
            return val;

        val = (x - 'A') + 10;
        if (val >= 10 && val <= 15)
            return val;

        throw wrapper.badHexDigit() ;
    }
}

class StubEntry {
    org.omg.CORBA.Object stub;
    boolean mostDerived;

    StubEntry(org.omg.CORBA.Object stub, boolean mostDerived) {
        this.stub = stub;
        this.mostDerived = mostDerived;
    }
}
