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

/**
 * @test
 * @bug 8231358
 * @run main/othervm/java.security.policy=policy1 Security policy1
 * @run main/othervm/java.security.policy=policy2 Security policy2
 * @summary Security test for Unix Domain socket and server socket channels
 */

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static java.net.StandardProtocolFamily.UNIX;

/**
 * Tests required all with security manager
 */

public class Security {

    static interface Command {
        public void run() throws Exception;
    }

    static <T extends Exception> void call(Command r, Class<? extends Exception> expectedException) {
        boolean threw = false;
        try {
            r.run();
        } catch (Throwable t) {
            if (expectedException == null) {
                t.printStackTrace();
                throw new RuntimeException("an exception was thrown but was not expected");
            }
            threw = true;
            if (!(expectedException.isAssignableFrom(t.getClass()))) {
                throw new RuntimeException("wrong exception type thrown " + t.toString());
            }
        }
        if (expectedException != null && !threw) {
            // should have thrown
            throw new RuntimeException("SecurityException was expected");
        }
    }


    public static void main(String[] args) throws Exception {
        try {
           SocketChannel.open(UNIX);
        }
        catch (UnsupportedOperationException e) {
            System.out.println("Unix domain not supported");
            return;
        }

        String policy = args[0];
        if (policy.equals("policy1")) {
            testPolicy1();
        } else {
            testPolicy2();
        }
    }

    static void close(NetworkChannel... channels) {

        for (NetworkChannel chan : channels) {
            try {
                chan.close();
            } catch (Exception e) {
            }
        }
    }

    private static final Class<SecurityException> SE = SecurityException.class;
    private static final Class<IOException> IOE = IOException.class;

    // No permission

    public static void testPolicy1() throws Exception {
        Path servername = Path.of("sock");
        Files.deleteIfExists(servername);
        // Permission exists to bind a ServerSocketChannel
        final UnixDomainSocketAddress saddr = UnixDomainSocketAddress.of(servername);
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        call(() -> {
            server.bind(saddr);
        }, SE);
        call(() -> {
            client.connect(saddr);
        }, SE);
        close(server, client);
        Files.deleteIfExists(servername);
    }

    // All permissions

    public static void testPolicy2() throws Exception {
        Path servername = Path.of("sock");
        Files.deleteIfExists(servername);
        final UnixDomainSocketAddress saddr = UnixDomainSocketAddress.of(servername);
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        call(() -> {
            server.bind(saddr);
        }, null);
        call(() -> {
            client.connect(saddr);
        }, null);
        close(server, client);
        Files.deleteIfExists(servername);
    }
}
