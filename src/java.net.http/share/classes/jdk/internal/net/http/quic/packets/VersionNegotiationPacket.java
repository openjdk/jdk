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
 * This class models Quic Version Negotiation Packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.2.1">RFC 9000, Section 17.2.1</a>:
 *
 *  <blockquote><pre>{@code
 *    A Version Negotiation packet is inherently not version-specific.
 *    Upon receipt by a client, it will be identified as a Version
 *    Negotiation packet based on the Version field having a value of 0.
 *
 *    The Version Negotiation packet is a response to a client packet that
 *    contains a version that is not supported by the server, and is only
 *    sent by servers.
 *
 *    The layout of a Version Negotiation packet is:
 *
 *    Version Negotiation Packet {
 *      Header Form (1) = 1,
 *      Unused (7),
 *      Version (32) = 0,
 *      Destination Connection ID Length (8),
 *      Destination Connection ID (0..2040),
 *      Source Connection ID Length (8),
 *      Source Connection ID (0..2040),
 *      Supported Version (32) ...,
 *    }
 * }</blockquote></pre>
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public interface VersionNegotiationPacket extends LongHeaderPacket {
    @Override
    default PacketType packetType() { return PacketType.VERSIONS; }
    @Override
    default int version() { return 0;}
    /**
     * This packet type is not numbered: returns
     * {@link PacketNumberSpace#NONE} always.
     * @return {@link PacketNumberSpace#NONE}
     */
    @Override
    default PacketNumberSpace numberSpace() { return PacketNumberSpace.NONE; }
    /**
     * This packet type is not numbered: returns -1L always.
     * @return -1L
     */
    @Override
    default long packetNumber() { return -1L; }
    int[] supportedVersions();
}
