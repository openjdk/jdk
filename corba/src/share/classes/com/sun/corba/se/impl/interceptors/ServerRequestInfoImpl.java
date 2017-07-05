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

import org.omg.CORBA.Any;
import org.omg.CORBA.BAD_INV_ORDER;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.LocalObject;
import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.NO_RESOURCES;
import org.omg.CORBA.NVList;
import org.omg.CORBA.Object;
import org.omg.CORBA.Policy;
import org.omg.CORBA.TypeCode;

import org.omg.PortableServer.Servant;

import org.omg.IOP.TaggedProfile;
import org.omg.IOP.ServiceContext;

import org.omg.Dynamic.Parameter;

import org.omg.PortableInterceptor.InvalidSlot;
import org.omg.PortableInterceptor.ServerRequestInfo;
import org.omg.PortableInterceptor.LOCATION_FORWARD;
import org.omg.PortableInterceptor.SUCCESSFUL;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import org.omg.PortableInterceptor.TRANSPORT_RETRY;
import org.omg.PortableInterceptor.USER_EXCEPTION;

import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter;

import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage;

import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.ior.ObjectKeyTemplate;
import com.sun.corba.se.spi.ior.ObjectAdapterId ;

import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import java.util.*;

/**
 * Implementation of the ServerRequestInfo interface as specified in
 * orbos/99-12-02 section 5.4.3.
 */
