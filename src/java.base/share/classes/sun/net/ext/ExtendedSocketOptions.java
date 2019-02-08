/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.ext;

import java.io.FileDescriptor;
import java.net.SocketException;
import java.net.SocketOption;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines the infrastructure to support extended socket options, beyond those
 * defined in {@link java.net.StandardSocketOptions}.
 *
 * Extended socket options are accessed through the jdk.net API, which is in
 * the jdk.net module.
 */
public abstract class ExtendedSocketOptions {

    public static final short SOCK_STREAM = 1;
    public static final short SOCK_DGRAM = 2;

    private final Set<SocketOption<?>> options;

    /** Tells whether or not the option is supported. */
    public final boolean isOptionSupported(SocketOption<?> option) {
        return options().contains(option);
    }

    /** Return the, possibly empty, set of extended socket options available. */
    public final Set<SocketOption<?>> options() { return options; }

    /**
     * Returns the (possibly empty) set of extended socket options for
     * stream-oriented listening sockets.
     */
    public static Set<SocketOption<?>> serverSocketOptions() {
        return getInstance().options0(SOCK_STREAM, true);
    }

    /**
     * Returns the (possibly empty) set of extended socket options for
     * stream-oriented connecting sockets.
     */
    public static Set<SocketOption<?>> clientSocketOptions() {
        return getInstance().options0(SOCK_STREAM, false);
    }

    /**
     * Returns the (possibly empty) set of extended socket options for
     * datagram-oriented sockets.
     */
    public static Set<SocketOption<?>> datagramSocketOptions() {
        return getInstance().options0(SOCK_DGRAM, false);
    }

    private boolean isDatagramOption(SocketOption<?> option) {
        return !option.name().startsWith("TCP_");
    }

    private boolean isStreamOption(SocketOption<?> option, boolean server) {
        if (server && "SO_FLOW_SLA".equals(option.name())) {
            return false;
        } else {
            return !option.name().startsWith("UDP_");
        }
    }

    private Set<SocketOption<?>> options0(short type, boolean server) {
        Set<SocketOption<?>> extOptions;
        switch (type) {
            case SOCK_DGRAM:
                extOptions = options.stream()
                        .filter(option -> isDatagramOption(option))
                        .collect(Collectors.toUnmodifiableSet());
                break;
            case SOCK_STREAM:
                extOptions = options.stream()
                        .filter(option -> isStreamOption(option, server))
                        .collect(Collectors.toUnmodifiableSet());
                break;
            default:
                //this will never happen
                throw new IllegalArgumentException("Invalid socket option type");
        }
        return extOptions;
    }

    /** Sets the value of a socket option, for the given socket. */
    public abstract void setOption(FileDescriptor fd, SocketOption<?> option, Object value)
            throws SocketException;

    /** Returns the value of a socket option, for the given socket. */
    public abstract Object getOption(FileDescriptor fd, SocketOption<?> option)
            throws SocketException;

    protected ExtendedSocketOptions(Set<SocketOption<?>> options) {
        this.options = options;
    }

    private static volatile ExtendedSocketOptions instance;

    public static final ExtendedSocketOptions getInstance() { return instance; }

    /** Registers support for extended socket options. Invoked by the jdk.net module. */
    public static final void register(ExtendedSocketOptions extOptions) {
        if (instance != null)
            throw new InternalError("Attempting to reregister extended options");

        instance = extOptions;
    }

    static {
        try {
            // If the class is present, it will be initialized which
            // triggers registration of the extended socket options.
            Class<?> c = Class.forName("jdk.net.ExtendedSocketOptions");
        } catch (ClassNotFoundException e) {
            // the jdk.net module is not present => no extended socket options
            instance = new NoExtendedSocketOptions();
        }
    }

    static final class NoExtendedSocketOptions extends ExtendedSocketOptions {

        NoExtendedSocketOptions() {
            super(Collections.<SocketOption<?>>emptySet());
        }

        @Override
        public void setOption(FileDescriptor fd, SocketOption<?> option, Object value)
            throws SocketException
        {
            throw new UnsupportedOperationException(
                    "no extended options: " + option.name());
        }

        @Override
        public Object getOption(FileDescriptor fd, SocketOption<?> option)
            throws SocketException
        {
            throw new UnsupportedOperationException(
                    "no extended options: " + option.name());
        }
    }
}
