/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.*;
import java.nio.*;
import java.util.*;
import javax.crypto.BadPaddingException;

import javax.net.ssl.*;

import sun.security.util.HexDumpEncoder;
import static sun.security.ssl.HandshakeMessage.*;

/**
 * DTLS {@code InputRecord} implementation for {@code SSLEngine}.
 */
final class DTLSInputRecord extends InputRecord implements DTLSRecord {

    private DTLSReassembler reassembler = null;

    int                 readEpoch;

    int                 prevReadEpoch;
    Authenticator       prevReadAuthenticator;
    CipherBox           prevReadCipher;

    DTLSInputRecord() {
        this.readEpoch = 0;
        this.readAuthenticator = new MAC(true);

        this.prevReadEpoch = 0;
        this.prevReadCipher = CipherBox.NULL;
        this.prevReadAuthenticator = new MAC(true);
    }

    @Override
    void changeReadCiphers(Authenticator readAuthenticator,
            CipherBox readCipher) {

        prevReadCipher.dispose();

        this.prevReadAuthenticator = this.readAuthenticator;
        this.prevReadCipher = this.readCipher;
        this.prevReadEpoch = this.readEpoch;

        this.readAuthenticator = readAuthenticator;
        this.readCipher = readCipher;
        this.readEpoch++;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isClosed) {
            prevReadCipher.dispose();
            super.close();
        }
    }

    @Override
    boolean isEmpty() {
        return ((reassembler == null) || reassembler.isEmpty());
    }

    @Override
    int estimateFragmentSize(int packetSize) {
        int macLen = 0;
        if (readAuthenticator instanceof MAC) {
            macLen = ((MAC)readAuthenticator).MAClen();
        }

        if (packetSize > 0) {
            return readCipher.estimateFragmentSize(
                    packetSize, macLen, headerSize);
        } else {
            return Record.maxDataSize;
        }
    }

    @Override
    void expectingFinishFlight() {
        if (reassembler != null) {
            reassembler.expectingFinishFlight();
        }
    }

    @Override
    Plaintext acquirePlaintext() {
        if (reassembler != null) {
            return reassembler.acquirePlaintext();
        }

        return null;
    }

    @Override
    Plaintext decode(ByteBuffer packet) {

        if (isClosed) {
            return null;
        }

        if (debug != null && Debug.isOn("packet")) {
             Debug.printHex(
                    "[Raw read]: length = " + packet.remaining(), packet);
        }

        // The caller should have validated the record.
        int srcPos = packet.position();
        int srcLim = packet.limit();

        byte contentType = packet.get();                   // pos: 0
        byte majorVersion = packet.get();                  // pos: 1
        byte minorVersion = packet.get();                  // pos: 2
        byte[] recordEnS = new byte[8];                    // epoch + seqence
        packet.get(recordEnS);
        int recordEpoch = ((recordEnS[0] & 0xFF) << 8) |
                           (recordEnS[1] & 0xFF);          // pos: 3, 4
        long recordSeq  = ((recordEnS[2] & 0xFFL) << 40) |
                          ((recordEnS[3] & 0xFFL) << 32) |
                          ((recordEnS[4] & 0xFFL) << 24) |
                          ((recordEnS[5] & 0xFFL) << 16) |
                          ((recordEnS[6] & 0xFFL) <<  8) |
                           (recordEnS[7] & 0xFFL);         // pos: 5-10

        int contentLen = ((packet.get() & 0xFF) << 8) |
                          (packet.get() & 0xFF);           // pos: 11, 12

        if (debug != null && Debug.isOn("record")) {
            Debug.log("READ: " +
                    ProtocolVersion.valueOf(majorVersion, minorVersion) +
                    " " + Record.contentName(contentType) + ", length = " +
                    contentLen);
        }

        int recLim = srcPos + DTLSRecord.headerSize + contentLen;

        if (this.prevReadEpoch > recordEpoch) {
            // Reset the position of the packet buffer.
            packet.position(recLim);
            if (debug != null && Debug.isOn("record")) {
                Debug.printHex("READ: discard this old record", recordEnS);
            }
            return null;
        }

        // Buffer next epoch message if necessary.
        if (this.readEpoch < recordEpoch) {
            // Discard the record younger than the current epcoh if:
            // 1. it is not a handshake message, or
            // 2. it is not of next epoch.
            if (((contentType != Record.ct_handshake) &&
                    (contentType != Record.ct_change_cipher_spec)) ||
                (this.readEpoch < (recordEpoch - 1))) {

                packet.position(recLim);

                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Premature record (epoch), discard it.");
                }

                return null;
            }

            // Not ready to decrypt this record, may be an encrypted Finished
            // message, need to buffer it.
            byte[] fragment = new byte[contentLen];
            packet.get(fragment);              // copy the fragment
            RecordFragment buffered = new RecordFragment(fragment, contentType,
                    majorVersion, minorVersion,
                    recordEnS, recordEpoch, recordSeq, true);

            reassembler.queueUpFragment(buffered);

            // consume the full record in the packet buffer.
            packet.position(recLim);

            return reassembler.acquirePlaintext();
        }

        //
        // Now, the message is of this epoch or the previous epoch.
        //
        Authenticator decodeAuthenticator;
        CipherBox decodeCipher;
        if (this.readEpoch == recordEpoch) {
            decodeAuthenticator = readAuthenticator;
            decodeCipher = readCipher;
        } else {                        // prevReadEpoch == recordEpoch
            decodeAuthenticator = prevReadAuthenticator;
            decodeCipher = prevReadCipher;
        }

        // decrypt the fragment
        packet.limit(recLim);
        packet.position(srcPos + DTLSRecord.headerSize);

        ByteBuffer plaintextFragment;
        try {
            plaintextFragment = decrypt(decodeAuthenticator,
                    decodeCipher, contentType, packet, recordEnS);
        } catch (BadPaddingException bpe) {
            if (debug != null && Debug.isOn("ssl")) {
                Debug.log("Discard invalid record: " + bpe);
            }

            // invalid, discard this record [section 4.1.2.7, RFC 6347]
            return null;
        } finally {
            // comsume a complete record
            packet.limit(srcLim);
            packet.position(recLim);
        }

        if (contentType != Record.ct_change_cipher_spec &&
            contentType != Record.ct_handshake) {   // app data or alert
                                                    // no retransmission
            // Cleanup the handshake reassembler if necessary.
            if ((reassembler != null) &&
                    (reassembler.handshakeEpoch < recordEpoch)) {
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Cleanup the handshake reassembler");
                }

                reassembler = null;
            }

            return new Plaintext(contentType, majorVersion, minorVersion,
                    recordEpoch, Authenticator.toLong(recordEnS),
                    plaintextFragment);
        }

        if (contentType == Record.ct_change_cipher_spec) {
            if (reassembler == null) {
                if (this.readEpoch != recordEpoch) {
                    // handshake has not started, should be an
                    // old handshake message, discard it.

                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log(
                                "Lagging behind ChangeCipherSpec, discard it.");
                    }

                    return null;
                }

                reassembler = new DTLSReassembler(recordEpoch);
            }

            reassembler.queueUpChangeCipherSpec(
                    new RecordFragment(plaintextFragment, contentType,
                            majorVersion, minorVersion,
                            recordEnS, recordEpoch, recordSeq, false));
        } else {    // handshake record
            // One record may contain 1+ more handshake messages.
            while (plaintextFragment.remaining() > 0) {

                HandshakeFragment hsFrag = parseHandshakeMessage(
                    contentType, majorVersion, minorVersion,
                    recordEnS, recordEpoch, recordSeq, plaintextFragment);

                if (hsFrag == null) {
                    // invalid, discard this record
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log("Invalid handshake message, discard it.");
                    }

                    return null;
                }

                if (reassembler == null) {
                    if (this.readEpoch != recordEpoch) {
                        // handshake has not started, should be an
                        // old handshake message, discard it.

                        if (debug != null && Debug.isOn("verbose")) {
                            Debug.log(
                                "Lagging behind handshake record, discard it.");
                        }

                        return null;
                    }

                    reassembler = new DTLSReassembler(recordEpoch);
                }

                reassembler.queueUpHandshake(hsFrag);
            }
        }

        // Completed the read of the full record.  Acquire the reassembled
        // messages.
        if (reassembler != null) {
            return reassembler.acquirePlaintext();
        }

        if (debug != null && Debug.isOn("verbose")) {
            Debug.log("The reassembler is not initialized yet.");
        }

        return null;
    }

    @Override
    int bytesInCompletePacket(ByteBuffer packet) throws SSLException {

        // DTLS length field is in bytes 11/12
        if (packet.remaining() < headerSize) {
            return -1;
        }

        // Last sanity check that it's not a wild record
        int pos = packet.position();

        // Check the content type of the record.
        byte contentType = packet.get(pos);
        if (!Record.isValidContentType(contentType)) {
            throw new SSLException(
                    "Unrecognized SSL message, plaintext connection?");
        }

        // Check the protocol version of the record.
        ProtocolVersion recordVersion =
            ProtocolVersion.valueOf(packet.get(pos + 1), packet.get(pos + 2));
        checkRecordVersion(recordVersion, false);

        // Get the fragment length of the record.
        int fragLen = ((packet.get(pos + 11) & 0xFF) << 8) +
                       (packet.get(pos + 12) & 0xFF) + headerSize;
        if (fragLen > Record.maxFragmentSize) {
            throw new SSLException(
                    "Record overflow, fragment length (" + fragLen +
                    ") MUST not exceed " + Record.maxFragmentSize);
        }

        return fragLen;
    }

    @Override
    void checkRecordVersion(ProtocolVersion recordVersion,
            boolean allowSSL20Hello) throws SSLException {

        if (!recordVersion.maybeDTLSProtocol()) {
            throw new SSLException(
                    "Unrecognized record version " + recordVersion +
                    " , plaintext connection?");
        }
    }

    private static HandshakeFragment parseHandshakeMessage(
            byte contentType, byte majorVersion, byte minorVersion,
            byte[] recordEnS, int recordEpoch, long recordSeq,
            ByteBuffer plaintextFragment) {

        int remaining = plaintextFragment.remaining();
        if (remaining < handshakeHeaderSize) {
            if (debug != null && Debug.isOn("ssl")) {
                Debug.log("Discard invalid record: " +
                        "too small record to hold a handshake fragment");
            }

            // invalid, discard this record [section 4.1.2.7, RFC 6347]
            return null;
        }

        byte handshakeType = plaintextFragment.get();       // pos: 0
        int messageLength =
                ((plaintextFragment.get() & 0xFF) << 16) |
                ((plaintextFragment.get() & 0xFF) << 8) |
                 (plaintextFragment.get() & 0xFF);          // pos: 1-3
        int messageSeq =
                ((plaintextFragment.get() & 0xFF) << 8) |
                 (plaintextFragment.get() & 0xFF);          // pos: 4/5
        int fragmentOffset =
                ((plaintextFragment.get() & 0xFF) << 16) |
                ((plaintextFragment.get() & 0xFF) << 8) |
                 (plaintextFragment.get() & 0xFF);          // pos: 6-8
        int fragmentLength =
                ((plaintextFragment.get() & 0xFF) << 16) |
                ((plaintextFragment.get() & 0xFF) << 8) |
                 (plaintextFragment.get() & 0xFF);          // pos: 9-11
        if ((remaining - handshakeHeaderSize) < fragmentLength) {
            if (debug != null && Debug.isOn("ssl")) {
                Debug.log("Discard invalid record: " +
                        "not a complete handshake fragment in the record");
            }

            // invalid, discard this record [section 4.1.2.7, RFC 6347]
            return null;
        }

        byte[] fragment = new byte[fragmentLength];
        plaintextFragment.get(fragment);

        return new HandshakeFragment(fragment, contentType,
                majorVersion, minorVersion,
                recordEnS, recordEpoch, recordSeq,
                handshakeType, messageLength,
                messageSeq, fragmentOffset, fragmentLength);
    }

    // buffered record fragment
    private static class RecordFragment implements Comparable<RecordFragment> {
        boolean         isCiphertext;

        byte            contentType;
        byte            majorVersion;
        byte            minorVersion;
        int             recordEpoch;
        long            recordSeq;
        byte[]          recordEnS;
        byte[]          fragment;

        RecordFragment(ByteBuffer fragBuf, byte contentType,
                byte majorVersion, byte minorVersion, byte[] recordEnS,
                int recordEpoch, long recordSeq, boolean isCiphertext) {
            this((byte[])null, contentType, majorVersion, minorVersion,
                    recordEnS, recordEpoch, recordSeq, isCiphertext);

            this.fragment = new byte[fragBuf.remaining()];
            fragBuf.get(this.fragment);
        }

        RecordFragment(byte[] fragment, byte contentType,
                byte majorVersion, byte minorVersion, byte[] recordEnS,
                int recordEpoch, long recordSeq, boolean isCiphertext) {
            this.isCiphertext = isCiphertext;

            this.contentType = contentType;
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.recordEpoch = recordEpoch;
            this.recordSeq = recordSeq;
            this.recordEnS = recordEnS;
            this.fragment = fragment;       // The caller should have cloned
                                            // the buffer if necessary.
        }

        @Override
        public int compareTo(RecordFragment o) {
            if (this.contentType == Record.ct_change_cipher_spec) {
                if (o.contentType == Record.ct_change_cipher_spec) {
                    // Only one incoming ChangeCipherSpec message for an epoch.
                    //
                    // Ignore duplicated ChangeCipherSpec messages.
                    return Integer.compare(this.recordEpoch, o.recordEpoch);
                } else if ((this.recordEpoch == o.recordEpoch) &&
                        (o.contentType == Record.ct_handshake)) {
                    // ChangeCipherSpec is the latest message of an epoch.
                    return 1;
                }
            } else if (o.contentType == Record.ct_change_cipher_spec) {
                if ((this.recordEpoch == o.recordEpoch) &&
                        (this.contentType == Record.ct_handshake)) {
                    // ChangeCipherSpec is the latest message of an epoch.
                    return -1;
                } else {
                    // different epoch or this is not a handshake message
                    return compareToSequence(o.recordEpoch, o.recordSeq);
                }
            }

            return compareToSequence(o.recordEpoch, o.recordSeq);
        }

        int compareToSequence(int epoch, long seq) {
            if (this.recordEpoch > epoch) {
                return 1;
            } else if (this.recordEpoch == epoch) {
                return Long.compare(this.recordSeq, seq);
            } else {
                return -1;
            }
        }
    }

    // buffered handshake message
    private static final class HandshakeFragment extends RecordFragment {

        byte            handshakeType;     // handshake msg_type
        int             messageSeq;        // message_seq
        int             messageLength;     // Handshake body length
        int             fragmentOffset;    // fragment_offset
        int             fragmentLength;    // fragment_length

        HandshakeFragment(byte[] fragment, byte contentType,
                byte majorVersion, byte minorVersion, byte[] recordEnS,
                int recordEpoch, long recordSeq,
                byte handshakeType, int messageLength,
                int messageSeq, int fragmentOffset, int fragmentLength) {

            super(fragment, contentType, majorVersion, minorVersion,
                    recordEnS, recordEpoch , recordSeq, false);

            this.handshakeType = handshakeType;
            this.messageSeq = messageSeq;
            this.messageLength = messageLength;
            this.fragmentOffset = fragmentOffset;
            this.fragmentLength = fragmentLength;
        }

        @Override
        public int compareTo(RecordFragment o) {
            if (o instanceof HandshakeFragment) {
                HandshakeFragment other = (HandshakeFragment)o;
                if (this.messageSeq != other.messageSeq) {
                    // keep the insertion order of handshake messages
                    return this.messageSeq - other.messageSeq;
                } else if (this.fragmentOffset != other.fragmentOffset) {
                    // small fragment offset was transmitted first
                    return this.fragmentOffset - other.fragmentOffset;
                } else if (this.fragmentLength == other.fragmentLength) {
                    // retransmissions, ignore duplicated messages.
                    return 0;
                }

                // Should be repacked for suitable fragment length.
                //
                // Note that the acquiring processes will reassemble the
                // the fragments later.
                return compareToSequence(o.recordEpoch, o.recordSeq);
            }

            return super.compareTo(o);
        }
    }

    private static final class HoleDescriptor {
        int offset;             // fragment_offset
        int limit;              // fragment_offset + fragment_length

        HoleDescriptor(int offset, int limit) {
            this.offset = offset;
            this.limit = limit;
        }
    }

    private static final class HandshakeFlight implements Cloneable {
        static final byte HF_UNKNOWN = HandshakeMessage.ht_not_applicable;

        byte        handshakeType;      // handshake type
        int         flightEpoch;        // the epoch of the first message
        int         minMessageSeq;      // minimal message sequence

        int         maxMessageSeq;      // maximum message sequence
        int         maxRecordEpoch;     // maximum record sequence number
        long        maxRecordSeq;       // maximum record sequence number

        HashMap<Byte, List<HoleDescriptor>> holesMap;

        HandshakeFlight() {
            this.handshakeType = HF_UNKNOWN;
            this.flightEpoch = 0;
            this.minMessageSeq = 0;

            this.maxMessageSeq = 0;
            this.maxRecordEpoch = 0;
            this.maxRecordSeq = -1;

            this.holesMap = new HashMap<>(5);
        }

        boolean isRetransmitOf(HandshakeFlight hs) {
            return (hs != null) &&
                   (this.handshakeType == hs.handshakeType) &&
                   (this.minMessageSeq == hs.minMessageSeq);
        }

        @Override
        public Object clone() {
            HandshakeFlight hf = new HandshakeFlight();

            hf.handshakeType = this.handshakeType;
            hf.flightEpoch = this.flightEpoch;
            hf.minMessageSeq = this.minMessageSeq;

            hf.maxMessageSeq = this.maxMessageSeq;
            hf.maxRecordEpoch = this.maxRecordEpoch;
            hf.maxRecordSeq = this.maxRecordSeq;

            hf.holesMap = new HashMap<>(this.holesMap);

            return hf;
        }
    }

    final class DTLSReassembler {
        // The handshake epoch.
        final int handshakeEpoch;

        // The buffered fragments.
        TreeSet<RecordFragment> bufferedFragments = new TreeSet<>();

        // The handshake flight in progress.
        HandshakeFlight handshakeFlight = new HandshakeFlight();

        // The preceding handshake flight.
        HandshakeFlight precedingFlight = null;

        // Epoch, sequence number and handshake message sequence of the
        // next message acquisition of a flight.
        int         nextRecordEpoch;        // next record epoch
        long        nextRecordSeq = 0;      // next record sequence number

        // Expect ChangeCipherSpec and Finished messages for the final flight.
        boolean     expectCCSFlight = false;

        // Ready to process this flight if received all messages of the flight.
        boolean     flightIsReady = false;
        boolean     needToCheckFlight = false;

        DTLSReassembler(int handshakeEpoch) {
            this.handshakeEpoch = handshakeEpoch;
            this.nextRecordEpoch = handshakeEpoch;

            this.handshakeFlight.flightEpoch = handshakeEpoch;
        }

        void expectingFinishFlight() {
            expectCCSFlight = true;
        }

        // Queue up a handshake message.
        void queueUpHandshake(HandshakeFragment hsf) {
            if (!isDesirable(hsf)) {
                // Not a dedired record, discard it.
                return;
            }

            // Clean up the retransmission messages if necessary.
            cleanUpRetransmit(hsf);

            // Is it the first message of next flight?
            //
            // Note: the Finished message is handled in the final CCS flight.
            boolean isMinimalFlightMessage = false;
            if (handshakeFlight.minMessageSeq == hsf.messageSeq) {
                isMinimalFlightMessage = true;
            } else if ((precedingFlight != null) &&
                    (precedingFlight.minMessageSeq == hsf.messageSeq)) {
                isMinimalFlightMessage = true;
            }

            if (isMinimalFlightMessage && (hsf.fragmentOffset == 0) &&
                    (hsf.handshakeType != HandshakeMessage.ht_finished)) {

                // reset the handshake flight
                handshakeFlight.handshakeType = hsf.handshakeType;
                handshakeFlight.flightEpoch = hsf.recordEpoch;
                handshakeFlight.minMessageSeq = hsf.messageSeq;
            }

            if (hsf.handshakeType == HandshakeMessage.ht_finished) {
                handshakeFlight.maxMessageSeq = hsf.messageSeq;
                handshakeFlight.maxRecordEpoch = hsf.recordEpoch;
                handshakeFlight.maxRecordSeq = hsf.recordSeq;
            } else {
                if (handshakeFlight.maxMessageSeq < hsf.messageSeq) {
                    handshakeFlight.maxMessageSeq = hsf.messageSeq;
                }

                int n = (hsf.recordEpoch - handshakeFlight.maxRecordEpoch);
                if (n > 0) {
                    handshakeFlight.maxRecordEpoch = hsf.recordEpoch;
                    handshakeFlight.maxRecordSeq = hsf.recordSeq;
                } else if (n == 0) {
                    // the same epoch
                    if (handshakeFlight.maxRecordSeq < hsf.recordSeq) {
                        handshakeFlight.maxRecordSeq = hsf.recordSeq;
                    }
                }   // Otherwise, it is unlikely to happen.
            }

            boolean fragmented = false;
            if ((hsf.fragmentOffset) != 0 ||
                (hsf.fragmentLength != hsf.messageLength)) {

                fragmented = true;
            }

            List<HoleDescriptor> holes =
                    handshakeFlight.holesMap.get(hsf.handshakeType);
            if (holes == null) {
                if (!fragmented) {
                    holes = Collections.emptyList();
                } else {
                    holes = new LinkedList<HoleDescriptor>();
                    holes.add(new HoleDescriptor(0, hsf.messageLength));
                }
                handshakeFlight.holesMap.put(hsf.handshakeType, holes);
            } else if (holes.isEmpty()) {
                // Have got the full handshake message.  This record may be
                // a handshake message retransmission.  Discard this record.
                //
                // It's OK to discard retransmission as the handshake hash
                // is computed as if each handshake message had been sent
                // as a single fragment.
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Have got the full message, discard it.");
                }

                return;
            }

            if (fragmented) {
                int fragmentLimit = hsf.fragmentOffset + hsf.fragmentLength;
                for (int i = 0; i < holes.size(); i++) {

                    HoleDescriptor hole = holes.get(i);
                    if ((hole.limit <= hsf.fragmentOffset) ||
                        (hole.offset >= fragmentLimit)) {
                        // Also discard overlapping handshake retransmissions.
                        continue;
                    }

                    // The ranges SHOULD NOT overlap.
                    if (((hole.offset > hsf.fragmentOffset) &&
                         (hole.offset < fragmentLimit)) ||
                        ((hole.limit > hsf.fragmentOffset) &&
                         (hole.limit < fragmentLimit))) {

                        if (debug != null && Debug.isOn("ssl")) {
                            Debug.log("Discard invalid record: " +
                                "handshake fragment ranges are overlapping");
                        }

                        // invalid, discard it [section 4.1.2.7, RFC 6347]
                        return;
                    }

                    // This record interacts with this hole, fill the hole.
                    holes.remove(i);
                    // i--;

                    if (hsf.fragmentOffset > hole.offset) {
                        holes.add(new HoleDescriptor(
                                hole.offset, hsf.fragmentOffset));
                        // i++;
                    }

                    if (fragmentLimit < hole.limit) {
                        holes.add(new HoleDescriptor(
                                fragmentLimit, hole.limit));
                        // i++;
                    }

                    // As no ranges overlap, no interact with other holes.
                    break;
                }
            }

            // buffer this fragment
            if (hsf.handshakeType == HandshakeMessage.ht_finished) {
                // Need no status update.
                bufferedFragments.add(hsf);
            } else {
                bufferFragment(hsf);
            }
        }

        // Queue up a ChangeCipherSpec message
        void queueUpChangeCipherSpec(RecordFragment rf) {
            if (!isDesirable(rf)) {
                // Not a dedired record, discard it.
                return;
            }

            // Clean up the retransmission messages if necessary.
            cleanUpRetransmit(rf);

            // Is it the first message of this flight?
            //
            // Note: the first message of the final flight is ChangeCipherSpec.
            if (expectCCSFlight) {
                handshakeFlight.handshakeType = HandshakeFlight.HF_UNKNOWN;
                handshakeFlight.flightEpoch = rf.recordEpoch;
            }

            // The epoch should be the same as the first message of the flight.
            if (handshakeFlight.maxRecordSeq < rf.recordSeq) {
                handshakeFlight.maxRecordSeq = rf.recordSeq;
            }

            // buffer this fragment
            bufferFragment(rf);
        }

        // Queue up a ciphertext message.
        //
        // Note: not yet be able to decrypt the message.
        void queueUpFragment(RecordFragment rf) {
            if (!isDesirable(rf)) {
                // Not a dedired record, discard it.
                return;
            }

            // Clean up the retransmission messages if necessary.
            cleanUpRetransmit(rf);

            // buffer this fragment
            bufferFragment(rf);
        }

        private void bufferFragment(RecordFragment rf) {
            // append this fragment
            bufferedFragments.add(rf);

            if (flightIsReady) {
                flightIsReady = false;
            }

            if (!needToCheckFlight) {
                needToCheckFlight = true;
            }
        }

        private void cleanUpRetransmit(RecordFragment rf) {
            // Does the next flight start?
            boolean isNewFlight = false;
            if (precedingFlight != null) {
                if (precedingFlight.flightEpoch < rf.recordEpoch) {
                    isNewFlight = true;
                } else {
                    if (rf instanceof HandshakeFragment) {
                        HandshakeFragment hsf = (HandshakeFragment)rf;
                        if (precedingFlight.maxMessageSeq  < hsf.messageSeq) {
                            isNewFlight = true;
                        }
                    } else if (rf.contentType != Record.ct_change_cipher_spec) {
                        // ciphertext
                        if (precedingFlight.maxRecordEpoch < rf.recordEpoch) {
                            isNewFlight = true;
                        }
                    }
                }
            }

            if (!isNewFlight) {
                // Need no cleanup.
                return;
            }

            // clean up the buffer
            for (Iterator<RecordFragment> it = bufferedFragments.iterator();
                    it.hasNext();) {

                RecordFragment frag = it.next();
                boolean isOld = false;
                if (frag.recordEpoch < precedingFlight.maxRecordEpoch) {
                    isOld = true;
                } else if (frag.recordEpoch == precedingFlight.maxRecordEpoch) {
                    if (frag.recordSeq <= precedingFlight.maxRecordSeq) {
                        isOld = true;
                    }
                }

                if (!isOld && (frag instanceof HandshakeFragment)) {
                    HandshakeFragment hsf = (HandshakeFragment)frag;
                    isOld = (hsf.messageSeq <= precedingFlight.maxMessageSeq);
                }

                if (isOld) {
                    it.remove();
                } else {
                    // Safe to break as items in the buffer are ordered.
                    break;
                }
            }

            // discard retransmissions of the previous flight if any.
            precedingFlight = null;
        }

        // Is a desired record?
        //
        // Check for retransmission and lost records.
        private boolean isDesirable(RecordFragment rf) {
            //
            // Discard records old than the previous epoch.
            //
            int previousEpoch = nextRecordEpoch - 1;
            if (rf.recordEpoch < previousEpoch) {
                // Too old to use, discard this record.
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Too old epoch to use this record, discard it.");
                }

                return false;
            }

            //
            // Allow retransmission of last flight of the previous epoch
            //
            // For example, the last server delivered flight for session
            // resuming abbreviated handshaking consist three messages:
            //      ServerHello
            //      [ChangeCipherSpec]
            //      Finished
            //
            // The epoch number is incremented and the sequence number is reset
            // if the ChangeCipherSpec is sent.
            if (rf.recordEpoch == previousEpoch) {
                boolean isDesired = true;
                if (precedingFlight == null) {
                    isDesired = false;
                } else {
                    if (rf instanceof HandshakeFragment) {
                        HandshakeFragment hsf = (HandshakeFragment)rf;
                        if (precedingFlight.minMessageSeq > hsf.messageSeq) {
                            isDesired = false;
                        }
                    } else if (rf.contentType == Record.ct_change_cipher_spec) {
                        // ChangeCipherSpec
                        if (precedingFlight.flightEpoch != rf.recordEpoch) {
                            isDesired = false;
                        }
                    } else {        // ciphertext
                        if ((rf.recordEpoch < precedingFlight.maxRecordEpoch) ||
                            (rf.recordEpoch == precedingFlight.maxRecordEpoch &&
                                rf.recordSeq <= precedingFlight.maxRecordSeq)) {
                            isDesired = false;
                        }
                    }
                }

                if (!isDesired) {
                    // Too old to use, discard this retransmitted record
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log("Too old retransmission to use, discard it.");
                    }

                    return false;
                }
            } else if ((rf.recordEpoch == nextRecordEpoch) &&
                    (nextRecordSeq > rf.recordSeq)) {

                // Previously disordered record for the current epoch.
                //
                // Should has been retransmitted. Discard this record.
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Lagging behind record (sequence), discard it.");
                }

                return false;
            }

            return true;
        }

        private boolean isEmpty() {
            return (bufferedFragments.isEmpty() ||
                    (!flightIsReady && !needToCheckFlight) ||
                    (needToCheckFlight && !flightIsReady()));
        }

        Plaintext acquirePlaintext() {
            if (bufferedFragments.isEmpty()) {
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("No received handshake messages");
                }
                return null;
            }

            if (!flightIsReady && needToCheckFlight) {
                // check the fligth status
                flightIsReady = flightIsReady();

                // Reset if this flight is ready.
                if (flightIsReady) {
                    // Retransmitted handshake messages are not needed for
                    // further handshaking processing.
                    if (handshakeFlight.isRetransmitOf(precedingFlight)) {
                        // cleanup
                        bufferedFragments.clear();

                        // Reset the next handshake flight.
                        resetHandshakeFlight(precedingFlight);

                        if (debug != null && Debug.isOn("verbose")) {
                            Debug.log("Received a retransmission flight.");
                        }

                        return Plaintext.PLAINTEXT_NULL;
                    }
                }

                needToCheckFlight = false;
            }

            if (!flightIsReady) {
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("The handshake flight is not ready to use: " +
                                handshakeFlight.handshakeType);
                }
                return null;
            }

            RecordFragment rFrag = bufferedFragments.first();
            Plaintext plaintext;
            if (!rFrag.isCiphertext) {
                // handshake message, or ChangeCipherSpec message
                plaintext = acquireHandshakeMessage();

                // Reset the handshake flight.
                if (bufferedFragments.isEmpty()) {
                    // Need not to backup the holes map.  Clear up it at first.
                    handshakeFlight.holesMap.clear();   // cleanup holes map

                    // Update the preceding flight.
                    precedingFlight = (HandshakeFlight)handshakeFlight.clone();

                    // Reset the next handshake flight.
                    resetHandshakeFlight(precedingFlight);

                    if (expectCCSFlight &&
                            (precedingFlight.flightEpoch ==
                                    HandshakeFlight.HF_UNKNOWN)) {
                        expectCCSFlight = false;
                    }
                }
            } else {
                // a Finished message or other ciphertexts
                plaintext = acquireCachedMessage();
            }

            return plaintext;
        }

        //
        // Reset the handshake flight from a previous one.
        //
        private void resetHandshakeFlight(HandshakeFlight prev) {
            // Reset the next handshake flight.
            handshakeFlight.handshakeType = HandshakeFlight.HF_UNKNOWN;
            handshakeFlight.flightEpoch = prev.maxRecordEpoch;
            if (prev.flightEpoch != prev.maxRecordEpoch) {
                // a new epoch starts
                handshakeFlight.minMessageSeq = 0;
            } else {
                // stay at the same epoch
                //
                // The minimal message sequence number will get updated if
                // a flight retransmission happens.
                handshakeFlight.minMessageSeq = prev.maxMessageSeq + 1;
            }

            // cleanup the maximum sequence number and epoch number.
            //
            // Note: actually, we need to do nothing because the reassembler
            // of handshake messages will reset them properly even for
            // retransmissions.
            //
            handshakeFlight.maxMessageSeq = 0;
            handshakeFlight.maxRecordEpoch = handshakeFlight.flightEpoch;

            // Record sequence number cannot wrap even for retransmissions.
            handshakeFlight.maxRecordSeq = prev.maxRecordSeq + 1;

            // cleanup holes map
            handshakeFlight.holesMap.clear();

            // Ready to accept new input record.
            flightIsReady = false;
            needToCheckFlight = false;
        }

        private Plaintext acquireCachedMessage() {

            RecordFragment rFrag = bufferedFragments.first();
            if (readEpoch != rFrag.recordEpoch) {
                if (readEpoch > rFrag.recordEpoch) {
                    // discard old records
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log("Discard old buffered ciphertext fragments.");
                    }
                    bufferedFragments.remove(rFrag);    // popup the fragment
                }

                // reset the flight
                if (flightIsReady) {
                    flightIsReady = false;
                }

                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Not yet ready to decrypt the cached fragments.");
                }
                return null;
            }

            bufferedFragments.remove(rFrag);    // popup the fragment

            ByteBuffer fragment = ByteBuffer.wrap(rFrag.fragment);
            ByteBuffer plaintextFragment = null;
            try {
                plaintextFragment = decrypt(readAuthenticator, readCipher,
                        rFrag.contentType, fragment, rFrag.recordEnS);
            } catch (BadPaddingException bpe) {
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Discard invalid record: " + bpe);
                }

                // invalid, discard this record [section 4.1.2.7, RFC 6347]
                return null;
            }

            // The ciphtext handshake message can only be Finished (the
            // end of this flight), ClinetHello or HelloRequest (the
            // beginning of the next flight) message.  Need not to check
            // any ChangeCipherSpec message.
            if (rFrag.contentType == Record.ct_handshake) {
                while (plaintextFragment.remaining() > 0) {
                    HandshakeFragment hsFrag = parseHandshakeMessage(
                            rFrag.contentType,
                            rFrag.majorVersion, rFrag.minorVersion,
                            rFrag.recordEnS, rFrag.recordEpoch, rFrag.recordSeq,
                            plaintextFragment);

                    if (hsFrag == null) {
                        // invalid, discard this record
                        if (debug != null && Debug.isOn("verbose")) {
                            Debug.printHex(
                                    "Invalid handshake fragment, discard it",
                                    plaintextFragment);
                        }
                        return null;
                    }

                    queueUpHandshake(hsFrag);
                    // The flight ready status (flightIsReady) should have
                    // been checked and updated for the Finished handshake
                    // message before the decryption.  Please don't update
                    // flightIsReady for Finished messages.
                    if (hsFrag.handshakeType != HandshakeMessage.ht_finished) {
                        flightIsReady = false;
                        needToCheckFlight = true;
                    }
                }

                return acquirePlaintext();
            } else {
                return new Plaintext(rFrag.contentType,
                        rFrag.majorVersion, rFrag.minorVersion,
                        rFrag.recordEpoch,
                        Authenticator.toLong(rFrag.recordEnS),
                        plaintextFragment);
            }
        }

        private Plaintext acquireHandshakeMessage() {

            RecordFragment rFrag = bufferedFragments.first();
            if (rFrag.contentType == Record.ct_change_cipher_spec) {
                this.nextRecordEpoch = rFrag.recordEpoch + 1;

                // For retransmissions, the next record sequence number is a
                // positive value.  Don't worry about it as the acquiring of
                // the immediately followed Finished handshake message will
                // reset the next record sequence number correctly.
                this.nextRecordSeq = 0;

                // Popup the fragment.
                bufferedFragments.remove(rFrag);

                // Reload if this message has been reserved for handshake hash.
                handshakeHash.reload();

                return new Plaintext(rFrag.contentType,
                        rFrag.majorVersion, rFrag.minorVersion,
                        rFrag.recordEpoch,
                        Authenticator.toLong(rFrag.recordEnS),
                        ByteBuffer.wrap(rFrag.fragment));
            } else {    // rFrag.contentType == Record.ct_handshake
                HandshakeFragment hsFrag = (HandshakeFragment)rFrag;
                if ((hsFrag.messageLength == hsFrag.fragmentLength) &&
                    (hsFrag.fragmentOffset == 0)) {     // no fragmentation

                    bufferedFragments.remove(rFrag);    // popup the fragment

                    // this.nextRecordEpoch = hsFrag.recordEpoch;
                    this.nextRecordSeq = hsFrag.recordSeq + 1;

                    // Note: may try to avoid byte array copy in the future.
                    byte[] recordFrag = new byte[hsFrag.messageLength + 4];
                    Plaintext plaintext = new Plaintext(hsFrag.contentType,
                            hsFrag.majorVersion, hsFrag.minorVersion,
                            hsFrag.recordEpoch,
                            Authenticator.toLong(hsFrag.recordEnS),
                            ByteBuffer.wrap(recordFrag));

                    // fill the handshake fragment of the record
                    recordFrag[0] = hsFrag.handshakeType;
                    recordFrag[1] =
                            (byte)((hsFrag.messageLength >>> 16) & 0xFF);
                    recordFrag[2] =
                            (byte)((hsFrag.messageLength >>> 8) & 0xFF);
                    recordFrag[3] = (byte)(hsFrag.messageLength & 0xFF);

                    System.arraycopy(hsFrag.fragment, 0,
                            recordFrag, 4, hsFrag.fragmentLength);

                    // handshake hashing
                    handshakeHashing(hsFrag, plaintext);

                    return plaintext;
                } else {                // fragmented handshake message
                    // the first record
                    //
                    // Note: may try to avoid byte array copy in the future.
                    byte[] recordFrag = new byte[hsFrag.messageLength + 4];
                    Plaintext plaintext = new Plaintext(hsFrag.contentType,
                            hsFrag.majorVersion, hsFrag.minorVersion,
                            hsFrag.recordEpoch,
                            Authenticator.toLong(hsFrag.recordEnS),
                            ByteBuffer.wrap(recordFrag));

                    // fill the handshake fragment of the record
                    recordFrag[0] = hsFrag.handshakeType;
                    recordFrag[1] =
                            (byte)((hsFrag.messageLength >>> 16) & 0xFF);
                    recordFrag[2] =
                            (byte)((hsFrag.messageLength >>> 8) & 0xFF);
                    recordFrag[3] = (byte)(hsFrag.messageLength & 0xFF);

                    int msgSeq = hsFrag.messageSeq;
                    long maxRecodeSN = hsFrag.recordSeq;
                    HandshakeFragment hmFrag = hsFrag;
                    do {
                        System.arraycopy(hmFrag.fragment, 0,
                                recordFrag, hmFrag.fragmentOffset + 4,
                                hmFrag.fragmentLength);
                        // popup the fragment
                        bufferedFragments.remove(rFrag);

                        if (maxRecodeSN < hmFrag.recordSeq) {
                            maxRecodeSN = hmFrag.recordSeq;
                        }

                        // Note: may buffer retransmitted fragments in order to
                        // speed up the reassembly in the future.

                        // read the next buffered record
                        if (!bufferedFragments.isEmpty()) {
                            rFrag = bufferedFragments.first();
                            if (rFrag.contentType != Record.ct_handshake) {
                                break;
                            } else {
                                hmFrag = (HandshakeFragment)rFrag;
                            }
                        }
                    } while (!bufferedFragments.isEmpty() &&
                            (msgSeq == hmFrag.messageSeq));

                    // handshake hashing
                    handshakeHashing(hsFrag, plaintext);

                    this.nextRecordSeq = maxRecodeSN + 1;

                    return plaintext;
                }
            }
        }

        boolean flightIsReady() {

            byte flightType = handshakeFlight.handshakeType;
            if (flightType == HandshakeFlight.HF_UNKNOWN) {
                //
                // the ChangeCipherSpec/Finished flight
                //
                if (expectCCSFlight) {
                    // Have the ChangeCipherSpec/Finished flight been received?
                    boolean isReady = hasFinishedMessage(bufferedFragments);
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log(
                            "Has the final flight been received? " + isReady);
                    }

                    return isReady;
                }

                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("No flight is received yet.");
                }

                return false;
            }

            if ((flightType == HandshakeMessage.ht_client_hello) ||
                (flightType == HandshakeMessage.ht_hello_request) ||
                (flightType == HandshakeMessage.ht_hello_verify_request)) {

                // single handshake message flight
                boolean isReady = hasCompleted(flightType);
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Is the handshake message completed? " + isReady);
                }

                return isReady;
            }

            //
            // the ServerHello flight
            //
            if (flightType == HandshakeMessage.ht_server_hello) {
                // Firstly, check the first flight handshake message.
                if (!hasCompleted(flightType)) {
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log(
                            "The ServerHello message is not completed yet.");
                    }

                    return false;
                }

                //
                // an abbreviated handshake
                //
                if (hasFinishedMessage(bufferedFragments)) {
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log("It's an abbreviated handshake.");
                    }

                    return true;
                }

                //
                // a full handshake
                //
                List<HoleDescriptor> holes = handshakeFlight.holesMap.get(
                        HandshakeMessage.ht_server_hello_done);
                if ((holes == null) || !holes.isEmpty()) {
                    // Not yet got the final message of the flight.
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log("Not yet got the ServerHelloDone message");
                    }

                    return false;
                }

                // Have all handshake message been received?
                boolean isReady = hasCompleted(bufferedFragments,
                            handshakeFlight.minMessageSeq,
                            handshakeFlight.maxMessageSeq);
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Is the ServerHello flight (message " +
                            handshakeFlight.minMessageSeq + "-" +
                            handshakeFlight.maxMessageSeq +
                            ") completed? " + isReady);
                }

                return isReady;
            }

            //
            // the ClientKeyExchange flight
            //
            // Note: need to consider more messages in this flight if
            //       ht_supplemental_data and ht_certificate_url are
            //       suppported in the future.
            //
            if ((flightType == HandshakeMessage.ht_certificate) ||
                (flightType == HandshakeMessage.ht_client_key_exchange)) {

                // Firstly, check the first flight handshake message.
                if (!hasCompleted(flightType)) {
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log(
                            "The ClientKeyExchange or client Certificate " +
                            "message is not completed yet.");
                    }

                    return false;
                }

                // Is client CertificateVerify a mandatory message?
                if (flightType == HandshakeMessage.ht_certificate) {
                    if (needClientVerify(bufferedFragments) &&
                        !hasCompleted(ht_certificate_verify)) {

                        if (debug != null && Debug.isOn("verbose")) {
                            Debug.log(
                                "Not yet have the CertificateVerify message");
                        }

                        return false;
                    }
                }

                if (!hasFinishedMessage(bufferedFragments)) {
                    // not yet have the ChangeCipherSpec/Finished messages
                    if (debug != null && Debug.isOn("verbose")) {
                        Debug.log(
                            "Not yet have the ChangeCipherSpec and " +
                            "Finished messages");
                    }

                    return false;
                }

                // Have all handshake message been received?
                boolean isReady = hasCompleted(bufferedFragments,
                            handshakeFlight.minMessageSeq,
                            handshakeFlight.maxMessageSeq);
                if (debug != null && Debug.isOn("verbose")) {
                    Debug.log("Is the ClientKeyExchange flight (message " +
                            handshakeFlight.minMessageSeq + "-" +
                            handshakeFlight.maxMessageSeq +
                            ") completed? " + isReady);
                }

                return isReady;
            }

            //
            // Otherwise, need to receive more handshake messages.
            //
            if (debug != null && Debug.isOn("verbose")) {
                Debug.log("Need to receive more handshake messages");
            }

            return false;
        }

        // Looking for the ChangeCipherSpec and Finished messages.
        //
        // As the cached Finished message should be a ciphertext, we don't
        // exactly know a ciphertext is a Finished message or not.  According
        // to the spec of TLS/DTLS handshaking, a Finished message is always
        // sent immediately after a ChangeCipherSpec message.  The first
        // ciphertext handshake message should be the expected Finished message.
        private boolean hasFinishedMessage(Set<RecordFragment> fragments) {

            boolean hasCCS = false;
            boolean hasFin = false;
            for (RecordFragment fragment : fragments) {
                if (fragment.contentType == Record.ct_change_cipher_spec) {
                    if (hasFin) {
                        return true;
                    }
                    hasCCS = true;
                } else if (fragment.contentType == Record.ct_handshake) {
                    // Finished is the first expected message of a new epoch.
                    if (fragment.isCiphertext) {
                        if (hasCCS) {
                            return true;
                        }
                        hasFin = true;
                    }
                }
            }

            return hasFin && hasCCS;
        }

        // Is client CertificateVerify a mandatory message?
        //
        // In the current implementation, client CertificateVerify is a
        // mandatory message if the client Certificate is not empty.
        private boolean needClientVerify(Set<RecordFragment> fragments) {

            // The caller should have checked the completion of the first
            // present handshake message.  Need not to check it again.
            for (RecordFragment rFrag : fragments) {
                if ((rFrag.contentType != Record.ct_handshake) ||
                        rFrag.isCiphertext) {
                    break;
                }

                HandshakeFragment hsFrag = (HandshakeFragment)rFrag;
                if (hsFrag.handshakeType != HandshakeMessage.ht_certificate) {
                    continue;
                }

                return (rFrag.fragment != null) &&
                   (rFrag.fragment.length > DTLSRecord.minCertPlaintextSize);
            }

            return false;
        }

        private boolean hasCompleted(byte handshakeType) {
            List<HoleDescriptor> holes =
                    handshakeFlight.holesMap.get(handshakeType);
            if (holes == null) {
                // not yet received this kind of handshake message
                return false;
            }

            return holes.isEmpty();  // no fragment hole for complete message
        }

        private boolean hasCompleted(
                Set<RecordFragment> fragments,
                int presentMsgSeq, int endMsgSeq) {

            // The caller should have checked the completion of the first
            // present handshake message.  Need not to check it again.
            for (RecordFragment rFrag : fragments) {
                if ((rFrag.contentType != Record.ct_handshake) ||
                        rFrag.isCiphertext) {
                    break;
                }

                HandshakeFragment hsFrag = (HandshakeFragment)rFrag;
                if (hsFrag.messageSeq == presentMsgSeq) {
                    continue;
                } else if (hsFrag.messageSeq == (presentMsgSeq + 1)) {
                    // check the completion of the handshake message
                    if (!hasCompleted(hsFrag.handshakeType)) {
                        return false;
                    }

                    presentMsgSeq = hsFrag.messageSeq;
                } else {
                    // not yet got handshake message next to presentMsgSeq
                    break;
                }
            }

            return (presentMsgSeq >= endMsgSeq);
                        // false: if not yet got all messages of the flight.
        }

        private void handshakeHashing(
                HandshakeFragment hsFrag, Plaintext plaintext) {

            byte hsType = hsFrag.handshakeType;
            if ((hsType == HandshakeMessage.ht_hello_request) ||
                (hsType == HandshakeMessage.ht_hello_verify_request)) {

                // omitted from handshake hash computation
                return;
            }

            if ((hsFrag.messageSeq == 0) &&
                (hsType == HandshakeMessage.ht_client_hello)) {

                // omit initial ClientHello message
                //
                //  4: handshake header
                //  2: ClientHello.client_version
                // 32: ClientHello.random
                int sidLen = plaintext.fragment.get(38);

                if (sidLen == 0) {      // empty session_id, initial handshake
                    return;
                }
            }

            // calculate the DTLS header
            byte[] temporary = new byte[12];    // 12: handshake header size

            // Handshake.msg_type
            temporary[0] = hsFrag.handshakeType;

            // Handshake.length
            temporary[1] = (byte)((hsFrag.messageLength >> 16) & 0xFF);
            temporary[2] = (byte)((hsFrag.messageLength >> 8) & 0xFF);
            temporary[3] = (byte)(hsFrag.messageLength & 0xFF);

            // Handshake.message_seq
            temporary[4] = (byte)((hsFrag.messageSeq >> 8) & 0xFF);
            temporary[5] = (byte)(hsFrag.messageSeq & 0xFF);

            // Handshake.fragment_offset
            temporary[6] = 0;
            temporary[7] = 0;
            temporary[8] = 0;

            // Handshake.fragment_length
            temporary[9] = temporary[1];
            temporary[10] = temporary[2];
            temporary[11] = temporary[3];

            plaintext.fragment.position(4);     // ignore the TLS header
            if ((hsType != HandshakeMessage.ht_finished) &&
                (hsType != HandshakeMessage.ht_certificate_verify)) {

                if (handshakeHash == null) {
                    // used for cache only
                    handshakeHash = new HandshakeHash(false);
                }
                handshakeHash.update(temporary, 0, 12);
                handshakeHash.update(plaintext.fragment);
            } else {
                // Reserve until this handshake message has been processed.
                if (handshakeHash == null) {
                    // used for cache only
                    handshakeHash = new HandshakeHash(false);
                }
                handshakeHash.reserve(temporary, 0, 12);
                handshakeHash.reserve(plaintext.fragment);
            }
            plaintext.fragment.position(0);     // restore the position
        }
    }
}

