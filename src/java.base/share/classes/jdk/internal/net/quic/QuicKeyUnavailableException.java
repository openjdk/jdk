/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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


import java.util.Objects;

import jdk.internal.net.quic.QuicTLSEngine.KeySpace;

/**
 * Thrown when an operation on {@link QuicTLSEngine} doesn't have the necessary
 * QUIC keys for encrypting or decrypting packets. This can either be because
 * the keys aren't available for a particular {@linkplain KeySpace keyspace} or
 * the keys for the {@code keyspace} have been discarded.
 */
public final class QuicKeyUnavailableException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 8553365136999153478L;

    public QuicKeyUnavailableException(final String message, final KeySpace keySpace) {
        super(Objects.requireNonNull(keySpace) + " keyspace: " + message);
    }
}
