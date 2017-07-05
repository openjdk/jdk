/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.portable.Delegate;
import org.omg.PortableInterceptor.LOCATION_FORWARD;
import org.omg.PortableInterceptor.SUCCESSFUL;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import org.omg.PortableInterceptor.TRANSPORT_RETRY;
import org.omg.PortableInterceptor.USER_EXCEPTION;
import org.omg.PortableInterceptor.ClientRequestInfo;
import org.omg.PortableInterceptor.ClientRequestInterceptor;
import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.IORInterceptor;
import org.omg.PortableInterceptor.IORInterceptor_3_0;
import org.omg.PortableInterceptor.ServerRequestInfo;
import org.omg.PortableInterceptor.ServerRequestInterceptor;
import org.omg.PortableInterceptor.ObjectReferenceTemplate;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.impl.orbutil.ORBUtility;

/**
 * Handles invocation of interceptors.  Has specific knowledge of how to
 * invoke IOR, ClientRequest, and ServerRequest interceptors.
 * Makes use of the InterceptorList to retrieve the list of interceptors to
 * be invoked.  Most methods in this class are package scope so that they
 * may only be called from the PIHandlerImpl.
 */
public class InterceptorInvoker {

    // The ORB
    private ORB orb;

    // The list of interceptors to be invoked
    private InterceptorList interceptorList;

    // True if interceptors are to be invoked, or false if not
    // Note: This is a global enable/disable flag, whereas the enable flag
    // in the RequestInfoStack in PIHandlerImpl is only for a particular Thread.
    private boolean enabled = false;

    // PICurrent variable.
    private PICurrent current;

    // NOTE: Be careful about adding additional attributes to this class.
    // Multiple threads may be calling methods on this invoker at the same
    // time.

    /**
     * Creates a new Interceptor Invoker.  Constructor is package scope so
     * only the ORB can create it.  The invoker is initially disabled, and
     * must be explicitly enabled using setEnabled().
     */
    InterceptorInvoker( ORB orb, InterceptorList interceptorList,
                        PICurrent piCurrent )
    {
        this.orb = orb;
        this.interceptorList = interceptorList;
        this.enabled = false;
        this.current = piCurrent;
    }

    /**
     * Enables or disables the interceptor invoker
     */
    void setEnabled( boolean enabled ) {
        this.enabled = enabled;
    }

    /*
     **********************************************************************
     * IOR Interceptor invocation
     **********************************************************************/

    /**
     * Called when a new POA is created.
     *
     * @param oa The Object Adapter associated with the IOR interceptor.
     */
    void objectAdapterCreated( ObjectAdapter oa ) {
        // If invocation is not yet enabled, don't do anything.
        if( enabled ) {
            // Create IORInfo object to pass to IORInterceptors:
            IORInfoImpl info = new IORInfoImpl( oa );

            // Call each IORInterceptor:
            IORInterceptor[] iorInterceptors =
                (IORInterceptor[])interceptorList.getInterceptors(
                InterceptorList.INTERCEPTOR_TYPE_IOR );
            int size = iorInterceptors.length;

            // Implementation note:
            // This loop counts backwards for greater efficiency.
            // Benchmarks have shown that counting down is more efficient
            // than counting up in Java for loops, as a compare to zero is
            // faster than a subtract and compare to zero.  In this case,
            // it doesn't really matter much, but it's simply a force of habit.

            for( int i = (size - 1); i >= 0; i-- ) {
                IORInterceptor interceptor = iorInterceptors[i];
                try {
                    interceptor.establish_components( info );
                }
                catch( Exception e ) {
                    // as per PI spec (orbos/99-12-02 sec 7.2.1), if
                    // establish_components throws an exception, ignore it.
                }
            }

            // Change the state so that only template operations are valid
            info.makeStateEstablished() ;

            for( int i = (size - 1); i >= 0; i-- ) {
                IORInterceptor interceptor = iorInterceptors[i];
                if (interceptor instanceof IORInterceptor_3_0) {
                    IORInterceptor_3_0 interceptor30 = (IORInterceptor_3_0)interceptor ;
                    // Note that exceptions here are NOT ignored, as per the
                    // ORT spec (orbos/01-01-04)
                    interceptor30.components_established( info );
                }
            }

            // Change the state so that no operations are valid,
            // in case a reference to info escapes this scope.
            // This also completes the actions associated with the
            // template interceptors on this POA.
            info.makeStateDone() ;
        }
    }

