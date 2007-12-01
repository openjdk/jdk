/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.ior ;

import org.omg.CORBA_2_3.portable.InputStream ;

import java.util.Map ;
import java.util.HashMap ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.ior.Identifiable ;
import com.sun.corba.se.spi.ior.IdentifiableFactory ;
import com.sun.corba.se.spi.ior.IdentifiableFactoryFinder ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.IORSystemException ;

public abstract class IdentifiableFactoryFinderBase implements
    IdentifiableFactoryFinder
{
    private ORB orb ;
    private Map map ;
    protected IORSystemException wrapper ;

    protected IdentifiableFactoryFinderBase( ORB orb )
    {
        map = new HashMap() ;
        this.orb = orb ;
        wrapper = IORSystemException.get( orb,
            CORBALogDomains.OA_IOR ) ;
    }

    protected IdentifiableFactory getFactory(int id)
    {
        Integer ident = new Integer( id ) ;
        IdentifiableFactory factory = (IdentifiableFactory)(map.get(
            ident ) ) ;
        return factory ;
    }

    public abstract Identifiable handleMissingFactory( int id, InputStream is ) ;

    public Identifiable create(int id, InputStream is)
    {
        IdentifiableFactory factory = getFactory( id ) ;

        if (factory != null)
            return factory.create( is ) ;
        else
            return handleMissingFactory( id, is ) ;
    }

    public void registerFactory(IdentifiableFactory factory)
    {
        Integer ident = new Integer( factory.getId() ) ;
        map.put( ident, factory ) ;
    }
}
