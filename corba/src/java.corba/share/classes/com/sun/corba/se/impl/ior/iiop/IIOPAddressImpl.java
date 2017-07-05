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

package com.sun.corba.se.impl.ior.iiop;

import org.omg.CORBA.BAD_PARAM ;

import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.IORSystemException ;

public final class IIOPAddressImpl extends IIOPAddressBase
{
    private ORB orb ;
    private IORSystemException wrapper ;
    private String host;
    private int port;

    public IIOPAddressImpl( ORB orb, String host, int port )
    {
        this.orb = orb ;
        wrapper = IORSystemException.get( orb,
            CORBALogDomains.OA_IOR ) ;

        if ((port < 0) || (port > 65535))
            throw wrapper.badIiopAddressPort( new Integer(port)) ;

        this.host = host ;
        this.port = port ;
    }

    public IIOPAddressImpl( InputStream is )
    {
        host = is.read_string() ;
        short thePort = is.read_short() ;
        port = shortToInt( thePort ) ;
    }

    public String getHost()
    {
        return host ;
    }

    public int getPort()
    {
        return port ;
    }
}
