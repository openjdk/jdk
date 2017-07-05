/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.legacy.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.transport.SocketOrChannelContactInfoImpl;
import com.sun.corba.se.impl.transport.SocketOrChannelAcceptorImpl;

/**
 * @author Harold Carr
 */
public class SocketFactoryAcceptorImpl
    extends
        SocketOrChannelAcceptorImpl
{
    public SocketFactoryAcceptorImpl(ORB orb, int port,
                                     String name, String type)
    {
        super(orb, port, name, type);
    }

    ////////////////////////////////////////////////////
    //
    // pept Acceptor
    //

    public boolean initialize()
    {
        if (initialized) {
            return false;
        }
        if (orb.transportDebugFlag) {
            dprint("initialize: " + this);
        }
        try {
            serverSocket = orb.getORBData()
                .getLegacySocketFactory().createServerSocket(type, port);
            internalInitialize();
        } catch (Throwable t) {
            throw wrapper.createListenerFailed( t, Integer.toString(port) ) ;
        }
        initialized = true;
        return true;
    }

    ////////////////////////////////////////////////////
    //
    // Implementation.
    //

    protected String toStringName()
    {
        return "SocketFactoryAcceptorImpl";
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint(toStringName(), msg);
    }
}

// End of file.
