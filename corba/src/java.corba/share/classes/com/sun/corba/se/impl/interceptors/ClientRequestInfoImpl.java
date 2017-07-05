/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap ;

import org.omg.CORBA.Any;
import org.omg.CORBA.BAD_INV_ORDER;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.Context;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.CTX_RESTRICT_SCOPE;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.LocalObject;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.NO_RESOURCES;
import org.omg.CORBA.NVList;
import org.omg.CORBA.Object;
import org.omg.CORBA.ParameterMode;
import org.omg.CORBA.Policy;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.UserException;
import org.omg.CORBA.portable.ApplicationException;
import org.omg.CORBA.portable.InputStream;
import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.spi.servicecontext.UnknownServiceContext;

import org.omg.IOP.ServiceContext;
import org.omg.IOP.ServiceContextHelper;
import org.omg.IOP.TaggedProfile;
import org.omg.IOP.TaggedProfileHelper;
import org.omg.IOP.TaggedComponent;
import org.omg.IOP.TaggedComponentHelper;
import org.omg.IOP.TAG_INTERNET_IOP;
import org.omg.Dynamic.Parameter;
import org.omg.PortableInterceptor.ClientRequestInfo;
import org.omg.PortableInterceptor.LOCATION_FORWARD;
import org.omg.PortableInterceptor.SUCCESSFUL;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import org.omg.PortableInterceptor.TRANSPORT_RETRY;
import org.omg.PortableInterceptor.USER_EXCEPTION;

import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.protocol.RetryType;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.CorbaContactInfoListIterator;

import com.sun.corba.se.impl.encoding.CDROutputStream;
import com.sun.corba.se.impl.encoding.CDRInputStream_1_0;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.CorbaInvocationInfo;
import com.sun.corba.se.impl.util.RepositoryId;

/**
 * Implementation of the ClientRequestInfo interface as specified in
 * orbos/99-12-02 section 5.4.2.
 */
