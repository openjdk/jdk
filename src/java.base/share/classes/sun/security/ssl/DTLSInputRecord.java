/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.*;
import javax.crypto.BadPaddingException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import sun.security.ssl.SSLCipher.SSLReadCipher;

/**
 * DTLS {@code InputRecord} implementation for {@code SSLEngine}.
 */
final class DTLSInputRecord extends InputRecord implements DTLSRecord {
    private DTLSReassembler reassembler = null;
    private int             readEpoch;
    private SSLContextImpl  sslContext;

    DTLSInputRecord(HandshakeHash handshakeHash) {
        super(handshakeHash, SSLReadCipher.nullDTlsReadCipher());
        this.readEpoch = 0;
    }

    // Method to set TransportContext
    public void setTransportContext(TransportContext tc) {
        this.tc = tc;
    }

    // Method to set SSLContext
    public void setSSLContext(SSLContextImpl sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    void changeReadCiphers(SSLReadCipher readCipher) {
        this.readCipher = readCipher;
        this.readEpoch++;
    }

    @Override
    public void close() throws IOException {
        if (!isClosed) {
            super.close();
        }
    }

    @Override
    boolean isEmpty() {
        return ((reassembler == null) || reassembler.isEmpty());
    }

    @Override
    int estimateFragmentSize(int packetSize) {
        if (packetSize > 0) {
            return readCipher.estimateFragmentSize(packetSize, headerSize);
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
    void finishHandshake() {
        reassembler = null;
    }

    @Override
    Plaintext acquirePlaintext() throws SSLProtocolException {
        if (reassembler != null) {
            return reassembler.acquirePlaintext();
        }

        return null;
    }

     @Override
    Plaintext[] decode(ByteBuffer[] srcs, int srcsOffset,
            int srcsLength) throws IOException, BadPaddingException {
        if (srcs == null || srcs.length == 0 || srcsLength == 0) {
            Plaintext pt = acquirePlaintext();
            return pt == null ? new Plaintext[0] : new Plaintext[] { pt };
        } else if (srcsLength == 1) {
            return decode(srcs[srcsOffset]);
        } else {
            ByteBuffer packet = extract(srcs,
                    srcsOffset, srcsLength, DTLSRecord.headerSize);
            return decode(packet);
        }
    }

    Plaintext[] decode(ByteBuffer packet) throws SSLProtocolException {
        if (isClosed) {
            return null;
        }

        if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.RECORD_PACKET)) {
            SSLLogger.fine("Raw read", packet);
        }

        // The caller should have validated the record.
        int srcPos = packet.position();
        int srcLim = packet.limit();

        byte contentType = packet.get();                   // pos: 0
        byte majorVersion = packet.get();                  // pos: 1
        byte minorVersion = packet.get();                  // pos: 2
        byte[] recordEnS = new byte[8];                    // epoch + sequence
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

        if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.RECORD)) {
            SSLLogger.fine("READ: " +
                    ProtocolVersion.nameOf(majorVersion, minorVersion) +
                    " " + ContentType.nameOf(contentType) + ", length = " +
                    contentLen);
        }

        int recLim = Math.addExact(srcPos, DTLSRecord.headerSize + contentLen);

