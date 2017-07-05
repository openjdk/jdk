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
package com.sun.corba.se.impl.interceptors;

import java.util.*;
import java.io.IOException;

import org.omg.CORBA.Any;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.BAD_POLICY;
import org.omg.CORBA.BAD_INV_ORDER;
import org.omg.CORBA.COMM_FAILURE;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.NVList;
import org.omg.CORBA.OBJECT_NOT_EXIST;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.UserException;
import org.omg.CORBA.UNKNOWN;

import org.omg.CORBA.portable.ApplicationException;
import org.omg.CORBA.portable.RemarshalException;

import org.omg.IOP.CodecFactory;

import org.omg.PortableInterceptor.ForwardRequest;
import org.omg.PortableInterceptor.Current;
import org.omg.PortableInterceptor.Interceptor;
import org.omg.PortableInterceptor.LOCATION_FORWARD;
import org.omg.PortableInterceptor.ORBInitializer;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ORBInitInfoPackage.DuplicateName;
import org.omg.PortableInterceptor.SUCCESSFUL;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import org.omg.PortableInterceptor.TRANSPORT_RETRY;
import org.omg.PortableInterceptor.USER_EXCEPTION;
import org.omg.PortableInterceptor.PolicyFactory;
import org.omg.PortableInterceptor.ObjectReferenceTemplate ;

import com.sun.corba.se.pept.encoding.OutputObject;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate;
import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orbutil.closure.ClosureFactory;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.protocol.ForwardException;
import com.sun.corba.se.spi.protocol.PIHandler;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.logging.InterceptorsSystemException;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.OMGSystemException;
import com.sun.corba.se.impl.corba.RequestImpl;
import com.sun.corba.se.impl.orbutil.ORBClassLoader;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.orbutil.StackImpl;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage;

/**
 * Provides portable interceptor functionality.
 */
public class PIHandlerImpl implements PIHandler
{
    // REVISIT - delete these after framework merging.
    boolean printPushPopEnabled = false;
    int pushLevel = 0;
    private void printPush()
    {
        if (! printPushPopEnabled) return;
        printSpaces(pushLevel);
        pushLevel++;
        System.out.println("PUSH");
    }
    private void printPop()
    {
        if (! printPushPopEnabled) return;
        pushLevel--;
        printSpaces(pushLevel);
        System.out.println("POP");
    }
    private void printSpaces(int n)
    {
        for (int i = 0; i < n; i++) {
            System.out.print(" ");
        }
    }

    private ORB orb ;
    InterceptorsSystemException wrapper ;
    ORBUtilSystemException orbutilWrapper ;
    OMGSystemException omgWrapper ;

    // A unique id used in ServerRequestInfo.
    // This does not correspond to the GIOP request id.
    private int serverRequestIdCounter = 0;

    // Stores the codec factory for producing codecs
    CodecFactory codecFactory = null;

    // The arguments passed to the application's main method.  May be null.
    // This is used for ORBInitializers and set from set_parameters.
    String[] arguments = null;

    // The list of portable interceptors, organized by type:
    private InterceptorList interceptorList;

    // Cached information for optimization - do we have any interceptors
    // registered of the given types?  Set during ORB initialization.
    private boolean hasIORInterceptors;
    private boolean hasClientInterceptors;  // temp always true
    private boolean hasServerInterceptors;

    // The class responsible for invoking interceptors
    private InterceptorInvoker interceptorInvoker;

    // There will be one PICurrent instantiated for every ORB.
    private PICurrent current;

    // This table contains a list of PolicyFactories registered using
    // ORBInitInfo.registerPolicyFactory() method.
    // Key for the table is PolicyType which is an Integer
    // Value is PolicyFactory.
    private HashMap policyFactoryTable;

    // Table to convert from a ReplyMessage.? to a PI replyStatus short.
    // Note that this table relies on the order and constants of
    // ReplyMessage not to change.
    private final static short REPLY_MESSAGE_TO_PI_REPLY_STATUS[] = {
        SUCCESSFUL.value,       // = ReplyMessage.NO_EXCEPTION
        USER_EXCEPTION.value,   // = ReplyMessage.USER_EXCEPTION
        SYSTEM_EXCEPTION.value, // = ReplyMessage.SYSTEM_EXCEPTION
        LOCATION_FORWARD.value, // = ReplyMessage.LOCATION_FORWARD
        LOCATION_FORWARD.value, // = ReplyMessage.LOCATION_FORWARD_PERM
        TRANSPORT_RETRY.value   // = ReplyMessage.NEEDS_ADDRESSING_MODE
    };

