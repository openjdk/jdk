/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.resolver ;

import org.omg.CORBA.portable.InputStream ;
import org.omg.CORBA.portable.OutputStream ;
import org.omg.CORBA.portable.ApplicationException ;
import org.omg.CORBA.portable.RemarshalException ;

import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.ior.IORFactories ;
import com.sun.corba.se.spi.ior.IORTemplate ;
import com.sun.corba.se.spi.ior.ObjectKey ;
import com.sun.corba.se.spi.ior.ObjectKeyFactory ;
import com.sun.corba.se.spi.ior.iiop.IIOPAddress ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate ;
import com.sun.corba.se.spi.ior.iiop.IIOPFactories ;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.resolver.Resolver ;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.impl.orbutil.ORBUtility ;

public class BootstrapResolverImpl implements Resolver {
    private org.omg.CORBA.portable.Delegate bootstrapDelegate ;
    private ORBUtilSystemException wrapper ;

    public BootstrapResolverImpl(ORB orb, String host, int port) {
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.ORB_RESOLVER ) ;

        // Create a new IOR with the magic of INIT
        byte[] initialKey = "INIT".getBytes() ;
        ObjectKey okey = orb.getObjectKeyFactory().create(initialKey) ;

        IIOPAddress addr = IIOPFactories.makeIIOPAddress( orb, host, port ) ;
        IIOPProfileTemplate ptemp = IIOPFactories.makeIIOPProfileTemplate(
            orb, GIOPVersion.V1_0, addr);

        IORTemplate iortemp = IORFactories.makeIORTemplate( okey.getTemplate() ) ;
        iortemp.add( ptemp ) ;

        IOR initialIOR = iortemp.makeIOR( (com.sun.corba.se.spi.orb.ORB)orb,
            "", okey.getId() ) ;

        bootstrapDelegate = ORBUtility.makeClientDelegate( initialIOR ) ;
    }

    /**
     * For the BootStrap operation we do not expect to have more than one
     * parameter. We do not want to extend BootStrap protocol any further,
     * as INS handles most of what BootStrap can handle in a portable way.
     *
     * @return InputStream which contains the response from the
     * BootStrapOperation.
     */
    private InputStream invoke( String operationName, String parameter )
    {
        boolean remarshal = true;

        // Invoke.

        InputStream inStream = null;

        // If there is a location forward then you will need
        // to invoke again on the updated information.
        // Just calling this same routine with the same host/port
        // does not take the location forward info into account.

        while (remarshal) {
            org.omg.CORBA.Object objref = null ;
            remarshal = false;

            OutputStream os = (OutputStream) bootstrapDelegate.request( objref,
                operationName, true);

            if ( parameter != null ) {
                os.write_string( parameter );
            }

            try {
                // The only reason a null objref is passed is to get the version of
                // invoke used by streams.  Otherwise the PortableInterceptor
                // call stack will become unbalanced since the version of
                // invoke which only takes the stream does not call
                // PortableInterceptor ending points.
                // Note that the first parameter is ignored inside invoke.

                inStream = bootstrapDelegate.invoke( objref, os);
            } catch (ApplicationException e) {
                throw wrapper.bootstrapApplicationException( e ) ;
            } catch (RemarshalException e) {
                // XXX log this
                remarshal = true;
            }
        }

        return inStream;
    }

    public org.omg.CORBA.Object resolve( String identifier )
    {
        InputStream inStream = null ;
        org.omg.CORBA.Object result = null ;

        try {
            inStream = invoke( "get", identifier ) ;

            result = inStream.read_Object();

            // NOTE: do note trap and ignore errors.
            // Let them flow out.
        } finally {
            bootstrapDelegate.releaseReply( null, inStream ) ;
        }

        return result ;
    }

    public java.util.Set list()
    {
        InputStream inStream = null ;
        java.util.Set result = new java.util.HashSet() ;

        try {
            inStream = invoke( "list", null ) ;

            int count = inStream.read_long();
            for (int i=0; i < count; i++)
                result.add( inStream.read_string() ) ;

            // NOTE: do note trap and ignore errors.
            // Let them flow out.
        } finally {
            bootstrapDelegate.releaseReply( null, inStream ) ;
        }

        return result ;
    }
}
