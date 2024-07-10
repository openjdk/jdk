/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.packets;

import jdk.internal.net.http.quic.QuicConnectionId;

/**
 * This class models Quic Long Header Packet header, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8999#section-5.1">RFC 8999, Section 5.1</a>:
 *
 * <blockquote><pre>{@code
 *    Long Header Packet {
 *       Header Form (1) = 1,
 *       Version-Specific Bits (7),
 *       Version (32),
 *       Destination Connection ID Length (8),
 *       Destination Connection ID (0..2040),
 *       Source Connection ID Length (8),
 *       Source Connection ID (0..2040),
 *       Version-Specific Data (..),
 *    }
 * }</pre></blockquote>
 *
 * @param version version
 * @param destinationId Destination Connection ID
 * @param sourceId Source Connection ID
 * @param headerLength length in bytes of the packet header
 * @spec https://www.rfc-editor.org/info/rfc8999
 *      RFC 8999: Version-Independent Properties of QUIC
  */
public record LongHeader(int version,
                         QuicConnectionId destinationId,
                         QuicConnectionId sourceId,
                         int headerLength) {
}
