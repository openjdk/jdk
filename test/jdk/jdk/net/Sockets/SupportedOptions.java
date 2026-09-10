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
 * @bug 8062744 8210362
 * @summary Check that IP_TOS is not supported by ServerSocket
 * @modules jdk.net
 * @run junit ${test.main.class}
 */

import java.net.*;
import jdk.net.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupportedOptions {

    @Test
    void serverSocketOptions() throws Exception {
        var option = StandardSocketOptions.IP_TOS;

        assertFalse(Sockets.supportedOptions(ServerSocket.class).contains(option),
                "ServerSocket class options unexpectedly include IP_TOS");

        try (var ss = new ServerSocket()) {
            assertFalse(ss.supportedOptions().contains(option),
                    "ServerSocket options unexpectedly include IP_TOS");

            assertThrows(UnsupportedOperationException.class,
                    () -> Sockets.setOption(ss, option, 128));
            assertThrows(UnsupportedOperationException.class,
                    () -> Sockets.getOption(ss, option));
        }
    }
}
