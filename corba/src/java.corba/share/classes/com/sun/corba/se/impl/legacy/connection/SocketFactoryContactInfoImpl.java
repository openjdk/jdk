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

import com.sun.corba.se.pept.transport.Connection;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.SocketInfo;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.transport.SocketOrChannelContactInfoImpl;


/**
 * @author Harold Carr
 */
public class SocketFactoryContactInfoImpl
    extends
        SocketOrChannelContactInfoImpl
{
    protected ORBUtilSystemException wrapper;
    protected SocketInfo socketInfo;

    // XREVISIT
    // See SocketOrChannelAcceptorImpl.createMessageMediator
    // See SocketFactoryContactInfoImpl.constructor()
    // See SocketOrChannelContactInfoImpl.constructor()
    public SocketFactoryContactInfoImpl()
    {
    }

    public SocketFactoryContactInfoImpl(
        ORB orb,
        CorbaContactInfoList contactInfoList,
        IOR effectiveTargetIOR,
        short addressingDisposition,
        SocketInfo cookie)
    {
        super(orb, contactInfoList);
        this.effectiveTargetIOR = effectiveTargetIOR;
        this.addressingDisposition = addressingDisposition;

        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_TRANSPORT ) ;

        socketInfo =
            orb.getORBData().getLegacySocketFactory()
                .getEndPointInfo(orb, effectiveTargetIOR, cookie);

        socketType = socketInfo.getType();
        hostname = socketInfo.getHost();
        port = socketInfo.getPort();
    }

    ////////////////////////////////////////////////////
    //
    // pept.transport.ContactInfo
    //

    public Connection createConnection()
    {
        Connection connection =
            new SocketFactoryConnectionImpl(
                orb, this,
                orb.getORBData().connectionSocketUseSelectThreadToWait(),
                orb.getORBData().connectionSocketUseWorkerThreadForEvent());
        return connection;
    }

    ////////////////////////////////////////////////////
    //
    // java.lang.Object
    //

    public String toString()
    {
        return
            "SocketFactoryContactInfoImpl["
            + socketType + " "
            + hostname + " "
            + port
            + "]";
    }
}

// End of file.
