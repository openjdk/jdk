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

import com.sun.corba.se.impl.logging.NamingSystemException;
import com.sun.corba.se.spi.logging.CORBALogDomains;

/**
 *  The corbaname: URL definitions from the -ORBInitDef and -ORBDefaultInitDef's
 *  will be stored in this object. This object is capable of storing CorbaLoc
 *  profiles as defined in the CorbaName grammer.
 *
 *  @Author Hemanth
 */
public class CorbanameURL extends INSURLBase
{
    private static NamingSystemException wrapper =
        NamingSystemException.get( CORBALogDomains.NAMING ) ;

    /**
     * This constructor takes a corbaname: url with 'corbaname:' prefix stripped
     * and initializes all the variables accordingly. If there are any parsing
     * errors then BAD_PARAM exception is raised.
     */
    public CorbanameURL( String aURL ) {
        String url = aURL;

        // First Clean the URL Escapes if there are any
        try {
            url = Utility.cleanEscapes( url );
        } catch( Exception e ) {
            badAddress( e );
        }

        int delimiterIndex = url.indexOf( '#' );
        String corbalocString = null;
        if( delimiterIndex != -1 ) {
                // Append corbaloc: for Grammar check, Get the string between
                // corbaname: and # which forms the corbaloc string
                corbalocString = "corbaloc:" +
                    url.substring( 0, delimiterIndex ) + "/";
        } else {
            // Build a corbaloc string to check the grammar.
            // 10 is the length of corbaname:
            corbalocString = "corbaloc:" + url.substring( 0, url.length() );
            // If the string doesnot end with a / then add one to end the
            // URL correctly
            if( corbalocString.endsWith( "/" ) != true ) {
                corbalocString = corbalocString + "/";
            }
        }
        try {
            // Check the corbaloc grammar and set the returned corbaloc
            // object to the CorbaName Object
            INSURL insURL =
                INSURLHandler.getINSURLHandler().parseURL( corbalocString );
            copyINSURL( insURL );
            // String after '#' is the Stringified name used to resolve
            // the Object reference from the rootnaming context. If
            // the String is null then the Root Naming context is passed
            // back
            if((delimiterIndex > -1) &&
               (delimiterIndex < (aURL.length() - 1)))
            {
                int start = delimiterIndex + 1 ;
                String result = url.substring(start) ;
                theStringifiedName = result ;
            }
        } catch( Exception e ) {
            badAddress( e );
        }
    }

    /**
     * A Utility method to throw BAD_PARAM exception.
     */
    private void badAddress( java.lang.Throwable e )
        throws org.omg.CORBA.BAD_PARAM
    {
        throw wrapper.insBadAddress( e ) ;
    }

    /**
     * A Utility method to copy all the variables from CorbalocURL object to
     * this instance.
     */
    private void copyINSURL( INSURL url ) {
        rirFlag = url.getRIRFlag( );
        theEndpointInfo = (java.util.ArrayList) url.getEndpointInfo( );
        theKeyString = url.getKeyString( );
        theStringifiedName = url.getStringifiedName( );
    }

    public boolean isCorbanameURL( ) {
        return true;
    }

}
