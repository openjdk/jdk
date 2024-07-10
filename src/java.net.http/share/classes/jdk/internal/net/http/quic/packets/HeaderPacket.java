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

import jdk.internal.net.http.quic.QuicConnectionId;

/**
 * This class is a super-interface for both {@link LongHeaderPacket}
 * and {@link ShortHeaderPacket} implementations.
 * Both long header packets and short header packets have a
 * Destination Connection ID. They typically also have a
 * Packet Number field - except for some specific subtypes
 * of {@link LongHeaderPacket}.
 *
 */
public interface HeaderPacket extends QuicPacket {

    /**
     * {@return the packet's Destination Connection ID}
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-7.2">
     *     RFC 9000, Section 7.2</a>
     */
    QuicConnectionId destinationId();
    /**
     * The length of this packet number, if this packet type is numbered,
     * 0 otherwise.
     * @return the packet number length, or 0
     */
    default int packetNumberLength() {
        if (numberSpace() == PacketNumberSpace.NONE) return 0;
        byte headerBits = headerBits();
        return (headerBits & PACKET_NUMBER_MASK) + 1;
    }

}
