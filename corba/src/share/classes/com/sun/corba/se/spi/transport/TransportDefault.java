/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.transport ;

import com.sun.corba.se.spi.protocol.CorbaClientDelegate ;
import com.sun.corba.se.spi.protocol.ClientDelegateFactory ;
import com.sun.corba.se.spi.transport.CorbaContactInfoList ;
import com.sun.corba.se.spi.transport.CorbaContactInfoListFactory ;
import com.sun.corba.se.spi.transport.ReadTimeouts;
import com.sun.corba.se.spi.transport.ReadTimeoutsFactory;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.ior.IOR ;

// Internal imports, not used in the interface to this package
import com.sun.corba.se.impl.protocol.CorbaClientDelegateImpl ;
import com.sun.corba.se.impl.transport.CorbaContactInfoListImpl;
import com.sun.corba.se.impl.transport.ReadTCPTimeoutsImpl;

/** This class provices standard building blocks for the ORB, as do all Default classes
 * in the various packages.
 */
public abstract class TransportDefault {
    private TransportDefault() {}

    public static CorbaContactInfoListFactory makeCorbaContactInfoListFactory(
        final ORB broker )
    {
        return new CorbaContactInfoListFactory() {
            public void setORB(ORB orb) { }
            public CorbaContactInfoList create( IOR ior ) {
                return new CorbaContactInfoListImpl(
                    (com.sun.corba.se.spi.orb.ORB)broker, ior ) ;
            }
        };
    }

    public static ClientDelegateFactory makeClientDelegateFactory(
        final ORB broker )
    {
        return new ClientDelegateFactory() {
            public CorbaClientDelegate create( CorbaContactInfoList info ) {
                return new CorbaClientDelegateImpl(
                    (com.sun.corba.se.spi.orb.ORB)broker, info ) ;
            }
        };
    }

    public static IORTransformer makeIORTransformer(
        final ORB broker )
    {
        return null ;
    }

    public static ReadTimeoutsFactory makeReadTimeoutsFactory()
    {
        return new ReadTimeoutsFactory() {
            public ReadTimeouts create(int initial_wait_time,
                                       int max_wait_time,
                                       int max_giop_hdr_wait_time,
                                       int backoff_percent_factor)
            {
                return new ReadTCPTimeoutsImpl(
                    initial_wait_time,
                    max_wait_time,
                    max_giop_hdr_wait_time,
                    backoff_percent_factor);
            };
        };
    }
}

// End of file.
