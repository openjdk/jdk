/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.corba.se.impl.ior;

import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.ObjectKey ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;

import com.sun.corba.se.impl.encoding.EncapsOutputStream ;

/**
 * @author
 */
public class ObjectKeyImpl implements ObjectKey
{
    private ObjectKeyTemplate oktemp;
    private ObjectId id;

    public boolean equals( Object obj )
    {
        if (obj == null)
            return false ;

        if (!(obj instanceof ObjectKeyImpl))
            return false ;

        ObjectKeyImpl other = (ObjectKeyImpl)obj ;

        return oktemp.equals( other.oktemp ) &&
            id.equals( other.id ) ;
    }

    public int hashCode()
    {
        return oktemp.hashCode() ^ id.hashCode() ;
    }

    public ObjectKeyTemplate getTemplate()
    {
        return oktemp ;
    }

    public ObjectId getId()
    {
        return id ;
    }

    public ObjectKeyImpl( ObjectKeyTemplate oktemp, ObjectId id )
    {
        this.oktemp = oktemp ;
        this.id = id ;
    }

    public void write( OutputStream os )
    {
        oktemp.write( id, os ) ;
    }

    public byte[] getBytes( org.omg.CORBA.ORB orb )
    {
        EncapsOutputStream os = new EncapsOutputStream( (ORB)orb ) ;
        write( os ) ;
        return os.toByteArray() ;
    }

    public CorbaServerRequestDispatcher getServerRequestDispatcher( ORB orb )
    {
        return oktemp.getServerRequestDispatcher( orb, id ) ;
    }
}
