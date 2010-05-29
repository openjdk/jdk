/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.interceptors;

import java.io.IOException;

import org.omg.CORBA.Any;
import org.omg.CORBA.NVList;

import org.omg.IOP.CodecFactory;

import org.omg.CORBA.portable.RemarshalException;

import org.omg.PortableInterceptor.ObjectReferenceTemplate ;
import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.Interceptor;
import org.omg.PortableInterceptor.PolicyFactory;
import org.omg.PortableInterceptor.Current;

import org.omg.PortableInterceptor.ORBInitInfoPackage.DuplicateName ;

import com.sun.corba.se.pept.encoding.OutputObject;

import com.sun.corba.se.spi.ior.ObjectKeyTemplate;

import com.sun.corba.se.spi.oa.ObjectAdapter;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.protocol.PIHandler;
import com.sun.corba.se.spi.protocol.ForwardException;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.impl.corba.RequestImpl;

import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage;

/**
 * This is No-Op implementation of PIHandler. It is used in ORBConfigurator
 * to initialize a piHandler before the Persistent Server Activation. This
 * PIHandler implementation will be replaced by the real PIHandler in
 * ORB.postInit( ) call.
 */
public class PINoOpHandlerImpl implements PIHandler
{
    public PINoOpHandlerImpl( ) {
    }

    public void initialize() {
    }

    public void destroyInterceptors() {
    }

    public void objectAdapterCreated( ObjectAdapter oa )
    {
    }

    public void adapterManagerStateChanged( int managerId,
        short newState )
    {
    }

    public void adapterStateChanged( ObjectReferenceTemplate[]
        templates, short newState )
    {
    }


    public void disableInterceptorsThisThread() {
    }

    public void enableInterceptorsThisThread() {
    }

    public void invokeClientPIStartingPoint()
        throws RemarshalException
    {
    }

    public Exception invokeClientPIEndingPoint(
        int replyStatus, Exception exception )
    {
        return null;
    }

    public void initiateClientPIRequest( boolean diiRequest ) {
    }

    public void cleanupClientPIRequest() {
    }

    public void setClientPIInfo(CorbaMessageMediator messageMediator)
    {
    }

    public void setClientPIInfo( RequestImpl requestImpl )
    {
    }

    final public void sendCancelRequestIfFinalFragmentNotSent()
    {
    }


    public void invokeServerPIStartingPoint()
    {
    }

    public void invokeServerPIIntermediatePoint()
    {
    }

    public void invokeServerPIEndingPoint( ReplyMessage replyMessage )
    {
    }

    public void setServerPIInfo( Exception exception ) {
    }

    public void setServerPIInfo( NVList arguments )
    {
    }

    public void setServerPIExceptionInfo( Any exception )
    {
    }

    public void setServerPIInfo( Any result )
    {
    }

    public void initializeServerPIInfo( CorbaMessageMediator request,
        ObjectAdapter oa, byte[] objectId, ObjectKeyTemplate oktemp )
    {
    }

    public void setServerPIInfo( java.lang.Object servant,
                                          String targetMostDerivedInterface )
    {
    }

    public void cleanupServerPIRequest() {
    }

    public void register_interceptor( Interceptor interceptor, int type )
        throws DuplicateName
    {
    }

    public Current getPICurrent( ) {
        return null;
    }

    public org.omg.CORBA.Policy create_policy(int type, org.omg.CORBA.Any val)
        throws org.omg.CORBA.PolicyError
    {
        return null;
    }

    public void registerPolicyFactory( int type, PolicyFactory factory ) {
    }

    public int allocateServerRequestId ()
    {
        return 0;
    }
}