    // ThreadLocal containing a stack to store client request info objects
    // and a disable count.
    private ThreadLocal threadLocalClientRequestInfoStack =
        new ThreadLocal() {
            protected Object initialValue() {
                return new RequestInfoStack();
            }
        };

    // ThreadLocal containing the current server request info object.
    private ThreadLocal threadLocalServerRequestInfoStack =
        new ThreadLocal() {
            protected Object initialValue() {
                return new RequestInfoStack();
            }
        };

    // Class to contain all ThreadLocal data for ClientRequestInfo
    // maintenance.
    //
    // We use an ArrayList instead since it is not thread-safe.
    // RequestInfoStack is used quite frequently.
    private final class RequestInfoStack extends Stack {
        // Number of times a request has been made to disable interceptors.
        // When this reaches 0, interception hooks are disabled.  Any higher
        // value indicates they are enabled.
        // NOTE: The is only currently used on the client side.
        public int disableCount = 0;
    }

    public PIHandlerImpl( ORB orb, String[] args ) {
        this.orb = orb ;
        wrapper = InterceptorsSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        orbutilWrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        omgWrapper = OMGSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        arguments = args ;

        // Create codec factory:
        codecFactory = new CodecFactoryImpl( orb );

        // Create new interceptor list:
        interceptorList = new InterceptorList( wrapper );

        // Create a new PICurrent.
        current = new PICurrent( orb );

        // Create new interceptor invoker, initially disabled:
        interceptorInvoker = new InterceptorInvoker( orb, interceptorList,
                                                     current );

        // Register the PI current and Codec factory objects
        orb.getLocalResolver().register( ORBConstants.PI_CURRENT_NAME,
            ClosureFactory.makeConstant( current ) ) ;
        orb.getLocalResolver().register( ORBConstants.CODEC_FACTORY_NAME,
            ClosureFactory.makeConstant( codecFactory ) ) ;
    }

    public void initialize() {
        // If we have any orb initializers, make use of them:
        if( orb.getORBData().getORBInitializers() != null ) {
            // Create the ORBInitInfo object to pass to ORB intializers:
            ORBInitInfoImpl orbInitInfo = createORBInitInfo();

            // Make sure get_slot and set_slot are not called from within
            // ORB initializers:
            current.setORBInitializing( true );

            // Call pre_init on all ORB initializers:
            preInitORBInitializers( orbInitInfo );

            // Call post_init on all ORB initializers:
            postInitORBInitializers( orbInitInfo );

            // Proprietary: sort interceptors:
            interceptorList.sortInterceptors();

            // Re-enable get_slot and set_slot to be called from within
            // ORB initializers:
            current.setORBInitializing( false );

            // Ensure nobody makes any more calls on this object.
            orbInitInfo.setStage( ORBInitInfoImpl.STAGE_CLOSED );

            // Set cached flags indicating whether we have interceptors
            // registered of a given type.
            hasIORInterceptors = interceptorList.hasInterceptorsOfType(
                InterceptorList.INTERCEPTOR_TYPE_IOR );
            // XXX This must always be true, so that using the new generic
            // RPC framework can pass info between the PI stack and the
            // framework invocation stack.  Temporary until Harold fixes
            // this.  Note that this must never be true until after the
            // ORBInitializer instances complete executing.
            //hasClientInterceptors = interceptorList.hasInterceptorsOfType(
                //InterceptorList.INTERCEPTOR_TYPE_CLIENT );
            hasClientInterceptors = true;
            hasServerInterceptors = interceptorList.hasInterceptorsOfType(
                InterceptorList.INTERCEPTOR_TYPE_SERVER );

            // Enable interceptor invoker (not necessary if no interceptors
            // are registered).  This should be the last stage of ORB
            // initialization.
            interceptorInvoker.setEnabled( true );
        }
    }

    /**
     *  ptc/00-08-06 p 205: "When an application calls ORB::destroy, the ORB
     *  1) waits for all requests in progress to complete
     *  2) calls the Interceptor::destroy operation for each interceptor
     *  3) completes destruction of the ORB"
     *
     * This must be called at the end of ORB.destroy.  Note that this is not
     * part of the PIHandler interface, since ORBImpl implements the ORB interface.
     */
    public void destroyInterceptors() {
        interceptorList.destroyAll();
    }

