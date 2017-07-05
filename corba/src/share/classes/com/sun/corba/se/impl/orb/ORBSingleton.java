/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orb;

import java.util.Collection;
import java.util.Properties;
import java.util.Hashtable;

import java.applet.Applet;

import java.net.URL;

import java.io.IOException ;

import java.util.logging.Logger ;

import org.omg.CORBA.Context;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.Environment;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CORBA.NVList;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.Object;
import org.omg.CORBA.Request;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Any;
import org.omg.CORBA.StructMember;
import org.omg.CORBA.UnionMember;
import org.omg.CORBA.ValueMember;
import org.omg.CORBA.Policy;
import org.omg.CORBA.PolicyError;

import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.RemarshalException;

import com.sun.corba.se.pept.protocol.ClientInvocationInfo ;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.corba.se.pept.transport.ConnectionCache;
import com.sun.corba.se.pept.transport.Selector ;
import com.sun.corba.se.pept.transport.TransportManager;

import com.sun.corba.se.spi.legacy.connection.ORBSocketFactory;
import com.sun.corba.se.spi.orb.ORBData;
import com.sun.corba.se.spi.orb.Operation;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ORBVersion;
import com.sun.corba.se.spi.orb.ORBVersionFactory;
import com.sun.corba.se.spi.oa.OAInvocationInfo;
import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.protocol.ClientDelegateFactory;
import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry;
import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher;
import com.sun.corba.se.spi.protocol.PIHandler;
import com.sun.corba.se.spi.resolver.Resolver;
import com.sun.corba.se.spi.resolver.LocalResolver;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.IdentifiableFactoryFinder;
import com.sun.corba.se.spi.ior.TaggedComponentFactoryFinder;
import com.sun.corba.se.spi.ior.ObjectKey;
import com.sun.corba.se.spi.ior.ObjectKeyFactory;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.pept.transport.ByteBufferPool ;
import com.sun.corba.se.spi.transport.CorbaContactInfoListFactory ;
import com.sun.corba.se.spi.transport.CorbaTransportManager;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketManager;
import com.sun.corba.se.spi.orbutil.closure.Closure;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPoolManager;
import com.sun.corba.se.spi.logging.LogWrapperFactory;
import com.sun.corba.se.spi.logging.LogWrapperBase;
import com.sun.corba.se.spi.copyobject.CopierManager;
import com.sun.corba.se.spi.presentation.rmi.PresentationManager;
import com.sun.corba.se.spi.presentation.rmi.PresentationDefaults;

import com.sun.corba.se.spi.servicecontext.ServiceContextRegistry;
import com.sun.corba.se.spi.servicecontext.ServiceContexts;

import com.sun.corba.se.impl.corba.TypeCodeFactory;
import com.sun.corba.se.impl.corba.TypeCodeImpl;
import com.sun.corba.se.impl.corba.NVListImpl;
import com.sun.corba.se.impl.corba.NamedValueImpl;
import com.sun.corba.se.impl.corba.ExceptionListImpl;
import com.sun.corba.se.impl.corba.ContextListImpl;
import com.sun.corba.se.impl.corba.EnvironmentImpl;
import com.sun.corba.se.impl.corba.AnyImpl;
import com.sun.corba.se.impl.encoding.BufferManagerFactory;
import com.sun.corba.se.impl.encoding.CodeSetComponentInfo;
import com.sun.corba.se.impl.encoding.MarshalInputStream;
import com.sun.corba.se.impl.encoding.EncapsOutputStream;
import com.sun.corba.se.impl.encoding.MarshalOutputStream;
import com.sun.corba.se.impl.oa.poa.BadServerIdHandler;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketEndPointInfo;

/*
 * The restricted singleton ORB implementation.
 *
 * For now, this class must implement just enough functionality to be
 * used as a factory for immutable TypeCode instances.
 *
 * See ORBImpl.java for the real ORB implementation.
 */
public class ORBSingleton extends ORB
{
    // This is used to support read_Object.
    private ORB fullORB;
    private static PresentationManager.StubFactoryFactory staticStubFactoryFactory =
        PresentationDefaults.getStaticStubFactoryFactory() ;

    public void set_parameters( Properties props ) {
    }

    protected void set_parameters(Applet app, Properties props) {
    }

    protected void set_parameters (String params[], Properties props) {
    }

