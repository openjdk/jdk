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

package com.sun.corba.se.impl.ior.iiop ;

import org.omg.CORBA.BAD_PARAM ;

import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.iiop.IIOPAddress ;

abstract class IIOPAddressBase implements IIOPAddress
{
    // Ports are marshalled as shorts on the wire.  The IDL
    // type is unsigned short, which lacks a convenient representation
    // in Java in the 32768-65536 range.  So, we treat ports as
    // ints throught this code, except that marshalling requires a
    // scaling conversion.  intToShort and shortToInt are provided
    // for this purpose.
    protected short intToShort( int value )
    {
        if (value > 32767)
            return (short)(value - 65536) ;
        return (short)value ;
    }

    protected int shortToInt( short value )
    {
        if (value < 0)
            return value + 65536 ;
        return value ;
    }

    public void write( OutputStream os )
    {
        os.write_string( getHost() ) ;
        int port = getPort() ;
        os.write_short( intToShort( port ) ) ;
    }

    public boolean equals( Object obj )
    {
        if (!(obj instanceof IIOPAddress))
            return false ;

        IIOPAddress other = (IIOPAddress)obj ;

        return getHost().equals(other.getHost()) &&
            (getPort() == other.getPort()) ;
    }

    public int hashCode()
    {
        return getHost().hashCode() ^ getPort() ;
    }

    public String toString()
    {
        return "IIOPAddress[" + getHost() + "," + getPort() + "]" ;
    }
}
