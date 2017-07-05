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

package com.sun.corba.se.spi.orb ;

import com.sun.corba.se.spi.orb.ORBVersion ;
import com.sun.corba.se.impl.orb.ORBVersionImpl ;
import org.omg.CORBA.portable.InputStream ;
import org.omg.CORBA.INTERNAL ;

public class ORBVersionFactory {
    private ORBVersionFactory() {} ;

    public static ORBVersion getFOREIGN()
    {
        return ORBVersionImpl.FOREIGN ;
    }

    public static ORBVersion getOLD()
    {
        return ORBVersionImpl.OLD ;
    }

    public static ORBVersion getNEW()
    {
        return ORBVersionImpl.NEW ;
    }

    public static ORBVersion getJDK1_3_1_01()
    {
        return ORBVersionImpl.JDK1_3_1_01 ;
    }

    public static ORBVersion getNEWER()
    {
        return ORBVersionImpl.NEWER ;
    }

    public static ORBVersion getPEORB()
    {
        return ORBVersionImpl.PEORB ;
    }

    /** Return the current version of this ORB
     */
    public static ORBVersion getORBVersion()
    {
        return ORBVersionImpl.PEORB ;
    }

    public static ORBVersion create( InputStream is )
    {
        byte value = is.read_octet() ;
        return byteToVersion( value ) ;
    }

    private static ORBVersion byteToVersion( byte value )
    {
        /* Throwing an exception here would cause this version to be
        * incompatible with future versions of the ORB, to the point
        * that this version could
        * not even unmarshal objrefs from a newer version that uses
        * extended versioning.  Therefore, we will simply treat all
        * unknown versions as the latest version.
        if (value < 0)
            throw new INTERNAL() ;
        */

        /**
         * Update: If we treat all unknown versions as the latest version
         * then when we send an IOR with a PEORB version to an ORB that
         * doesn't know the PEORB version it will treat it as whatever
         * its idea of the latest version is.  Then, if that IOR is
         * sent back to the server and compared with the original
         * the equality check will fail because the versions will be
         * different.
         *
         * Instead, just capture the version bytes.
         */

        switch (value) {
            case ORBVersion.FOREIGN : return ORBVersionImpl.FOREIGN ;
            case ORBVersion.OLD : return ORBVersionImpl.OLD ;
            case ORBVersion.NEW : return ORBVersionImpl.NEW ;
            case ORBVersion.JDK1_3_1_01: return ORBVersionImpl.JDK1_3_1_01 ;
            case ORBVersion.NEWER : return ORBVersionImpl.NEWER ;
            case ORBVersion.PEORB : return ORBVersionImpl.PEORB ;
            default : return new ORBVersionImpl(value);
        }
    }
}
