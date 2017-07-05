/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

Summary and suggested reading order:

==============================================================================
Connection interceptor (called an ORBSocketFactory):

Summary:

The server side part of the ORBSocketFactory is told the type to
create as well as a port number.

The client side part of the ORBSocketFactory is called on every client
request.  An ORB first asks the factory for type/host/port information
(given an IOR).  If the ORB already has a connection of the
type/host/port it will use the existing connection.  Otherwise it will
then ask the factory to create a client socket, giving it that
type/host/port.  Finally, the createSocket method may throw an
exception to tell the ORB to ask it for type/host/port info again.
The information passed back and forth between the ORB and factory can
act as a cookie for the factory if desired.

Interfaces:

	com.sun.corba.se.spi.legacy.connection.ORBSocketFactory
	com.sun.corba.se.spi.legacy.connection.EndPointInfo
	com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException

==============================================================================
Access to a request's socket:

Summary:

The request's socket is available via ClientRequestInfo and
ServerRequestInfo.  We enable this by having them implement the
RequestInfoExt interface.

Interfaces:

	com.sun.corba.se.spi.legacy.interceptor.RequestInfoExt
	com.sun.corba.se.spi.legacy.connection.Connection

==============================================================================
Extending IORInfo to support the multiple server port API:

Summary:

We support the multiple server port API in PortableInterceptors by
having IORInfo implement the IORInfoExt interface.  The description on
how to use the multiple server port APIs is found in
ORBSocketFactory.java.

Interfaces:

       com.sun.corba.se.spi.legacy.interceptor.IORInfoExt
       com.sun.corba.se.spi.legacy.interceptor.UnknownType

;; End.


