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

package com.sun.corba.se.impl.naming.namingutil;

/** The corbaloc: URL definitions from the -ORBInitDef and -ORBDefaultInitDef's
 *  will be stored in this object. This object is capable of storing multiple
 *  Host profiles as defined in the CorbaLoc grammer.
 *
 *  @author  Hemanth
 */
public abstract class INSURLBase implements INSURL {

    // If rirFlag is set to true that means internal
    // boot strapping technique will be used. If set to
    // false then the EndpointInfo will be used to create the
    // Service Object reference.
    protected boolean rirFlag = false ;
    protected java.util.ArrayList theEndpointInfo = null ;
    protected String theKeyString = "NameService" ;
    protected String theStringifiedName = null ;

    public boolean getRIRFlag( ) {
        return rirFlag;
    }

    public java.util.List getEndpointInfo( ) {
        return theEndpointInfo;
    }

    public String getKeyString( ) {
        return theKeyString;
    }

    public String getStringifiedName( ) {
        return theStringifiedName;
    }

    public abstract boolean isCorbanameURL( );

    public void dPrint( ) {
        System.out.println( "URL Dump..." );
        System.out.println( "Key String = " + getKeyString( ) );
        System.out.println( "RIR Flag = " + getRIRFlag( ) );
        System.out.println( "isCorbanameURL = " + isCorbanameURL() );
        for( int i = 0; i < theEndpointInfo.size( ); i++ ) {
            ((IIOPEndpointInfo) theEndpointInfo.get( i )).dump( );
        }
        if( isCorbanameURL( ) ) {
            System.out.println( "Stringified Name = " + getStringifiedName() );
        }
    }

}