    public void objectAdapterCreated( ObjectAdapter oa )
    {
        if (!hasIORInterceptors)
            return ;

        interceptorInvoker.objectAdapterCreated( oa ) ;
    }

    public void adapterManagerStateChanged( int managerId,
        short newState )
    {
        if (!hasIORInterceptors)
            return ;

        interceptorInvoker.adapterManagerStateChanged( managerId, newState ) ;
    }

    public void adapterStateChanged( ObjectReferenceTemplate[]
        templates, short newState )
    {
        if (!hasIORInterceptors)
            return ;

        interceptorInvoker.adapterStateChanged( templates, newState ) ;
    }

    /*
     *****************
     * Client PI hooks
     *****************/

    public void disableInterceptorsThisThread() {
        if( !hasClientInterceptors ) return;

        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalClientRequestInfoStack.get();
        infoStack.disableCount++;
    }

    public void enableInterceptorsThisThread() {
        if( !hasClientInterceptors ) return;

        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalClientRequestInfoStack.get();
        infoStack.disableCount--;
    }

    public void invokeClientPIStartingPoint()
        throws RemarshalException
    {
        if( !hasClientInterceptors ) return;
        if( !isClientPIEnabledForThisThread() ) return;

        // Invoke the starting interception points and record exception
        // and reply status info in the info object:
        ClientRequestInfoImpl info = peekClientRequestInfoImplStack();
        interceptorInvoker.invokeClientInterceptorStartingPoint( info );

        // Check reply status.  If we will not have another chance later
        // to invoke the client ending points, do it now.
        short replyStatus = info.getReplyStatus();
        if( (replyStatus == SYSTEM_EXCEPTION.value) ||
            (replyStatus == LOCATION_FORWARD.value) )
        {
            // Note: Transport retry cannot happen here since this happens
            // before the request hits the wire.

            Exception exception = invokeClientPIEndingPoint(
                convertPIReplyStatusToReplyMessage( replyStatus ),
                info.getException() );
            if( exception == null ) {
                // Do not throw anything.  Otherwise, it must be a
                // SystemException, UserException or RemarshalException.
            } if( exception instanceof SystemException ) {
                throw (SystemException)exception;
            } else if( exception instanceof RemarshalException ) {
                throw (RemarshalException)exception;
            } else if( (exception instanceof UserException) ||
                     (exception instanceof ApplicationException) ) {
                // It should not be possible for an interceptor to throw
                // a UserException.  By asserting instead of throwing the
                // UserException, we need not declare anything but
                // RemarshalException in the throws clause.
                throw wrapper.exceptionInvalid() ;
            }
        }
        else if( replyStatus != ClientRequestInfoImpl.UNINITIALIZED ) {
            throw wrapper.replyStatusNotInit() ;
        }
    }

    public Exception invokeClientPIEndingPoint(
        int replyStatus, Exception exception )
    {
        if( !hasClientInterceptors ) return exception;
        if( !isClientPIEnabledForThisThread() ) return exception;

        // Translate ReplyMessage.replyStatus into PI replyStatus:
        // Note: this is also an assertion to make sure a valid replyStatus
        // is passed in (IndexOutOfBoundsException will be thrown otherwise)
        short piReplyStatus = REPLY_MESSAGE_TO_PI_REPLY_STATUS[replyStatus];

        // Invoke the ending interception points and record exception
        // and reply status info in the info object:
        ClientRequestInfoImpl info = peekClientRequestInfoImplStack();
        info.setReplyStatus( piReplyStatus );
        info.setException( exception );
        interceptorInvoker.invokeClientInterceptorEndingPoint( info );
        piReplyStatus = info.getReplyStatus();

        // Check reply status:
        if( (piReplyStatus == LOCATION_FORWARD.value) ||
            (piReplyStatus == TRANSPORT_RETRY.value) )
        {
            // If this is a forward or a retry, reset and reuse
            // info object:
            info.reset();
            info.setRetryRequest( true );

            // ... and return a RemarshalException so the orb internals know
            exception = new RemarshalException();
        }
        else if( (piReplyStatus == SYSTEM_EXCEPTION.value) ||
                 (piReplyStatus == USER_EXCEPTION.value) )
        {
            exception = info.getException();
        }

        return exception;
    }