    public OutputStream create_output_stream() {
        return sun.corba.OutputStreamFactory.newEncapsOutputStream(this);
    }

    public TypeCode create_struct_tc(String id,
                                     String name,
                                     StructMember[] members)
    {
        return new TypeCodeImpl(this, TCKind._tk_struct, id, name, members);
    }

    public TypeCode create_union_tc(String id,
                                    String name,
                                    TypeCode discriminator_type,
                                    UnionMember[] members)
    {
        return new TypeCodeImpl(this,
                                TCKind._tk_union,
                                id,
                                name,
                                discriminator_type,
                                members);
    }

    public TypeCode create_enum_tc(String id,
                                   String name,
                                   String[] members)
    {
        return new TypeCodeImpl(this, TCKind._tk_enum, id, name, members);
    }

    public TypeCode create_alias_tc(String id,
                                    String name,
                                    TypeCode original_type)
    {
        return new TypeCodeImpl(this, TCKind._tk_alias, id, name, original_type);
    }

    public TypeCode create_exception_tc(String id,
                                        String name,
                                        StructMember[] members)
    {
        return new TypeCodeImpl(this, TCKind._tk_except, id, name, members);
    }

    public TypeCode create_interface_tc(String id,
                                        String name)
    {
        return new TypeCodeImpl(this, TCKind._tk_objref, id, name);
    }

    public TypeCode create_string_tc(int bound) {
        return new TypeCodeImpl(this, TCKind._tk_string, bound);
    }

    public TypeCode create_wstring_tc(int bound) {
        return new TypeCodeImpl(this, TCKind._tk_wstring, bound);
    }

    public TypeCode create_sequence_tc(int bound,
                                       TypeCode element_type)
    {
        return new TypeCodeImpl(this, TCKind._tk_sequence, bound, element_type);
    }

    public TypeCode create_recursive_sequence_tc(int bound,
                                                 int offset)
    {
        return new TypeCodeImpl(this, TCKind._tk_sequence, bound, offset);
    }

    public TypeCode create_array_tc(int length,
                                    TypeCode element_type)
    {
        return new TypeCodeImpl(this, TCKind._tk_array, length, element_type);
    }

    public org.omg.CORBA.TypeCode create_native_tc(String id,
                                                   String name)
    {
        return new TypeCodeImpl(this, TCKind._tk_native, id, name);
    }

    public org.omg.CORBA.TypeCode create_abstract_interface_tc(
                                                               String id,
                                                               String name)
    {
        return new TypeCodeImpl(this, TCKind._tk_abstract_interface, id, name);
    }

    public org.omg.CORBA.TypeCode create_fixed_tc(short digits, short scale)
    {
        return new TypeCodeImpl(this, TCKind._tk_fixed, digits, scale);
    }

    // orbos 98-01-18: Objects By Value -- begin

    public org.omg.CORBA.TypeCode create_value_tc(String id,
                                                  String name,
                                                  short type_modifier,
                                                  TypeCode concrete_base,
                                                  ValueMember[] members)
    {
        return new TypeCodeImpl(this, TCKind._tk_value, id, name,
                                type_modifier, concrete_base, members);
    }

    public org.omg.CORBA.TypeCode create_recursive_tc(String id) {
        return new TypeCodeImpl(this, id);
    }

    public org.omg.CORBA.TypeCode create_value_box_tc(String id,
                                                      String name,
                                                      TypeCode boxed_type)
    {
        return new TypeCodeImpl(this, TCKind._tk_value_box, id, name, boxed_type);
    }

    public TypeCode get_primitive_tc( TCKind tckind )
    {
        return get_primitive_tc( tckind.value() ) ;
    }

    public Any create_any() {
        return new AnyImpl(this);
    }

    // TypeCodeFactory interface methods.
    // Keeping track of type codes by repository id.
    /*
     * Not strictly needed for TypeCode factory duty but these seem
     * harmless enough.
     */

    public NVList create_list(int count) {
        return new NVListImpl(this, count);
    }

    public org.omg.CORBA.NVList
        create_operation_list(org.omg.CORBA.Object oper) {
        throw wrapper.genericNoImpl() ;
    }

    public org.omg.CORBA.NamedValue
        create_named_value(String s, Any any, int flags) {
        return new NamedValueImpl(this, s, any, flags);
    }

