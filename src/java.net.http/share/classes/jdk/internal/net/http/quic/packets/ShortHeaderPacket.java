/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This interface models Quic Short Header packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc8999#section-5.2">RFC 8999, Section 5.2</a>:
 *
 * <blockquote><pre>{@code
 *    Short Header Packet {
 *      Header Form (1) = 0,
 *      Version-Specific Bits (7),
 *      Destination Connection ID (..),
 *      Version-Specific Data (..),
 *    }
 * }</pre></blockquote>
 *
 * <p>Subclasses of this class may be used to model packets exchanged with either
 * <a href="https://www.rfc-editor.org/info/rfc9000>Quic Version 1</a> or
 * <a href="https://www.rfc-editor.org/info/rfc9369>Quic Version 2</a>.
 *
 * @spec https://www.rfc-editor.org/info/rfc8999
 *      RFC 8999: Version-Independent Properties of QUIC
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9369
 *      RFC 9369: QUIC Version 2
 */
public interface ShortHeaderPacket extends QuicPacket {
    @Override
    default HeadersType headersType() { return HeadersType.SHORT; }
}
