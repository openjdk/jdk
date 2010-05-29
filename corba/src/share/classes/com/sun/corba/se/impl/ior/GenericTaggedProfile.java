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

import org.omg.CORBA_2_3.portable.InputStream ;

import com.sun.corba.se.spi.ior.TaggedProfile ;
import com.sun.corba.se.spi.ior.TaggedProfileTemplate ;
import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;
import com.sun.corba.se.spi.ior.ObjectKey ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion ;

import com.sun.corba.se.impl.encoding.EncapsOutputStream ;

/**
 * @author
 */
public class GenericTaggedProfile extends GenericIdentifiable implements TaggedProfile
{
    private ORB orb ;

    public GenericTaggedProfile( int id, InputStream is )
    {
        super( id, is ) ;
        this.orb = (ORB)(is.orb()) ;
    }

    public GenericTaggedProfile( ORB orb, int id, byte[] data )
    {
        super( id, data ) ;
        this.orb = orb ;
    }

    public TaggedProfileTemplate getTaggedProfileTemplate()
    {
        return null ;
    }

    public ObjectId getObjectId()
    {
        return null ;
    }

    public ObjectKeyTemplate getObjectKeyTemplate()
    {
        return null ;
    }

    public ObjectKey getObjectKey()
    {
        return null ;
    }

    public boolean isEquivalent( TaggedProfile prof )
    {
        return equals( prof ) ;
    }

    public void makeImmutable()
    {
        // NO-OP
    }

    public boolean isLocal()
    {
        return false ;
    }

    public org.omg.IOP.TaggedProfile getIOPProfile()
    {
        EncapsOutputStream os = new EncapsOutputStream( orb ) ;
        write( os ) ;
        InputStream is = (InputStream)(os.create_input_stream()) ;
        return org.omg.IOP.TaggedProfileHelper.read( is ) ;
    }
}
