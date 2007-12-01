/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.corba.se.spi.servicecontext;

import org.omg.CORBA.SystemException;
import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.orb.ORBVersion ;
import com.sun.corba.se.spi.orb.ORBVersionFactory ;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.servicecontext.ServiceContext ;
import com.sun.corba.se.impl.orbutil.ORBConstants ;

public class ORBVersionServiceContext extends ServiceContext {

    public ORBVersionServiceContext( )
    {
        version = ORBVersionFactory.getORBVersion() ;
    }

    public ORBVersionServiceContext( ORBVersion ver )
    {
        this.version = ver ;
    }

    public ORBVersionServiceContext(InputStream is, GIOPVersion gv)
    {
        super(is, gv) ;
        // pay particular attention to where the version is being read from!
        // is contains an encapsulation, ServiceContext reads off the
        // encapsulation and leaves the pointer in the variable "in",
        // which points to the long value.

        version = ORBVersionFactory.create( in ) ;
    }

    // Required SERVICE_CONTEXT_ID and getId definitions
    public static final int SERVICE_CONTEXT_ID = ORBConstants.TAG_ORB_VERSION ;
    public int getId() { return SERVICE_CONTEXT_ID ; }

    public void writeData( OutputStream os ) throws SystemException
    {
        version.write( os ) ;
    }

    public ORBVersion getVersion()
    {
        return version ;
    }

    // current ORB Version
    private ORBVersion version = ORBVersionFactory.getORBVersion() ;

    public String toString()
    {
        return "ORBVersionServiceContext[ version=" + version + " ]" ;
    }
}