    public void initiateClientPIRequest( boolean diiRequest ) {
        if( !hasClientInterceptors ) return;
        if( !isClientPIEnabledForThisThread() ) return;

        // Get the most recent info object from the thread local
        // ClientRequestInfoImpl stack:
        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalClientRequestInfoStack.get();
        ClientRequestInfoImpl info = null;
        if( !infoStack.empty() ) info =
            (ClientRequestInfoImpl)infoStack.peek();

        if( !diiRequest && (info != null) && info.isDIIInitiate() ) {
            // In RequestImpl.doInvocation we already called
            // initiateClientPIRequest( true ), so ignore this initiate.
            info.setDIIInitiate( false );
        }
        else {
            // If there is no info object or if we are not retrying a request,
            // push a new ClientRequestInfoImpl on the stack:
            if( (info == null) || !info.getRetryRequest() ) {
                info = new ClientRequestInfoImpl( orb );
                infoStack.push( info );
                printPush();
                // Note: the entry count is automatically initialized to 0.
            }

            // Reset the retry request flag so that recursive calls will
            // push a new info object, and bump up entry count so we know
            // when to pop this info object:
            info.setRetryRequest( false );
            info.incrementEntryCount();

            // If this is a DII request, make sure we ignore the next initiate.
            if( diiRequest ) {
                info.setDIIInitiate( true );
            }
        }
    }

    public void cleanupClientPIRequest() {
        if( !hasClientInterceptors ) return;
        if( !isClientPIEnabledForThisThread() ) return;

        ClientRequestInfoImpl info = peekClientRequestInfoImplStack();

        // If the replyStatus has not yet been set, this is an indication
        // that the ORB threw an exception before we had a chance to
        // invoke the client interceptor ending points.
        //
        // _REVISIT_ We cannot handle any exceptions or ForwardRequests
        // flagged by the ending points here because there is no way
        // to gracefully handle this in any of the calling code.
        // This is a rare corner case, so we will ignore this for now.
        short replyStatus = info.getReplyStatus();
        if( replyStatus == info.UNINITIALIZED ) {
            invokeClientPIEndingPoint( ReplyMessage.SYSTEM_EXCEPTION,
                wrapper.unknownRequestInvoke(
                    CompletionStatus.COMPLETED_MAYBE ) ) ;
        }

        // Decrement entry count, and if it is zero, pop it from the stack.
        info.decrementEntryCount();
        if( info.getEntryCount() == 0 ) {
            RequestInfoStack infoStack =
                (RequestInfoStack)threadLocalClientRequestInfoStack.get();
            infoStack.pop();
            printPop();
        }
    }

    public void setClientPIInfo(CorbaMessageMediator messageMediator)
    {
        if( !hasClientInterceptors ) return;
        if( !isClientPIEnabledForThisThread() ) return;

        peekClientRequestInfoImplStack().setInfo(messageMediator);
    }

    public void setClientPIInfo( RequestImpl requestImpl ) {
        if( !hasClientInterceptors ) return;
        if( !isClientPIEnabledForThisThread() ) return;

        peekClientRequestInfoImplStack().setDIIRequest( requestImpl );
    }

    /*
     *****************
     * Server PI hooks
     *****************/

    public void invokeServerPIStartingPoint()
    {
        if( !hasServerInterceptors ) return;

        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();
        interceptorInvoker.invokeServerInterceptorStartingPoint( info );

        // Handle SystemException or ForwardRequest:
        serverPIHandleExceptions( info );
    }

    public void invokeServerPIIntermediatePoint()
    {
        if( !hasServerInterceptors ) return;

        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();
        interceptorInvoker.invokeServerInterceptorIntermediatePoint( info );

        // Clear servant from info object so that the user has control over
        // its lifetime:
        info.releaseServant();

        // Handle SystemException or ForwardRequest:
        serverPIHandleExceptions( info );
    }

