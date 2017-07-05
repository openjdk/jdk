/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.resolver ;

import com.sun.corba.se.spi.resolver.Resolver ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.orb.Operation ;
import com.sun.corba.se.spi.orb.StringPair ;

public class ORBInitRefResolverImpl implements Resolver {
    Operation urlHandler ;
    java.util.Map orbInitRefTable ;

    public ORBInitRefResolverImpl( Operation urlHandler, StringPair[] initRefs )
    {
        this.urlHandler = urlHandler ;
        orbInitRefTable = new java.util.HashMap() ;

        for( int i = 0; i < initRefs.length ; i++ ) {
            StringPair sp = initRefs[i] ;
            orbInitRefTable.put( sp.getFirst(), sp.getSecond() ) ;
        }
    }

    public org.omg.CORBA.Object resolve( String ident )
    {
        String url = (String)orbInitRefTable.get( ident ) ;
        if (url == null)
            return null ;

        org.omg.CORBA.Object result =
            (org.omg.CORBA.Object)urlHandler.operate( url ) ;
        return result ;
    }

    public java.util.Set list()
    {
        return orbInitRefTable.keySet() ;
    }
}