    void adapterManagerStateChanged( int managerId, short newState )
    {
        if (enabled) {
            IORInterceptor[] interceptors =
                (IORInterceptor[])interceptorList.getInterceptors(
                InterceptorList.INTERCEPTOR_TYPE_IOR );
            int size = interceptors.length;

            for( int i = (size - 1); i >= 0; i-- ) {
                try {
                    IORInterceptor interceptor = interceptors[i];
                    if (interceptor instanceof IORInterceptor_3_0) {
                        IORInterceptor_3_0 interceptor30 = (IORInterceptor_3_0)interceptor ;
                        interceptor30.adapter_manager_state_changed( managerId,
                            newState );
                    }
                } catch (Exception exc) {
                    // No-op: ignore exception in this case
                }
            }
        }
    }

    void adapterStateChanged( ObjectReferenceTemplate[] templates,
        short newState )
    {
        if (enabled) {
            IORInterceptor[] interceptors =
                (IORInterceptor[])interceptorList.getInterceptors(
                InterceptorList.INTERCEPTOR_TYPE_IOR );
            int size = interceptors.length;

            for( int i = (size - 1); i >= 0; i-- ) {
                try {
                    IORInterceptor interceptor = interceptors[i];
                    if (interceptor instanceof IORInterceptor_3_0) {
                        IORInterceptor_3_0 interceptor30 = (IORInterceptor_3_0)interceptor ;
                        interceptor30.adapter_state_changed( templates, newState );
                    }
                } catch (Exception exc) {
                    // No-op: ignore exception in this case
                }
            }
        }
    }

    /*
     **********************************************************************
     * Client Interceptor invocation
     **********************************************************************/

    /**
     * Invokes either send_request, or send_poll, depending on the value
     * of info.getStartingPointCall()
     */
    void invokeClientInterceptorStartingPoint( ClientRequestInfoImpl info ) {
        // If invocation is not yet enabled, don't do anything.
        if( enabled ) {
            try {
                // Make a a fresh slot table available to TSC in case
                // interceptors need to make out calls.
                // Client's TSC is now RSC via RequestInfo.
                current.pushSlotTable( );
                info.setPICurrentPushed( true );
                info.setCurrentExecutionPoint( info.EXECUTION_POINT_STARTING );

                // Get all ClientRequestInterceptors:
                ClientRequestInterceptor[] clientInterceptors =
                    (ClientRequestInterceptor[])interceptorList.
                    getInterceptors( InterceptorList.INTERCEPTOR_TYPE_CLIENT );
                int size = clientInterceptors.length;

                // We will assume that all interceptors returned successfully,
                // and adjust the flowStackIndex to the appropriate value if
                // we later discover otherwise.
                int flowStackIndex = size;
                boolean continueProcessing = true;

                // Determine whether we are calling send_request or send_poll:
                // (This is currently commented out because our ORB does not
                // yet support the Messaging specification, so send_poll will
                // never occur.  Once we have implemented messaging, this may
                // be uncommented.)
                // int startingPointCall = info.getStartingPointCall();
                for( int i = 0; continueProcessing && (i < size); i++ ) {
                    try {
                        clientInterceptors[i].send_request( info );

                        // Again, it is not necessary for a switch here, since
                        // there is only one starting point call type (see
                        // above comment).

                        //switch( startingPointCall ) {
                        //case ClientRequestInfoImpl.CALL_SEND_REQUEST:
                            //clientInterceptors[i].send_request( info );
                            //break;
                        //case ClientRequestInfoImpl.CALL_SEND_POLL:
                            //clientInterceptors[i].send_poll( info );
                            //break;
                        //}

                    }
                    catch( ForwardRequest e ) {
                        // as per PI spec (orbos/99-12-02 sec 5.2.1.), if
                        // interception point throws a ForwardRequest,
                        // no other Interceptors' send_request operations are
                        // called.
                        flowStackIndex = i;
                        info.setForwardRequest( e );
                        info.setEndingPointCall(
                            ClientRequestInfoImpl.CALL_RECEIVE_OTHER );
                        info.setReplyStatus( LOCATION_FORWARD.value );

                        updateClientRequestDispatcherForward( info );

                        // For some reason, using break here causes the VM on
                        // NT to lose track of the value of flowStackIndex
                        // after exiting the for loop.  I changed this to
                        // check a boolean value instead and it seems to work
                        // fine.
                        continueProcessing = false;
                    }
                    catch( SystemException e ) {
                        // as per PI spec (orbos/99-12-02 sec 5.2.1.), if
                        // interception point throws a SystemException,
                        // no other Interceptors' send_request operations are
                        // called.
                        flowStackIndex = i;
                        info.setEndingPointCall(
                            ClientRequestInfoImpl.CALL_RECEIVE_EXCEPTION );
                        info.setReplyStatus( SYSTEM_EXCEPTION.value );
                        info.setException( e );

                        // For some reason, using break here causes the VM on
                        // NT to lose track of the value of flowStackIndex
                        // after exiting the for loop.  I changed this to
                        // check a boolean value instead and it seems to
                        // work fine.
                        continueProcessing = false;
                    }
                }

                // Remember where we left off in the flow stack:
                info.setFlowStackIndex( flowStackIndex );
            }
            finally {
                // Make the SlotTable fresh for the next interception point.
                current.resetSlotTable( );
            }
        } // end enabled check
    }