    public void invokeServerPIEndingPoint( ReplyMessage replyMessage )
    {
        if( !hasServerInterceptors ) return;
        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();

        // REVISIT: This needs to be done "early" for the following workaround.
        info.setReplyMessage( replyMessage );

        // REVISIT: This was done inside of invokeServerInterceptorEndingPoint
        // but needs to be here for now.  See comment in that method for why.
        info.setCurrentExecutionPoint( info.EXECUTION_POINT_ENDING );

        // It is possible we might have entered this method more than
        // once (e.g. if an ending point threw a SystemException, then
        // a new ServerResponseImpl is created).
        if( !info.getAlreadyExecuted() ) {
            int replyStatus = replyMessage.getReplyStatus();

            // Translate ReplyMessage.replyStatus into PI replyStatus:
            // Note: this is also an assertion to make sure a valid
            // replyStatus is passed in (IndexOutOfBoundsException will be
            // thrown otherwise)
            short piReplyStatus =
                REPLY_MESSAGE_TO_PI_REPLY_STATUS[replyStatus];

            // Make forwarded IOR available to interceptors, if applicable:
            if( ( piReplyStatus == LOCATION_FORWARD.value ) ||
                ( piReplyStatus == TRANSPORT_RETRY.value ) )
            {
                info.setForwardRequest( replyMessage.getIOR() );
            }

            // REVISIT: Do early above for now.
            // Make reply message available to interceptors:
            //info.setReplyMessage( replyMessage );

            // Remember exception so we can tell if an interceptor changed it.
            Exception prevException = info.getException();

            // _REVISIT_ We do not have access to the User Exception at
            // this point, so treat it as an UNKNOWN for now.
            // Note that if this is a DSI call, we do have the user exception.
            if( !info.isDynamic() &&
                (piReplyStatus == USER_EXCEPTION.value) )
            {
                info.setException( omgWrapper.unknownUserException(
                    CompletionStatus.COMPLETED_MAYBE ) ) ;
            }

            // Invoke the ending interception points:
            info.setReplyStatus( piReplyStatus );
            interceptorInvoker.invokeServerInterceptorEndingPoint( info );
            short newPIReplyStatus = info.getReplyStatus();
            Exception newException = info.getException();

            // Check reply status.  If an interceptor threw a SystemException
            // and it is different than the one that we came in with,
            // rethrow it so the proper response can be constructed:
            if( ( newPIReplyStatus == SYSTEM_EXCEPTION.value ) &&
                ( newException != prevException ) )
            {
                throw (SystemException)newException;
            }

            // If we are to forward the location:
            if( newPIReplyStatus == LOCATION_FORWARD.value ) {
                if( piReplyStatus != LOCATION_FORWARD.value ) {
                    // Treat a ForwardRequest as a ForwardException.
                    IOR ior = info.getForwardRequestIOR();
                    throw new ForwardException( orb, ior ) ;
                }
                else if( info.isForwardRequestRaisedInEnding() ) {
                    // Treat a ForwardRequest by changing the IOR.
                    replyMessage.setIOR( info.getForwardRequestIOR() );
                }
            }
        }
    }

    public void setServerPIInfo( Exception exception ) {
        if( !hasServerInterceptors ) return;

        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();
        info.setException( exception );
    }

    public void setServerPIInfo( NVList arguments )
    {
        if( !hasServerInterceptors ) return;

        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();
        info.setDSIArguments( arguments );
    }

    public void setServerPIExceptionInfo( Any exception )
    {
        if( !hasServerInterceptors ) return;

        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();
        info.setDSIException( exception );
    }

    public void setServerPIInfo( Any result )
    {
        if( !hasServerInterceptors ) return;

        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();
        info.setDSIResult( result );
    }

    public void initializeServerPIInfo( CorbaMessageMediator request,
        ObjectAdapter oa, byte[] objectId, ObjectKeyTemplate oktemp )
    {
        if( !hasServerInterceptors ) return;

        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalServerRequestInfoStack.get();
        ServerRequestInfoImpl info = new ServerRequestInfoImpl( orb );
        infoStack.push( info );
        printPush();

        // Notify request object that once response is constructed, make
        // sure we execute ending points.
        request.setExecutePIInResponseConstructor( true );

        info.setInfo( request, oa, objectId, oktemp );
    }

    public void setServerPIInfo( java.lang.Object servant,
                                          String targetMostDerivedInterface )
    {
        if( !hasServerInterceptors ) return;

        ServerRequestInfoImpl info = peekServerRequestInfoImplStack();
        info.setInfo( servant, targetMostDerivedInterface );
    }

    public void cleanupServerPIRequest() {
        if( !hasServerInterceptors ) return;

        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalServerRequestInfoStack.get();
        infoStack.pop();
        printPop();
    }

