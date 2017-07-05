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

package com.sun.corba.se.impl.protocol ;

import java.util.Iterator ;

import org.omg.CORBA.SystemException ;

import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.ior.ObjectKey ;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher ;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.impl.encoding.MarshalInputStream ;
import com.sun.corba.se.impl.encoding.MarshalOutputStream ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

/**
 * Class BootstrapServerRequestDispatcher handles the requests coming to the
 * BootstrapServer. It implements Server so that it can be registered
 * as a subcontract. It is passed a BootstrapServiceProperties object
 * which contains
 * the supported ids and their values for the bootstrap service. This
 * Properties object is only read from, never written to, and is shared
 * among all threads.
 * <p>
 * The BootstrapServerRequestDispatcher responds primarily to GIOP requests,
 * but LocateRequests are also handled for graceful interoperability.
 * The BootstrapServerRequestDispatcher handles one request at a time.
 */
public class BootstrapServerRequestDispatcher
    implements CorbaServerRequestDispatcher
{
    private ORB orb;

    ORBUtilSystemException wrapper ;

    private static final boolean debug = false;

    public BootstrapServerRequestDispatcher(ORB orb )
    {
        this.orb = orb;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
    }

    /**
     * Dispatch is called by the ORB and will serve get(key) and list()
     * invocations on the initial object key.
     */
    public void dispatch(MessageMediator messageMediator)
    {
        CorbaMessageMediator request = (CorbaMessageMediator) messageMediator;
        CorbaMessageMediator response = null;

        try {
            MarshalInputStream is = (MarshalInputStream)
                request.getInputObject();
            String method = request.getOperationName();
            response = request.getProtocolHandler().createResponse(request, null);
            MarshalOutputStream os = (MarshalOutputStream)
                response.getOutputObject();

            if (method.equals("get")) {
                // Get the name of the requested service
                String serviceKey = is.read_string();

                // Look it up
                org.omg.CORBA.Object serviceObject =
                    orb.getLocalResolver().resolve( serviceKey ) ;

                // Write reply value
                os.write_Object(serviceObject);
            } else if (method.equals("list")) {
                java.util.Set keys = orb.getLocalResolver().list() ;
                os.write_long( keys.size() ) ;
                Iterator iter = keys.iterator() ;
                while (iter.hasNext()) {
                    String obj = (String)iter.next() ;
                    os.write_string( obj ) ;
                }
            } else {
                throw wrapper.illegalBootstrapOperation( method ) ;
            }

        } catch (org.omg.CORBA.SystemException ex) {
            // Marshal the exception thrown
            response = request.getProtocolHandler().createSystemExceptionResponse(
                request, ex, null);
        } catch (java.lang.RuntimeException ex) {
            // Unknown exception
            SystemException sysex = wrapper.bootstrapRuntimeException( ex ) ;
            response = request.getProtocolHandler().createSystemExceptionResponse(
                 request, sysex, null ) ;
        } catch (java.lang.Exception ex) {
            // Unknown exception
            SystemException sysex = wrapper.bootstrapException( ex ) ;
            response = request.getProtocolHandler().createSystemExceptionResponse(
                 request, sysex, null ) ;
        }

        return;
    }

    /**
     * Locates the object mentioned in the locate requests, and returns
     * object here iff the object is the initial object key. A SystemException
     * thrown if the object key is not the initial object key.
     */
    public IOR locate( ObjectKey objectKey) {
        return null;
    }

    /**
     * Not implemented
     */
    public int getId() {
        throw wrapper.genericNoImpl() ;
    }
}
