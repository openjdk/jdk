/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.protocol;

import org.omg.PortableInterceptor.ObjectReferenceTemplate ;
import org.omg.PortableInterceptor.Interceptor ;
import org.omg.PortableInterceptor.Current ;
import org.omg.PortableInterceptor.PolicyFactory ;
import org.omg.PortableInterceptor.ORBInitInfoPackage.DuplicateName ;

import org.omg.CORBA.NVList ;
import org.omg.CORBA.Any ;
import org.omg.CORBA.Policy ;
import org.omg.CORBA.PolicyError ;

import org.omg.CORBA.portable.RemarshalException;

import com.sun.corba.se.spi.oa.ObjectAdapter ;

import com.sun.corba.se.spi.protocol.CorbaMessageMediator ;

import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;

// XXX These need to go away.
import com.sun.corba.se.impl.corba.RequestImpl ;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage ;

/** This interface defines the PI interface that is used to interface the rest of the
 * ORB to the PI implementation.
 */
public interface PIHandler {
    /** Complete the initialization of the PIHandler.  This will execute the methods
    * on the ORBInitializers, if any are defined.  This must be done here so that
    * the ORB can obtain the PIHandler BEFORE the ORBInitializers run, since they
    * will need access to the PIHandler through the ORB.
    */
    public void initialize() ;

    public void destroyInterceptors() ;

    /*
     ****************************
     * IOR interceptor PI hooks
     ****************************/

    /**
     * Called when a new object adapter is created.
     *
     * @param oa The adapter associated with the interceptors to be
     *   invoked.
     */
    void objectAdapterCreated( ObjectAdapter oa )  ;

    /**
     * Called whenever a state change occurs in an adapter manager.
     *
     * @param managerId managerId The adapter manager id
     * @param newState newState The new state of the adapter manager,
     * and by implication of all object adapters managed by this manager.
     */
    void adapterManagerStateChanged( int managerId,
        short newState ) ;

    /** Called whenever a state change occurs in an object adapter that
    * was not caused by an adapter manager state change.
    *
    * @param templates The templates that are changing state.
    * @param newState The new state of the adapters identified by the
    * templates.
    */
    void adapterStateChanged( ObjectReferenceTemplate[] templates,
        short newState ) ;

    /*
     *****************
     * Client PI hooks
     *****************/

    /**
     * Called for pseudo-ops to temporarily disable portable interceptor
     * hooks for calls on this thread.  Keeps track of the number of
     * times this is called and increments the disabledCount.
     */
    void disableInterceptorsThisThread() ;

    /**
     * Called for pseudo-ops to re-enable portable interceptor
     * hooks for calls on this thread.  Decrements the disabledCount.
     * If disabledCount is 0, interceptors are re-enabled.
     */
    void enableInterceptorsThisThread() ;

    /**
     * Called when the send_request or send_poll portable interception point
     * is to be invoked for all appropriate client-side request interceptors.
     *
     * @exception RemarhsalException - Thrown when this request needs to
     *     be retried.
     */
    void invokeClientPIStartingPoint()
        throws RemarshalException ;

    /**
     * Called when the appropriate client ending interception point is
     * to be invoked for all apporpriate client-side request interceptors.
     *
     * @param replyStatus One of the constants in iiop.messages.ReplyMessage
     *     indicating which reply status to set.
     * @param exception The exception before ending interception points have
     *     been invoked, or null if no exception at the moment.
     * @return The exception to be thrown, after having gone through
     *     all ending points, or null if there is no exception to be
     *     thrown.  Note that this exception can be either the same or
     *     different from the exception set using setClientPIException.
     *     There are four possible return types: null (no exception),
     *     SystemException, UserException, or RemarshalException.
     */
    Exception invokeClientPIEndingPoint(
        int replyStatus, Exception exception ) ;

    /**
     * Invoked when a request is about to be created.  Must be called before
     * any of the setClientPI* methods so that a new info object can be
     * prepared for information collection.
     *
     * @param diiRequest True if this is to be a DII request, or false if it
     *     is a "normal" request.  In the DII case, initiateClientPIRequest
     *     is called twice and we need to ignore the second one.
     */
    void initiateClientPIRequest( boolean diiRequest ) ;

