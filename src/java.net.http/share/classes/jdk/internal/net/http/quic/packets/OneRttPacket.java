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

import java.util.List;

import jdk.internal.net.http.quic.frames.QuicFrame;

/**
 * This class models Quic 1-RTT packets, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.3.1">RFC 9000, Section 17.3.1</a>:
 *
 * <blockquote><pre>{@code
 *    A 1-RTT packet uses a short packet header. It is used after the
 *    version and 1-RTT keys are negotiated.
 *
 *    1-RTT Packet {
 *      Header Form (1) = 0,
 *      Fixed Bit (1) = 1,
 *      Spin Bit (1),
 *      Reserved Bits (2),         # Protected
 *      Key Phase (1),             # Protected
 *      Packet Number Length (2),  # Protected
 *      Destination Connection ID (0..160),
 *      Packet Number (8..32),     # Protected
 *      # Protected Packet Payload:
 *      Protected Payload (0..24), # Skipped Part
 *      Protected Payload (128),   # Sampled Part
 *      Protected Payload (..),    # Remainder
 *    }
 * }</pre></blockquote>
 *
 * <p>Subclasses of this class may be used to model packets exchanged with either
 * <a href="https://www.rfc-editor.org/info/rfc9000>Quic Version 1</a> or
 * <a href="https://www.rfc-editor.org/info/rfc9369>Quic Version 2</a>.
 * Quic Version 2 uses the same 1-RTT packet structure than
 * Quic Version 1.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9369
 *      RFC 9369: QUIC Version 2
 */
public interface OneRttPacket extends ShortHeaderPacket {

    @Override
    List<QuicFrame> frames();

    @Override
    default PacketNumberSpace numberSpace() {
        return PacketNumberSpace.APPLICATION;
    }

    @Override
    default PacketType packetType() {
        return PacketType.ONERTT;
    }

    /**
     * Returns the packet's Key Phase Bit: 0 or 1, if known.
     * Returns -1 for outgoing packets.
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.3.1">RFC 9000, Section 17.3.1</a>:
     *
     * <blockquote><pre>{@code
     *     Bit (0x04) of byte 0 indicates the key phase, which allows a recipient
     *     of a packet to identify the packet protection keys that are used to
     *     protect the packet. See [QUIC-TLS] for details.
     *     This bit is protected using header protection; see Section 5.4 of [QUIC-TLS].
     * }</pre></blockquote>
     *
     * @return the packet's Key Phase Bit
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9001">RFC 9001, [QUIC-TLS]</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9001#section-5.4">RFC 9001, Section 5.4, [QUIC-TLS]</a>
     */
    default int keyPhase() {
        return -1;
    }

    /**
     * Returns the packet's Latency Spin Bit: 0 or 1, if known.
     * Returns -1 for outgoing packets.
     * <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.3.1">RFC 9000, Section 17.3.1</a>:
     *
     * <blockquote><pre>{@code
     *     The third most significant bit (0x20) of byte 0 is the latency spin
     *     bit, set as described in Section 17.4.
     * }</pre></blockquote>
     *
     * @return the packet's Latency Spin Bit
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-17.4">RFC 9000, Section 17.4</a>
     */
    default int spin() {
        return -1;
    }

    @Override
    default String prettyPrint() {
        return String.format("%s(pn:%s, size=%s, phase:%s, spin:%s, frames:%s)", packetType(), packetNumber(),
                size(), keyPhase(), spin(), frames());
    }
}