        if (this.readEpoch > recordEpoch) {
            // Reset the position of the packet buffer.
            packet.position(recLim);
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.RECORD)) {
                SSLLogger.fine("READ: discard this old record", recordEnS);
            }
            return null;
        }

        // Buffer next epoch message if necessary.
        if (this.readEpoch < recordEpoch) {
            // Discard the record younger than the current epoch if:
            // 1. it is not a handshake message, or
            // 3. it is not of next epoch.
            if ((contentType != ContentType.HANDSHAKE.id &&
                    contentType != ContentType.CHANGE_CIPHER_SPEC.id) ||
                (reassembler == null &&
                    contentType != ContentType.HANDSHAKE.id) ||
                (this.readEpoch < (recordEpoch - 1))) {

                packet.position(recLim);

                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine("Premature record (epoch), discard it.");
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

            if (reassembler == null) {
                reassembler = new DTLSReassembler(recordEpoch);
            }
            reassembler.queueUpFragment(buffered);

            // consume the full record in the packet buffer.
            packet.position(recLim);

            Plaintext pt = reassembler.acquirePlaintext();
            return pt == null ? null : new Plaintext[] { pt };
        }

        //
        // Now, the message is of this epoch.
        //
        // decrypt the fragment
        packet.limit(recLim);
        packet.position(srcPos + DTLSRecord.headerSize);

        ByteBuffer plaintextFragment;
        try {
            Plaintext plaintext =
                    readCipher.decrypt(contentType, packet, recordEnS);
            plaintextFragment = plaintext.fragment;
            contentType = plaintext.contentType;
        } catch (GeneralSecurityException gse) {
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine("Discard invalid record: " + gse);
            }

            // invalid, discard this record [section 4.1.2.7, RFC 6347]
            return null;
        } finally {
            // consume a complete record
            packet.limit(srcLim);
            packet.position(recLim);
        }

        if (contentType != ContentType.CHANGE_CIPHER_SPEC.id &&
            contentType != ContentType.HANDSHAKE.id) {   // app data or alert
                                                    // no retransmission
            // Cleanup the handshake reassembler if necessary.
            if ((reassembler != null) &&
                    (reassembler.handshakeEpoch < recordEpoch)) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine("Cleanup the handshake reassembler");
                }

                reassembler = null;
            }

            return new Plaintext[] {
                    new Plaintext(contentType, majorVersion, minorVersion,
                            recordEpoch, Authenticator.toLong(recordEnS),
                            plaintextFragment)};
        }

        if (contentType == ContentType.CHANGE_CIPHER_SPEC.id) {
            if (reassembler == null) {
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
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                                "Invalid handshake message, discard it.");
                    }

                    return null;
                }

                if (reassembler == null) {
                    reassembler = new DTLSReassembler(recordEpoch);
                }

                reassembler.queueUpHandshake(hsFrag);
            }
        }

        // Completed the read of the full record.  Acquire the reassembled
        // messages.
        if (reassembler != null) {
            Plaintext pt = reassembler.acquirePlaintext();
            return pt == null ? null : new Plaintext[] { pt };
        }

        if (SSLLogger.isOn() &&
                SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
             SSLLogger.fine("The reassembler is not initialized yet.");
        }

        return null;
    }

    @Override
    int bytesInCompletePacket(
        ByteBuffer[] srcs, int srcsOffset, int srcsLength) throws IOException {

        return bytesInCompletePacket(srcs[srcsOffset]);
    }

    private int bytesInCompletePacket(ByteBuffer packet) throws SSLException {

        // DTLS length field is in bytes 11/12
        if (packet.remaining() < headerSize) {
            return -1;
        }

        // Last sanity check that it's not a wild record
        int pos = packet.position();

        // Check the content type of the record.
        byte contentType = packet.get(pos);
        if (ContentType.valueOf(contentType) == null) {
            throw new SSLException(
                    "Unrecognized SSL message, plaintext connection?");
        }

        // Check the protocol version of the record.
        byte majorVersion = packet.get(pos + 1);
        byte minorVersion = packet.get(pos + 2);
        if (!ProtocolVersion.isNegotiable(
                majorVersion, minorVersion, true, false)) {
            throw new SSLException("Unrecognized record version " +
                    ProtocolVersion.nameOf(majorVersion, minorVersion) +
                    " , plaintext connection?");
        }

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

    private static HandshakeFragment parseHandshakeMessage(
            byte contentType, byte majorVersion, byte minorVersion,
            byte[] recordEnS, int recordEpoch, long recordSeq,
            ByteBuffer plaintextFragment) throws SSLProtocolException {

        int remaining = plaintextFragment.remaining();
        if (remaining < handshakeHeaderSize) {
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine("Discard invalid record: " +
                        "too small record to hold a handshake fragment");
            }

            // invalid, discard this record [section 4.1.2.7, RFC 6347]
            return null;
        }

        // Fail fast for unknown handshake message.
        byte handshakeType = plaintextFragment.get();       // pos: 0
        if (!SSLHandshake.isKnown(handshakeType)) {
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine("Discard invalid record: " +
                        "unknown handshake type size, Handshake.msg_type = " +
                        (handshakeType & 0xFF));
            }

            // invalid, discard this record [section 4.1.2.7, RFC 6347]
            return null;
        }

        int messageLength =
                ((plaintextFragment.get() & 0xFF) << 16) |
                ((plaintextFragment.get() & 0xFF) << 8) |
                 (plaintextFragment.get() & 0xFF);          // pos: 1-3

        if (messageLength > SSLConfiguration.maxHandshakeMessageSize) {
            throw new SSLProtocolException(
                    "The size of the handshake message ("
                    + messageLength
                    + ") exceeds the maximum allowed size ("
                    + SSLConfiguration.maxHandshakeMessageSize
                    + ")");
        }

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
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine("Discard invalid record: " +
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
            if (this.contentType == ContentType.CHANGE_CIPHER_SPEC.id) {
                if (o.contentType == ContentType.CHANGE_CIPHER_SPEC.id) {
                    // Only one incoming ChangeCipherSpec message for an epoch.
                    //
                    // Ignore duplicated ChangeCipherSpec messages.
                    return Integer.compare(this.recordEpoch, o.recordEpoch);
                } else if ((this.recordEpoch == o.recordEpoch) &&
                        (o.contentType == ContentType.HANDSHAKE.id)) {
                    // ChangeCipherSpec is the latest message of an epoch.
                    return 1;
                }
            } else if (o.contentType == ContentType.CHANGE_CIPHER_SPEC.id) {
                if ((this.recordEpoch == o.recordEpoch) &&
                        (this.contentType == ContentType.HANDSHAKE.id)) {
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
            if (o instanceof HandshakeFragment other) {
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
                // Note that the acquiring processes will reassemble
                // the fragments later.
                return compareToSequence(o.recordEpoch, o.recordSeq);
            }

            return super.compareTo(o);
        }
    }

    /**
     * Turn a sufficiently-large initial ClientHello fragment into one that
     * stops immediately after the compression methods.  This is only used
     * for the initial CH message fragment at offset 0.
     *
     * @param srcFrag the fragment actually received by the DTLSReassembler
     * @param limit the size of the new, cloned/truncated handshake fragment
     *
     * @return a truncated handshake fragment that is sized to look like a
     * complete message, but actually contains only up to the compression
     * methods (no extensions)
     */
    private static HandshakeFragment truncateChFragment(HandshakeFragment srcFrag,
            int limit) {
        return new HandshakeFragment(Arrays.copyOf(srcFrag.fragment, limit),
                srcFrag.contentType, srcFrag.majorVersion,
                srcFrag.minorVersion, srcFrag.recordEnS, srcFrag.recordEpoch,
                srcFrag.recordSeq, srcFrag.handshakeType, limit,
                srcFrag.messageSeq, srcFrag.fragmentOffset, limit);
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
        static final byte HF_UNKNOWN = SSLHandshake.NOT_APPLICABLE.id;

        byte        handshakeType;      // handshake type
        int         flightEpoch;        // the epoch of the first message
        int         minMessageSeq;      // minimal message sequence

        int         maxMessageSeq;      // maximum message sequence
        int         maxRecordEpoch;     // maximum record sequence number
        long        maxRecordSeq;       // maximum record sequence number

        HashMap<Byte, List<HoleDescriptor>> holesMap;

        // A map used to check duplicated handshake messages.
        HashMap<Byte, Integer> messageSeqMap;

        HandshakeFlight() {
            this.handshakeType = HF_UNKNOWN;
            this.flightEpoch = 0;
            this.minMessageSeq = 0;

            this.maxMessageSeq = 0;
            this.maxRecordEpoch = 0;
            this.maxRecordSeq = -1;

            this.holesMap = new HashMap<>(5);
            this.messageSeqMap = new HashMap<>(5);
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
            hf.messageSeqMap = new HashMap<>(this.messageSeqMap);

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
        void queueUpHandshake(HandshakeFragment hsf) throws SSLProtocolException {
            if (!isDesirable(hsf)) {
                // Not a desired record, discard it.
                return;
            }

            if (hsf.handshakeType == SSLHandshake.CLIENT_HELLO.id) {
                // validate the first or subsequent ClientHello message
                if ((hsf = valHello(hsf, hsf.messageSeq == 0)) == null) {
                    return;
                }
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
                    (hsf.handshakeType != SSLHandshake.FINISHED.id)) {

                // reset the handshake flight
                handshakeFlight.handshakeType = hsf.handshakeType;
                handshakeFlight.flightEpoch = hsf.recordEpoch;
                handshakeFlight.minMessageSeq = hsf.messageSeq;
            }

            if (hsf.handshakeType == SSLHandshake.FINISHED.id) {
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

            boolean fragmented = (hsf.fragmentOffset) != 0 ||
                    (hsf.fragmentLength != hsf.messageLength);

            List<HoleDescriptor> holes =
                    handshakeFlight.holesMap.get(hsf.handshakeType);
            if (holes == null) {
                if (!fragmented) {
                    holes = Collections.emptyList();
                } else {
                    holes = new LinkedList<>();
                    holes.add(new HoleDescriptor(0, hsf.messageLength));
                }
                handshakeFlight.holesMap.put(hsf.handshakeType, holes);
                handshakeFlight.messageSeqMap.put(hsf.handshakeType, hsf.messageSeq);
            } else if (holes.isEmpty()) {
                // Have got the full handshake message.  This record may be
                // a handshake message retransmission.  Discard this record.
                //
                // It's OK to discard retransmission as the handshake hash
                // is computed as if each handshake message had been sent
                // as a single fragment.
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine("Have got the full message, discard it.");
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
                    if (hole.offset > hsf.fragmentOffset ||
                            hole.limit < fragmentLimit) {

                        if (SSLLogger.isOn() &&
                                SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                            SSLLogger.fine("Discard invalid record: " +
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
            if (hsf.handshakeType == SSLHandshake.FINISHED.id) {
                // Make sure it's not a retransmitted message
                if (hsf.recordEpoch > handshakeEpoch) {
                    bufferedFragments.add(hsf);
                    flightIsReady = holes.isEmpty();
                }
            } else {
                bufferFragment(hsf);
            }
        }

        private HandshakeFragment valHello(HandshakeFragment hsf,
                boolean firstHello) {
            ServerHandshakeContext shc =
                    (ServerHandshakeContext) tc.handshakeContext;
            // Drop any fragment that is not a zero offset until we've received
            // a second (or possibly later) CH message that passes the cookie
            // check.
            if (shc == null || !shc.acceptCliHelloFragments) {
                if (hsf.fragmentOffset != 0) {
                    return null;
                }
            } else {
                // Let this fragment through to the DTLSReassembler as-is
                return hsf;
            }

            try {
                ByteBuffer fragmentData = ByteBuffer.wrap(hsf.fragment);

                ProtocolVersion pv = ProtocolVersion.valueOf(
                        Record.getInt16(fragmentData));
                if (!pv.isDTLS) {
                    return null;
                }
                // Read the random (32 bytes)
                if (fragmentData.remaining() < 32) {
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine("Rejected client hello fragment" +
                            "(bad random len) fo=" +
                            hsf.fragmentOffset + " fl=" + hsf.fragmentLength);
                    }
                    return null;
                }
                fragmentData.position(fragmentData.position() + 32);

                // SessionID
                byte[] sessId = Record.getBytes8(fragmentData);
                if (sessId.length > 0  &&
                        !SSLConfiguration.enableDtlsResumeCookie) {
                    // If we are in a resumption it is possible that the cookie
                    // exchange will be skipped.  This is a server-side setting
                    // and it is NOT the default.  If enableDtlsResumeCookie is
                    // false though, then we will buffer fragments since there
                    // is no cookie exchange to execute prior to performing
                    // reassembly.
                    return hsf;
                }

                // Cookie
                byte[] cookie = Record.getBytes8(fragmentData);
                if (firstHello && cookie.length != 0) {
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine("Rejected initial client hello " +
                            " fragment (bad cookie len) fo=" +
                            hsf.fragmentOffset + " fl=" + hsf.fragmentLength);
                    }
                    return null;
                }
                // CipherSuites
                Record.getBytes16(fragmentData);
                // Compression methods
                Record.getBytes8(fragmentData);

                // If it's the first fragment, we'll truncate it and push it
                // through the reassembler.
                if (firstHello) {
                    return truncateChFragment(hsf, fragmentData.position());
                } else {
                    HelloCookieManager hcMgr = sslContext.
                            getHelloCookieManager(ProtocolVersion.DTLS10);
                    ByteBuffer msgFragBuf = ByteBuffer.wrap(hsf.fragment, 0,
                            fragmentData.position());
                    ClientHello.ClientHelloMessage chMsg =
                            new ClientHello.ClientHelloMessage(shc, msgFragBuf, null);
                    if (!hcMgr.isCookieValid(shc, chMsg, cookie)) {
                        // Bad cookie check, truncate it and let the ClientHello
                        // consumer recheck, fail and take the appropriate action.
                        return truncateChFragment(hsf, fragmentData.position());
                    } else {
                        // It's a good cookie, return the original handshake
                        // fragment and let it go into the DTLSReassembler like
                        // any other fragment so we can wait for the rest of
                        // the CH message.
                        shc.acceptCliHelloFragments = true;
                        return hsf;
                    }
                }
            } catch (IOException ioe) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine("Rejected client hello fragment " +
                            "fo=" + hsf.fragmentOffset + " fl=" +
                            hsf.fragmentLength);
                }
                return null;
            }
        }

        // Queue up a ChangeCipherSpec message
        void queueUpChangeCipherSpec(RecordFragment rf)
                throws SSLProtocolException {
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
        void queueUpFragment(RecordFragment rf) throws SSLProtocolException {
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
                    if (rf instanceof HandshakeFragment hsf) {
                        if (precedingFlight.maxMessageSeq  < hsf.messageSeq) {
                            isNewFlight = true;
                        }
                    } else if (
                        rf.contentType != ContentType.CHANGE_CIPHER_SPEC.id) {

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

                if (!isOld && (frag instanceof HandshakeFragment hsf)) {
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
        private boolean isDesirable(RecordFragment rf) throws SSLProtocolException {
            //
            // Discard records older than the previous epoch.
            //
            int previousEpoch = nextRecordEpoch - 1;
            if (rf.recordEpoch < previousEpoch) {
                // Too old to use, discard this record.
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine(
                            "Too old epoch to use this record, discard it.");
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
                    if (rf instanceof HandshakeFragment hsf) {
                        if (precedingFlight.minMessageSeq > hsf.messageSeq) {
                            isDesired = false;
                        }
                    } else if (
                        rf.contentType == ContentType.CHANGE_CIPHER_SPEC.id) {

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
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                                "Too old retransmission to use, discard it.");
                    }

                    return false;
                }
            } else if ((rf.recordEpoch == nextRecordEpoch) &&
                    (nextRecordSeq > rf.recordSeq)) {

                // Previously disordered record for the current epoch.
                //
                // Should have been retransmitted. Discard this record.
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine(
                            "Lagging behind record (sequence), discard it.");
                }

                return false;
            }

            // Unexpected duplicated handshake messages.
            if (rf.recordEpoch == handshakeEpoch &&
                    // For handshake messages only.
                    rf instanceof HandshakeFragment hsf &&
                    // Check on the received handshake messages.
                    handshakeFlight.holesMap.containsKey(hsf.handshakeType)) {
                Integer cachedMsgSeq = handshakeFlight.messageSeqMap.get(
                        hsf.handshakeType);
                if (cachedMsgSeq != null && cachedMsgSeq != hsf.messageSeq) {
                    // Handshake messages of the same type but with different
                    // message sequence numbers are not allowed.
                    throw new SSLProtocolException(
                            "Two message sequence numbers are used for the "
                          + "same handshake message ("
                          + SSLHandshake.nameOf(hsf.handshakeType)
                          + ")");
                }
            }

            return true;
        }

        private boolean isEmpty() {
            return (bufferedFragments.isEmpty() ||
                    (!flightIsReady && !needToCheckFlight) ||
                    (needToCheckFlight && !flightIsReady()));
        }

        Plaintext acquirePlaintext() throws SSLProtocolException {
            if (bufferedFragments.isEmpty()) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine("No received handshake messages");
                }
                return null;
            }

            if (!flightIsReady && needToCheckFlight) {
                // check the flight status
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

                        if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                            SSLLogger.fine("Received a retransmission flight.");
                        }

                        return Plaintext.PLAINTEXT_NULL;
                    }
                }

                needToCheckFlight = false;
            }

            if (!flightIsReady) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine(
                            "The handshake flight is not ready to use: " +
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
                    // Need not backup the holes map.  Clear up it at first.
                    handshakeFlight.holesMap.clear();   // cleanup holes map

                    // Update the preceding flight.
                    precedingFlight = (HandshakeFlight)handshakeFlight.clone();

                    // Reset the next handshake flight.
                    resetHandshakeFlight(precedingFlight);

                    if (expectCCSFlight &&
                            (precedingFlight.handshakeType ==
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

            // cleanup handshake message sequence numbers map
            handshakeFlight.messageSeqMap.clear();

            // Ready to accept new input record.
            flightIsReady = false;
            needToCheckFlight = false;
        }

        private Plaintext acquireCachedMessage() throws SSLProtocolException {
            RecordFragment rFrag = bufferedFragments.first();
            if (readEpoch != rFrag.recordEpoch) {
                if (readEpoch > rFrag.recordEpoch) {
                    // discard old records
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                                "Discard old buffered ciphertext fragments.");
                    }
                    bufferedFragments.remove(rFrag);    // popup the fragment
                }

                // reset the flight
                if (flightIsReady) {
                    flightIsReady = false;
                }

                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine(
                            "Not yet ready to decrypt the cached fragments.");
                }
                return null;
            }

            bufferedFragments.remove(rFrag);    // popup the fragment

            ByteBuffer fragment = ByteBuffer.wrap(rFrag.fragment);
            ByteBuffer plaintextFragment;
            try {
                Plaintext plaintext = readCipher.decrypt(
                        rFrag.contentType, fragment, rFrag.recordEnS);
                plaintextFragment = plaintext.fragment;
                rFrag.contentType = plaintext.contentType;
            } catch (GeneralSecurityException gse) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine("Discard invalid record: ", gse);
                }

                // invalid, discard this record [section 4.1.2.7, RFC 6347]
                return null;
            }

            // The ciphertext handshake message can only be Finished (the
            // end of this flight), ClientHello or HelloRequest (the
            // beginning of the next flight) message.  Need not to check
            // any ChangeCipherSpec message.
            if (rFrag.contentType == ContentType.HANDSHAKE.id) {
                while (plaintextFragment.remaining() > 0) {
                    HandshakeFragment hsFrag = parseHandshakeMessage(
                            rFrag.contentType,
                            rFrag.majorVersion, rFrag.minorVersion,
                            rFrag.recordEnS, rFrag.recordEpoch, rFrag.recordSeq,
                            plaintextFragment);

                    if (hsFrag == null) {
                        // invalid, discard this record
                        if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                            SSLLogger.fine(
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
                    if (hsFrag.handshakeType != SSLHandshake.FINISHED.id) {
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
            if (rFrag.contentType == ContentType.CHANGE_CIPHER_SPEC.id) {
                this.nextRecordEpoch = rFrag.recordEpoch + 1;

                // For retransmissions, the next record sequence number is a
                // positive value.  Don't worry about it as the acquiring of
                // the immediately followed Finished handshake message will
                // reset the next record sequence number correctly.
                this.nextRecordSeq = 0;

                // Popup the fragment.
                bufferedFragments.remove(rFrag);
                return new Plaintext(rFrag.contentType,
                        rFrag.majorVersion, rFrag.minorVersion,
                        rFrag.recordEpoch,
                        Authenticator.toLong(rFrag.recordEnS),
                        ByteBuffer.wrap(rFrag.fragment));
            } else {    // rFrag.contentType == ContentType.HANDSHAKE.id
                HandshakeFragment hsFrag = (HandshakeFragment)rFrag;
                if ((hsFrag.messageLength == hsFrag.fragmentLength) &&
                    (hsFrag.fragmentOffset == 0)) {     // no fragmentation

                    bufferedFragments.remove(rFrag);    // popup the fragment

                    // this.nextRecordEpoch = hsFrag.recordEpoch;
                    this.nextRecordSeq = hsFrag.recordSeq + 1;

                    // Note: may try to avoid byte array copy in the future.
                    byte[] recordFrag = new byte[hsFrag.messageLength + 4];
                    Plaintext plaintext = new Plaintext(
                            hsFrag.contentType,
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
                    Plaintext plaintext = new Plaintext(
                            hsFrag.contentType,
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
                            if (rFrag.contentType != ContentType.HANDSHAKE.id) {
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
                    boolean isReady = hasFinishedMessage();
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                            "Has the final flight been received? " + isReady);
                    }

                    return isReady;
                }

                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine("No flight is received yet.");
                }

                return false;
            }

            if ((flightType == SSLHandshake.CLIENT_HELLO.id) ||
                (flightType == SSLHandshake.HELLO_REQUEST.id) ||
                (flightType == SSLHandshake.HELLO_VERIFY_REQUEST.id)) {

                // single handshake message flight
                boolean isReady = hasCompleted(flightType);
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine(
                            "Is the handshake message completed? " + isReady);
                }

                return isReady;
            }

            //
            // the ServerHello flight
            //
            if (flightType == SSLHandshake.SERVER_HELLO.id) {
                // Firstly, check the first flight handshake message.
                if (!hasCompleted(flightType)) {
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                            "The ServerHello message is not completed yet.");
                    }

                    return false;
                }

                //
                // an abbreviated handshake
                //
                if (hasFinishedMessage()) {
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine("It's an abbreviated handshake.");
                    }

                    return true;
                }

                //
                // a full handshake
                //
                List<HoleDescriptor> holes = handshakeFlight.holesMap.get(
                        SSLHandshake.SERVER_HELLO_DONE.id);
                if ((holes == null) || !holes.isEmpty()) {
                    // Not yet got the final message of the flight.
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                                "Not yet got the ServerHelloDone message");
                    }

                    return false;
                }

                // Have all handshake message been received?
                boolean isReady = hasCompleted(bufferedFragments,
                            handshakeFlight.minMessageSeq,
                            handshakeFlight.maxMessageSeq);
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine(
                            "Is the ServerHello flight (message " +
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
            //       supported in the future.
            //
            if ((flightType == SSLHandshake.CERTIFICATE.id) ||
                (flightType == SSLHandshake.CLIENT_KEY_EXCHANGE.id)) {

                // Firstly, check the first flight handshake message.
                if (!hasCompleted(flightType)) {
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                            "The ClientKeyExchange or client Certificate " +
                            "message is not completed yet.");
                    }

                    return false;
                }

                // Is client CertificateVerify a mandatory message?
                if (flightType == SSLHandshake.CERTIFICATE.id) {
                    if (needClientVerify(bufferedFragments) &&
                        !hasCompleted(SSLHandshake.CERTIFICATE_VERIFY.id)) {

                        if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                            SSLLogger.fine(
                                "Not yet have the CertificateVerify message");
                        }

                        return false;
                    }
                }

                if (!hasFinishedMessage()) {
                    // not yet have the ChangeCipherSpec/Finished messages
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                        SSLLogger.fine(
                            "Not yet have the ChangeCipherSpec and " +
                            "Finished messages");
                    }

                    return false;
                }

                // Have all handshake message been received?
                boolean isReady = hasCompleted(bufferedFragments,
                            handshakeFlight.minMessageSeq,
                            handshakeFlight.maxMessageSeq);
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                    SSLLogger.fine(
                            "Is the ClientKeyExchange flight (message " +
                            handshakeFlight.minMessageSeq + "-" +
                            handshakeFlight.maxMessageSeq +
                            ") completed? " + isReady);
                }

                return isReady;
            }

            //
            // Otherwise, need to receive more handshake messages.
            //
            if (SSLLogger.isOn() &&
                    SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE_VERBOSE)) {
                SSLLogger.fine("Need to receive more handshake messages");
            }

            return false;
        }

        // Looking for the ChangeCipherSpec, Finished and
        // NewSessionTicket messages.
        //
        // As the cached Finished message should be a ciphertext, we don't
        // exactly know a ciphertext is a Finished message or not.  According
        // to the spec of TLS/DTLS handshaking, a Finished message is always
        // sent immediately after a ChangeCipherSpec message.  The first
        // ciphertext handshake message should be the expected Finished message.
        private boolean hasFinishedMessage() {
            boolean hasCCS = false;
            boolean hasFin = false;

            for (RecordFragment fragment : bufferedFragments) {
                if (fragment.contentType == ContentType.CHANGE_CIPHER_SPEC.id) {
                    hasCCS = true;
                } else if (fragment.contentType == ContentType.HANDSHAKE.id
                        && fragment.isCiphertext) {
                    hasFin = true;
                }
            }

            // NewSessionTicket message presence in the Finished flight
            // should only be expected on the client side, and only
            // if stateless resumption is enabled.
            return hasCCS && hasFin && (!tc.sslConfig.isClientMode
                    || !tc.handshakeContext.statelessResumption
                    || hasCompleted(SSLHandshake.NEW_SESSION_TICKET.id));
        }

        // Is client CertificateVerify a mandatory message?
        //
        // In the current implementation, client CertificateVerify is a
        // mandatory message if the client Certificate is not empty.
        private boolean needClientVerify(Set<RecordFragment> fragments) {

            // The caller should have checked the completion of the first
            // present handshake message.  Need not to check it again.
            for (RecordFragment rFrag : fragments) {
                if ((rFrag.contentType != ContentType.HANDSHAKE.id) ||
                        rFrag.isCiphertext) {
                    break;
                }

                HandshakeFragment hsFrag = (HandshakeFragment)rFrag;
                if (hsFrag.handshakeType != SSLHandshake.CERTIFICATE.id) {
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
            // present handshake message.  Need not check it again.
            for (RecordFragment rFrag : fragments) {
                if ((rFrag.contentType != ContentType.HANDSHAKE.id) ||
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
            if (!handshakeHash.isHashable(hsType)) {
                // omitted from handshake hash computation
                return;
            }

            // calculate the DTLS header and reserve the handshake message
            plaintext.fragment.position(4);     // ignore the TLS header
            byte[] temporary = new byte[plaintext.fragment.remaining() + 12];
                                                // 12: handshake header size

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

            plaintext.fragment.get(temporary,
                    12, plaintext.fragment.remaining());
            handshakeHash.receive(temporary);
            plaintext.fragment.position(0);     // restore the position
        }
    }
}
