/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4430139
 * @summary Test result of read on stream from nonblocking channel
 * @library ..
 */

import java.io.*;
import java.net.*;
import java.nio.channels.*;


public class Stream {

    static void test(TestServers.DayTimeServer daytimeServer) throws Exception {
        InetSocketAddress isa
            = new InetSocketAddress(daytimeServer.getAddress(),
                                    daytimeServer.getPort());
        SocketChannel sc = SocketChannel.open();
        sc.connect(isa);
        sc.configureBlocking(false);
        InputStream is = sc.socket().getInputStream();
        byte b[] = new byte[10];
        try {
            int n = is.read(b);
            throw new RuntimeException("Exception expected; none thrown");
        } catch (IllegalBlockingModeException e) {
            // expected result
        }
        sc.close();
    }

    public static void main(String[] args) throws Exception {
        try (TestServers.DayTimeServer dayTimeServer
                = TestServers.DayTimeServer.startNewServer(100)) {
            test(dayTimeServer);
        }
    }
}
