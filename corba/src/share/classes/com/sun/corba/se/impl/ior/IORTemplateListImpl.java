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

package com.sun.corba.se.impl.ior;

import java.util.ArrayList ;
import java.util.Iterator ;

import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.IORTemplate ;
import com.sun.corba.se.spi.ior.IORTemplateList ;
import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.IORTemplate ;
import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.ior.IORFactory ;
import com.sun.corba.se.spi.ior.IORFactories ;

import com.sun.corba.se.spi.orb.ORB ;

public class IORTemplateListImpl extends FreezableList implements IORTemplateList
{
    /* This class must override add( int, Object ) and set( int, Object )
     * so that adding an IORTemplateList to an IORTemplateList just results
     * in a list of TaggedProfileTemplates.
     */
    public Object set( int index, Object element )
    {
        if (element instanceof IORTemplate) {
            return super.set( index, element ) ;
        } else if (element instanceof IORTemplateList) {
            Object result = remove( index ) ;
            add( index, element ) ;
            return result ;
        } else
            throw new IllegalArgumentException() ;
    }

    public void add( int index, Object element )
    {
        if (element instanceof IORTemplate) {
            super.add( index, element ) ;
        } else if (element instanceof IORTemplateList) {
            IORTemplateList tl = (IORTemplateList)element ;
            addAll( index, tl ) ;
        } else
            throw new IllegalArgumentException() ;
    }

    public IORTemplateListImpl()
    {
        super( new ArrayList() ) ;
    }

    public IORTemplateListImpl( InputStream is )
    {
        this() ;
        int size = is.read_long() ;
        for (int ctr=0; ctr<size; ctr++) {
            IORTemplate iortemp = IORFactories.makeIORTemplate( is ) ;
            add( iortemp ) ;
        }

        makeImmutable() ;
    }

    public void makeImmutable()
    {
        makeElementsImmutable() ;
        super.makeImmutable() ;
    }

    public void write( OutputStream os )
    {
        os.write_long( size() ) ;
        Iterator iter = iterator() ;
        while (iter.hasNext()) {
            IORTemplate iortemp = (IORTemplate)(iter.next()) ;
            iortemp.write( os ) ;
        }
    }

    public IOR makeIOR( ORB orb, String typeid, ObjectId oid )
    {
        return new IORImpl( orb, typeid, this, oid ) ;
    }

    public boolean isEquivalent( IORFactory other )
    {
        if (!(other instanceof IORTemplateList))
            return false ;

        IORTemplateList list = (IORTemplateList)other ;

        Iterator thisIterator = iterator() ;
        Iterator listIterator = list.iterator() ;
        while (thisIterator.hasNext() && listIterator.hasNext()) {
            IORTemplate thisTemplate = (IORTemplate)thisIterator.next() ;
            IORTemplate listTemplate = (IORTemplate)listIterator.next() ;
            if (!thisTemplate.isEquivalent( listTemplate ))
                return false ;
        }

        return thisIterator.hasNext() == listIterator.hasNext() ;
    }
}
