/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.resolver ;

import com.sun.corba.se.spi.resolver.LocalResolver ;
import com.sun.corba.se.spi.orbutil.closure.Closure ;

public class LocalResolverImpl implements LocalResolver {
    java.util.Map nameToClosure = new java.util.HashMap() ;

    public synchronized org.omg.CORBA.Object resolve( String name )
    {
        Closure cl = (Closure)nameToClosure.get( name ) ;
        if (cl == null)
            return null ;

        return (org.omg.CORBA.Object)(cl.evaluate()) ;
    }

    public synchronized java.util.Set list()
    {
        return nameToClosure.keySet() ;
    }

    public synchronized void register( String name, Closure closure )
    {
        nameToClosure.put( name, closure ) ;
    }
}