    /**
     * Invoked when a request is about to be cleaned up.  Must be called
     * after ending points are called so that the info object on the stack
     * can be deinitialized and popped from the stack at the appropriate
     * time.
     */
    void cleanupClientPIRequest() ;

    /**
     * Notifies PI of additional information for client-side interceptors.
     * PI will use this information as a source of information for the
     * ClientRequestInfo object.
     */
    void setClientPIInfo( RequestImpl requestImpl ) ;

    /**
     * Notify PI of the MessageMediator for the request.
     */
    void setClientPIInfo(CorbaMessageMediator messageMediator) ;

    /*
     *****************
     * Server PI hooks
     *****************/

    /**
     * Called when the appropriate server starting interception point is
     * to be invoked for all appropriate server-side request interceptors.
     *
     * @throws ForwardException Thrown if an interceptor raises
     *     ForwardRequest.  This is an unchecked exception so that we need
     *     not modify the entire execution path to declare throwing
     *     ForwardException.
     */
    void invokeServerPIStartingPoint() ;

    /**
     * Called when the appropriate server intermediate interception point is
     * to be invoked for all appropriate server-side request interceptors.
     *
     * @throws ForwardException Thrown if an interceptor raises
     *     ForwardRequest.  This is an unchecked exception so that we need
     *     not modify the entire execution path to declare throwing
     *     ForwardException.
     */
    void invokeServerPIIntermediatePoint() ;

    /**
     * Called when the appropriate server ending interception point is
     * to be invoked for all appropriate server-side request interceptors.
     *
     * @param replyMessage The iiop.messages.ReplyMessage containing the
     *     reply status.
     * @throws ForwardException Thrown if an interceptor raises
     *     ForwardRequest.  This is an unchecked exception so that we need
     *     not modify the entire execution path to declare throwing
     *     ForwardException.
     */
    void invokeServerPIEndingPoint( ReplyMessage replyMessage ) ;

    /**
     * Notifies PI to start a new server request and set initial
     * information for server-side interceptors.
     * PI will use this information as a source of information for the
     * ServerRequestInfo object.  poaimpl is declared as an Object so that
     * we need not introduce a dependency on the POA package.
     */
    void initializeServerPIInfo( CorbaMessageMediator request,
        ObjectAdapter oa, byte[] objectId, ObjectKeyTemplate oktemp ) ;

    /**
     * Notifies PI of additional information reqired for ServerRequestInfo.
     *
     * @param servant The servant.  This is java.lang.Object because in the
     *     POA case, this will be a org.omg.PortableServer.Servant whereas
     *     in the ServerRequestDispatcher case this will be an ObjectImpl.
     * @param targetMostDerivedInterface.  The most derived interface.  This
     *     is passed in instead of calculated when needed because it requires
     *     extra information in the POA case that we didn't want to bother
     *     creating extra methods for to pass in.
     */
    void setServerPIInfo( java.lang.Object servant,
                                    String targetMostDerivedInterface ) ;

    /**
     * Notifies PI of additional information required for ServerRequestInfo.
     */
    void setServerPIInfo( Exception exception ) ;

    /**
     * Notifies PI of additional information for server-side interceptors.
     * PI will use this information as a source of information for the
     * ServerRequestInfo object.  These are the arguments for a DSI request.
     */
    void setServerPIInfo( NVList arguments ) ;

    /**
     * Notifies PI of additional information for server-side interceptors.
     * PI will use this information as a source of information for the
     * ServerRequestInfo object.  This is the exception of a DSI request.
     */
    void setServerPIExceptionInfo( Any exception ) ;

    /**
     * Notifies PI of additional information for server-side interceptors.
     * PI will use this information as a source of information for the
     * ServerRequestInfo object.  This is the result of a DSI request.
     */
    void setServerPIInfo( Any result ) ;

    /**
     * Invoked when a request is about to be cleaned up.  Must be called
     * after ending points are called so that the info object on the stack
     * can be deinitialized and popped from the stack at the appropriate
     * time.
     */
    void cleanupServerPIRequest() ;

    Policy create_policy( int type, Any val ) throws PolicyError ;

    void register_interceptor( Interceptor interceptor, int type )
        throws DuplicateName ;

    Current getPICurrent() ;

    void registerPolicyFactory( int type, PolicyFactory factory ) ;

    int allocateServerRequestId() ;
}
