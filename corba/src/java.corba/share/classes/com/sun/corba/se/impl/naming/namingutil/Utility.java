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

import java.io.StringWriter;

import org.omg.CORBA.DATA_CONVERSION;
import org.omg.CORBA.CompletionStatus;

import com.sun.corba.se.impl.logging.NamingSystemException;
import com.sun.corba.se.spi.logging.CORBALogDomains;

/**
 *  Utility methods for Naming.
 *
 *  @author Hemanth
 */
class Utility {
    private static NamingSystemException wrapper =
        NamingSystemException.get( CORBALogDomains.NAMING ) ;

    /**
     * cleanEscapes removes URL escapes as per IETF 2386 RFP.
     */
    static String cleanEscapes( String stringToDecode ) {
        StringWriter theStringWithoutEscape = new StringWriter();
        for( int i = 0; i < stringToDecode.length(); i++ ) {
            char c = stringToDecode.charAt( i ) ;
            if( c != '%' ) {
                theStringWithoutEscape.write( c );
            } else {
                // Get the two hexadecimal digits and convert that into int
                i++;
                int Hex1 = hexOf( stringToDecode.charAt(i) );
                i++;
                int Hex2 = hexOf( stringToDecode.charAt(i) );
                int value = (Hex1 * 16) + Hex2;
                // Convert the integer to ASCII
                theStringWithoutEscape.write( (char) value );
            }
        }
        return theStringWithoutEscape.toString();
    }

    /**
     *  Converts an Ascii Character into Hexadecimal digit
     *  NOTE: THIS METHOD IS DUPLICATED TO DELIVER NAMING AS A SEPARATE
     *  COMPONENT TO RI.
     **/
    static int hexOf( char x )
    {
        int val;

        val = x - '0';
        if (val >=0 && val <= 9)
            return val;

        val = (x - 'a') + 10;
        if (val >= 10 && val <= 15)
            return val;

        val = (x - 'A') + 10;
        if (val >= 10 && val <= 15)
            return val;

        throw new DATA_CONVERSION( );
    }

    /**
     * If GIOP Version is not correct, This method throws a BAD_PARAM
     * Exception.
     **/
    static void validateGIOPVersion( IIOPEndpointInfo endpointInfo ) {
        if ((endpointInfo.getMajor() > NamingConstants.MAJORNUMBER_SUPPORTED) ||
            (endpointInfo.getMinor() > NamingConstants.MINORNUMBERMAX ) )
        {
            throw wrapper.insBadAddress() ;
        }
    }
}
