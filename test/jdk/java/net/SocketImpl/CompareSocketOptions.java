/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8221481
 * @modules java.base/java.net:+open java.base/sun.nio.ch:+open
 * @run testng CompareSocketOptions
 * @summary Compare the set of socket options supported by the old and new SocketImpls
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketImpl;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class CompareSocketOptions {

    /**
     * Test that the old and new platform SocketImpl support the same set of
     * client socket options.
     */
    public void testClientSocketSupportedOptions() throws IOException {
        Socket s1 = new Socket(createOldSocketImpl(false)) { };
        Socket s2 = new Socket(createNewSocketImpl(false)) { };
        assertEquals(s1.supportedOptions(), s2.supportedOptions());
    }

    /**
     * Test that the old and new platform SocketImpl support the same set of
     * server socket options.
     */
    public void testServerSocketSupportedOptions() throws IOException {
        ServerSocket ss1 = new ServerSocket(createOldSocketImpl(true)) { };
        ServerSocket ss2 = new ServerSocket(createNewSocketImpl(true)) { };
        assertEquals(ss1.supportedOptions(), ss2.supportedOptions());
    }

    private static SocketImpl createOldSocketImpl(boolean server) {
        return newPlatformSocketImpl("java.net.PlainSocketImpl", server);
    }

    private static SocketImpl createNewSocketImpl(boolean server) {
        return newPlatformSocketImpl("sun.nio.ch.NioSocketImpl", server);
    }

    private static SocketImpl newPlatformSocketImpl(String name, boolean server) {
        try {
            var ctor = Class.forName(name).getDeclaredConstructor(boolean.class);
            ctor.setAccessible(true);
            return (SocketImpl) ctor.newInstance(server);
        } catch (Exception e) {
            fail("Should not get here", e);
            return null;
        }
    }
}