    /**
     * Invokes either receive_reply, receive_exception, or receive_other,
     * depending on the value of info.getEndingPointCall()
     */
    void invokeClientInterceptorEndingPoint( ClientRequestInfoImpl info ) {
        // If invocation is not yet enabled, don't do anything.
        if( enabled ) {
            try {
                // NOTE: It is assumed someplace else prepared a
                // fresh TSC slot table.

                info.setCurrentExecutionPoint( info.EXECUTION_POINT_ENDING );

                // Get all ClientRequestInterceptors:
                ClientRequestInterceptor[] clientInterceptors =
                    (ClientRequestInterceptor[])interceptorList.
                    getInterceptors( InterceptorList.INTERCEPTOR_TYPE_CLIENT );
                int flowStackIndex = info.getFlowStackIndex();

                // Determine whether we are calling receive_reply,
                // receive_exception, or receive_other:
                int endingPointCall = info.getEndingPointCall();

                // If we would be calling RECEIVE_REPLY, but this is a
                // one-way call, override this and call receive_other:
                if( ( endingPointCall ==
                      ClientRequestInfoImpl.CALL_RECEIVE_REPLY ) &&
                    info.getIsOneWay() )
                {
                    endingPointCall = ClientRequestInfoImpl.CALL_RECEIVE_OTHER;
                    info.setEndingPointCall( endingPointCall );
                }

                // Only step through the interceptors whose starting points
                // have successfully returned.
                // Unlike the previous loop, this one counts backwards for a
                // reason - we must execute these in the reverse order of the
                // starting points.
                for( int i = (flowStackIndex - 1); i >= 0; i-- ) {

                    try {
                        switch( endingPointCall ) {
                        case ClientRequestInfoImpl.CALL_RECEIVE_REPLY:
                            clientInterceptors[i].receive_reply( info );
                            break;
                        case ClientRequestInfoImpl.CALL_RECEIVE_EXCEPTION:
                            clientInterceptors[i].receive_exception( info );
                            break;
                        case ClientRequestInfoImpl.CALL_RECEIVE_OTHER:
                            clientInterceptors[i].receive_other( info );
                            break;
                        }
                    }
                    catch( ForwardRequest e ) {

                        // as per PI spec (orbos/99-12-02 sec 5.2.1.), if
                        // interception point throws a ForwardException,
                        // ending point call changes to receive_other.
                        endingPointCall =
                            ClientRequestInfoImpl.CALL_RECEIVE_OTHER;
                        info.setEndingPointCall( endingPointCall );
                        info.setReplyStatus( LOCATION_FORWARD.value );
                        info.setForwardRequest( e );
                        updateClientRequestDispatcherForward( info );
                    }
                    catch( SystemException e ) {

                        // as per PI spec (orbos/99-12-02 sec 5.2.1.), if
                        // interception point throws a SystemException,
                        // ending point call changes to receive_exception.
                        endingPointCall =
                            ClientRequestInfoImpl.CALL_RECEIVE_EXCEPTION;
                        info.setEndingPointCall( endingPointCall );
                        info.setReplyStatus( SYSTEM_EXCEPTION.value );
                        info.setException( e );
                    }
                }
            }
            finally {
                // See doc for setPICurrentPushed as to why this is necessary.
                // Check info for null in case errors happen before initiate.
                if (info != null && info.isPICurrentPushed()) {
                    current.popSlotTable( );
                    // After the pop, original client's TSC slot table
                    // remains avaiable via PICurrent.
                }
            }
        } // end enabled check
    }

    /*
     **********************************************************************
     * Server Interceptor invocation
     **********************************************************************/

