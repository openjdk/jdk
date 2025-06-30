/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.httpclient.test.lib.common;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

import jdk.internal.util.OperatingSystem;

public final class TestUtil {

    private TestUtil() {}

    public static boolean sysPortsMayConflict() {
        if (OperatingSystem.isMacOS()) {
            // syslogd udp_in module may be dynamically started and opens an udp4 port
            // on the wildcard address. In addition, macOS will allow different processes
            // to bind to the same port on the wildcard, if one uses udp4 and the other
            // binds using udp46 (dual IPv4 IPv6 socket).
            // Binding to the loopback (or a specific interface) instead of binding
            // to the wildcard can prevent such conflicts.
            return true;
        }
        return false;
    }

    public static Optional<InetSocketAddress> chooseClientBindAddress() {
        if (!TestUtil.sysPortsMayConflict()) {
            return Optional.empty();
        }
        final InetSocketAddress address = new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 0);
        return Optional.of(address);
    }

}
