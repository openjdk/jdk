/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package sun.net.httpserver;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Field;
import java.util.Set;

public class HttpServerAccess {

   // Given a HttpServer object get the number of idleConnections it currently has
    public static int getIdleConnectionCount(HttpServer server) throws Exception{
        // Use reflection to get server object which is HTTPServerImpl
        Field serverField = server.getClass().getDeclaredField("server");
        serverField.setAccessible(true);

        // Get the actual serverImpl class, then get the IdleConnection Field
        Object serverImpl = serverField.get(server);
        Field idleConnectionField = serverImpl.getClass().getDeclaredField("idleConnections");
        idleConnectionField.setAccessible(true);

        // Finally get the IdleConnection object which is of type Set<HttpConnection>
        Object idleConnectionSet = idleConnectionField.get(serverImpl);
        Set<HttpConnection> idleConnectionPool = (Set<HttpConnection>) idleConnectionSet;
        return idleConnectionPool.size();
    }
}
