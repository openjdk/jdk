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

import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.BAD_INV_ORDER;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.OBJECT_NOT_EXIST;
import org.omg.CORBA.LocalObject;
import org.omg.CORBA.Policy;
import org.omg.CORBA.PolicyError;
import org.omg.IOP.CodecFactory;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ClientRequestInterceptor;
import org.omg.PortableInterceptor.IORInterceptor;
import org.omg.PortableInterceptor.PolicyFactory;
import org.omg.PortableInterceptor.ServerRequestInterceptor;
import org.omg.PortableInterceptor.ORBInitInfoPackage.DuplicateName;
import org.omg.PortableInterceptor.ORBInitInfoPackage.InvalidName;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.legacy.interceptor.ORBInitInfoExt ;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.orbutil.ORBUtility;

import com.sun.corba.se.impl.logging.InterceptorsSystemException;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.OMGSystemException;

/**
 * ORBInitInfoImpl is the implementation of the ORBInitInfo class to be
 * passed to ORBInitializers, as described in orbos/99-12-02.
 */
public final class ORBInitInfoImpl
    extends org.omg.CORBA.LocalObject
    implements ORBInitInfo, ORBInitInfoExt
{
    // The ORB we are initializing
    private ORB orb;

    private InterceptorsSystemException wrapper ;
    private ORBUtilSystemException orbutilWrapper ;
    private OMGSystemException omgWrapper ;

    // The arguments passed to ORB_init
    private String[] args;

    // The ID of the ORB being initialized
    private String orbId;

    // The CodecFactory
    private CodecFactory codecFactory;

    // The current stage of initialization
    private int stage = STAGE_PRE_INIT;

    // The pre-initialization stage (pre_init() being called)
    public static final int STAGE_PRE_INIT = 0;

    // The post-initialization stage (post_init() being called)
    public static final int STAGE_POST_INIT = 1;

    // Reject all calls - this object should no longer be around.
    public static final int STAGE_CLOSED = 2;

    // The description for the OBJECT_NOT_EXIST exception in STAGE_CLOSED
    private static final String MESSAGE_ORBINITINFO_INVALID =
        "ORBInitInfo object is only valid during ORB_init";

    /**
     * Creates a new ORBInitInfoImpl object (scoped to package)
     *
     * @param args The arguments passed to ORB_init.
     */
    ORBInitInfoImpl( ORB orb, String[] args,
        String orbId, CodecFactory codecFactory )
    {
        this.orb = orb;

        wrapper = InterceptorsSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        orbutilWrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        omgWrapper = OMGSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;

        this.args = args;
        this.orbId = orbId;
        this.codecFactory = codecFactory;
    }

    /** Return the ORB behind this ORBInitInfo.  This is defined in the
     * ORBInitInfoExt interface.
     */
    public ORB getORB()
    {
        return orb ;
    }

    /**
     * Sets the current stage we are in.  This limits access to certain
     * functionality.
     */
    void setStage( int stage ) {
        this.stage = stage;
    }

    /**
     * Throws an exception if the current stage is STAGE_CLOSED.
     * This is called before any method is invoked to ensure that
     * no method invocations are attempted after all calls to post_init()
     * are completed.
     */
    private void checkStage() {
        if( stage == STAGE_CLOSED ) {
            throw wrapper.orbinitinfoInvalid() ;
        }
    }

    /*
     *******************************************************************
     * The following are implementations of the ORBInitInfo operations.
     *******************************************************************/

    /**
     * This attribute contains the arguments passed to ORB_init.  They may
     * or may not contain the ORB's arguments
     */
    public String[] arguments () {
        checkStage();
        return args;
    }

    /**
     * This attribute is the ID of the ORB being initialized
     */
    public String orb_id () {
        checkStage();
        return orbId;
    }

    /**
     * This attribute is the IOP::CodecFactory.  The CodecFactory is normally
     * obtained via a call to ORB::resolve_initial_references( "CodecFactory" )
     * but since the ORB is not yet available and Interceptors, particularly
     * when processing service contexts, will require a Codec, a means of
     * obtaining a Codec is necessary during ORB intialization.
     */
    public CodecFactory codec_factory () {
        checkStage();
        return codecFactory;
    }

    /**
     * See orbos/99-12-02, Chapter 11, Dynamic Initial References on page
     * 11-81.  This operation is identical to ORB::register_initial_reference
     * described there.  This same functionality exists here because the ORB,
     * not yet fully initialized, is not yet available but initial references
     * may need to be registered as part of Interceptor registration.
     * <p>
     * This method may not be called during post_init.
     */
    public void register_initial_reference( String id,
                                            org.omg.CORBA.Object obj )
        throws InvalidName
    {
        checkStage();
        if( id == null ) nullParam();

        // As per CORBA 3.0 section 21.8.1,
        // if null is passed as the obj parameter,
        // throw BAD_PARAM with minor code OMGSystemException.RIR_WITH_NULL_OBJECT.
        // Though the spec is talking about IDL null, we will address both
        // Java null and IDL null:
        // Note: Local Objects can never be nil!
        if( obj == null ) {
            throw omgWrapper.rirWithNullObject() ;
        }

        // This check was made to determine that the objref is a
        // non-local objref that is fully
        // initialized: this was called only for its side-effects of
        // possibly throwing exceptions.  However, registering
        // local objects should be permitted!
        // XXX/Revisit?
        // IOR ior = ORBUtility.getIOR( obj ) ;

        // Delegate to ORB.  If ORB version throws InvalidName, convert to
        // equivalent Portable Interceptors InvalidName.
        try {
            orb.register_initial_reference( id, obj );
        } catch( org.omg.CORBA.ORBPackage.InvalidName e ) {
            InvalidName exc = new InvalidName( e.getMessage() );
            exc.initCause( e ) ;
            throw exc ;
        }
    }

    /**
     * This operation is only valid during post_init.  It is identical to
     * ORB::resolve_initial_references.  This same functionality exists here
     * because the ORB, not yet fully initialized, is not yet available,
     * but initial references may be required from the ORB as part
     * of Interceptor registration.
     * <p>
     * (incorporates changes from errata in orbos/00-01-01)
     * <p>
     * This method may not be called during pre_init.
     */
    public org.omg.CORBA.Object resolve_initial_references (String id)
        throws InvalidName
    {
        checkStage();
        if( id == null ) nullParam();

        if( stage == STAGE_PRE_INIT ) {
            // Initializer is not allowed to invoke this method during
            // this stage.

            // _REVISIT_ Spec issue: What exception should really be
            // thrown here?
            throw wrapper.rirInvalidPreInit() ;
        }

        org.omg.CORBA.Object objRef = null;

        try {
            objRef = orb.resolve_initial_references( id );
        }
        catch( org.omg.CORBA.ORBPackage.InvalidName e ) {
            // Convert PIDL to IDL exception:
            throw new InvalidName();
        }

        return objRef;
    }

    // New method from CORBA 3.1
    public void add_client_request_interceptor_with_policy (
        ClientRequestInterceptor interceptor, Policy[] policies )
        throws DuplicateName
    {
        // XXX ignore policies for now
        add_client_request_interceptor( interceptor ) ;
    }

    /**
     * This operation is used to add a client-side request Interceptor to
     * the list of client-side request Interceptors.
     * <p>
     * If a client-side request Interceptor has already been registered
     * with this Interceptor's name, DuplicateName is raised.
     */
    public void add_client_request_interceptor (
        ClientRequestInterceptor interceptor)
        throws DuplicateName
    {
        checkStage();
        if( interceptor == null ) nullParam();

        orb.getPIHandler().register_interceptor( interceptor,
            InterceptorList.INTERCEPTOR_TYPE_CLIENT );
    }

    // New method from CORBA 3.1
    public void add_server_request_interceptor_with_policy (
        ServerRequestInterceptor interceptor, Policy[] policies )
        throws DuplicateName, PolicyError
    {
        // XXX ignore policies for now
        add_server_request_interceptor( interceptor ) ;
    }

    /**
     * This operation is used to add a server-side request Interceptor to
     * the list of server-side request Interceptors.
     * <p>
     * If a server-side request Interceptor has already been registered
     * with this Interceptor's name, DuplicateName is raised.
     */
    public void add_server_request_interceptor (
        ServerRequestInterceptor interceptor)
        throws DuplicateName
    {
        checkStage();
        if( interceptor == null ) nullParam();

        orb.getPIHandler().register_interceptor( interceptor,
            InterceptorList.INTERCEPTOR_TYPE_SERVER );
    }

    // New method from CORBA 3.1
    public void add_ior_interceptor_with_policy (
        IORInterceptor interceptor, Policy[] policies )
        throws DuplicateName, PolicyError
    {
        // XXX ignore policies for now
        add_ior_interceptor( interceptor ) ;
    }

    /**
     * This operation is used to add an IOR Interceptor to
     * the list of IOR Interceptors.
     * <p>
     * If an IOR Interceptor has already been registered
     * with this Interceptor's name, DuplicateName is raised.
     */
    public void add_ior_interceptor (
        IORInterceptor interceptor )
        throws DuplicateName
    {
        checkStage();
        if( interceptor == null ) nullParam();

        orb.getPIHandler().register_interceptor( interceptor,
            InterceptorList.INTERCEPTOR_TYPE_IOR );
    }

    /**
     * A service calls allocate_slot_id to allocate a slot on
     * PortableInterceptor::Current.
     *
     * @return The index to the slot which has been allocated.
     */
    public int allocate_slot_id () {
        checkStage();

        return ((PICurrent)orb.getPIHandler().getPICurrent()).allocateSlotId( );

    }

    /**
     * Register a PolicyFactory for the given PolicyType.
     * <p>
     * If a PolicyFactory already exists for the given PolicyType,
     * BAD_INV_ORDER is raised with a minor code of TBD_BIO+2.
     */
    public void register_policy_factory( int type,
                                         PolicyFactory policy_factory )
    {
        checkStage();
        if( policy_factory == null ) nullParam();
        orb.getPIHandler().registerPolicyFactory( type, policy_factory );
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
}
