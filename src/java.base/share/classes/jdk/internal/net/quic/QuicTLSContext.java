/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.quic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLParameters;

import sun.security.ssl.QuicTLSEngineImpl;
import sun.security.ssl.SSLContextImpl;

/**
 * Instances of this class act as a factory for creation
 * of {@link QuicTLSEngine QUIC TLS engine}.
 */
public final class QuicTLSContext {

    // In this implementation, we have a dependency on
    // sun.security.ssl.SSLContextImpl. We can only support
    // Quic on SSLContext instances created by the default
    // SunJSSE Provider
    private final SSLContextImpl sslCtxImpl;

    /**
     * {@return {@code true} if the given {@code sslContext} supports QUIC TLS, {@code false} otherwise}
     * @param sslContext an {@link SSLContext}
     */
    public static boolean isQuicCompatible(final SSLContext sslContext) {
        boolean parametersSupported = isQuicCompatible(sslContext.getSupportedSSLParameters());
        if (!parametersSupported) {
            return false;
        }
        // horrible hack - what we do here is try and get hold of a SSLContext
        // that has already been initialised and configured with the HttpClient.
        // We see if that SSLContext is created using an implementation of
        // sun.security.ssl.SSLContextImpl. Since there's no API
        // available to get hold of that underlying implementation, we use
        // MethodHandle lookup to get access to the field which holds that
        // detail.
        final Object underlyingImpl = CONTEXT_SPI.get(sslContext);
        if (!(underlyingImpl instanceof SSLContextImpl ssci)) {
            return false;
        }
        return ssci.isUsableWithQuic();
    }

    /**
     * {@return {@code true} if protocols of the given {@code parameters} support QUIC TLS, {@code false} otherwise}
     */
    public static boolean isQuicCompatible(SSLParameters parameters) {
        String[] protocols = parameters.getProtocols();
        return protocols != null && Arrays.asList(protocols).contains("TLSv1.3");
    }

    private static SSLContextImpl getSSLContextImpl(
            final SSLContext sslContext) {
        final Object underlyingImpl = CONTEXT_SPI.get(sslContext);
        assert underlyingImpl instanceof SSLContextImpl;
        return (SSLContextImpl) underlyingImpl;
    }

    /**
     * Constructs a QuicTLSContext for the given {@code sslContext}
     *
     * @param sslContext The SSLContext
     * @throws IllegalArgumentException If the passed {@code sslContext} isn't
     *                                  supported by the QuicTLSContext
     * @see #isQuicCompatible(SSLContext)
     */
    public QuicTLSContext(final SSLContext sslContext) {
        Objects.requireNonNull(sslContext);
        if (!isQuicCompatible(sslContext)) {
            throw new IllegalArgumentException(
                    "Cannot construct a QUIC TLS context with the given SSLContext");
        }
        this.sslCtxImpl = getSSLContextImpl(sslContext);
    }

    /**
     * Creates a {@link QuicTLSEngine} using this context
     * <p>
     * This method does not provide hints for session caching.
     *
     * @return the newly created QuicTLSEngine
     */
    public QuicTLSEngine createEngine() {
        return createEngine(null, -1);
    }

    /**
     * Creates a {@link QuicTLSEngine} using this context using
     * advisory peer information.
     * <p>
     * The provided parameters will be used as hints for session caching.
     * The {@code peerHost} parameter will be used in the server_name extension,
     * unless overridden later.
     *
     * @param peerHost The peer hostname or IP address. Can be null.
     * @param peerPort The peer port, can be -1 if the port is unknown
     * @return the newly created QuicTLSEngine
     */
    public QuicTLSEngine createEngine(final String peerHost, final int peerPort) {
        return new QuicTLSEngineImpl(this.sslCtxImpl, peerHost, peerPort);
    }

    // This VarHandle is used to access the SSLContext::contextSpi
    // field which is not publicly accessible.
    // In this implementation, Quic is only supported for SSLContext
    // instances whose underlying implementation is provided by a
    // sun.security.ssl.SSLContextImpl
    private static final VarHandle CONTEXT_SPI;
    static {
        try {
            final MethodHandles.Lookup lookup =
                    MethodHandles.privateLookupIn(SSLContext.class,
                            MethodHandles.lookup());
            final VarHandle vh = lookup.findVarHandle(SSLContext.class,
                    "contextSpi", SSLContextSpi.class);
            CONTEXT_SPI = vh;
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }
}