    public org.omg.CORBA.ExceptionList create_exception_list() {
        return new ExceptionListImpl();
    }

    public org.omg.CORBA.ContextList create_context_list() {
        return new ContextListImpl(this);
    }

    public org.omg.CORBA.Context get_default_context()
    {
        throw wrapper.genericNoImpl() ;
    }

    public org.omg.CORBA.Environment create_environment()
    {
        return new EnvironmentImpl();
    }

    public org.omg.CORBA.Current get_current()
    {
        throw wrapper.genericNoImpl() ;
    }

    /*
     * Things that aren't allowed.
     */

    public String[] list_initial_services ()
    {
        throw wrapper.genericNoImpl() ;
    }

    public org.omg.CORBA.Object resolve_initial_references(String identifier)
        throws InvalidName
    {
        throw wrapper.genericNoImpl() ;
    }

    public void register_initial_reference(
        String id, org.omg.CORBA.Object obj ) throws InvalidName
    {
        throw wrapper.genericNoImpl() ;
    }

    public void send_multiple_requests_oneway(Request[] req) {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void send_multiple_requests_deferred(Request[] req) {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public boolean poll_next_response() {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public org.omg.CORBA.Request get_next_response() {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public String object_to_string(org.omg.CORBA.Object obj) {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public org.omg.CORBA.Object string_to_object(String s) {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public java.rmi.Remote string_to_remote(String s)
        throws java.rmi.RemoteException
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void connect(org.omg.CORBA.Object servant) {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void disconnect(org.omg.CORBA.Object obj) {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void run()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void shutdown(boolean wait_for_completion)
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    protected void shutdownServants(boolean wait_for_completion) {
        throw new SecurityException("ORBSingleton: access denied");
    }

    protected void destroyConnections() {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void destroy() {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public boolean work_pending()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void perform_work()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public org.omg.CORBA.portable.ValueFactory register_value_factory(String repositoryID,
                                org.omg.CORBA.portable.ValueFactory factory)
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void unregister_value_factory(String repositoryID)
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public org.omg.CORBA.portable.ValueFactory lookup_value_factory(String repositoryID)
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public TransportManager getTransportManager()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public CorbaTransportManager getCorbaTransportManager()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public LegacyServerSocketManager getLegacyServerSocketManager()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

/*************************************************************************
    These are methods from com.sun.corba.se.impl.se.core.ORB
 ************************************************************************/

    private synchronized ORB getFullORB()
    {
        if (fullORB == null) {
            Properties props = new Properties() ;
            fullORB = new ORBImpl() ;
            fullORB.set_parameters( props ) ;
        }

        return fullORB ;
    }

    public RequestDispatcherRegistry getRequestDispatcherRegistry()
    {
        // To enable read_Object.

        return getFullORB().getRequestDispatcherRegistry();
    }

    /**
     * Return the service context registry
     */
    public ServiceContextRegistry getServiceContextRegistry()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    /**
     * Get the transient server ID
     */
    public int getTransientServerId()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    /**
     * Return the bootstrap naming port specified in the ORBInitialPort param.
     */
    public int getORBInitialPort()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    /**
     * Return the bootstrap naming host specified in the ORBInitialHost param.
     */
    public String getORBInitialHost()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public String getORBServerHost()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public int getORBServerPort()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public CodeSetComponentInfo getCodeSetComponentInfo()
    {
            return new CodeSetComponentInfo();
    }

    public boolean isLocalHost( String host )
    {
        // To enable read_Object.
        return false;
    }

    public boolean isLocalServerId( int subcontractId, int serverId )
    {
        // To enable read_Object.
        return false;
    }

    /*
     * Things from corba.ORB.
     */

    public ORBVersion getORBVersion()
    {
        // Always use our latest ORB version (latest fixes, etc)
        return ORBVersionFactory.getORBVersion();
    }

    public void setORBVersion(ORBVersion verObj)
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public String getAppletHost()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public URL getAppletCodeBase()
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public int getHighWaterMark(){
        throw new SecurityException("ORBSingleton: access denied");
    }

    public int getLowWaterMark(){
        throw new SecurityException("ORBSingleton: access denied");
    }

    public int getNumberToReclaim(){
        throw new SecurityException("ORBSingleton: access denied");
    }

    public int getGIOPFragmentSize() {
        return ORBConstants.GIOP_DEFAULT_BUFFER_SIZE;
    }

    public int getGIOPBuffMgrStrategy(GIOPVersion gv) {
        return BufferManagerFactory.GROW;
    }

    public IOR getFVDCodeBaseIOR(){
        throw new SecurityException("ORBSingleton: access denied");
    }

    public Policy create_policy( int type, Any val ) throws PolicyError
    {
        throw new NO_IMPLEMENT();
    }

    public LegacyServerSocketEndPointInfo getServerEndpoint()
    {
        return null ;
    }

    public void setPersistentServerId( int id )
    {
    }

    public TypeCodeImpl getTypeCodeForClass( Class c )
    {
        return null ;
    }

    public void setTypeCodeForClass( Class c, TypeCodeImpl tcimpl )
    {
    }

    public boolean alwaysSendCodeSetServiceContext()
    {
        return true ;
    }

    public boolean isDuringDispatch()
    {
        return false ;
    }

    public void notifyORB() { }

    public PIHandler getPIHandler()
    {
        return null ;
    }

    public void checkShutdownState()
    {
    }

    public void startingDispatch()
    {
    }

    public void finishedDispatch()
    {
    }

    public void registerInitialReference( String id, Closure closure )
    {
    }

    public ORBData getORBData()
    {
        return getFullORB().getORBData() ;
    }

    public void setClientDelegateFactory( ClientDelegateFactory factory )
    {
    }

    public ClientDelegateFactory getClientDelegateFactory()
    {
        return getFullORB().getClientDelegateFactory() ;
    }

    public void setCorbaContactInfoListFactory( CorbaContactInfoListFactory factory )
    {
    }

    public CorbaContactInfoListFactory getCorbaContactInfoListFactory()
    {
        return getFullORB().getCorbaContactInfoListFactory() ;
    }

    public Operation getURLOperation()
    {
        return null ;
    }

    public void setINSDelegate( CorbaServerRequestDispatcher sdel )
    {
    }

    public TaggedComponentFactoryFinder getTaggedComponentFactoryFinder()
    {
        return getFullORB().getTaggedComponentFactoryFinder() ;
    }

    public IdentifiableFactoryFinder getTaggedProfileFactoryFinder()
    {
        return getFullORB().getTaggedProfileFactoryFinder() ;
    }

    public IdentifiableFactoryFinder getTaggedProfileTemplateFactoryFinder()
    {
        return getFullORB().getTaggedProfileTemplateFactoryFinder() ;
    }

    public ObjectKeyFactory getObjectKeyFactory()
    {
        return getFullORB().getObjectKeyFactory() ;
    }

    public void setObjectKeyFactory( ObjectKeyFactory factory )
    {
        throw new SecurityException("ORBSingleton: access denied");
    }

    public void handleBadServerId( ObjectKey okey )
    {
    }

    public OAInvocationInfo peekInvocationInfo()
    {
        return null ;
    }

    public void pushInvocationInfo( OAInvocationInfo info )
    {
    }

    public OAInvocationInfo popInvocationInfo()
    {
        return null ;
    }

    public ClientInvocationInfo createOrIncrementInvocationInfo()
    {
        return null ;
    }

    public void releaseOrDecrementInvocationInfo()
    {
    }

    public ClientInvocationInfo getInvocationInfo()
    {
        return null ;
    }

    public ConnectionCache getConnectionCache(ContactInfo contactInfo)
    {
        return null;
    }

    public void setResolver( Resolver resolver )
    {
    }

    public Resolver getResolver()
    {
        return null ;
    }

    public void setLocalResolver( LocalResolver resolver )
    {
    }

    public LocalResolver getLocalResolver()
    {
        return null ;
    }

    public void setURLOperation( Operation stringToObject )
    {
    }

    // NOTE: REMOVE THIS METHOD ONCE WE HAVE A ORT BASED ORBD
    public void setBadServerIdHandler( BadServerIdHandler handler )
    {
    }

    // NOTE: REMOVE THIS METHOD ONCE WE HAVE A ORT BASED ORBD
    public void initBadServerIdHandler()
    {
    }

    public Selector getSelector(int x)
    {
        return null;
    }

    public void setThreadPoolManager(ThreadPoolManager mgr) {
    }

    public ThreadPoolManager getThreadPoolManager() {
        return null;
    }

    public CopierManager getCopierManager() {
        return null ;
    }
}

// End of file.
