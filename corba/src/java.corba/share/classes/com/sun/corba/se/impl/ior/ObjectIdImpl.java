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

package com.sun.corba.se.impl.ior;

import java.util.Arrays ;
import com.sun.corba.se.spi.ior.ObjectId ;
import org.omg.CORBA_2_3.portable.OutputStream ;

public final class ObjectIdImpl implements ObjectId
{
    private byte[] id;

    public boolean equals( Object obj )
    {
        if (!(obj instanceof ObjectIdImpl))
            return false ;

        ObjectIdImpl other = (ObjectIdImpl)obj ;

        return Arrays.equals( this.id, other.id ) ;
    }

    public int hashCode()
    {
        int result = 17 ;
        for (int ctr=0; ctr<id.length; ctr++)
            result = 37*result + id[ctr] ;
        return result ;
    }

    public ObjectIdImpl( byte[] id )
    {
        this.id = id ;
    }

    public byte[] getId()
    {
        return id ;
    }

    public void write( OutputStream os )
    {
        os.write_long( id.length ) ;
        os.write_octet_array( id, 0, id.length ) ;
    }
}