public final class ServerRequestInfoImpl
    extends RequestInfoImpl
    implements ServerRequestInfo
{
    // The available constants for startingPointCall
    static final int CALL_RECEIVE_REQUEST_SERVICE_CONTEXT = 0;

    // The available constants for intermediatePointCall.  The default (0)
    // is receive_request, but can be set to none on demand.
    static final int CALL_RECEIVE_REQUEST = 0;
    static final int CALL_INTERMEDIATE_NONE = 1;

    // The available constants for endingPointCall
    static final int CALL_SEND_REPLY = 0;
    static final int CALL_SEND_EXCEPTION = 1;
    static final int CALL_SEND_OTHER = 2;

    //////////////////////////////////////////////////////////////////////
    //
    // NOTE: IF AN ATTRIBUTE IS ADDED, PLEASE UPDATE RESET();
    //
    //////////////////////////////////////////////////////////////////////

    // Set to true if the server ending point raised ForwardRequest at some
    // point in the ending point.
    private boolean forwardRequestRaisedInEnding;

    // Sources of server request information:
    private CorbaMessageMediator request;
    private java.lang.Object servant;
    private byte[] objectId;
    private ObjectKeyTemplate oktemp ;

    // Information cached from calls to oktemp
    private byte[] adapterId;
    private String[] adapterName;

    private ArrayList addReplyServiceContextQueue;
    private ReplyMessage replyMessage;
    private String targetMostDerivedInterface;
    private NVList dsiArguments;
    private Any dsiResult;
    private Any dsiException;
    private boolean isDynamic;
    private ObjectAdapter objectAdapter;
    private int serverRequestId;

    // Cached information:
    private Parameter[] cachedArguments;
    private Any cachedSendingException;
    // key = Integer, value = IOP.ServiceContext.
    private HashMap cachedRequestServiceContexts;
    // key = Integer, value = IOP.ServiceContext.
    private HashMap cachedReplyServiceContexts;

    //////////////////////////////////////////////////////////////////////
    //
    // NOTE: IF AN ATTRIBUTE IS ADDED, PLEASE UPDATE RESET();
    //
    //////////////////////////////////////////////////////////////////////


    /**
     * Reset the info object so that it can be reused for a retry,
     * for example.
     */
    void reset() {
        super.reset();

        // Please keep these in the same order as declared above.

        forwardRequestRaisedInEnding = false;

        request = null;
        servant = null;
        objectId = null;
        oktemp = null;

        adapterId = null;
        adapterName = null;

        addReplyServiceContextQueue = null;
        replyMessage = null;
        targetMostDerivedInterface = null;
        dsiArguments = null;
        dsiResult = null;
        dsiException = null;
        isDynamic = false;
        objectAdapter = null;
        serverRequestId = myORB.getPIHandler().allocateServerRequestId();

        // reset cached attributes:
        cachedArguments = null;
        cachedSendingException = null;
        cachedRequestServiceContexts = null;
        cachedReplyServiceContexts = null;

        startingPointCall = CALL_RECEIVE_REQUEST_SERVICE_CONTEXT;
        intermediatePointCall = CALL_RECEIVE_REQUEST;
        endingPointCall = CALL_SEND_REPLY;
    }

    /*
     **********************************************************************
     * Access protection
     **********************************************************************/

    // Method IDs for all methods in ServerRequestInfo.  This allows for a
    // convenient O(1) lookup for checkAccess().
    protected static final int MID_SENDING_EXCEPTION       = MID_RI_LAST +  1;
    protected static final int MID_OBJECT_ID               = MID_RI_LAST +  2;
    protected static final int MID_ADAPTER_ID              = MID_RI_LAST +  3;
    protected static final int MID_TARGET_MOST_DERIVED_INTERFACE
                                                           = MID_RI_LAST +  4;
    protected static final int MID_GET_SERVER_POLICY       = MID_RI_LAST +  5;
    protected static final int MID_SET_SLOT                = MID_RI_LAST +  6;
    protected static final int MID_TARGET_IS_A             = MID_RI_LAST +  7;
    protected static final int MID_ADD_REPLY_SERVICE_CONTEXT
                                                           = MID_RI_LAST +  8;
    protected static final int MID_SERVER_ID               = MID_RI_LAST +  9;
    protected static final int MID_ORB_ID                  = MID_RI_LAST +  10;
    protected static final int MID_ADAPTER_NAME            = MID_RI_LAST +  11;

    // ServerRequestInfo validity table (see ptc/00-08-06 table 21-2).
    // Note: These must be in the same order as specified in contants.
    protected static final boolean validCall[][] = {
        // LEGEND:
        // r_rsc = receive_request_service_contexts
        // r_req = receive_request
        // s_rep = send_reply
        // s_exc = send_exception
        // s_oth = send_other
        //
        // A true value indicates call is valid at specified point.
        // A false value indicates the call is invalid.
        //
        // NOTE: If the order or number of columns change, update
        // checkAccess() accordingly.
        //
        //                              { r_rsc, r_req, s_rep, s_exc, s_oth }
        // RequestInfo methods:
        /*request_id*/                  { true , true , true , true , true  },
        /*operation*/                   { true , true , true , true , true  },
        /*arguments*/                   { false, true , true , false, false },
        /*exceptions*/                  { false, true , true , true , true  },
        /*contexts*/                    { false, true , true , true , true  },
        /*operation_context*/           { false, true , true , false, false },
        /*result*/                      { false, false, true , false, false },
        /*response_expected*/           { true , true , true , true , true  },
        /*sync_scope*/                  { true , true , true , true , true  },
        /*reply_status*/                { false, false, true , true , true  },
        /*forward_reference*/           { false, false, false, false, true  },
        /*get_slot*/                    { true , true , true , true , true  },
        /*get_request_service_context*/ { true , true , true , true , true  },
        /*get_reply_service_context*/   { false, false, true , true , true  },
        //
        // ServerRequestInfo methods::
        /*sending_exception*/           { false, false, false, true , false },
        /*object_id*/                   { false, true , true , true , true  },
        /*adapter_id*/                  { false, true , true , true , true  },
        /*target_most_derived_inte...*/ { false, true , false, false, false },
        /*get_server_policy*/           { true , true , true , true , true  },
        /*set_slot*/                    { true , true , true , true , true  },
        /*target_is_a*/                 { false, true , false, false, false },
        /*add_reply_service_context*/   { true , true , true , true , true  },
        /*orb_id*/                      { false, true , true , true , true  },
        /*server_id*/                   { false, true , true , true , true  },
        /*adapter_name*/                { false, true , true , true , true  }
    };

    /*
     **********************************************************************
     * Public interfaces
     **********************************************************************/

    /**
     * Creates a new ServerRequestInfo implementation.
     * The constructor is package scope since no other package need create
     * an instance of this class.
     */
    ServerRequestInfoImpl( ORB myORB ) {
        super( myORB );
        startingPointCall = CALL_RECEIVE_REQUEST_SERVICE_CONTEXT;
        intermediatePointCall = CALL_RECEIVE_REQUEST;
        endingPointCall = CALL_SEND_REPLY;
        serverRequestId = myORB.getPIHandler().allocateServerRequestId();
    }

    /**
     * Any containing the exception to be returned to the client.
     */
    public Any sending_exception () {
        checkAccess( MID_SENDING_EXCEPTION );

        if( cachedSendingException == null ) {
            Any result = null ;

            if( dsiException != null ) {
                result = dsiException;
            } else if( exception != null ) {
                result = exceptionToAny( exception );
            } else {
                // sending_exception should not be callable if both dsiException
                // and exception are null.
                throw wrapper.exceptionUnavailable() ;
            }

            cachedSendingException = result;
        }

        return cachedSendingException;
    }

    /**
     * The opaque object_id describing the target of the operation invocation.
     */
    public byte[] object_id () {
        checkAccess( MID_OBJECT_ID );

        if( objectId == null ) {
            // For some reason, we never set object id.  This could be
            // because a servant locator caused a location forward or
            // raised an exception.  As per ptc/00-08-06, section 21.3.14,
            // we throw NO_RESOURCES
            throw stdWrapper.piOperationNotSupported6() ;
        }

        // Good citizen: In the interest of efficiency, we will assume
        // interceptors will not change the resulting byte[] array.
        // Otherwise, we would need to make a clone of this array.

        return objectId;
    }

    private void checkForNullTemplate()
    {
        if (oktemp == null) {
            // For some reason, we never set the ObjectKeyTemplate
            // because a servant locator caused a location forward or
            // raised an exception.  As per ptc/00-08-06, section 21.3.14,
            // we throw NO_RESOURCES
            throw stdWrapper.piOperationNotSupported7() ;
        }
    }

    public String server_id()
    {
        checkAccess( MID_SERVER_ID ) ;
        checkForNullTemplate() ;

        // Good citizen: In the interest of efficiency, we will assume
        // interceptors will not change the resulting byte[] array.
        // Otherwise, we would need to make a clone of this array.

        return Integer.toString( oktemp.getServerId() ) ;
    }

    public String orb_id()
    {
        checkAccess( MID_ORB_ID ) ;

        return myORB.getORBData().getORBId() ;
    }

    synchronized public String[] adapter_name()
    {
        checkAccess( MID_ADAPTER_NAME ) ;

        if (adapterName == null) {
            checkForNullTemplate() ;

            ObjectAdapterId oaid = oktemp.getObjectAdapterId() ;
            adapterName = oaid.getAdapterName() ;
        }

        return adapterName ;
    }

    /**
     * The opaque identifier for the object adapter.
     */
    synchronized public byte[] adapter_id ()
    {
        checkAccess( MID_ADAPTER_ID );

        if( adapterId == null ) {
            checkForNullTemplate() ;
            adapterId = oktemp.getAdapterId() ;
        }

        return adapterId;
    }

    /**
     * The RepositoryID for the most derived interface of the servant.
     */
    public String target_most_derived_interface () {
        checkAccess( MID_TARGET_MOST_DERIVED_INTERFACE );
        return targetMostDerivedInterface;
    }

    /**
     * Returns the policy in effect for this operation for the given policy
     * type.
     */
    public Policy get_server_policy (int type) {
        // access is currently valid for all states:
        //checkAccess( MID_GET_SERVER_POLICY );

        Policy result = null;

        if( objectAdapter != null ) {
            result = objectAdapter.getEffectivePolicy( type );
        }

        // _REVISIT_ RTF Issue: get_server_policy spec not in sync with
        // get_effective_policy spec.

        return result;
    }

    /**
     * Allows an Interceptor to set a slot in the Current that is in the scope
     * of the request.  If data already exists in that slot, it will be
     * overwritten.  If the ID does not define an allocated slot, InvalidSlot
     * is raised.
     */
    public void set_slot (int id, Any data) throws InvalidSlot {
        // access is currently valid for all states:
        //checkAccess( MID_SET_SLOT );

        slotTable.set_slot( id, data );
    }

    /**
     * Returns true if the servant is the given RepositoryId, false if it is
     * not.
     */
    public boolean target_is_a (String id) {
        checkAccess( MID_TARGET_IS_A );

        boolean result = false ;
        if( servant instanceof Servant ) {
            result = ((Servant)servant)._is_a( id );
        } else if (StubAdapter.isStub( servant )) {
            result = ((org.omg.CORBA.Object)servant)._is_a( id );
        } else {
            throw wrapper.servantInvalid() ;
        }

        return result;
    }

    /**
     * Allows Interceptors to add service contexts to the request.
     */
    public void add_reply_service_context ( ServiceContext service_context,
                                            boolean replace )
    {
        // access is currently valid for all states:
        //checkAccess( MID_ADD_REPLY_SERVICE_CONTEXT );

        if( currentExecutionPoint == EXECUTION_POINT_ENDING ) {
            ServiceContexts scs = replyMessage.getServiceContexts();

            // May be null.  If this is null, create a new one in its place.
            if( scs == null ) {
                scs = new ServiceContexts( myORB );
                replyMessage.setServiceContexts( scs );
            }

            if( cachedReplyServiceContexts == null ) {
                cachedReplyServiceContexts = new HashMap();
            }

            // This is during and ending point, so we now have enough
            // information to add the reply service context.
            addServiceContext( cachedReplyServiceContexts, scs,
                               service_context, replace );
        }

        // We enqueue all adds for the following reasons:
        //
        // If we are not in the ending point then we do not yet have a
        // pointer to the ServiceContexts object so we cannot access the
        // service contexts until we get to the ending point.
        // So we enqueue this add reply service context request.
        // It is added when we do have a handle on the service contexts object.
        //
        // If we are in the ending point and we just add directly to the
        // SC container but then an interceptor raises a SystemException
        // then that add will be lost since a new container is created
        // for the SystemException response.
        //
        // Therefore we always enqueue and never dequeue (per request) so
        // that all adds will be completed.

        AddReplyServiceContextCommand addReply =
            new AddReplyServiceContextCommand();
        addReply.service_context = service_context;
        addReply.replace = replace;

        if( addReplyServiceContextQueue == null ) {
            addReplyServiceContextQueue = new ArrayList();
        }

        // REVISIT: this does not add to the cache.
        enqueue( addReply );
    }

    // NOTE: When adding a method, be sure to:
    // 1. Add a MID_* constant for that method
    // 2. Call checkAccess at the start of the method
    // 3. Define entries in the validCall[][] table for interception points.

    /*
     **********************************************************************
     * Public RequestInfo interfaces
     *
     * These are implemented here because they have differing
     * implementations depending on whether this is a client or a server
     * request info object.
     **********************************************************************/

    /**
     * See ServerRequestInfo for javadocs.
     */
    public int request_id (){
        // access is currently valid for all states:
        //checkAccess( MID_REQUEST_ID );
        /*
         * NOTE: The request id in server interceptors is NOT the
         * same as the GIOP request id.  The ORB may be servicing several
         * connections, each with possibly overlapping sets of request ids.
         * Therefore we create a request id specific to interceptors.
         */
        return serverRequestId;
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public String operation (){
        // access is currently valid for all states:
        //checkAccess( MID_OPERATION );
        return request.getOperationName();
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public Parameter[] arguments (){
        checkAccess( MID_ARGUMENTS );

        if( cachedArguments == null ) {
            if( !isDynamic ) {
                throw stdWrapper.piOperationNotSupported1() ;
            }

            if( dsiArguments == null ) {
                throw stdWrapper.piOperationNotSupported8() ;
            }

            // If it is a DSI request then get the arguments from the DSI req
            // and convert that into parameters.
            cachedArguments = nvListToParameterArray( dsiArguments );
        }

        // Good citizen: In the interest of efficiency, we assume
        // interceptors will be "good citizens" in that they will not
        // modify the contents of the Parameter[] array.  We also assume
        // they will not change the values of the containing Anys.

        return cachedArguments;
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public TypeCode[] exceptions (){
        checkAccess( MID_EXCEPTIONS );

        // _REVISIT_ PI RTF Issue: No exception list on server side.

        throw stdWrapper.piOperationNotSupported2() ;
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public String[] contexts (){
        checkAccess( MID_CONTEXTS );

        // We do not support this because our ORB does not send contexts.

        throw stdWrapper.piOperationNotSupported3() ;
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public String[] operation_context (){
        checkAccess( MID_OPERATION_CONTEXT );

        // We do not support this because our ORB does not send
        // operation_context.

        throw stdWrapper.piOperationNotSupported4() ;
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public Any result (){
        checkAccess( MID_RESULT );

        if( !isDynamic ) {
            throw stdWrapper.piOperationNotSupported5() ;
        }

        if( dsiResult == null ) {
            throw wrapper.piDsiResultIsNull() ;
        }

        // Good citizen: In the interest of efficiency, we assume that
        // interceptors will not modify the contents of the result Any.
        // Otherwise, we would need to create a deep copy of the Any.

        return dsiResult;
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public boolean response_expected (){
        // access is currently valid for all states:
        //checkAccess( MID_RESPONSE_EXPECTED );
        return !request.isOneWay();
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public Object forward_reference (){
        checkAccess( MID_FORWARD_REFERENCE );
        // Check to make sure we are in LOCATION_FORWARD
        // state as per ptc/00-08-06, table 21-2
        // footnote 2.
        if( replyStatus != LOCATION_FORWARD.value ) {
            throw stdWrapper.invalidPiCall1() ;
        }

        return getForwardRequestException().forward;
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public org.omg.IOP.ServiceContext get_request_service_context( int id ) {
        checkAccess( MID_GET_REQUEST_SERVICE_CONTEXT );

        if( cachedRequestServiceContexts == null ) {
            cachedRequestServiceContexts = new HashMap();
        }

        return getServiceContext( cachedRequestServiceContexts,
                                  request.getRequestServiceContexts(), id );
    }

    /**
     * See ServerRequestInfo for javadocs.
     */
    public org.omg.IOP.ServiceContext get_reply_service_context( int id ) {
        checkAccess( MID_GET_REPLY_SERVICE_CONTEXT );

        if( cachedReplyServiceContexts == null ) {
            cachedReplyServiceContexts = new HashMap();
        }

        return getServiceContext( cachedReplyServiceContexts,
                                  replyMessage.getServiceContexts(), id );
    }

    /*
     **********************************************************************
     * Private-scope classes and methods
     **********************************************************************/

    // A command encapsulating a request to add a reply service context.
    // These commands are enqueued until we have a handle on the actual
    // reply service context, at which point they are executed.
    private class AddReplyServiceContextCommand {
        ServiceContext service_context;
        boolean replace;
    }

    // Adds the given add reply service context command to the queue of
    // such commands.  If a command is detected to have the same id as
    // the service context in this command, and replace is false,
    // BAD_INV_ORDER is thrown.  If replace is true, the original command
    // in the queue is replaced by this command.
    private void enqueue( AddReplyServiceContextCommand addReply ) {
        int size = addReplyServiceContextQueue.size();
        boolean found = false;

        for( int i = 0; i < size; i++ ) {
            AddReplyServiceContextCommand cmd =
                (AddReplyServiceContextCommand)
                addReplyServiceContextQueue.get( i );

            if( cmd.service_context.context_id ==
                addReply.service_context.context_id )
            {
                found = true;
                if( addReply.replace ) {
                    addReplyServiceContextQueue.set( i, addReply );
                } else {
                    throw stdWrapper.serviceContextAddFailed(
                        new Integer( cmd.service_context.context_id ) ) ;
                }
                break;
            }
        }

        if( !found ) {
            addReplyServiceContextQueue.add( addReply );
        }
    }

    /*
     **********************************************************************
     * Package and protected-scope methods
     **********************************************************************/

    /**
     * Overridden from RequestInfoImpl.  This version calls the super
     * and then, if we are changing to ending points, executes all
     * enqueued AddReplyServiceContextCommands.
     */
    protected void setCurrentExecutionPoint( int executionPoint ) {
        super.setCurrentExecutionPoint( executionPoint );

        // If we are transitioning to ending point, we will now have a pointer
        // to the reply service contexts, so we can execute all queued
        // add reply service context requests.
        if( (executionPoint == EXECUTION_POINT_ENDING) &&
            (addReplyServiceContextQueue != null) )
        {
            int size = addReplyServiceContextQueue.size();
            for( int i = 0; i < size; i++ ) {
                AddReplyServiceContextCommand addReply =
                    (AddReplyServiceContextCommand)
                    addReplyServiceContextQueue.get( i );
                try {
                    add_reply_service_context( addReply.service_context,
                                               addReply.replace );
                }
                catch( BAD_INV_ORDER e ) {
                    // _REVISIT_  The only way this can happen is if during
                    // rrsc or rr, the interceptor tried to add with
                    // replace=false to a service context that is present in
                    // the reply message.  At that time there was no way for
                    // us to check for this, so the best we can do is ignore
                    // the original request.
                }
            }

            // We specifically do not empty the SC queue so that if
            // the interceptor raises an exception the queued service contexts
            // will be put in the exception response.
        }
    }

    /**
     * Stores the various sources of information used for this info object.
     */
    protected void setInfo( CorbaMessageMediator request, ObjectAdapter oa,
        byte[] objectId, ObjectKeyTemplate oktemp )
    {
        this.request = request;
        this.objectId = objectId;
        this.oktemp = oktemp;
        this.objectAdapter = oa ;
        this.connection = (com.sun.corba.se.spi.legacy.connection.Connection)
            request.getConnection();
    }

    /**
     * Stores the various sources of information used for this info object.
     */
    protected void setDSIArguments( NVList arguments ) {
        this.dsiArguments = arguments;
    }

    /**
     * Stores the various sources of information used for this info object.
     */
    protected void setDSIException( Any exception ) {
        this.dsiException = exception;

        // Clear cached exception value:
        cachedSendingException = null;
    }

    /**
     * Stores the various sources of information used for this info object.
     */
    protected void setDSIResult( Any result ) {
        this.dsiResult = result;
    }

    /**
     * Sets the exception to be returned by received_exception and
     * received_exception_id.
     */
    protected void setException( Exception exception ) {
        super.setException( exception );

        // Make sure DSIException is null because this is the more recent one.
        this.dsiException = null;

        // Clear cached exception value:
        cachedSendingException = null;
    }

    /**
     * Stores the various sources of information used for this info object.
     */
    protected void setInfo( java.lang.Object servant,
                            String targetMostDerivedInterface )
    {
        this.servant = servant;
        this.targetMostDerivedInterface = targetMostDerivedInterface;
        this.isDynamic =
            (servant instanceof
            org.omg.PortableServer.DynamicImplementation) ||
            (servant instanceof org.omg.CORBA.DynamicImplementation);
    }

    /**
     * Set reply message
     */
    void setReplyMessage( ReplyMessage replyMessage ) {
        this.replyMessage = replyMessage;
    }

    /**
     * Overridden from RequestInfoImpl.  Calls the super class, then
     * sets the ending point call depending on the reply status.
     */
    protected void setReplyStatus( short replyStatus ) {
        super.setReplyStatus( replyStatus );
        switch( replyStatus ) {
        case SUCCESSFUL.value:
            endingPointCall = CALL_SEND_REPLY;
            break;
        case SYSTEM_EXCEPTION.value:
        case USER_EXCEPTION.value:
            endingPointCall = CALL_SEND_EXCEPTION;
            break;
        case LOCATION_FORWARD.value:
        case TRANSPORT_RETRY.value:
            endingPointCall = CALL_SEND_OTHER;
            break;
        }
    }

    /**
     * Release the servant object so the user has control over its lifetime.
     * Called after receive_request is finished executing.
     */
    void releaseServant() {
        this.servant = null;
    }

    /**
     * Sets the forwardRequestRaisedInEnding flag to true, indicating that
     * a server ending point has raised location forward at some point.
     */
    void setForwardRequestRaisedInEnding() {
        this.forwardRequestRaisedInEnding = true;
    }

    /**
     * Returns true if ForwardRequest was raised by a server ending point
     * or false otherwise.
     */
    boolean isForwardRequestRaisedInEnding() {
        return this.forwardRequestRaisedInEnding;
    }

    /**
     * Returns true if this is a dynamic invocation, or false if not
     */
    boolean isDynamic() {
      return this.isDynamic;
    }

    /**
     * See description for RequestInfoImpl.checkAccess
     */
    protected void checkAccess( int methodID )
    {
        // Make sure currentPoint matches the appropriate index in the
        // validCall table:
        int validCallIndex = 0;
        switch( currentExecutionPoint ) {
        case EXECUTION_POINT_STARTING:
            validCallIndex = 0;
            break;
        case EXECUTION_POINT_INTERMEDIATE:
            validCallIndex = 1;
            break;
        case EXECUTION_POINT_ENDING:
            switch( endingPointCall ) {
            case CALL_SEND_REPLY:
                validCallIndex = 2;
                break;
            case CALL_SEND_EXCEPTION:
                validCallIndex = 3;
                break;
            case CALL_SEND_OTHER:
                validCallIndex = 4;
                break;
            }
            break;
        }

        // Check the validCall table:
        if( !validCall[methodID][validCallIndex] ) {
            throw stdWrapper.invalidPiCall2() ;
        }
    }

}
