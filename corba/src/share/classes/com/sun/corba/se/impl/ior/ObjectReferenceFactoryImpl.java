/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

import org.omg.CORBA.portable.InputStream ;
import org.omg.CORBA.portable.OutputStream ;
import org.omg.CORBA.portable.StreamableValue ;

import org.omg.CORBA.TypeCode ;

import org.omg.PortableInterceptor.ObjectReferenceFactory ;
import org.omg.PortableInterceptor.ObjectReferenceFactoryHelper ;

import com.sun.corba.se.spi.oa.ObjectAdapter ;

import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;
import com.sun.corba.se.spi.ior.ObjectAdapterId ;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.IORFactory;
import com.sun.corba.se.spi.ior.IORTemplateList;
import com.sun.corba.se.spi.ior.IORFactories;

import com.sun.corba.se.impl.orbutil.ORBUtility ;

import com.sun.corba.se.spi.orb.ORB ;

/** This is an implementation of the ObjectReferenceFactory abstract value
* type defined by the portable interceptors IDL.
* Note that this is a direct Java implementation
* of the abstract value type: there is no stateful value type defined in IDL,
* since defining the state in IDL is awkward and inefficient.  The best way
* to define the state is to use internal data structures that can be written
* to and read from CORBA streams.
*/
public class ObjectReferenceFactoryImpl extends ObjectReferenceProducerBase
    implements ObjectReferenceFactory, StreamableValue
{
    transient private IORTemplateList iorTemplates ;

    public ObjectReferenceFactoryImpl( InputStream is )
    {
        super( (ORB)(is.orb()) ) ;
        _read( is ) ;
    }

    public ObjectReferenceFactoryImpl( ORB orb, IORTemplateList iortemps )
    {
        super( orb ) ;
        iorTemplates = iortemps ;
    }

    public boolean equals( Object obj )
    {
        if (!(obj instanceof ObjectReferenceFactoryImpl))
            return false ;

        ObjectReferenceFactoryImpl other = (ObjectReferenceFactoryImpl)obj ;

        return (iorTemplates != null) &&
            iorTemplates.equals( other.iorTemplates ) ;
    }

    public int hashCode()
    {
        return iorTemplates.hashCode() ;
    }

    // Note that this repository ID must reflect the implementation
    // of the abstract valuetype (that is, this class), not the
    // repository ID of the org.omg.PortableInterceptor.ObjectReferenceFactory
    // class.  This allows for multiple independent implementations
    // of the abstract valuetype, should that become necessary.
    public static final String repositoryId =
        "IDL:com/sun/corba/se/impl/ior/ObjectReferenceFactoryImpl:1.0" ;

    public String[] _truncatable_ids()
    {
        return new String[] { repositoryId } ;
    }

    public TypeCode _type()
    {
        return ObjectReferenceFactoryHelper.type() ;
    }

    /** Read the data into a (presumably) empty ObjectReferenceFactoryImpl.
    * This sets the orb to the ORB of the InputStream.
    */
    public void _read( InputStream is )
    {
        org.omg.CORBA_2_3.portable.InputStream istr =
            (org.omg.CORBA_2_3.portable.InputStream)is ;

        iorTemplates = IORFactories.makeIORTemplateList( istr ) ;
    }

    /** Write the state to the OutputStream.
     */
    public void _write( OutputStream os )
    {
        org.omg.CORBA_2_3.portable.OutputStream ostr =
            (org.omg.CORBA_2_3.portable.OutputStream)os ;

        iorTemplates.write( ostr ) ;
    }

    public IORFactory getIORFactory()
    {
        return iorTemplates ;
    }

    public IORTemplateList getIORTemplateList()
    {
        return iorTemplates ;
    }
}
