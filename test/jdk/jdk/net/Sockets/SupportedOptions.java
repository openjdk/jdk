/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8062744 8392528
 * @summary Sockets.supportedOptions should report the same options as the socket itself
 * @modules jdk.net
 * @run junit ${test.main.class}
 */

import java.net.*;
import jdk.net.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SupportedOptions {

    @Test
    void supportedOptionsMatchSocketInstances() throws Exception {
        try (var socket = new Socket()) {
            assertEquals(socket.supportedOptions(), Sockets.supportedOptions(Socket.class));
        }
        try (var serverSocket = new ServerSocket()) {
            assertEquals(serverSocket.supportedOptions(), Sockets.supportedOptions(ServerSocket.class));
        }
        try (var datagramSocket = new DatagramSocket(null)) {
            assertEquals(datagramSocket.supportedOptions(), Sockets.supportedOptions(DatagramSocket.class));
        }
        try (var multicastSocket = new MulticastSocket(null)) {
            assertEquals(multicastSocket.supportedOptions(), Sockets.supportedOptions(MulticastSocket.class));
        }
    }

    @Test
    void invalidSocketTypes() {
        assertThrows(IllegalArgumentException.class, () -> Sockets.supportedOptions(null));
        assertThrows(IllegalArgumentException.class, () -> Sockets.supportedOptions(String.class));
    }
}
