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

package com.sun.corba.se.impl.ior ;

import java.util.Iterator ;

import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.ObjectAdapterId ;

abstract class ObjectAdapterIdBase implements ObjectAdapterId {
    public boolean equals( Object other )
    {
        if (!(other instanceof ObjectAdapterId))
            return false ;

        ObjectAdapterId theOther = (ObjectAdapterId)other ;

        Iterator iter1 = iterator() ;
        Iterator iter2 = theOther.iterator() ;

        while (iter1.hasNext() && iter2.hasNext()) {
            String str1 = (String)(iter1.next()) ;
            String str2 = (String)(iter2.next()) ;

            if (!str1.equals( str2 ))
                return false ;
        }

        return iter1.hasNext() == iter2.hasNext() ;
    }

    public int hashCode()
    {
        int result = 17 ;
        Iterator iter = iterator() ;
        while (iter.hasNext()) {
            String str = (String)(iter.next()) ;
            result = 37*result + str.hashCode() ;
        }
        return result ;
    }

    public String toString()
    {
        StringBuffer buff = new StringBuffer() ;
        buff.append( "ObjectAdapterID[" ) ;
        Iterator iter = iterator() ;
        boolean first = true ;
        while (iter.hasNext()) {
            String str = (String)(iter.next()) ;

            if (first)
                first = false ;
            else
                buff.append( "/" ) ;

            buff.append( str ) ;
        }

        buff.append( "]" ) ;

        return buff.toString() ;
    }

    public void write( OutputStream os )
    {
        os.write_long( getNumLevels() ) ;
        Iterator iter = iterator() ;
        while (iter.hasNext()) {
            String str = (String)(iter.next()) ;
            os.write_string( str ) ;
        }
    }
}
