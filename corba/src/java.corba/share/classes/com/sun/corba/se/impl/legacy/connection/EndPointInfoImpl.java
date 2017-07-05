/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketEndPointInfo;
import com.sun.corba.se.spi.transport.SocketInfo;

public class EndPointInfoImpl
    implements
        SocketInfo,
        LegacyServerSocketEndPointInfo
{

    protected String type;
    protected String hostname;
    protected int port;
    protected int locatorPort;
    protected String name;

    public EndPointInfoImpl(String type, int port, String hostname) {
        this.type = type;
        this.port = port;
        this.hostname = hostname;
        this.locatorPort = -1;
        this.name = LegacyServerSocketEndPointInfo.NO_NAME;
    }

    public String getType() {
        return type;
    }

    public String getHost() {
        return hostname;
    }

    public String getHostName() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public int getLocatorPort ()
    {
        return locatorPort;
    }

    public void setLocatorPort (int port)
    {
        locatorPort = port;
    }

    public String getName()
    {
        return name;
    }

    public int hashCode() {
        return type.hashCode() ^ hostname.hashCode() ^ port;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof EndPointInfoImpl)) {
            return false;
        }
        EndPointInfoImpl other = (EndPointInfoImpl)obj;
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        if (port != other.port) {
            return false;
        }
        if (!hostname.equals(other.hostname)) {
            return false;
        }
        return true;
    }

    public String toString ()
    {
        return
            type + " " +
            name + " " +
            hostname + " " +
            port;
    }
}

// End of file.
