/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.net;


import java.util.Locale;

// Patched implementation only meant to be used in certain tests
public class InetSocketAddress extends SocketAddress {

    @java.io.Serial
    private static final long serialVersionUID = 5076001401234631237L;

    private static boolean enableDelay;

    static {
        System.out.println("patched InetSocketAddress class in use");
    }

    private final String hostname;
    private final InetAddress addr;
    private final int port;

    public InetSocketAddress(int port) {
        this(InetAddress.anyLocalAddress(), port);
    }

    public InetSocketAddress(InetAddress addr, int port) {
        this(null,
                addr == null ? InetAddress.anyLocalAddress() : addr,
                checkPort(port));
    }

    public InetSocketAddress(String hostname, int port) {
        checkHost(hostname);
        InetAddress addr = null;
        String host = null;
        try {
            addr = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            host = hostname;
        }
        this.hostname = host;
        this.addr = addr;
        this.port = checkPort(port);
    }

    public static InetSocketAddress createUnresolved(String host, int port) {
        return new InetSocketAddress(checkHost(host), null, checkPort(port));
    }

    public static void enableDelay() {
        enableDelay = true;
    }

    public static void disableDelay() {
        enableDelay = false;
    }

    private InetSocketAddress(String hostname, InetAddress addr, int port) {
        this.hostname = hostname;
        this.addr = addr;
        this.port = port;
        if (enableDelay) {
            doDelay();
        }
    }

    /**
     * Gets the port number.
     *
     * @return the port number.
     */
    public final int getPort() {
        if (enableDelay) {
            doDelay();
        }
        return this.port;
    }

    /**
     * Gets the {@code InetAddress}.
     *
     * @return the InetAddress or {@code null} if it is unresolved.
     */
    public final InetAddress getAddress() {
        return this.addr;
    }

    public final String getHostName() {
        if (hostname != null) {
            return hostname;
        }
        if (addr != null) {
            return addr.getHostName();
        }
        return null;
    }

    public final String getHostString() {
        if (hostname != null) {
            return hostname;
        }
        if (addr != null) {
            if (addr.holder().getHostName() != null) {
                return addr.holder().getHostName();
            } else {
                return addr.getHostAddress();
            }
        }
        return null;
    }

    public final boolean isUnresolved() {
        return addr == null;
    }

    @Override
    public String toString() {
        String formatted;
        if (isUnresolved()) {
            formatted = hostname + "/<unresolved>";
        } else {
            formatted = addr.toString();
            if (addr instanceof Inet6Address) {
                int i = formatted.lastIndexOf("/");
                formatted = formatted.substring(0, i + 1)
                        + "[" + formatted.substring(i + 1) + "]";
            }
        }
        return formatted + ":" + port;
    }

    @Override
    public final boolean equals(Object other) {
        if (!(other instanceof InetSocketAddress that)) {
            return false;
        }
        boolean sameIP;
        if (addr != null) {
            sameIP = addr.equals(that.addr);
        } else if (hostname != null) {
            sameIP = (that.addr == null) &&
                    hostname.equalsIgnoreCase(that.hostname);
        } else {
            sameIP = (that.addr == null) && (that.hostname == null);
        }
        return sameIP && (port == that.port);
    }

    @Override
    public final int hashCode() {
        if (addr != null) {
            return addr.hashCode() + port;
        }
        if (hostname != null) {
            return hostname.toLowerCase(Locale.ROOT).hashCode() + port;
        }
        return port;
    }

    private static int checkPort(int port) {
        if (port < 0 || port > 0xFFFF)
            throw new IllegalArgumentException("port out of range:" + port);
        return port;
    }

    private static String checkHost(String hostname) {
        if (hostname == null)
            throw new IllegalArgumentException("hostname can't be null");
        return hostname;
    }

    private static void doDelay() {
        System.out.println("intentional delay injected in InetSocketAddress");
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
