/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import jtreg.SkippedException;

/**
 * Determines Internet Protocol version support at the TCP socket level.
 */
public class IPSupport {

    private static final boolean hasIPv4;
    private static final boolean hasIPv6;
    private static final boolean preferIPv4Stack;
    private static final boolean preferIPv6Addresses;

    static {
        hasIPv4 = runPrivilegedAction(() -> isSupported(Inet4Address.class));
        hasIPv6 = runPrivilegedAction(() -> isSupported(Inet6Address.class));
        preferIPv4Stack = runPrivilegedAction(() -> Boolean.parseBoolean(
            System.getProperty("java.net.preferIPv4Stack")));
        preferIPv6Addresses = runPrivilegedAction(() -> Boolean.parseBoolean(
            System.getProperty("java.net.preferIPv6Addresses")));
        if (!preferIPv4Stack && !hasIPv4 && !hasIPv6) {
            throw new AssertionError("IPv4 and IPv6 both not available and java.net.preferIPv4Stack is not true");
        }
    }

    private static boolean isSupported(Class<? extends InetAddress> addressType) {
        ProtocolFamily family = addressType == Inet4Address.class ?
                StandardProtocolFamily.INET : StandardProtocolFamily.INET6;
        try (var sc = SocketChannel.open(family)) {
            return true;
        } catch (IOException | UnsupportedOperationException ex) {
            return false;
        }
    }

    private static <T> T runPrivilegedAction(Callable<T> callable) {
        try {
            PrivilegedExceptionAction<T> pa = () -> callable.call();
            return AccessController.doPrivileged(pa);
        } catch (PrivilegedActionException pae) {
            throw new UncheckedIOException((IOException) pae.getCause());
        }
    }

    private IPSupport() { }

    /**
     * Whether or not IPv4 is supported.
     */
    public static final boolean hasIPv4() {
        return hasIPv4;
    }

    /**
     * Whether or not IPv6 is supported.
     */
    public static final boolean hasIPv6() {
        return hasIPv6;
    }

    /**
     * Whether or not the "java.net.preferIPv4Stack" system property is set.
     */
    public static final boolean preferIPv4Stack() {
        return preferIPv4Stack;
    }

    /**
     * Whether or not the "java.net.preferIPv6Addresses" system property is set.
     */
    public static final boolean preferIPv6Addresses() {
        return preferIPv6Addresses;
    }


    /**
     * Whether or not the current networking configuration is valid or not.
     *
     * If preferIPv4Stack is true but there is no IPv4 support, the configuration is invalid.
     */
    public static final boolean currentConfigurationIsValid() {
        return hasIPv4() || hasIPv6();
    }

    /**
     * Ensures that the platform supports the ability to create a
     * minimally-operational socket whose protocol is either one of IPv4
     * or IPv6.
     *
     * <p> A minimally-operation socket is one that can be created and
     * bound to an IP-specific loopback address. IP support is
     * considered non-operational if a socket cannot be bound to either
     * one of, an IPv4 loopback address, or the IPv6 loopback address.
     *
     * @throws SkippedException if the current networking configuration
     *         is non-operational
     */
    public static void throwSkippedExceptionIfNonOperational() throws SkippedException {
        if (!currentConfigurationIsValid()) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(os);
            ps.println("Invalid networking configuration");
            printPlatformSupport(ps);
            throw new SkippedException(os.toString());
        }
    }

    /**
     * Prints the platform supported configurations.
     */
    public static void printPlatformSupport(PrintStream out) {
        out.println("IPSupport - IPv4: " + hasIPv4());
        out.println("IPSupport - IPv6: " + hasIPv6());
        out.println("preferIPv4Stack: " + preferIPv4Stack());
        out.println("preferIPv6Addresses: " + preferIPv6Addresses());
    }

}