    /*
     **********************************************************************
     *  The following methods are private utility methods.
     ************************************************************************/

    /**
     * Handles exceptions for the starting and intermediate points for
     * server request interceptors.  This is common code that has been
     * factored out into this utility method.
     * <p>
     * This method will NOT work for ending points.
     */
    private void serverPIHandleExceptions( ServerRequestInfoImpl info )
    {
        int endingPointCall = info.getEndingPointCall();
        if(endingPointCall == ServerRequestInfoImpl.CALL_SEND_EXCEPTION) {
            // If a system exception was thrown, throw it to caller:
            throw (SystemException)info.getException();
        }
        else if( (endingPointCall == ServerRequestInfoImpl.CALL_SEND_OTHER) &&
                 (info.getForwardRequestException() != null) )
        {
            // If an interceptor throws a forward request, convert it
            // into a ForwardException for easier handling:
            IOR ior = info.getForwardRequestIOR();
            throw new ForwardException( orb, ior );
        }
    }

    /**
     * Utility method to convert a PI reply status short to a ReplyMessage
     * constant.  This is a reverse lookup on the table defined in
     * REPLY_MESSAGE_TO_PI_REPLY_STATUS.  The reverse lookup need not be
     * performed as quickly since it is only executed in exception
     * conditions.
     */
    private int convertPIReplyStatusToReplyMessage( short replyStatus ) {
        int result = 0;
        for( int i = 0; i < REPLY_MESSAGE_TO_PI_REPLY_STATUS.length; i++ ) {
            if( REPLY_MESSAGE_TO_PI_REPLY_STATUS[i] == replyStatus ) {
                result = i;
                break;
            }
        }
        return result;
    }

    /**
     * Convenience method to get the ClientRequestInfoImpl object off the
     * top of the ThreadLocal stack.  Throws an INTERNAL exception if
     * the Info stack is empty.
     */
    private ClientRequestInfoImpl peekClientRequestInfoImplStack() {
        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalClientRequestInfoStack.get();
        ClientRequestInfoImpl info = null;
        if( !infoStack.empty() ) {
            info = (ClientRequestInfoImpl)infoStack.peek();
        } else {
            throw wrapper.clientInfoStackNull() ;
        }

        return info;
    }

    /**
     * Convenience method to get the ServerRequestInfoImpl object off the
     * top of the ThreadLocal stack.  Returns null if there are none.
     */
    private ServerRequestInfoImpl peekServerRequestInfoImplStack() {
        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalServerRequestInfoStack.get();
        ServerRequestInfoImpl info = null;

        if( !infoStack.empty() ) {
            info = (ServerRequestInfoImpl)infoStack.peek();
        } else {
            throw wrapper.serverInfoStackNull() ;
        }

        return info;
    }

    /**
     * Convenience method to determine whether Client PI is enabled
     * for requests on this thread.
     */
    private boolean isClientPIEnabledForThisThread() {
        RequestInfoStack infoStack =
            (RequestInfoStack)threadLocalClientRequestInfoStack.get();
        return (infoStack.disableCount == 0);
    }

    /**
     * Call pre_init on all ORB initializers
     */
    private void preInitORBInitializers( ORBInitInfoImpl info ) {

        // Inform ORBInitInfo we are in pre_init stage
        info.setStage( ORBInitInfoImpl.STAGE_PRE_INIT );

        // Step through each initializer instantiation and call its
        // pre_init.  Ignore any exceptions.
        for( int i = 0; i < orb.getORBData().getORBInitializers().length;
            i++ ) {
            ORBInitializer init = orb.getORBData().getORBInitializers()[i];
            if( init != null ) {
                try {
                    init.pre_init( info );
                }
                catch( Exception e ) {
                    // As per orbos/99-12-02, section 9.3.1.2, "If there are
                    // any exceptions, the ORB shall ignore them and proceed."
                }
            }
        }
    }

