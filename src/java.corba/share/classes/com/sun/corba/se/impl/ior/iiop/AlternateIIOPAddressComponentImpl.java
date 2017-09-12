/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.TaggedComponentBase ;

import com.sun.corba.se.spi.ior.iiop.IIOPAddress ;
import com.sun.corba.se.spi.ior.iiop.AlternateIIOPAddressComponent ;

import org.omg.IOP.TAG_ALTERNATE_IIOP_ADDRESS ;

/**
 * @author Ken Cavanaugh
 */
public class AlternateIIOPAddressComponentImpl extends TaggedComponentBase
    implements AlternateIIOPAddressComponent
{
    private IIOPAddress addr ;

    public boolean equals( Object obj )
    {
        if (!(obj instanceof AlternateIIOPAddressComponentImpl))
            return false ;

        AlternateIIOPAddressComponentImpl other =
            (AlternateIIOPAddressComponentImpl)obj ;

        return addr.equals( other.addr ) ;
    }

    public int hashCode()
    {
        return addr.hashCode() ;
    }

    public String toString()
    {
        return "AlternateIIOPAddressComponentImpl[addr=" + addr + "]" ;
    }

    public AlternateIIOPAddressComponentImpl( IIOPAddress addr )
    {
        this.addr = addr ;
    }

    public IIOPAddress getAddress()
    {
        return addr ;
    }

    public void writeContents(OutputStream os)
    {
        addr.write( os ) ;
    }

    public int getId()
    {
        return TAG_ALTERNATE_IIOP_ADDRESS.value ; // 3 in CORBA 2.3.1 13.6.3
    }
}
