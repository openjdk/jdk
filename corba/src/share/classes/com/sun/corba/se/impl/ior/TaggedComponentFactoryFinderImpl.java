/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

import com.sun.corba.se.spi.ior.Identifiable ;
import com.sun.corba.se.spi.ior.IdentifiableFactory ;
import com.sun.corba.se.spi.ior.IdentifiableFactoryFinder ;
import com.sun.corba.se.spi.ior.TaggedComponent ;
import com.sun.corba.se.spi.ior.TaggedComponentFactoryFinder ;

import com.sun.corba.se.impl.ior.GenericTaggedComponent ;
import com.sun.corba.se.impl.ior.IdentifiableFactoryFinderBase ;

import com.sun.corba.se.impl.encoding.EncapsOutputStream ;

import com.sun.corba.se.spi.orb.ORB ;

import org.omg.CORBA_2_3.portable.InputStream ;

/**
 * @author Ken Cavanaugh
 */
public class TaggedComponentFactoryFinderImpl extends
    IdentifiableFactoryFinderBase implements TaggedComponentFactoryFinder
{
    public TaggedComponentFactoryFinderImpl( ORB orb )
    {
        super( orb ) ;
    }

    public Identifiable handleMissingFactory( int id, InputStream is ) {
        return new GenericTaggedComponent( id, is ) ;
    }

    public TaggedComponent create( org.omg.CORBA.ORB orb,
        org.omg.IOP.TaggedComponent comp )
    {
        EncapsOutputStream os =
            sun.corba.OutputStreamFactory.newEncapsOutputStream((ORB)orb);
        org.omg.IOP.TaggedComponentHelper.write( os, comp ) ;
        InputStream is = (InputStream)(os.create_input_stream() ) ;
        // Skip the component ID: we just wrote it out above
        is.read_ulong() ;

        return (TaggedComponent)create( comp.tag, is ) ;
    }
}
