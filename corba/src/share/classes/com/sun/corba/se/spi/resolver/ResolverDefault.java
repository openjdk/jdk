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

package com.sun.corba.se.spi.resolver ;

import java.io.File ;

import com.sun.corba.se.impl.resolver.LocalResolverImpl ;
import com.sun.corba.se.impl.resolver.ORBInitRefResolverImpl ;
import com.sun.corba.se.impl.resolver.ORBDefaultInitRefResolverImpl ;
import com.sun.corba.se.impl.resolver.BootstrapResolverImpl ;
import com.sun.corba.se.impl.resolver.CompositeResolverImpl ;
import com.sun.corba.se.impl.resolver.INSURLOperationImpl ;
import com.sun.corba.se.impl.resolver.SplitLocalResolverImpl ;
import com.sun.corba.se.impl.resolver.FileResolverImpl ;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.orb.Operation ;
import com.sun.corba.se.spi.orb.StringPair ;

/** Utility class that provides factory methods for all of the
 * standard resolvers that we provide.
 */
public class ResolverDefault {
    /** Return a local resolver that simply stores bindings in a map.
    */
    public static LocalResolver makeLocalResolver( )
    {
        return new LocalResolverImpl() ;
    }

    /** Return a resolver that relies on configured values of ORBInitRef for data.
    */
    public static Resolver makeORBInitRefResolver( Operation urlOperation,
        StringPair[] initRefs )
    {
        return new ORBInitRefResolverImpl( urlOperation, initRefs ) ;
    }

    public static Resolver makeORBDefaultInitRefResolver( Operation urlOperation,
        String defaultInitRef )
    {
        return new ORBDefaultInitRefResolverImpl( urlOperation,
            defaultInitRef ) ;
    }

    /** Return a resolver that uses the proprietary bootstrap protocol
    * to implement a resolver.  Obtains the necessary host and port
    * information from the ORB.
    */
    public static Resolver makeBootstrapResolver( ORB orb, String host, int port )
    {
        return new BootstrapResolverImpl( orb, host, port ) ;
    }

    /** Return a resolver composed of the two given resolvers.  result.list() is the
    * union of first.list() and second.list().  result.resolve( name ) returns
    * first.resolve( name ) if that is not null, otherwise returns the result of
    * second.resolve( name ).
    */
    public static Resolver makeCompositeResolver( Resolver first, Resolver second )
    {
        return new CompositeResolverImpl( first, second ) ;
    }

    public static Operation makeINSURLOperation( ORB orb, Resolver bootstrapResolver )
    {
        return new INSURLOperationImpl(
            (com.sun.corba.se.spi.orb.ORB)orb, bootstrapResolver ) ;
    }

    public static LocalResolver makeSplitLocalResolver( Resolver resolver,
        LocalResolver localResolver )
    {
        return new SplitLocalResolverImpl( resolver, localResolver ) ;
    }

    public static Resolver makeFileResolver( ORB orb, File file )
    {
        return new FileResolverImpl( orb, file ) ;
    }
}
