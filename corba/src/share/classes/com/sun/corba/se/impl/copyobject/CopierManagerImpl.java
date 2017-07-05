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

package com.sun.corba.se.impl.copyobject ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.impl.orbutil.DenseIntMapImpl ;

import com.sun.corba.se.spi.copyobject.ObjectCopierFactory ;
import com.sun.corba.se.spi.copyobject.CopierManager ;
import com.sun.corba.se.spi.copyobject.ObjectCopierFactory ;

public class CopierManagerImpl implements CopierManager
{
    private int defaultId ;
    private DenseIntMapImpl map ;
    private ORB orb ;

    public CopierManagerImpl( ORB orb )
    {
        defaultId = 0 ;
        map = new DenseIntMapImpl() ;
        this.orb = orb ;
    }

    public void setDefaultId( int id )
    {
        defaultId = id ;
    }

    public int getDefaultId()
    {
        return defaultId ;
    }

    public ObjectCopierFactory getObjectCopierFactory( int id )
    {
        return (ObjectCopierFactory)(map.get( id )) ;
    }

    public ObjectCopierFactory getDefaultObjectCopierFactory()
    {
        return (ObjectCopierFactory)(map.get( defaultId )) ;
    }

    public void registerObjectCopierFactory( ObjectCopierFactory factory, int id )
    {
        map.set( id, factory ) ;
    }
}
