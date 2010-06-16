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

package com.sun.corba.se.impl.orb ;

import org.omg.CORBA.portable.OutputStream ;

import com.sun.corba.se.spi.orb.ORBVersion ;

public class ORBVersionImpl implements ORBVersion {
    private byte orbType ;

    public ORBVersionImpl( byte orbType )
    {
        this.orbType = orbType ;
    }

    public static final ORBVersion FOREIGN = new ORBVersionImpl(
        ORBVersion.FOREIGN ) ;

    public static final ORBVersion OLD = new ORBVersionImpl(
        ORBVersion.OLD ) ;

    public static final ORBVersion NEW = new ORBVersionImpl(
        ORBVersion.NEW ) ;

    public static final ORBVersion JDK1_3_1_01 = new ORBVersionImpl(
        ORBVersion.JDK1_3_1_01 ) ;

    public static final ORBVersion NEWER = new ORBVersionImpl(
        ORBVersion.NEWER ) ;

    public static final ORBVersion PEORB = new ORBVersionImpl(
        ORBVersion.PEORB ) ;

    public byte getORBType()
    {
        return orbType ;
    }

    public void write( OutputStream os )
    {
        os.write_octet( (byte)orbType ) ;
    }

    public String toString()
    {
        return "ORBVersionImpl[" + Byte.toString( orbType ) + "]" ;
    }

    public boolean equals( Object obj )
    {
        if (!(obj instanceof ORBVersion))
            return false ;

        ORBVersion version = (ORBVersion)obj ;
        return version.getORBType() == orbType ;
    }

    public int hashCode()
    {
        return orbType ;
    }

    public boolean lessThan(ORBVersion version) {
        return orbType < version.getORBType();
    }

    public int compareTo(Object obj) {
        // The Comparable interface says that this
        // method throws a ClassCastException if the
        // given object's type prevents it from being
        // compared.
        return getORBType() - ((ORBVersion)obj).getORBType();
    }
}