    /**
     * Invokes receive_request_service_context interception points.
     */
    void invokeServerInterceptorStartingPoint( ServerRequestInfoImpl info ) {
        // If invocation is not yet enabled, don't do anything.
        if( enabled ) {
            try {
                // Make a fresh slot table for RSC.
                current.pushSlotTable();
                info.setSlotTable(current.getSlotTable());

                // Make a fresh slot table for TSC in case
                // interceptors need to make out calls.
                current.pushSlotTable( );

                info.setCurrentExecutionPoint( info.EXECUTION_POINT_STARTING );

                // Get all ServerRequestInterceptors:
                ServerRequestInterceptor[] serverInterceptors =
                    (ServerRequestInterceptor[])interceptorList.
                    getInterceptors( InterceptorList.INTERCEPTOR_TYPE_SERVER );
                int size = serverInterceptors.length;

                // We will assume that all interceptors returned successfully,
                // and adjust the flowStackIndex to the appropriate value if
                // we later discover otherwise.
                int flowStackIndex = size;
                boolean continueProcessing = true;

                // Currently, there is only one server-side starting point
                // interceptor called receive_request_service_contexts.
                for( int i = 0; continueProcessing && (i < size); i++ ) {

                    try {
                        serverInterceptors[i].
                            receive_request_service_contexts( info );
                    }
                    catch( ForwardRequest e ) {
                        // as per PI spec (orbos/99-12-02 sec 5.3.1.), if
                        // interception point throws a ForwardRequest,
                        // no other Interceptors' starting points are
                        // called and send_other is called.
                        flowStackIndex = i;
                        info.setForwardRequest( e );
                        info.setIntermediatePointCall(
                            ServerRequestInfoImpl.CALL_INTERMEDIATE_NONE );
                        info.setEndingPointCall(
                            ServerRequestInfoImpl.CALL_SEND_OTHER );
                        info.setReplyStatus( LOCATION_FORWARD.value );

                        // For some reason, using break here causes the VM on
                        // NT to lose track of the value of flowStackIndex
                        // after exiting the for loop.  I changed this to
                        // check a boolean value instead and it seems to work
                        // fine.
                        continueProcessing = false;
                    }
                    catch( SystemException e ) {

                        // as per PI spec (orbos/99-12-02 sec 5.3.1.), if
                        // interception point throws a SystemException,
                        // no other Interceptors' starting points are
                        // called.
                        flowStackIndex = i;
                        info.setException( e );
                        info.setIntermediatePointCall(
                            ServerRequestInfoImpl.CALL_INTERMEDIATE_NONE );
                        info.setEndingPointCall(
                            ServerRequestInfoImpl.CALL_SEND_EXCEPTION );
                        info.setReplyStatus( SYSTEM_EXCEPTION.value );

                        // For some reason, using break here causes the VM on
                        // NT to lose track of the value of flowStackIndex
                        // after exiting the for loop.  I changed this to
                        // check a boolean value instead and it seems to
                        // work fine.
                        continueProcessing = false;
                    }

                }

                // Remember where we left off in the flow stack:
                info.setFlowStackIndex( flowStackIndex );
            }
            finally {
                // The remaining points, ServantManager and Servant
                // all run in the same logical thread.
                current.popSlotTable( );
                // Now TSC and RSC are equivalent.
            }
        } // end enabled check
    }

    /**
     * Invokes receive_request interception points
     */
    void invokeServerInterceptorIntermediatePoint(
        ServerRequestInfoImpl info )
    {
        int intermediatePointCall = info.getIntermediatePointCall();
        // If invocation is not yet enabled, don't do anything.
        if( enabled && ( intermediatePointCall !=
                         ServerRequestInfoImpl.CALL_INTERMEDIATE_NONE ) )
        {
            // NOTE: do not touch the slotStack.  The RSC and TSC are
            // equivalent at this point.

            info.setCurrentExecutionPoint( info.EXECUTION_POINT_INTERMEDIATE );

            // Get all ServerRequestInterceptors:
            ServerRequestInterceptor[] serverInterceptors =
                (ServerRequestInterceptor[])
                interceptorList.getInterceptors(
                InterceptorList.INTERCEPTOR_TYPE_SERVER );
            int size = serverInterceptors.length;

            // Currently, there is only one server-side intermediate point
            // interceptor called receive_request.
            for( int i = 0; i < size; i++ ) {

                try {
                    serverInterceptors[i].receive_request( info );
                }
                catch( ForwardRequest e ) {

                    // as per PI spec (orbos/99-12-02 sec 5.3.1.), if
                    // interception point throws a ForwardRequest,
                    // no other Interceptors' intermediate points are
                    // called and send_other is called.
                    info.setForwardRequest( e );
                    info.setEndingPointCall(
                        ServerRequestInfoImpl.CALL_SEND_OTHER );
                    info.setReplyStatus( LOCATION_FORWARD.value );
                    break;
                }
                catch( SystemException e ) {

                    // as per PI spec (orbos/99-12-02 sec 5.3.1.), if
                    // interception point throws a SystemException,
                    // no other Interceptors' starting points are
                    // called.
                    info.setException( e );
                    info.setEndingPointCall(
                        ServerRequestInfoImpl.CALL_SEND_EXCEPTION );
                    info.setReplyStatus( SYSTEM_EXCEPTION.value );
                    break;
                }
            }
        } // end enabled check
    }

