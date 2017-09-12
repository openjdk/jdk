/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.corba.se.spi.orb.Operation ;

public class ORBDefaultInitRefResolverImpl implements Resolver {
    Operation urlHandler ;
    String orbDefaultInitRef ;

    public ORBDefaultInitRefResolverImpl( Operation urlHandler, String orbDefaultInitRef )
    {
        this.urlHandler = urlHandler ;

        // XXX Validate the URL?
        this.orbDefaultInitRef = orbDefaultInitRef ;
    }

    public org.omg.CORBA.Object resolve( String ident )
    {
        // If the ORBDefaultInitRef is not defined simply return null
        if( orbDefaultInitRef == null ) {
            return null;
        }

        String urlString;
        // If the ORBDefaultInitDef is  defined as corbaloc: then create the
        // corbaloc String in the format
        // <ORBInitDefaultInitDef Param>/<Identifier>
        // and resolve it using resolveCorbaloc method
        if( orbDefaultInitRef.startsWith( "corbaloc:" ) ) {
            urlString = orbDefaultInitRef + "/" + ident;
        } else {
            urlString = orbDefaultInitRef + "#" + ident;
        }

        return (org.omg.CORBA.Object)urlHandler.operate( urlString ) ;
    }

    public java.util.Set list()
    {
        return new java.util.HashSet() ;
    }
}
