/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.net;

import java.io.IOError;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOption;
import java.util.Objects;
import java.util.Set;

/**
 * Defines static methods to set and get socket options defined by the
 * {@link java.net.SocketOption} interface. All of the standard options defined
 * by {@link java.net.Socket}, {@link java.net.ServerSocket}, and
 * {@link java.net.DatagramSocket} can be set this way, as well as additional
 * or platform specific options supported by each socket type.
 * <p>
 * The {@link #supportedOptions(Class)} method can be called to determine
 * the complete set of options available (per socket type) on the
 * current system.
 *
 * @deprecated
 * Java SE 9 added standard methods to set/get socket options, and retrieve the per-Socket
 * supported options effectively rendering this API redundant. Please refer to the corresponding
 * socket's class for the equivalent method to set/get a socket option or retrieve available socket options.
 *
 * @see java.nio.channels.NetworkChannel
 *
 * @since 1.8
 */
@Deprecated(since = "16")
public class Sockets {

    private Sockets() {}

    /**
     * Sets the value of a socket option on a {@link java.net.Socket}
     *
     * @param s the socket
     * @param name The socket option
     * @param value The value of the socket option. May be null for some
     *              options.
     * @param <T> The type of the socket option
     *
     * @throws UnsupportedOperationException if the socket does not support
     *         the option.
     *
     * @throws IllegalArgumentException if the value is not valid for
     *         the option.
     *
     * @throws IOException if an I/O error occurs, or socket is closed.
     *
     * @throws NullPointerException if name is null
     *
     * @deprecated use {@link java.net.Socket#setOption(SocketOption, Object)} instead.
     *
     * @see java.net.StandardSocketOptions
     */
    @Deprecated(since = "16")
    public static <T> void setOption(Socket s, SocketOption<T> name, T value) throws IOException
    {
        s.setOption(name, value);
    }

    /**
     * Returns the value of a socket option from a {@link java.net.Socket}
     *
     * @param s the socket
     * @param name The socket option
     * @param <T> The type of the socket option
     *
     * @return The value of the socket option.
     *
     * @throws UnsupportedOperationException if the socket does not support
     *         the option.
     *
     * @throws IOException if an I/O error occurs
     *
     * @throws NullPointerException if name is null
     *
     * @deprecated use {@link java.net.Socket#getOption(SocketOption)} instead.
     *
     * @see java.net.StandardSocketOptions
     */
    @Deprecated(since = "16")
    public static <T> T getOption(Socket s, SocketOption<T> name) throws IOException
    {
        return s.getOption(name);
    }

    /**
     * Sets the value of a socket option on a {@link java.net.ServerSocket}
     *
     * @param s the socket
     * @param name The socket option
     * @param value The value of the socket option
     * @param <T> The type of the socket option
     *
     * @throws UnsupportedOperationException if the socket does not support
     *         the option.
     *
     * @throws IllegalArgumentException if the value is not valid for
     *         the option.
     *
     * @throws IOException if an I/O error occurs
     *
     * @throws NullPointerException if name is null
     *
     * @deprecated use {@link java.net.ServerSocket#setOption(SocketOption, Object)} instead.
     *
     * @see java.net.StandardSocketOptions
     */
    @Deprecated(since = "16")
    public static <T> void setOption(ServerSocket s, SocketOption<T> name, T value) throws IOException
    {
        s.setOption(name, value);
    }

    /**
     * Returns the value of a socket option from a {@link java.net.ServerSocket}
     *
     * @param s the socket
     * @param name The socket option
     * @param <T> The type of the socket option
     *
     * @return The value of the socket option.
     *
     * @throws UnsupportedOperationException if the socket does not support
     *         the option.
     *
     * @throws IOException if an I/O error occurs
     *
     * @throws NullPointerException if name is null
     *
     * @deprecated use {@link java.net.ServerSocket#getOption(SocketOption)} instead.
     *
     * @see java.net.StandardSocketOptions
     */
    @Deprecated(since = "16")
    public static <T> T getOption(ServerSocket s, SocketOption<T> name) throws IOException
    {
        return s.getOption(name);
    }

    /**
     * Sets the value of a socket option on a {@link java.net.DatagramSocket}
     * or {@link java.net.MulticastSocket}
     *
     * @param s the socket
     * @param name The socket option
     * @param value The value of the socket option
     * @param <T> The type of the socket option
     *
     * @throws UnsupportedOperationException if the socket does not support
     *         the option.
     *
     * @throws IllegalArgumentException if the value is not valid for
     *         the option.
     *
     * @throws IOException if an I/O error occurs
     *
     * @throws NullPointerException if name is null
     *
     * @deprecated use {@link java.net.DatagramSocket#setOption(SocketOption, Object)} instead.
     *
     * @see java.net.StandardSocketOptions
     */
    @Deprecated(since = "16")
    public static <T> void setOption(DatagramSocket s, SocketOption<T> name, T value) throws IOException
    {
        s.setOption(name, value);
    }

    /**
     * Returns the value of a socket option from a
     * {@link java.net.DatagramSocket} or {@link java.net.MulticastSocket}
     *
     * @param s the socket
     * @param name The socket option
     * @param <T> The type of the socket option
     *
     * @return The value of the socket option.
     *
     * @throws UnsupportedOperationException if the socket does not support
     *         the option.
     *
     * @throws IOException if an I/O error occurs
     *
     * @throws NullPointerException if name is null
     *
     * @deprecated use {@link java.net.DatagramSocket#getOption(SocketOption)} instead.
     *
     * @see java.net.StandardSocketOptions
     */
    @Deprecated(since = "16")
    public static <T> T getOption(DatagramSocket s, SocketOption<T> name) throws IOException
    {
        return s.getOption(name);
    }

    /**
     * Returns a set of {@link java.net.SocketOption}s supported by the
     * given socket type. This set may include standard options and also
     * non standard extended options.
     *
     * @param socketType the type of java.net socket
     *
     * @return A set of socket options
     *
     * @throws IllegalArgumentException if socketType is not a valid
     *         socket type from the java.net package.
     *
     * @deprecated use {@link Socket#supportedOptions()}, {@link ServerSocket#supportedOptions()},
     *             or {@link DatagramSocket#supportedOptions()} instead.
     */
    @Deprecated(since = "16", forRemoval=true)
    public static Set<SocketOption<?>> supportedOptions(Class<?> socketType) {
        Objects.requireNonNull(socketType);
        try {
            if (socketType == Socket.class) {
                try (var s = new Socket()) {
                    return s.supportedOptions();
                }
            } else if (socketType == ServerSocket.class) {
                try (var s = new ServerSocket()) {
                    return s.supportedOptions();
                }
            } else if (socketType == DatagramSocket.class) {
                try (var s = new DatagramSocket(null)) {
                    return s.supportedOptions();
                }
            } else if (socketType == MulticastSocket.class) {
                try (var s = new MulticastSocket(null)) {
                    return s.supportedOptions();
                }
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
        throw new IllegalArgumentException("unknown socket type");
    }

    private static void checkValueType(Object value, Class<?> type) {
        if (!type.isAssignableFrom(value.getClass())) {
            String s = "Found: " + value.getClass().toString() + " Expected: "
                        + type.toString();
            throw new IllegalArgumentException(s);
        }
    }

}
