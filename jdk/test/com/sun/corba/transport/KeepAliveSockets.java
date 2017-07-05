/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8017195
 * @summary Introduce option to setKeepAlive parameter on CORBA sockets
 *
 * @run main/othervm KeepAliveSockets
 * @run main/othervm -Dcom.sun.CORBA.transport.enableTcpKeepAlive KeepAliveSockets
 * @run main/othervm -Dcom.sun.CORBA.transport.enableTcpKeepAlive=true KeepAliveSockets
 * @run main/othervm -Dcom.sun.CORBA.transport.enableTcpKeepAlive=false KeepAliveSockets
 */

import java.lang.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.util.*;
import com.sun.corba.se.impl.orb.*;

import com.sun.corba.se.impl.transport.*;

public class KeepAliveSockets {

    public static void main(String[] args) throws Exception {

        boolean keepAlive = false;
        String prop = System.getProperty("com.sun.CORBA.transport.enableTcpKeepAlive");
        if (prop != null)
            keepAlive = !"false".equalsIgnoreCase(prop);

        DefaultSocketFactoryImpl sfImpl = new DefaultSocketFactoryImpl();
        ORBImpl orb = new ORBImpl();
        orb.set_parameters(null);
        sfImpl.setORB(orb);

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(0));

        InetSocketAddress isa = new InetSocketAddress("localhost", ssc.socket().getLocalPort());
        Socket s = sfImpl.createSocket("ignore", isa);
        System.out.println("Received factory socket" + s);
        if (keepAlive != s.getKeepAlive())
            throw new RuntimeException("KeepAlive value not honoured in CORBA socket");
    }

}