    /**
     * Call post_init on all ORB initializers
     */
    private void postInitORBInitializers( ORBInitInfoImpl info ) {

        // Inform ORBInitInfo we are in post_init stage
        info.setStage( ORBInitInfoImpl.STAGE_POST_INIT );

        // Step through each initializer instantiation and call its post_init.
        // Ignore any exceptions.
        for( int i = 0; i < orb.getORBData().getORBInitializers().length;
            i++ ) {
            ORBInitializer init = orb.getORBData().getORBInitializers()[i];
            if( init != null ) {
                try {
                    init.post_init( info );
                }
                catch( Exception e ) {
                    // As per orbos/99-12-02, section 9.3.1.2, "If there are
                    // any exceptions, the ORB shall ignore them and proceed."
                }
            }
        }
    }

    /**
     * Creates the ORBInitInfo object to be passed to ORB intializers'
     * pre_init and post_init methods
     */
    private ORBInitInfoImpl createORBInitInfo() {
        ORBInitInfoImpl result = null;

        // arguments comes from set_parameters.  May be null.

        // _REVISIT_ The spec does not specify which ID this is to be.
        // We currently get this from the corba.ORB, which reads it from
        // the ORB_ID_PROPERTY property.
        String orbId = orb.getORBData().getORBId() ;

        result = new ORBInitInfoImpl( orb, arguments, orbId, codecFactory );

        return result;
    }

    /**
     * Called by ORBInitInfo when an interceptor needs to be registered.
     * The type is one of:
     * <ul>
     *   <li>INTERCEPTOR_TYPE_CLIENT - ClientRequestInterceptor
     *   <li>INTERCEPTOR_TYPE_SERVER - ServerRequestInterceptor
     *   <li>INTERCEPTOR_TYPE_IOR - IORInterceptor
     * </ul>
     *
     * @exception DuplicateName Thrown if an interceptor of the given
     *     name already exists for the given type.
     */
    public void register_interceptor( Interceptor interceptor, int type )
        throws DuplicateName
    {
        // We will assume interceptor is not null, since it is called
        // internally.
        if( (type >= InterceptorList.NUM_INTERCEPTOR_TYPES) || (type < 0) ) {
            throw wrapper.typeOutOfRange( new Integer( type ) ) ;
        }

        String interceptorName = interceptor.name();

        if( interceptorName == null ) {
            throw wrapper.nameNull() ;
        }

        // Register with interceptor list:
        interceptorList.register_interceptor( interceptor, type );
    }

    public Current getPICurrent( ) {
        return current;
    }

    /**
     * Called when an invalid null parameter was passed.  Throws a
     * BAD_PARAM with a minor code of 1
     */
    private void nullParam()
        throws BAD_PARAM
    {
        throw orbutilWrapper.nullParam() ;
    }

    /** This is the implementation of standard API defined in org.omg.CORBA.ORB
     *  class. This method finds the Policy Factory for the given Policy Type
     *  and instantiates the Policy object from the Factory. It will throw
     *  PolicyError exception, If the PolicyFactory for the given type is
     *  not registered.
     *  _REVISIT_, Once Policy Framework work is completed, Reorganize
     *  this method to com.sun.corba.se.spi.orb.ORB.
     */
    public org.omg.CORBA.Policy create_policy(int type, org.omg.CORBA.Any val)
        throws org.omg.CORBA.PolicyError
    {
        if( val == null ) {
            nullParam( );
        }
        if( policyFactoryTable == null ) {
            throw new org.omg.CORBA.PolicyError(
                "There is no PolicyFactory Registered for type " + type,
                BAD_POLICY.value );
        }
        PolicyFactory factory = (PolicyFactory)policyFactoryTable.get(
            new Integer(type) );
        if( factory == null ) {
            throw new org.omg.CORBA.PolicyError(
                " Could Not Find PolicyFactory for the Type " + type,
                BAD_POLICY.value);
        }
        org.omg.CORBA.Policy policy = factory.create_policy( type, val );
        return policy;
    }

    /** This method registers the Policy Factory in the policyFactoryTable,
     *  which is a HashMap. This method is made package private, because
     *  it is used internally by the  Interceptors.
     */
    public void registerPolicyFactory( int type, PolicyFactory factory ) {
        if( policyFactoryTable == null ) {
            policyFactoryTable = new HashMap();
        }
        Integer key = new Integer( type );
        java.lang.Object val = policyFactoryTable.get( key );
        if( val == null ) {
            policyFactoryTable.put( key, factory );
        }
        else {
            throw omgWrapper.policyFactoryRegFailed( new Integer( type ) ) ;
        }
    }

    public synchronized int allocateServerRequestId ()
    {
        return serverRequestIdCounter++;
    }
}
