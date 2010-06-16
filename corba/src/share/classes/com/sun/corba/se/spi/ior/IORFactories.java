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

package com.sun.corba.se.spi.ior ;

import java.io.Serializable ;

import org.omg.CORBA_2_3.portable.InputStream ;

import org.omg.CORBA.BAD_PARAM ;
import org.omg.CORBA.portable.ValueFactory ;

import org.omg.PortableInterceptor.ObjectReferenceTemplate ;
import org.omg.PortableInterceptor.ObjectReferenceFactory ;

import com.sun.corba.se.impl.ior.ObjectIdImpl ;
import com.sun.corba.se.impl.ior.ObjectKeyImpl ;
import com.sun.corba.se.impl.ior.IORImpl ;
import com.sun.corba.se.impl.ior.IORTemplateImpl ;
import com.sun.corba.se.impl.ior.IORTemplateListImpl ;
import com.sun.corba.se.impl.ior.ObjectReferenceProducerBase ;
import com.sun.corba.se.impl.ior.ObjectReferenceFactoryImpl ;
import com.sun.corba.se.impl.ior.ObjectReferenceTemplateImpl ;
import com.sun.corba.se.impl.ior.ObjectKeyFactoryImpl ;

import com.sun.corba.se.impl.orbutil.ORBUtility ;

import com.sun.corba.se.spi.orb.ORB ;

/** This class provides a number of factory methods for creating
 * various IOR SPI classes which are not subclassed for specific protocols.
 * The following types must be created using this class:
 * <ul>
 * <li>ObjectId</li>
 * <li>ObjectKey</li>
 * <li>IOR</li>
 * <li>IORTemplate</li>
 * </ul>
 */
public class IORFactories {
    private IORFactories() {}

    /** Create an ObjectId for the given byte sequence.
     */
    public static ObjectId makeObjectId( byte[] id )
    {
        return new ObjectIdImpl( id ) ;
    }

    /** Create an ObjectKey for the given ObjectKeyTemplate and
     * ObjectId.
     */
    public static ObjectKey makeObjectKey( ObjectKeyTemplate oktemp, ObjectId oid )
    {
        return new ObjectKeyImpl( oktemp, oid ) ;
    }

    /** Create an empty IOR for the given orb and typeid.  The result is mutable.
     */
    public static IOR makeIOR( ORB orb, String typeid )
    {
        return new IORImpl( orb, typeid ) ;
    }

    /** Create an empty IOR for the given orb with a null typeid.  The result is mutable.
     */
    public static IOR makeIOR( ORB orb )
    {
        return new IORImpl( orb ) ;
    }

    /** Read an IOR from an InputStream.  ObjectKeys are not shared.
     */
    public static IOR makeIOR( InputStream is )
    {
        return new IORImpl( is ) ;
    }

    /** Create an IORTemplate with the given ObjectKeyTemplate.  The result
     * is mutable.
     */
    public static IORTemplate makeIORTemplate( ObjectKeyTemplate oktemp )
    {
        return new IORTemplateImpl( oktemp ) ;
    }

    /** Read an IORTemplate from an InputStream.
     */
    public static IORTemplate makeIORTemplate( InputStream is )
    {
        return new IORTemplateImpl( is ) ;
    }

    public static IORTemplateList makeIORTemplateList()
    {
        return new IORTemplateListImpl() ;
    }

    public static IORTemplateList makeIORTemplateList( InputStream is )
    {
        return new IORTemplateListImpl( is ) ;
    }

    public static IORFactory getIORFactory( ObjectReferenceTemplate ort )
    {
        if (ort instanceof ObjectReferenceTemplateImpl) {
            ObjectReferenceTemplateImpl orti =
                (ObjectReferenceTemplateImpl)ort ;
            return orti.getIORFactory() ;
        }

        throw new BAD_PARAM() ;
    }

    public static IORTemplateList getIORTemplateList( ObjectReferenceFactory orf )
    {
        if (orf instanceof ObjectReferenceProducerBase) {
            ObjectReferenceProducerBase base =
                (ObjectReferenceProducerBase)orf ;
            return base.getIORTemplateList() ;
        }

        throw new BAD_PARAM() ;
    }

    public static ObjectReferenceTemplate makeObjectReferenceTemplate( ORB orb,
        IORTemplate iortemp )
    {
        return new ObjectReferenceTemplateImpl( orb, iortemp ) ;
    }

    public static ObjectReferenceFactory makeObjectReferenceFactory( ORB orb,
        IORTemplateList iortemps )
    {
        return new ObjectReferenceFactoryImpl( orb, iortemps ) ;
    }

    public static ObjectKeyFactory makeObjectKeyFactory( ORB orb )
    {
        return new ObjectKeyFactoryImpl( orb ) ;
    }

    public static IOR getIOR( org.omg.CORBA.Object obj )
    {
        return ORBUtility.getIOR( obj ) ;
    }

    public static org.omg.CORBA.Object makeObjectReference( IOR ior )
    {
        return ORBUtility.makeObjectReference( ior ) ;
    }

    /** This method must be called in order to register the value
     * factories for the ObjectReferenceTemplate and ObjectReferenceFactory
     * value types.
     */
    public static void registerValueFactories( ORB orb )
    {
        // Create and register the factory for the Object Reference Template
        // implementation.
        ValueFactory vf = new ValueFactory() {
            public Serializable read_value( InputStream is )
            {
                return new ObjectReferenceTemplateImpl( is ) ;
            }
        } ;

        orb.register_value_factory( ObjectReferenceTemplateImpl.repositoryId, vf ) ;

        // Create and register the factory for the Object Reference Factory
        // implementation.
        vf = new ValueFactory() {
            public Serializable read_value( InputStream is )
            {
                return new ObjectReferenceFactoryImpl( is ) ;
            }
        } ;

        orb.register_value_factory( ObjectReferenceFactoryImpl.repositoryId, vf ) ;
    }
}
