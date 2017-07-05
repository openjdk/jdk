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

package com.sun.corba.se.impl.naming.namingutil;

import com.sun.corba.se.impl.orbutil.ORBConstants;

/**
 *  EndpointInfo is used internally by CorbaLoc object to store the
 *  host information used in creating the Service Object reference
 *  from the -ORBInitDef and -ORBDefaultInitDef definitions.
 *
 *  @Author Hemanth
 */
public class IIOPEndpointInfo
{
    // Version information
    private int major, minor;

    // Host Name and Port Number
    private String host;
    private int port;

    IIOPEndpointInfo( ) {
        // Default IIOP Version
        major = ORBConstants.DEFAULT_INS_GIOP_MAJOR_VERSION;
        minor = ORBConstants.DEFAULT_INS_GIOP_MINOR_VERSION;
        // Default host is localhost
        host = ORBConstants.DEFAULT_INS_HOST;
        // Default INS Port
        port = ORBConstants.DEFAULT_INS_PORT;
    }

    public void setHost( String theHost ) {
        host = theHost;
    }

    public String getHost( ) {
        return host;
    }

    public void setPort( int thePort ) {
        port = thePort;
    }

    public int getPort( ) {
        return port;
    }

    public void setVersion( int theMajor, int theMinor ) {
        major = theMajor;
        minor = theMinor;
    }

    public int getMajor( ) {
        return major;
    }

    public int getMinor( ) {
        return minor;
    }

    /** Internal Debug Method.
     */
    public void dump( ) {
        System.out.println( " Major -> " + major + " Minor -> " + minor );
        System.out.println( "host -> " + host );
        System.out.println( "port -> " + port );
    }
}
