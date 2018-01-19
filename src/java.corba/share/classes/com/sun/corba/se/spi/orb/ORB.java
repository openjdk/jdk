/*
 * Copyright (c) 2002, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.orb;

import java.util.Map ;
import java.util.HashMap ;
import java.util.Properties ;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger ;

import org.omg.CORBA.TCKind ;

import com.sun.corba.se.pept.broker.Broker ;
import com.sun.corba.se.pept.transport.ByteBufferPool;

import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry ;
import com.sun.corba.se.spi.protocol.ClientDelegateFactory ;
import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher ;
import com.sun.corba.se.spi.protocol.PIHandler ;
import com.sun.corba.se.spi.resolver.LocalResolver ;
import com.sun.corba.se.spi.resolver.Resolver ;
import com.sun.corba.se.spi.transport.CorbaContactInfoListFactory ;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketManager;
import com.sun.corba.se.spi.monitoring.MonitoringConstants;
import com.sun.corba.se.spi.monitoring.MonitoringManager;
import com.sun.corba.se.spi.monitoring.MonitoringFactories;

import com.sun.corba.se.spi.ior.IdentifiableFactoryFinder ;
import com.sun.corba.se.spi.ior.TaggedComponentFactoryFinder ;
import com.sun.corba.se.spi.ior.ObjectKey ;
import com.sun.corba.se.spi.ior.ObjectKeyFactory ;
import com.sun.corba.se.spi.ior.IOR ;

import com.sun.corba.se.spi.orbutil.threadpool.ThreadPoolManager;

import com.sun.corba.se.spi.oa.OAInvocationInfo ;
import com.sun.corba.se.spi.transport.CorbaTransportManager;

import com.sun.corba.se.spi.logging.LogWrapperFactory ;
import com.sun.corba.se.spi.logging.LogWrapperBase ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.spi.copyobject.CopierManager ;

import com.sun.corba.se.spi.presentation.rmi.PresentationManager ;
import com.sun.corba.se.spi.presentation.rmi.PresentationDefaults ;

import com.sun.corba.se.spi.servicecontext.ServiceContextRegistry ;

// XXX needs an SPI or else it does not belong here
import com.sun.corba.se.impl.corba.TypeCodeImpl ;
import com.sun.corba.se.impl.corba.TypeCodeFactory ;

// XXX Should there be a SPI level constants ?
import com.sun.corba.se.impl.orbutil.ORBConstants ;

import com.sun.corba.se.impl.oa.poa.BadServerIdHandler ;

import com.sun.corba.se.impl.transport.ByteBufferPoolImpl;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.impl.logging.OMGSystemException ;

import com.sun.corba.se.impl.presentation.rmi.PresentationManagerImpl ;

public abstract class ORB extends com.sun.corba.se.org.omg.CORBA.ORB
    implements Broker, TypeCodeFactory
{
    // As much as possible, this class should be stateless.  However,
    // there are a few reasons why it is not:
    //
    // 1. The ORB debug flags are defined here because they are accessed
    //    frequently, and we do not want a cast to the impl just for that.
    // 2. typeCodeMap and primitiveTypeCodeConstants are here because they
    //    are needed in both ORBImpl and ORBSingleton.
    // 3. Logging support is here so that we can avoid problems with
    //    incompletely initialized ORBs that need to perform logging.

    // Flag set at compile time to debug flag processing: this can't
    // be one of the xxxDebugFlags because it is used to debug the mechanism
    // that sets the xxxDebugFlags!
    public static boolean ORBInitDebug = false;

    // Currently defined debug flags.  Any additions must be called xxxDebugFlag.
    // All debug flags must be public boolean types.
    // These are set by passing the flag -ORBDebug x,y,z in the ORB init args.
    // Note that x,y,z must not contain spaces.
    public boolean transportDebugFlag = false ;
    public boolean subcontractDebugFlag = false ;
    public boolean poaDebugFlag = false ;
    public boolean poaConcurrencyDebugFlag = false ;
    public boolean poaFSMDebugFlag = false ;
    public boolean orbdDebugFlag = false ;
    public boolean namingDebugFlag = false ;
    public boolean serviceContextDebugFlag = false ;
    public boolean transientObjectManagerDebugFlag = false ;
    public boolean giopVersionDebugFlag = false;
    public boolean shutdownDebugFlag = false;
    public boolean giopDebugFlag = false;
    public boolean invocationTimingDebugFlag = false ;
    public boolean orbInitDebugFlag = false ;

    // SystemException log wrappers.  Protected so that they can be used in
    // subclasses.
    protected static ORBUtilSystemException staticWrapper ;
    protected ORBUtilSystemException wrapper ;
    protected OMGSystemException omgWrapper ;

    // This map is needed for resolving recursive type code placeholders
    // based on the unique repository id.
    // XXX Should this be a WeakHashMap for GC?
    private Map<String, TypeCodeImpl> typeCodeMap;

    private TypeCodeImpl[] primitiveTypeCodeConstants;

    // ByteBufferPool - needed by both ORBImpl and ORBSingleton
    ByteBufferPool byteBufferPool;

    // Local testing
    // XXX clean this up, probably remove these
    public abstract boolean isLocalHost( String hostName ) ;
    public abstract boolean isLocalServerId( int subcontractId, int serverId ) ;

    // Invocation stack manipulation
    public abstract OAInvocationInfo peekInvocationInfo() ;
    public abstract void pushInvocationInfo( OAInvocationInfo info ) ;
    public abstract OAInvocationInfo popInvocationInfo() ;

    public abstract CorbaTransportManager getCorbaTransportManager();
    public abstract LegacyServerSocketManager getLegacyServerSocketManager();

    // wrapperMap maintains a table of LogWrapper instances used by
    // different classes to log exceptions.  The key is a StringPair
    // representing LogDomain and ExceptionGroup.
    private Map<StringPair, LogWrapperBase> wrapperMap;

    static class Holder {
        static final PresentationManager defaultPresentationManager =
                                         setupPresentationManager();
    }

    private static Map<StringPair, LogWrapperBase> staticWrapperMap =
            new ConcurrentHashMap<>();

    protected MonitoringManager monitoringManager;

    private static PresentationManager setupPresentationManager() {
        staticWrapper = ORBUtilSystemException.get(
            CORBALogDomains.RPC_PRESENTATION ) ;

        boolean useDynamicStub = false;

        PresentationManager.StubFactoryFactory dynamicStubFactoryFactory = null;

        PresentationManager pm = new PresentationManagerImpl( useDynamicStub ) ;
        pm.setStubFactoryFactory( false,
            PresentationDefaults.getStaticStubFactoryFactory() ) ;
        pm.setStubFactoryFactory( true, dynamicStubFactoryFactory ) ;
        return pm;
    }

    public void destroy() {
        wrapper = null;
        omgWrapper = null;
        typeCodeMap = null;
        primitiveTypeCodeConstants = null;
        byteBufferPool = null;
    }

    /** Get the single instance of the PresentationManager
     */
    public static PresentationManager getPresentationManager()
    {
        return Holder.defaultPresentationManager;
    }

    /** Get the appropriate StubFactoryFactory.  This
     * will be dynamic or static depending on whether
     * com.sun.CORBA.ORBUseDynamicStub is true or false.
     */
    public static PresentationManager.StubFactoryFactory
        getStubFactoryFactory()
    {
        PresentationManager gPM = getPresentationManager();
        boolean useDynamicStubs = gPM.useDynamicStubs() ;
        return gPM.getStubFactoryFactory( useDynamicStubs ) ;
    }

    protected ORB()
    {
        // Initialize logging first, since it is needed nearly
        // everywhere (for example, in TypeCodeImpl).
        wrapperMap = new ConcurrentHashMap<>();
        wrapper = ORBUtilSystemException.get( this,
            CORBALogDomains.RPC_PRESENTATION ) ;
        omgWrapper = OMGSystemException.get( this,
            CORBALogDomains.RPC_PRESENTATION ) ;

        typeCodeMap = new HashMap<>();

        primitiveTypeCodeConstants = new TypeCodeImpl[] {
            new TypeCodeImpl(this, TCKind._tk_null),
            new TypeCodeImpl(this, TCKind._tk_void),
            new TypeCodeImpl(this, TCKind._tk_short),
            new TypeCodeImpl(this, TCKind._tk_long),
            new TypeCodeImpl(this, TCKind._tk_ushort),
            new TypeCodeImpl(this, TCKind._tk_ulong),
            new TypeCodeImpl(this, TCKind._tk_float),
            new TypeCodeImpl(this, TCKind._tk_double),
            new TypeCodeImpl(this, TCKind._tk_boolean),
            new TypeCodeImpl(this, TCKind._tk_char),
            new TypeCodeImpl(this, TCKind._tk_octet),
            new TypeCodeImpl(this, TCKind._tk_any),
            new TypeCodeImpl(this, TCKind._tk_TypeCode),
            new TypeCodeImpl(this, TCKind._tk_Principal),
            new TypeCodeImpl(this, TCKind._tk_objref),
            null,       // tk_struct
            null,       // tk_union
            null,       // tk_enum
            new TypeCodeImpl(this, TCKind._tk_string),
            null,       // tk_sequence
            null,       // tk_array
            null,       // tk_alias
            null,       // tk_except
            new TypeCodeImpl(this, TCKind._tk_longlong),
            new TypeCodeImpl(this, TCKind._tk_ulonglong),
            new TypeCodeImpl(this, TCKind._tk_longdouble),
            new TypeCodeImpl(this, TCKind._tk_wchar),
            new TypeCodeImpl(this, TCKind._tk_wstring),
            new TypeCodeImpl(this, TCKind._tk_fixed),
            new TypeCodeImpl(this, TCKind._tk_value),
            new TypeCodeImpl(this, TCKind._tk_value_box),
            new TypeCodeImpl(this, TCKind._tk_native),
            new TypeCodeImpl(this, TCKind._tk_abstract_interface)
        } ;

        monitoringManager =
            MonitoringFactories.getMonitoringManagerFactory( ).
                createMonitoringManager(
                MonitoringConstants.DEFAULT_MONITORING_ROOT,
                MonitoringConstants.DEFAULT_MONITORING_ROOT_DESCRIPTION);
    }

    // Typecode support: needed in both ORBImpl and ORBSingleton
    public TypeCodeImpl get_primitive_tc(int kind)
    {
        synchronized (this) {
            checkShutdownState();
        }
        try {
            return primitiveTypeCodeConstants[kind] ;
        } catch (Throwable t) {
            throw wrapper.invalidTypecodeKind( t, new Integer(kind) ) ;
        }
    }

    public synchronized void setTypeCode(String id, TypeCodeImpl code)
    {
        checkShutdownState();
        typeCodeMap.put(id, code);
    }

    public synchronized TypeCodeImpl getTypeCode(String id)
    {
        checkShutdownState();
        return typeCodeMap.get(id);
    }

    public MonitoringManager getMonitoringManager( ) {
        synchronized (this) {
            checkShutdownState();
        }
        return monitoringManager;
    }

    // Special non-standard set_parameters method for
    // creating a precisely controlled ORB instance.
    // An ORB created by this call is affected only by
    // those properties passes explicitly in props, not by
    // the system properties and orb.properties files as
    // with the standard ORB.init methods.
    public abstract void set_parameters( Properties props ) ;

    // ORB versioning
    public abstract ORBVersion getORBVersion() ;
    public abstract void setORBVersion( ORBVersion version ) ;

    // XXX This needs a better name
    public abstract IOR getFVDCodeBaseIOR() ;

    /**
     * Handle a bad server id for the given object key.  This should
     * always through an exception: either a ForwardException to
     * allow another server to handle the request, or else an error
     * indication.  XXX Remove after ORT for ORBD work is integrated.
     */
    public abstract void handleBadServerId( ObjectKey okey ) ;
    public abstract void setBadServerIdHandler( BadServerIdHandler handler ) ;
    public abstract void initBadServerIdHandler() ;

    public abstract void notifyORB() ;

    public abstract PIHandler getPIHandler() ;

    public abstract void checkShutdownState();

    // Dispatch support: in the ORB because it is needed for shutdown.
    // This is used by the first level server side subcontract.
    public abstract boolean isDuringDispatch() ;
    public abstract void startingDispatch();
    public abstract void finishedDispatch();

    /** Return this ORB's transient server ID.  This is needed for
     * initializing object adapters.
     */
    public abstract int getTransientServerId();

    public abstract ServiceContextRegistry getServiceContextRegistry() ;

    public abstract RequestDispatcherRegistry getRequestDispatcherRegistry();

    public abstract ORBData getORBData() ;

    public abstract void setClientDelegateFactory( ClientDelegateFactory factory ) ;

    public abstract ClientDelegateFactory getClientDelegateFactory() ;

    public abstract void setCorbaContactInfoListFactory( CorbaContactInfoListFactory factory ) ;

    public abstract CorbaContactInfoListFactory getCorbaContactInfoListFactory() ;

    // XXX These next 7 methods should be moved to a ResolverManager.

    /** Set the resolver used in this ORB.  This resolver will be used for list_initial_services
     * and resolve_initial_references.
     */
    public abstract void setResolver( Resolver resolver ) ;

    /** Get the resolver used in this ORB.  This resolver will be used for list_initial_services
     * and resolve_initial_references.
     */
    public abstract Resolver getResolver() ;

    /** Set the LocalResolver used in this ORB.  This LocalResolver is used for
     * register_initial_reference only.
     */
    public abstract void setLocalResolver( LocalResolver resolver ) ;

    /** Get the LocalResolver used in this ORB.  This LocalResolver is used for
     * register_initial_reference only.
     */
    public abstract LocalResolver getLocalResolver() ;

    /** Set the operation used in string_to_object calls.  The Operation must expect a
     * String and return an org.omg.CORBA.Object.
     */
    public abstract void setURLOperation( Operation stringToObject ) ;

    /** Get the operation used in string_to_object calls.  The Operation must expect a
     * String and return an org.omg.CORBA.Object.
     */
    public abstract Operation getURLOperation() ;

    /** Set the ServerRequestDispatcher that should be used for handling INS requests.
     */
    public abstract void setINSDelegate( CorbaServerRequestDispatcher insDelegate ) ;

    // XXX The next 5 operations should be moved to an IORManager.

    /** Factory finders for the various parts of the IOR: tagged components, tagged
     * profiles, and tagged profile templates.
     */
    public abstract TaggedComponentFactoryFinder getTaggedComponentFactoryFinder() ;
    public abstract IdentifiableFactoryFinder getTaggedProfileFactoryFinder() ;
    public abstract IdentifiableFactoryFinder getTaggedProfileTemplateFactoryFinder() ;

    public abstract ObjectKeyFactory getObjectKeyFactory() ;
    public abstract void setObjectKeyFactory( ObjectKeyFactory factory ) ;

    // Logging SPI

    /**
     * Returns the logger based on the category.
     */
    public Logger getLogger( String domain )
    {
        synchronized (this) {
            checkShutdownState();
        }
        ORBData odata = getORBData() ;

        // Determine the correct ORBId.  There are 3 cases:
        // 1. odata is null, which happens if we are getting a logger before
        //    ORB initialization is complete.  In this case we cannot determine
        //    the ORB ID (it's not known yet), so we set the ORBId to
        //    _INITIALIZING_.
        // 2. odata is not null, so initialization is complete, but ORBId is set to
        //    the default "".  To avoid a ".." in
        //    the log domain, we simply use _DEFAULT_ in this case.
        // 3. odata is not null, ORBId is not "": just use the ORBId.
        String ORBId ;
        if (odata == null)
            ORBId = "_INITIALIZING_" ;
        else {
            ORBId = odata.getORBId() ;
            if (ORBId.equals(""))
                ORBId = "_DEFAULT_" ;
        }

        return getCORBALogger( ORBId, domain ) ;
    }

    public static Logger staticGetLogger( String domain )
    {
        return getCORBALogger( "_CORBA_", domain ) ;
    }

    private static Logger getCORBALogger( String ORBId, String domain )
    {
        String fqLogDomain = CORBALogDomains.TOP_LEVEL_DOMAIN + "." +
            ORBId + "." + domain;

        return Logger.getLogger( fqLogDomain, ORBConstants.LOG_RESOURCE_FILE );
    }

    /** get the log wrapper class (its type is dependent on the exceptionGroup) for the
     * given log domain and exception group in this ORB instance.
     */
    public LogWrapperBase getLogWrapper(String logDomain,
        String exceptionGroup, LogWrapperFactory factory)
    {
        return wrapperMap.computeIfAbsent(
            new StringPair(logDomain, exceptionGroup),
            x -> factory.create(getLogger(logDomain)));
    }

    /** get the log wrapper class (its type is dependent on the exceptionGroup) for the
     * given log domain and exception group in this ORB instance.
     */
    public static LogWrapperBase staticGetLogWrapper(String logDomain,
        String exceptionGroup, LogWrapperFactory factory)
    {
        return staticWrapperMap.computeIfAbsent(
            new StringPair(logDomain, exceptionGroup),
            x -> factory.create(staticGetLogger(logDomain)));
    }

    // get a reference to a ByteBufferPool, a pool of NIO ByteBuffers
    // NOTE: ByteBuffer pool must be unique per ORB, not per process.
    //       There can be more than one ORB per process.
    //       This method must also be inherited by both ORB and ORBSingleton.
    public ByteBufferPool getByteBufferPool()
    {
        synchronized (this) {
            checkShutdownState();
        }
        if (byteBufferPool == null)
            byteBufferPool = new ByteBufferPoolImpl(this);

        return byteBufferPool;
    }

    public abstract void setThreadPoolManager(ThreadPoolManager mgr);

    public abstract ThreadPoolManager getThreadPoolManager();

    public abstract CopierManager getCopierManager() ;

    /*
     * This method is called to verify that a stringified IOR passed to
     * an org.omg.CORBA.ORB::string_to_object method contains a valid and acceptable IOR type.
     * If an ORB is configured with IOR type checking enabled,
     * the ORB executes a IOR type registry lookup to
     * validate that the class name extract from a type id in
     * a stringified IOR is a known and accepted type.
     * A CORBA {@code org.omg.CORBA.DATA_CONVERSION} exception will be thrown should the type check fail.
     *
     * @param iorClassName
     *        a string representing the class name corresponding to the type id of an IOR
     * @throws org.omg.CORBA.DATA_CONVERSION
     *           exception with an indication that it is a "Bad stringified IOR", which is thrown
     *           when the type check fails.
     */
    public abstract void validateIORClass(String iorClassName);

}

// End of file.