    /**
     * Invokes either send_reply, send_exception, or send_other,
     * depending on the value of info.getEndingPointCall()
     */
    void invokeServerInterceptorEndingPoint( ServerRequestInfoImpl info ) {
        // If invocation is not yet enabled, don't do anything.
        if( enabled ) {
            try {
                // NOTE: do not touch the slotStack.  The RSC and TSC are
                // equivalent at this point.

                // REVISIT: This is moved out to PIHandlerImpl until dispatch
                // path is rearchitected.  It must be there so that
                // it always gets executed so if an interceptor raises
                // an exception any service contexts added in earlier points
                // this point get put in the exception reply (via the SC Q).
                //info.setCurrentExecutionPoint( info.EXECUTION_POINT_ENDING );

                // Get all ServerRequestInterceptors:
                ServerRequestInterceptor[] serverInterceptors =
                    (ServerRequestInterceptor[])interceptorList.
                    getInterceptors( InterceptorList.INTERCEPTOR_TYPE_SERVER );
                int flowStackIndex = info.getFlowStackIndex();

                // Determine whether we are calling
                // send_exception, or send_other:
                int endingPointCall = info.getEndingPointCall();

                // Only step through the interceptors whose starting points
                // have successfully returned.
                for( int i = (flowStackIndex - 1); i >= 0; i-- ) {
                    try {
                        switch( endingPointCall ) {
                        case ServerRequestInfoImpl.CALL_SEND_REPLY:
                            serverInterceptors[i].send_reply( info );
                            break;
                        case ServerRequestInfoImpl.CALL_SEND_EXCEPTION:
                            serverInterceptors[i].send_exception( info );
                            break;
                        case ServerRequestInfoImpl.CALL_SEND_OTHER:
                            serverInterceptors[i].send_other( info );
                            break;
                        }
                    }
                    catch( ForwardRequest e ) {
                        // as per PI spec (orbos/99-12-02 sec 5.3.1.), if
                        // interception point throws a ForwardException,
                        // ending point call changes to receive_other.
                        endingPointCall =
                            ServerRequestInfoImpl.CALL_SEND_OTHER;
                        info.setEndingPointCall( endingPointCall );
                        info.setForwardRequest( e );
                        info.setReplyStatus( LOCATION_FORWARD.value );
                        info.setForwardRequestRaisedInEnding();
                    }
                    catch( SystemException e ) {
                        // as per PI spec (orbos/99-12-02 sec 5.3.1.), if
                        // interception point throws a SystemException,
                        // ending point call changes to send_exception.
                        endingPointCall =
                            ServerRequestInfoImpl.CALL_SEND_EXCEPTION;
                        info.setEndingPointCall( endingPointCall );
                        info.setException( e );
                        info.setReplyStatus( SYSTEM_EXCEPTION.value );
                    }
                }

                // Remember that all interceptors' starting and ending points
                // have already been executed so we need not do anything.
                info.setAlreadyExecuted( true );
            }
            finally {
                // Get rid of the Server side RSC.
                current.popSlotTable();
            }
        } // end enabled check
    }

    /*
     **********************************************************************
     * Private utility methods
     **********************************************************************/

    /**
     * Update the client delegate in the event of a ForwardRequest, given the
     * information in the passed-in info object.
     */
    private void updateClientRequestDispatcherForward(
        ClientRequestInfoImpl info )
    {
        ForwardRequest forwardRequest = info.getForwardRequestException();

        // ForwardRequest may be null if the forwarded IOR is set internal
        // to the ClientRequestDispatcher rather than explicitly through Portable
        // Interceptors.  In this case, we need not update the client
        // delegate ForwardRequest object.
        if( forwardRequest != null ) {
            org.omg.CORBA.Object object = forwardRequest.forward;

            // Convert the forward object into an IOR:
            IOR ior = ORBUtility.getIOR( object ) ;
            info.setLocatedIOR( ior );
        }
    }

}
