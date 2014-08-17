/*
 * Copyright (c) 1996, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.corba;

import org.omg.CORBA.Any;
import org.omg.CORBA.Context;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.NVList;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.ServerRequest;
import org.omg.CORBA.Bounds;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.CompletionStatus;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class ServerRequestImpl extends ServerRequest {

    ///////////////////////////////////////////////////////////////////////////
    // data members

    private ORB                  _orb           = null;
    private ORBUtilSystemException _wrapper     = null;
    private String               _opName        = null;
    private NVList               _arguments     = null;
    private Context              _ctx           = null;
    private InputStream          _ins           = null;

    // booleans to check for various operation invocation restrictions
    private boolean             _paramsCalled   = false;
    private boolean             _resultSet      = false;
    private boolean             _exceptionSet   = false;
    private Any                 _resultAny      = null;
    private Any                 _exception      = null;


    public ServerRequestImpl (CorbaMessageMediator req, ORB orb) {
        _opName = req.getOperationName();
        _ins    = (InputStream)req.getInputObject();
        _ctx    = null;         // if we support contexts, this would
                                // presumably also  be available on
                                // the server invocation
        _orb = orb;
        _wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.OA_INVOCATION ) ;
    }

    public String operation() {
        return _opName;
    }

    public void arguments(NVList args)
    {
        if (_paramsCalled)
            throw _wrapper.argumentsCalledMultiple() ;

        if (_exceptionSet)
            throw _wrapper.argumentsCalledAfterException() ;

        if (args == null )
            throw _wrapper.argumentsCalledNullArgs() ;

        _paramsCalled = true;

        NamedValue arg = null;
        for (int i=0; i < args.count() ; i++) {
            try {
                arg = args.item(i);
            } catch (Bounds e) {
                throw _wrapper.boundsCannotOccur(e) ;
            }

            try {
                if ((arg.flags() == org.omg.CORBA.ARG_IN.value) ||
                    (arg.flags() == org.omg.CORBA.ARG_INOUT.value)) {
                    // unmarshal the value into the Any
                    arg.value().read_value(_ins, arg.value().type());
                }
            } catch ( Exception ex ) {
                throw _wrapper.badArgumentsNvlist( ex ) ;
            }
        }

        // hang on to the NVList for marshaling the result
        _arguments = args;

        _orb.getPIHandler().setServerPIInfo( _arguments );
        _orb.getPIHandler().invokeServerPIIntermediatePoint();
    }

    public void set_result(Any res) {
        // check for invocation restrictions
        if (!_paramsCalled)
            throw _wrapper.argumentsNotCalled() ;
        if (_resultSet)
            throw _wrapper.setResultCalledMultiple() ;
        if (_exceptionSet)
            throw _wrapper.setResultAfterException() ;
        if ( res == null )
            throw _wrapper.setResultCalledNullArgs() ;

        _resultAny = res;
        _resultSet = true;

        // Notify portable interceptors of the result so that
        // ServerRequestInfo.result() functions as desired.
        _orb.getPIHandler().setServerPIInfo( _resultAny );

        // actual marshaling of the reply msg header and params takes place
        // after the DSI returns control to the ORB.
    }

    public void set_exception(Any exc)
    {
        // except can be called by the DIR at any time (CORBA 2.2 section 6.3).

        if ( exc == null )
            throw _wrapper.setExceptionCalledNullArgs() ;

        // Ensure that the Any contains a SystemException or a
        // UserException. If the UserException is not a declared exception,
        // the client will get an UNKNOWN exception.
        TCKind kind = exc.type().kind();
        if ( kind != TCKind.tk_except )
            throw _wrapper.setExceptionCalledBadType() ;

        _exception = exc;

        // Inform Portable interceptors of the exception that was set
        // so sending_exception can return the right value.
        _orb.getPIHandler().setServerPIExceptionInfo( _exception );

        // The user can only call arguments once and not at all after
        // set_exception.  (internal flags ensure this).  However, the user
        // can call set_exception multiple times.  Therefore, we only
        // invoke receive_request the first time set_exception is
        // called (if they haven't already called arguments).
        if( !_exceptionSet && !_paramsCalled ) {
            // We need to invoke intermediate points here.
            _orb.getPIHandler().invokeServerPIIntermediatePoint();
        }

        _exceptionSet = true;

        // actual marshaling of the reply msg header and exception takes place
        // after the DSI returns control to the ORB.
    }


    /** This is called from the ORB after the DynamicImplementation.invoke
     *  returns. Here we set the result if result() has not already been called.
     *  @return the exception if there is one (then ORB will not call
     *  marshalReplyParams()) otherwise return null.
     */
    public Any checkResultCalled()
    {
        // Two things to be checked (CORBA 2.2 spec, section 6.3):
        // 1. Unless it calls set_exception(), the DIR must call arguments()
        //    exactly once, even if the operation signature contains
        //    no parameters.
        // 2. Unless set_exception() is called, if the invoked operation has a
        //    non-void result type, set_result() must be called exactly once
        //    before the DIR returns.

        if ( _paramsCalled && _resultSet ) // normal invocation return
            return null;
        else if ( _paramsCalled && !_resultSet && !_exceptionSet ) {
            try {
                // Neither a result nor an exception has been set.
                // Assume that the return type is void. If this is not so,
                // the client will throw a MARSHAL exception while
                // unmarshaling the return value.
                TypeCode result_tc = _orb.get_primitive_tc(
                    org.omg.CORBA.TCKind.tk_void);
                _resultAny = _orb.create_any();
                _resultAny.type(result_tc);
                _resultSet = true;

                return null;
            } catch ( Exception ex ) {
                throw _wrapper.dsiResultException(
                    CompletionStatus.COMPLETED_MAYBE, ex ) ;
            }
        } else if ( _exceptionSet )
            return _exception;
        else {
            throw _wrapper.dsimethodNotcalled(
                CompletionStatus.COMPLETED_MAYBE ) ;
        }
    }

    /** This is called from the ORB after the DynamicImplementation.invoke
     *  returns. Here we marshal the return value and inout/out params.
     */
    public void marshalReplyParams(OutputStream os)
    {
        // marshal the operation return value
        _resultAny.write_value(os);

        // marshal the inouts/outs
        NamedValue arg = null;

        for (int i=0; i < _arguments.count() ; i++) {
            try {
                arg = _arguments.item(i);
            } catch (Bounds e) {}

            if ((arg.flags() == org.omg.CORBA.ARG_OUT.value) ||
                (arg.flags() == org.omg.CORBA.ARG_INOUT.value)) {
                arg.value().write_value(os);
            }
        }
    }

    public Context ctx()
    {
        if ( !_paramsCalled || _resultSet || _exceptionSet )
            throw _wrapper.contextCalledOutOfOrder() ;

        throw _wrapper.contextNotImplemented() ;
    }
}