public final class ClientRequestInfoImpl
    extends RequestInfoImpl
    implements ClientRequestInfo
{

    // The available constants for startingPointCall
    static final int CALL_SEND_REQUEST = 0;
    static final int CALL_SEND_POLL = 1;

    // The available constants for endingPointCall
    static final int CALL_RECEIVE_REPLY = 0;
    static final int CALL_RECEIVE_EXCEPTION = 1;
    static final int CALL_RECEIVE_OTHER = 2;

    //////////////////////////////////////////////////////////////////////
    //
    // NOTE: IF AN ATTRIBUTE IS ADDED, PLEASE UPDATE RESET();
    //
    //////////////////////////////////////////////////////////////////////

    // The current retry request status.  True if this request is being
    // retried and this info object is to be reused, or false otherwise.
    private RetryType retryRequest;

    // The number of times this info object has been (re)used.  This is
    // incremented every time a request is retried, and decremented every
    // time a request is complete.  When this reaches zero, the info object
    // is popped from the ClientRequestInfoImpl ThreadLocal stack in the ORB.
    private int entryCount = 0;

    // The RequestImpl is set when the call is DII based.
    // The DII query calls like ParameterList, ExceptionList,
    // ContextList will be delegated to RequestImpl.
    private org.omg.CORBA.Request request;

    // Sources of client request information
    private boolean diiInitiate;
    private CorbaMessageMediator messageMediator;

    // Cached information:
    private org.omg.CORBA.Object cachedTargetObject;
    private org.omg.CORBA.Object cachedEffectiveTargetObject;
    private Parameter[] cachedArguments;
    private TypeCode[] cachedExceptions;
    private String[] cachedContexts;
    private String[] cachedOperationContext;
    private String cachedReceivedExceptionId;
    private Any cachedResult;
    private Any cachedReceivedException;
    private TaggedProfile cachedEffectiveProfile;
    // key = Integer, value = IOP.ServiceContext.
    private HashMap cachedRequestServiceContexts;
    // key = Integer, value = IOP.ServiceContext.
    private HashMap cachedReplyServiceContexts;
    // key = Integer, value = TaggedComponent
    private HashMap cachedEffectiveComponents;


    protected boolean piCurrentPushed;

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

        // Please keep these in the same order that they're declared above.

        // 6763340
        retryRequest = RetryType.NONE;

        // Do not reset entryCount because we need to know when to pop this
        // from the stack.

        request = null;
        diiInitiate = false;
        messageMediator = null;

        // Clear cached attributes:
        cachedTargetObject = null;
        cachedEffectiveTargetObject = null;
        cachedArguments = null;
        cachedExceptions = null;
        cachedContexts = null;
        cachedOperationContext = null;
        cachedReceivedExceptionId = null;
        cachedResult = null;
        cachedReceivedException = null;
        cachedEffectiveProfile = null;
        cachedRequestServiceContexts = null;
        cachedReplyServiceContexts = null;
        cachedEffectiveComponents = null;

        piCurrentPushed = false;

        startingPointCall = CALL_SEND_REQUEST;
        endingPointCall = CALL_RECEIVE_REPLY;

    }

    /*
     **********************************************************************
     * Access protection
     **********************************************************************/

    // Method IDs for all methods in ClientRequestInfo.  This allows for a
    // convenient O(1) lookup for checkAccess().
    protected static final int MID_TARGET                  = MID_RI_LAST + 1;
    protected static final int MID_EFFECTIVE_TARGET        = MID_RI_LAST + 2;
    protected static final int MID_EFFECTIVE_PROFILE       = MID_RI_LAST + 3;
    protected static final int MID_RECEIVED_EXCEPTION      = MID_RI_LAST + 4;
    protected static final int MID_RECEIVED_EXCEPTION_ID   = MID_RI_LAST + 5;
    protected static final int MID_GET_EFFECTIVE_COMPONENT = MID_RI_LAST + 6;
    protected static final int MID_GET_EFFECTIVE_COMPONENTS
                                                           = MID_RI_LAST + 7;
    protected static final int MID_GET_REQUEST_POLICY      = MID_RI_LAST + 8;
    protected static final int MID_ADD_REQUEST_SERVICE_CONTEXT
                                                           = MID_RI_LAST + 9;

    // ClientRequestInfo validity table (see ptc/00-08-06 table 21-1).
    // Note: These must be in the same order as specified in contants.
    private static final boolean validCall[][] = {
        // LEGEND:
        // s_req = send_request     r_rep = receive_reply
        // s_pol = send_poll        r_exc = receive_exception
        //                          r_oth = receive_other
        //
        // A true value indicates call is valid at specified point.
        // A false value indicates the call is invalid.
        //
        //
        // NOTE: If the order or number of columns change, update
        // checkAccess() accordingly.
        //
        //                              { s_req, s_pol, r_rep, r_exc, r_oth }
        // RequestInfo methods:
        /*request_id*/                  { true , true , true , true , true  },
        /*operation*/                   { true , true , true , true , true  },
        /*arguments*/                   { true , false, true , false, false },
        /*exceptions*/                  { true , false, true , true , true  },
        /*contexts*/                    { true , false, true , true , true  },
        /*operation_context*/           { true , false, true , true , true  },
        /*result*/                      { false, false, true , false, false },
        /*response_expected*/           { true , true , true , true , true  },
        /*sync_scope*/                  { true , false, true , true , true  },
        /*reply_status*/                { false, false, true , true , true  },
        /*forward_reference*/           { false, false, false, false, true  },
        /*get_slot*/                    { true , true , true , true , true  },
        /*get_request_service_context*/ { true , false, true , true , true  },
        /*get_reply_service_context*/   { false, false, true , true , true  },
        //
        // ClientRequestInfo methods::
        /*target*/                      { true , true , true , true , true  },
        /*effective_target*/            { true , true , true , true , true  },
        /*effective_profile*/           { true , true , true , true , true  },
        /*received_exception*/          { false, false, false, true , false },
        /*received_exception_id*/       { false, false, false, true , false },
        /*get_effective_component*/     { true , false, true , true , true  },
        /*get_effective_components*/    { true , false, true , true , true  },
        /*get_request_policy*/          { true , false, true , true , true  },
        /*add_request_service_context*/ { true , false, false, false, false }
    };


    /*
     **********************************************************************
     * Public ClientRequestInfo interfaces
     **********************************************************************/

    /**
     * Creates a new ClientRequestInfo implementation.
     * The constructor is package scope since no other package need create
     * an instance of this class.
     */
    protected ClientRequestInfoImpl( ORB myORB ) {
        super( myORB );
        startingPointCall = CALL_SEND_REQUEST;
        endingPointCall = CALL_RECEIVE_REPLY;
    }

    /**
     * The object which the client called to perform the operation.
     */
    public org.omg.CORBA.Object target (){
        // access is currently valid for all states:
        //checkAccess( MID_TARGET );
        if (cachedTargetObject == null) {
            CorbaContactInfo corbaContactInfo = (CorbaContactInfo)
                messageMediator.getContactInfo();
            cachedTargetObject =
                iorToObject(corbaContactInfo.getTargetIOR());
        }
        return cachedTargetObject;
    }

    /**
     * The actual object on which the operation will be invoked.  If the
     * reply_status is LOCATION_FORWARD, then on subsequent requests,
     * effective_target will contain the forwarded IOR while target will
     * remain unchanged.
     */
    public org.omg.CORBA.Object effective_target() {
        // access is currently valid for all states:
        //checkAccess( MID_EFFECTIVE_TARGET );

        // Note: This is not necessarily the same as locatedIOR.
        // Reason: See the way we handle COMM_FAILURES in
        // ClientRequestDispatcher.createRequest, v1.32

        if (cachedEffectiveTargetObject == null) {
            CorbaContactInfo corbaContactInfo = (CorbaContactInfo)
                messageMediator.getContactInfo();
            // REVISIT - get through chain like getLocatedIOR helper below.
            cachedEffectiveTargetObject =
                iorToObject(corbaContactInfo.getEffectiveTargetIOR());
        }
        return cachedEffectiveTargetObject;
    }

    /**
     * The profile that will be used to send the request.  If a location
     * forward has occurred for this operation's object and that object's
     * profile change accordingly, then this profile will be that located
     * profile.
     */
    public TaggedProfile effective_profile (){
        // access is currently valid for all states:
        //checkAccess( MID_EFFECTIVE_PROFILE );

        if( cachedEffectiveProfile == null ) {
            CorbaContactInfo corbaContactInfo = (CorbaContactInfo)
                messageMediator.getContactInfo();
            cachedEffectiveProfile =
                corbaContactInfo.getEffectiveProfile().getIOPProfile();
        }

        // Good citizen: In the interest of efficiency, we assume interceptors
        // will not modify the returned TaggedProfile in any way so we need
        // not make a deep copy of it.

        return cachedEffectiveProfile;
    }

    /**
     * Contains the exception to be returned to the client.
     */
    public Any received_exception (){
        checkAccess( MID_RECEIVED_EXCEPTION );

        if( cachedReceivedException == null ) {
            cachedReceivedException = exceptionToAny( exception );
        }

        // Good citizen: In the interest of efficiency, we assume interceptors
        // will not modify the returned Any in any way so we need
        // not make a deep copy of it.

        return cachedReceivedException;
    }

    /**
     * The CORBA::RepositoryId of the exception to be returned to the client.
     */
    public String received_exception_id (){
        checkAccess( MID_RECEIVED_EXCEPTION_ID );

        if( cachedReceivedExceptionId == null ) {
            String result = null;

            if( exception == null ) {
                // Note: exception should never be null here since we will
                // throw a BAD_INV_ORDER if this is not called from
                // receive_exception.
                throw wrapper.exceptionWasNull() ;
            } else if( exception instanceof SystemException ) {
                String name = exception.getClass().getName();
                result = ORBUtility.repositoryIdOf(name);
            } else if( exception instanceof ApplicationException ) {
                result = ((ApplicationException)exception).getId();
            }

            // _REVISIT_ We need to be able to handle a UserException in the
            // DII case.  How do we extract the ID from a UserException?

            cachedReceivedExceptionId = result;
        }

        return cachedReceivedExceptionId;
    }

    /**
     * Returns the IOP::TaggedComponent with the given ID from the profile
     * selected for this request.  IF there is more than one component for a
     * given component ID, it is undefined which component this operation
     * returns (get_effective_component should be called instead).
     */
    public TaggedComponent get_effective_component (int id){
        checkAccess( MID_GET_EFFECTIVE_COMPONENT );

        return get_effective_components( id )[0];
    }

    /**
     * Returns all the tagged components with the given ID from the profile
     * selected for this request.
     */
    public TaggedComponent[] get_effective_components (int id){
        checkAccess( MID_GET_EFFECTIVE_COMPONENTS );
        Integer integerId = new Integer( id );
        TaggedComponent[] result = null;
        boolean justCreatedCache = false;

        if( cachedEffectiveComponents == null ) {
            cachedEffectiveComponents = new HashMap();
            justCreatedCache = true;
        }
        else {
            // Look in cache:
            result = (TaggedComponent[])cachedEffectiveComponents.get(
                integerId );
        }

        // null could mean we cached null or not in cache.
        if( (result == null) &&
            (justCreatedCache ||
            !cachedEffectiveComponents.containsKey( integerId ) ) )
        {
            // Not in cache.  Get it from the profile:
            CorbaContactInfo corbaContactInfo = (CorbaContactInfo)
                messageMediator.getContactInfo();
            IIOPProfileTemplate ptemp =
                (IIOPProfileTemplate)corbaContactInfo.getEffectiveProfile().
                getTaggedProfileTemplate();
            result = ptemp.getIOPComponents(myORB, id);
            cachedEffectiveComponents.put( integerId, result );
        }

        // As per ptc/00-08-06, section 21.3.13.6., If not found, raise
        // BAD_PARAM with minor code INVALID_COMPONENT_ID.
        if( (result == null) || (result.length == 0) ) {
            throw stdWrapper.invalidComponentId( integerId ) ;
        }

        // Good citizen: In the interest of efficiency, we will assume
        // interceptors will not modify the returned TaggedCompoent[], or
        // the TaggedComponents inside of it.  Otherwise, we would need to
        // clone the array and make a deep copy of its contents.

        return result;
    }

    /**
     * Returns the given policy in effect for this operation.
     */
    public Policy get_request_policy (int type){
        checkAccess( MID_GET_REQUEST_POLICY );
        // _REVISIT_ Our ORB is not policy-based at this time.
        throw wrapper.piOrbNotPolicyBased() ;
    }

    /**
     * Allows interceptors to add service contexts to the request.
     * <p>
     * There is no declaration of the order of the service contexts.  They
     * may or may not appear in the order they are added.
     */
    public void add_request_service_context (ServiceContext service_context,
                                             boolean replace)
    {
        checkAccess( MID_ADD_REQUEST_SERVICE_CONTEXT );

        if( cachedRequestServiceContexts == null ) {
            cachedRequestServiceContexts = new HashMap();
        }

        addServiceContext( cachedRequestServiceContexts,
                           messageMediator.getRequestServiceContexts(),
                           service_context, replace );
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
     * See RequestInfoImpl for javadoc.
     */
    public int request_id (){
        // access is currently valid for all states:
        //checkAccess( MID_REQUEST_ID );
        /*
         * NOTE: The requestId in client interceptors is the same as the
         * GIOP request id.  This works because both interceptors and
         * request ids are scoped by the ORB on the client side.
         */
        return messageMediator.getRequestId();
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public String operation (){
        // access is currently valid for all states:
        //checkAccess( MID_OPERATION );
        return messageMediator.getOperationName();
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public Parameter[] arguments (){
        checkAccess( MID_ARGUMENTS );

        if( cachedArguments == null ) {
            if( request == null ) {
                throw stdWrapper.piOperationNotSupported1() ;
            }

            // If it is DII request then get the arguments from the DII req
            // and convert that into parameters.
            cachedArguments = nvListToParameterArray( request.arguments() );
        }

        // Good citizen: In the interest of efficiency, we assume
        // interceptors will be "good citizens" in that they will not
        // modify the contents of the Parameter[] array.  We also assume
        // they will not change the values of the containing Anys.

        return cachedArguments;
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public TypeCode[] exceptions (){
        checkAccess( MID_EXCEPTIONS );

        if( cachedExceptions == null ) {
            if( request == null ) {
               throw stdWrapper.piOperationNotSupported2() ;
            }

            // Get the list of exceptions from DII request data, If there are
            // no exceptions raised then this method will return null.
            ExceptionList excList = request.exceptions( );
            int count = excList.count();
            TypeCode[] excTCList = new TypeCode[count];
            try {
                for( int i = 0; i < count; i++ ) {
                    excTCList[i] = excList.item( i );
                }
            } catch( Exception e ) {
                throw wrapper.exceptionInExceptions( e ) ;
            }

            cachedExceptions = excTCList;
        }

        // Good citizen: In the interest of efficiency, we assume
        // interceptors will be "good citizens" in that they will not
        // modify the contents of the TypeCode[] array.  We also assume
        // they will not change the values of the containing TypeCodes.

        return cachedExceptions;
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public String[] contexts (){
        checkAccess( MID_CONTEXTS );

        if( cachedContexts == null ) {
            if( request == null ) {
                throw stdWrapper.piOperationNotSupported3() ;
            }

            // Get the list of contexts from DII request data, If there are
            // no contexts then this method will return null.
            ContextList ctxList = request.contexts( );
            int count = ctxList.count();
            String[] ctxListToReturn = new String[count];
            try {
                for( int i = 0; i < count; i++ ) {
                    ctxListToReturn[i] = ctxList.item( i );
                }
            } catch( Exception e ) {
                throw wrapper.exceptionInContexts( e ) ;
            }

            cachedContexts = ctxListToReturn;
        }

        // Good citizen: In the interest of efficiency, we assume
        // interceptors will be "good citizens" in that they will not
        // modify the contents of the String[] array.

        return cachedContexts;
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public String[] operation_context (){
        checkAccess( MID_OPERATION_CONTEXT );

        if( cachedOperationContext == null ) {
            if( request == null ) {
                throw stdWrapper.piOperationNotSupported4() ;
            }

            // Get the list of contexts from DII request data, If there are
            // no contexts then this method will return null.
            Context ctx = request.ctx( );
            // _REVISIT_ The API for get_values is not compliant with the spec,
            // Revisit this code once it's fixed.
            // _REVISIT_ Our ORB doesn't support Operation Context, This code
            // will not be excerscised until it's supported.
            // The first parameter in get_values is the start_scope which
            // if blank makes it as a global scope.
            // The second parameter is op_flags which is set to RESTRICT_SCOPE
            // As there is only one defined in the spec.
            // The Third param is the pattern which is '*' requiring it to
            // get all the contexts.
            NVList nvList = ctx.get_values( "", CTX_RESTRICT_SCOPE.value,"*" );
            String[] context = new String[(nvList.count() * 2) ];
            if( ( nvList != null ) &&( nvList.count() != 0 ) ) {
                // The String[] array will contain Name and Value for each
                // context and hence double the size in the array.
                int index = 0;
                for( int i = 0; i < nvList.count(); i++ ) {
                    NamedValue nv;
                    try {
                        nv = nvList.item( i );
                    }
                    catch (Exception e ) {
                        return (String[]) null;
                    }
                    context[index] = nv.name();
                    index++;
                    context[index] = nv.value().extract_string();
                    index++;
                }
            }

            cachedOperationContext = context;
        }

        // Good citizen: In the interest of efficiency, we assume
        // interceptors will be "good citizens" in that they will not
        // modify the contents of the String[] array.

        return cachedOperationContext;
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public Any result (){
        checkAccess( MID_RESULT );

        if( cachedResult == null ) {
            if( request == null ) {
                throw stdWrapper.piOperationNotSupported5() ;
            }
            // Get the result from the DII request data.
            NamedValue nvResult = request.result( );

            if( nvResult == null ) {
                throw wrapper.piDiiResultIsNull() ;
            }

            cachedResult = nvResult.value();
        }

        // Good citizen: In the interest of efficiency, we assume that
        // interceptors will not modify the contents of the result Any.
        // Otherwise, we would need to create a deep copy of the Any.

        return cachedResult;
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public boolean response_expected (){
        // access is currently valid for all states:
        //checkAccess( MID_RESPONSE_EXPECTED );
        return ! messageMediator.isOneWay();
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public Object forward_reference (){
        checkAccess( MID_FORWARD_REFERENCE );
        // Check to make sure we are in LOCATION_FORWARD
        // state as per ptc/00-08-06, table 21-1
        // footnote 2.
        if( replyStatus != LOCATION_FORWARD.value ) {
            throw stdWrapper.invalidPiCall1() ;
        }

        // Do not cache this value since if an interceptor raises
        // forward request then the next interceptor in the
        // list should see the new value.
        IOR ior = getLocatedIOR();
        return iorToObject(ior);
    }

    private IOR getLocatedIOR()
    {
        IOR ior;
        CorbaContactInfoList contactInfoList = (CorbaContactInfoList)
            messageMediator.getContactInfo().getContactInfoList();
        ior = contactInfoList.getEffectiveTargetIOR();
        return ior;
    }

    protected void setLocatedIOR(IOR ior)
    {
        ORB orb = (ORB) messageMediator.getBroker();

        CorbaContactInfoListIterator iterator = (CorbaContactInfoListIterator)
            ((CorbaInvocationInfo)orb.getInvocationInfo())
            .getContactInfoListIterator();

        // REVISIT - this most likely causes reportRedirect to happen twice.
        // Once here and once inside the request dispatcher.
        iterator.reportRedirect(
            (CorbaContactInfo)messageMediator.getContactInfo(),
            ior);
    }

    /**
     * See RequestInfoImpl for javadoc.
     */
    public org.omg.IOP.ServiceContext get_request_service_context( int id ) {
        checkAccess( MID_GET_REQUEST_SERVICE_CONTEXT );

        if( cachedRequestServiceContexts == null ) {
            cachedRequestServiceContexts = new HashMap();
        }

        return  getServiceContext(cachedRequestServiceContexts,
                                  messageMediator.getRequestServiceContexts(),
                                  id);
    }

    /**
     * does not contain an etry for that ID, BAD_PARAM with a minor code of
     * TBD_BP is raised.
     */
    public org.omg.IOP.ServiceContext get_reply_service_context( int id ) {
        checkAccess( MID_GET_REPLY_SERVICE_CONTEXT );

        if( cachedReplyServiceContexts == null ) {
            cachedReplyServiceContexts = new HashMap();
        }

        // In the event this is called from a oneway, we will have no
        // response object.
        //
        // In the event this is called after a IIOPConnection.purgeCalls,
        // we will have a response object, but that object will
        // not contain a header (which would hold the service context
        // container).  See bug 4624102.
        //
        // REVISIT: this is the only thing used
        // from response at this time.  However, a more general solution
        // would avoid accessing other parts of response's header.
        //
        // Instead of throwing a NullPointer, we will
        // "gracefully" handle these with a BAD_PARAM with minor code 25.

        try {
            ServiceContexts serviceContexts =
                messageMediator.getReplyServiceContexts();
            if (serviceContexts == null) {
                throw new NullPointerException();
            }
            return getServiceContext(cachedReplyServiceContexts,
                                     serviceContexts, id);
        } catch (NullPointerException e) {
            // REVISIT how this is programmed - not what it does.
            // See purge calls test.  The waiter is woken up by the
            // call to purge calls - but there is no reply containing
            // service contexts.
            throw stdWrapper.invalidServiceContextId( e ) ;
        }
    }

    //
    // REVISIT
    // Override RequestInfoImpl connection to work in framework.
    //

    public com.sun.corba.se.spi.legacy.connection.Connection connection()
    {
        return (com.sun.corba.se.spi.legacy.connection.Connection)
            messageMediator.getConnection();
    }



    /*
     **********************************************************************
     * Package-scope interfaces
     **********************************************************************/

    protected void setInfo(MessageMediator messageMediator)
    {
        this.messageMediator = (CorbaMessageMediator)messageMediator;
        // REVISIT - so mediator can handle DII in subcontract.
        this.messageMediator.setDIIInfo(request);
    }

    /**
     * Set or reset the retry request flag.
     */
    void setRetryRequest( RetryType retryRequest ) {
        this.retryRequest = retryRequest;
    }

    /**
     * Retrieve the current retry request status.
     */
    RetryType getRetryRequest() {
        // 6763340
        return this.retryRequest;
    }

    /**
     * Increases the entry count by 1.
     */
    void incrementEntryCount() {
        this.entryCount++;
    }

    /**
     * Decreases the entry count by 1.
     */
    void decrementEntryCount() {
        this.entryCount--;
    }

    /**
     * Retrieve the current entry count
     */
    int getEntryCount() {
        return this.entryCount;
    }

    /**
     * Overridden from RequestInfoImpl.  Calls the super class, then
     * sets the ending point call depending on the reply status.
     */
    protected void setReplyStatus( short replyStatus ) {
        super.setReplyStatus( replyStatus );
        switch( replyStatus ) {
        case SUCCESSFUL.value:
            endingPointCall = CALL_RECEIVE_REPLY;
            break;
        case SYSTEM_EXCEPTION.value:
        case USER_EXCEPTION.value:
            endingPointCall = CALL_RECEIVE_EXCEPTION;
            break;
        case LOCATION_FORWARD.value:
        case TRANSPORT_RETRY.value:
            endingPointCall = CALL_RECEIVE_OTHER;
            break;
        }
    }

    /**
     * Sets DII request object in the RequestInfoObject.
     */
    protected void setDIIRequest(org.omg.CORBA.Request req) {
         request = req;
    }

    /**
     * Keeps track of whether initiate was called for a DII request.  The ORB
     * needs to know this so it knows whether to ignore a second call to
     * initiateClientPIRequest or not.
     */
    protected void setDIIInitiate( boolean diiInitiate ) {
        this.diiInitiate = diiInitiate;
    }

    /**
     * See comment for setDIIInitiate
     */
    protected boolean isDIIInitiate() {
        return this.diiInitiate;
    }

    /**
     * The PICurrent stack should only be popped if it was pushed.
     * This is generally the case.  But exceptions which occur
     * after the stub's entry to _request but before the push
     * end up in _releaseReply which will try to pop unless told not to.
     */
    protected void setPICurrentPushed( boolean piCurrentPushed ) {
        this.piCurrentPushed = piCurrentPushed;
    }

    protected boolean isPICurrentPushed() {
        return this.piCurrentPushed;
    }

    /**
     * Overridden from RequestInfoImpl.
     */
    protected void setException( Exception exception ) {
        super.setException( exception );

        // Clear cached values:
        cachedReceivedException = null;
        cachedReceivedExceptionId = null;
    }

    protected boolean getIsOneWay() {
        return ! response_expected();
    }

    /**
     * See description for RequestInfoImpl.checkAccess
     */
    protected void checkAccess( int methodID )
        throws BAD_INV_ORDER
    {
        // Make sure currentPoint matches the appropriate index in the
        // validCall table:
        int validCallIndex = 0;
        switch( currentExecutionPoint ) {
        case EXECUTION_POINT_STARTING:
            switch( startingPointCall ) {
            case CALL_SEND_REQUEST:
                validCallIndex = 0;
                break;
            case CALL_SEND_POLL:
                validCallIndex = 1;
                break;
            }
            break;
        case EXECUTION_POINT_ENDING:
            switch( endingPointCall ) {
            case CALL_RECEIVE_REPLY:
                validCallIndex = 2;
                break;
            case CALL_RECEIVE_EXCEPTION:
                validCallIndex = 3;
                break;
            case CALL_RECEIVE_OTHER:
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

// End of file.
