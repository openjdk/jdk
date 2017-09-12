/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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
import org.omg.CORBA.ARG_IN;
import org.omg.CORBA.ARG_OUT;
import org.omg.CORBA.ARG_INOUT;
import org.omg.CORBA.Context;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.Environment;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.NVList;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.Request;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.UnknownUserException;
import org.omg.CORBA.Bounds;
import org.omg.CORBA.UNKNOWN;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.WrongTransaction;

import org.omg.CORBA.portable.ApplicationException ;
import org.omg.CORBA.portable.RemarshalException ;
import org.omg.CORBA.portable.InputStream ;
import org.omg.CORBA.portable.OutputStream ;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.corba.AsynchInvoke;

public class RequestImpl
    extends Request
{
    ///////////////////////////////////////////////////////////////////////////
    // data members

    protected org.omg.CORBA.Object _target;
    protected String             _opName;
    protected NVList             _arguments;
    protected ExceptionList      _exceptions;
    private NamedValue           _result;
    protected Environment        _env;
    private Context              _ctx;
    private ContextList          _ctxList;
    protected ORB                _orb;
    private ORBUtilSystemException _wrapper;

    // invocation-specific stuff
    protected boolean            _isOneWay      = false;
    private int[]                _paramCodes;
    private long[]               _paramLongs;
    private java.lang.Object[]   _paramObjects;

    // support for deferred invocations.
    // protected instead of private since it needs to be set by the
    // thread object doing the asynchronous invocation.
    protected boolean            gotResponse    = false;

    ///////////////////////////////////////////////////////////////////////////
    // constructor

    // REVISIT - used to be protected.  Now public so it can be
    // accessed from xgiop.
    public RequestImpl (ORB orb,
                        org.omg.CORBA.Object targetObject,
                        Context ctx,
                        String operationName,
                        NVList argumentList,
                        NamedValue resultContainer,
                        ExceptionList exceptionList,
                        ContextList ctxList)
    {

        // initialize the orb
        _orb    = orb;
        _wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.OA_INVOCATION ) ;

        // initialize target, context and operation name
        _target     = targetObject;
        _ctx    = ctx;
        _opName = operationName;

        // initialize argument list if not passed in
        if (argumentList == null)
            _arguments = new NVListImpl(_orb);
        else
            _arguments = argumentList;

        // set result container.
        _result = resultContainer;

        // initialize exception list if not passed in
        if (exceptionList == null)
            _exceptions = new ExceptionListImpl();
        else
            _exceptions = exceptionList;

        // initialize context list if not passed in
        if (ctxList == null)
            _ctxList = new ContextListImpl(_orb);
        else
            _ctxList = ctxList;

        // initialize environment
        _env    = new EnvironmentImpl();

    }

    public org.omg.CORBA.Object target()
    {
        return _target;
    }

    public String operation()
    {
        return _opName;
    }

    public NVList arguments()
    {
        return _arguments;
    }

    public NamedValue result()
    {
        return _result;
    }

    public Environment env()
    {
        return _env;
    }

    public ExceptionList exceptions()
    {
        return _exceptions;
    }

    public ContextList contexts()
    {
        return _ctxList;
    }

    public synchronized Context ctx()
    {
        if (_ctx == null)
            _ctx = new ContextImpl(_orb);
        return _ctx;
    }

    public synchronized void ctx(Context newCtx)
    {
        _ctx = newCtx;
    }

    public synchronized Any add_in_arg()
    {
        return _arguments.add(org.omg.CORBA.ARG_IN.value).value();
    }

    public synchronized Any add_named_in_arg(String name)
    {
        return _arguments.add_item(name, org.omg.CORBA.ARG_IN.value).value();
    }

    public synchronized Any add_inout_arg()
    {
        return _arguments.add(org.omg.CORBA.ARG_INOUT.value).value();
    }

    public synchronized Any add_named_inout_arg(String name)
    {
        return _arguments.add_item(name, org.omg.CORBA.ARG_INOUT.value).value();
    }

    public synchronized Any add_out_arg()
    {
        return _arguments.add(org.omg.CORBA.ARG_OUT.value).value();
    }

    public synchronized Any add_named_out_arg(String name)
    {
        return _arguments.add_item(name, org.omg.CORBA.ARG_OUT.value).value();
    }

    public synchronized void set_return_type(TypeCode tc)
    {
        if (_result == null)
            _result = new NamedValueImpl(_orb);
        _result.value().type(tc);
    }

    public synchronized Any return_value()
    {
        if (_result == null)
            _result = new NamedValueImpl(_orb);
        return _result.value();
    }

    public synchronized void add_exception(TypeCode exceptionType)
    {
        _exceptions.add(exceptionType);
    }

    public synchronized void invoke()
    {
        doInvocation();
    }

    public synchronized void send_oneway()
    {
        _isOneWay = true;
        doInvocation();
    }

    public synchronized void send_deferred()
    {
        AsynchInvoke invokeObject = new AsynchInvoke(_orb, this, false);
        new Thread(null, invokeObject, "Async-Request-Invoker-Thread", 0, false).start();
    }

    public synchronized boolean poll_response()
    {
        // this method has to be synchronized even though it seems
        // "readonly" since the thread object doing the asynchronous
        // invocation can potentially update this variable in parallel.
        // updates are currently simply synchronized againt the request
        // object.
        return gotResponse;
    }

    public synchronized void get_response()
        throws org.omg.CORBA.WrongTransaction
    {
        while (gotResponse == false) {
            // release the lock. wait to be notified by the thread that is
            // doing the asynchronous invocation.
            try {
                wait();
            }
            catch (InterruptedException e) {}
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // private helper methods

    /*
     * The doInvocation operation is where the real mechanics of
     * performing the request invocation is done.
     */
    protected void doInvocation()
    {
        org.omg.CORBA.portable.Delegate delegate = StubAdapter.getDelegate(
            _target ) ;

        // Initiate Client Portable Interceptors.  Inform the PIHandler that
        // this is a DII request so that it knows to ignore the second
        // inevitable call to initiateClientPIRequest in createRequest.
        // Also, save the RequestImpl object for later use.
        _orb.getPIHandler().initiateClientPIRequest( true );
        _orb.getPIHandler().setClientPIInfo( this );

        InputStream $in = null;
        try {
            OutputStream $out = delegate.request(null, _opName, !_isOneWay);
            // Marshal args
            try {
                for (int i=0; i<_arguments.count() ; i++) {
                    NamedValue nv = _arguments.item(i);
                    switch (nv.flags()) {
                    case ARG_IN.value:
                        nv.value().write_value($out);
                        break;
                    case ARG_OUT.value:
                        break;
                    case ARG_INOUT.value:
                        nv.value().write_value($out);
                        break;
                    }
                }
            } catch ( org.omg.CORBA.Bounds ex ) {
                throw _wrapper.boundsErrorInDiiRequest( ex ) ;
            }

            $in = delegate.invoke(null, $out);
        } catch (ApplicationException e) {
            // REVISIT - minor code.
            // This is already handled in subcontract.
            // REVISIT - uncomment.
            //throw new INTERNAL();
        } catch (RemarshalException e) {
            doInvocation();
        } catch( SystemException ex ) {
            _env.exception(ex);
            // NOTE: The exception should not be thrown.
            // However, JDK 1.4 and earlier threw the exception,
            // so we keep the behavior to be compatible.
            throw ex;
        } finally {
            delegate.releaseReply(null, $in);
        }
    }

    // REVISIT -  make protected after development - so xgiop can get it.
    public void unmarshalReply(InputStream is)
    {
        // First unmarshal the return value if it is not void
        if ( _result != null ) {
            Any returnAny = _result.value();
            TypeCode returnType = returnAny.type();
            if ( returnType.kind().value() != TCKind._tk_void )
                returnAny.read_value(is, returnType);
        }

        // Now unmarshal the out/inout args
        try {
            for ( int i=0; i<_arguments.count() ; i++) {
                NamedValue nv = _arguments.item(i);
                switch( nv.flags() ) {
                case ARG_IN.value:
                    break;
                case ARG_OUT.value:
                case ARG_INOUT.value:
                    Any any = nv.value();
                    any.read_value(is, any.type());
                    break;
                }
            }
        }
        catch ( org.omg.CORBA.Bounds ex ) {
            // Cannot happen since we only iterate till _arguments.count()
        }
    }
}
