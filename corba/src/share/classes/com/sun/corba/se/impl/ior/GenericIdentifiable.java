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

import java.util.Arrays ;

import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;

import com.sun.corba.se.spi.ior.Identifiable ;

/**
 * @author
 * This is used for unknown components and profiles.  A TAG_MULTICOMPONENT_PROFILE will be represented this way.
 */
public abstract class GenericIdentifiable implements Identifiable
{
    private int id;
    private byte data[];

    public GenericIdentifiable(int id, InputStream is)
    {
        this.id = id ;
        data = EncapsulationUtility.readOctets( is ) ;
    }

    public int getId()
    {
        return id ;
    }

    public void write(OutputStream os)
    {
        os.write_ulong( data.length ) ;
        os.write_octet_array( data, 0, data.length ) ;
    }

    public String toString()
    {
        return "GenericIdentifiable[id=" + getId() + "]" ;
    }

    public boolean equals(Object obj)
    {
        if (obj == null)
            return false ;

        if (!(obj instanceof GenericIdentifiable))
            return false ;

        GenericIdentifiable encaps = (GenericIdentifiable)obj ;

        return (getId() == encaps.getId()) &&
            Arrays.equals( getData(), encaps.getData() ) ;
    }

    public int hashCode()
    {
        int result = 17 ;
        for (int ctr=0; ctr<data.length; ctr++ )
            result = 37*result + data[ctr] ;
        return result ;
    }

    public GenericIdentifiable(int id, byte[] data)
    {
        this.id = id ;
        this.data = (byte[])(data.clone()) ;
    }

    public byte[] getData()
    {
        return data ;
    }
}
