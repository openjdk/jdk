/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

    // Cache the session identifier for the detection of session-resuming
    // handshake.
    byte[]              prevSessionID = new byte[0];

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
            Plaintext plaintext = reassembler.acquirePlaintext();
            if (reassembler.finished()) {
                // discard all buffered unused message.
                reassembler = null;
            }

            return plaintext;
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
        long recordSeq  = Authenticator.toLong(recordEnS);
        int contentLen = ((packet.get() & 0xFF) << 8) |
                          (packet.get() & 0xFF);            // pos: 11, 12

        if (debug != null && Debug.isOn("record")) {
             System.out.println(Thread.currentThread().getName() +
                    ", READ: " +
                    ProtocolVersion.valueOf(majorVersion, minorVersion) +
                    " " + Record.contentName(contentType) + ", length = " +
                    contentLen);
        }

        int recLim = srcPos + DTLSRecord.headerSize + contentLen;
        if (this.readEpoch > recordEpoch) {
            // Discard old records delivered before this epoch.

            // Reset the position of the packet buffer.
            packet.position(recLim);
            return null;
        }

        if (this.readEpoch < recordEpoch) {
            if (contentType != Record.ct_handshake) {
                // just discard it if not a handshake message
                packet.position(recLim);
                return null;
            }

            // Not ready to decrypt this record, may be encrypted Finished
            // message, need to buffer it.
            if (reassembler == null) {
               reassembler = new DTLSReassembler();
            }

            byte[] fragment = new byte[contentLen];
            packet.get(fragment);              // copy the fragment
            RecordFragment buffered = new RecordFragment(fragment, contentType,
                    majorVersion, minorVersion,
                    recordEnS, recordEpoch, recordSeq, true);

            reassembler.queueUpFragment(buffered);

            // consume the full record in the packet buffer.
            packet.position(recLim);

            Plaintext plaintext = reassembler.acquirePlaintext();
            if (reassembler.finished()) {
                // discard all buffered unused message.
                reassembler = null;
            }

            return plaintext;
        }

        if (this.readEpoch == recordEpoch) {
            // decrypt the fragment
            packet.limit(recLim);
            packet.position(srcPos + DTLSRecord.headerSize);

            ByteBuffer plaintextFragment;
            try {
                plaintextFragment = decrypt(readAuthenticator,
                        readCipher, contentType, packet, recordEnS);
            } catch (BadPaddingException bpe) {
                if (debug != null && Debug.isOn("ssl")) {
                    System.out.println(Thread.currentThread().getName() +
                            " discard invalid record: " + bpe);
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
               return new Plaintext(contentType, majorVersion, minorVersion,
                        recordEpoch, recordSeq, plaintextFragment);
            }

            if (contentType == Record.ct_change_cipher_spec) {
                if (reassembler == null) {
                    // handshake has not started, should be an
                    // old handshake message, discard it.
                    return null;
                }

                reassembler.queueUpFragment(
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
                        return null;
                    }

                    if ((reassembler == null) &&
                            isKickstart(hsFrag.handshakeType)) {
                       reassembler = new DTLSReassembler();
                    }

                    if (reassembler != null) {
                        reassembler.queueUpHandshake(hsFrag);
                    }   // else, just ignore the message.
                }
            }

            // Completed the read of the full record. Acquire the reassembled
            // messages.
            if (reassembler != null) {
                Plaintext plaintext = reassembler.acquirePlaintext();
                if (reassembler.finished()) {
                    // discard all buffered unused message.
                    reassembler = null;
                }

                return plaintext;
            }
        }

        return null;    // make the complier happy
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

    private static boolean isKickstart(byte handshakeType) {
        return (handshakeType == HandshakeMessage.ht_client_hello) ||
               (handshakeType == HandshakeMessage.ht_hello_request) ||
               (handshakeType == HandshakeMessage.ht_hello_verify_request);
    }

    private static HandshakeFragment parseHandshakeMessage(
            byte contentType, byte majorVersion, byte minorVersion,
            byte[] recordEnS, int recordEpoch, long recordSeq,
            ByteBuffer plaintextFragment) {

        int remaining = plaintextFragment.remaining();
        if (remaining < handshakeHeaderSize) {
            if (debug != null && Debug.isOn("ssl")) {
                System.out.println(
                        Thread.currentThread().getName() +
                        " discard invalid record: " +
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
                System.out.println(
                        Thread.currentThread().getName() +
                        " discard invalid record: " +
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
            return Long.compareUnsigned(this.recordSeq, o.recordSeq);
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
                    // keep the insertion order for the same message
                    return this.messageSeq - other.messageSeq;
                }
            }

            return Long.compareUnsigned(this.recordSeq, o.recordSeq);
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

    final class DTLSReassembler {
        TreeSet<RecordFragment> bufferedFragments = new TreeSet<>();

        HashMap<Byte, List<HoleDescriptor>> holesMap = new HashMap<>(5);

        // Epoch, sequence number and handshake message sequence of the
        // beginning message of a flight.
        byte        flightType = (byte)0xFF;

        int         flightTopEpoch = 0;
        long        flightTopRecordSeq = -1;
        int         flightTopMessageSeq = 0;

        // Epoch, sequence number and handshake message sequence of the
        // next message acquisition of a flight.
        int         nextRecordEpoch = 0;    // next record epoch
        long        nextRecordSeq = 0;      // next record sequence number
        int         nextMessageSeq = 0;     // next handshake message number

        // Expect ChangeCipherSpec and Finished messages for the final flight.
        boolean     expectCCSFlight = false;

        // Ready to process this flight if received all messages of the flight.
        boolean     flightIsReady = false;
        boolean     needToCheckFlight = false;

        // Is it a session-resuming abbreviated handshake.?
        boolean     isAbbreviatedHandshake = false;

        // The handshke fragment with the biggest record sequence number
        // in a flight, not counting the Finished message.
        HandshakeFragment lastHandshakeFragment = null;

        // Is handshake (intput) finished?
        boolean handshakeFinished = false;

        DTLSReassembler() {
            // blank
        }

        boolean finished() {
            return handshakeFinished;
        }

        void expectingFinishFlight() {
            expectCCSFlight = true;
        }

        void queueUpHandshake(HandshakeFragment hsf) {

            if ((nextRecordEpoch > hsf.recordEpoch) ||
                    (nextRecordSeq > hsf.recordSeq) ||
                    (nextMessageSeq > hsf.messageSeq)) {
                // too old, discard this record
                return;
            }

            // Is it the first message of next flight?
            if ((flightTopMessageSeq == hsf.messageSeq) &&
                    (hsf.fragmentOffset == 0) && (flightTopRecordSeq == -1)) {

                flightType = hsf.handshakeType;
                flightTopEpoch = hsf.recordEpoch;
                flightTopRecordSeq = hsf.recordSeq;

                if (hsf.handshakeType == HandshakeMessage.ht_server_hello) {
                    // Is it a session-resuming handshake?
                    try {
                        isAbbreviatedHandshake =
                                isSessionResuming(hsf.fragment, prevSessionID);
                    } catch (SSLException ssle) {
                        if (debug != null && Debug.isOn("ssl")) {
                            System.out.println(
                                    Thread.currentThread().getName() +
                                    " discard invalid record: " + ssle);
                        }

                        // invalid, discard it [section 4.1.2.7, RFC 6347]
                        return;
                    }

                    if (!isAbbreviatedHandshake) {
                        prevSessionID = getSessionID(hsf.fragment);
                    }
                }
            }

            boolean fragmented = false;
            if ((hsf.fragmentOffset) != 0 ||
                (hsf.fragmentLength != hsf.messageLength)) {

                fragmented = true;
            }

            List<HoleDescriptor> holes = holesMap.get(hsf.handshakeType);
            if (holes == null) {
                if (!fragmented) {
                    holes = Collections.emptyList();
                } else {
                    holes = new LinkedList<HoleDescriptor>();
                    holes.add(new HoleDescriptor(0, hsf.messageLength));
                }
                holesMap.put(hsf.handshakeType, holes);
            } else if (holes.isEmpty()) {
                // Have got the full handshake message.  This record may be
                // a handshake message retransmission.  Discard this record.
                //
                // It's OK to discard retransmission as the handshake hash
                // is computed as if each handshake message had been sent
                // as a single fragment.
                //
                // Note that ClientHello messages are delivered twice in
                // DTLS handshaking.
                if ((hsf.handshakeType != HandshakeMessage.ht_client_hello &&
                     hsf.handshakeType != ht_hello_verify_request) ||
                        (nextMessageSeq != hsf.messageSeq)) {
                    return;
                }

                if (fragmented) {
                    holes = new LinkedList<HoleDescriptor>();
                    holes.add(new HoleDescriptor(0, hsf.messageLength));
                }
                holesMap.put(hsf.handshakeType, holes);
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
                            System.out.println(
                                Thread.currentThread().getName() +
                                " discard invalid record: " +
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

            // append this fragment
            bufferedFragments.add(hsf);

            if ((lastHandshakeFragment == null) ||
                (lastHandshakeFragment.compareTo(hsf) < 0)) {

                lastHandshakeFragment = hsf;
            }

            if (flightIsReady) {
                flightIsReady = false;
            }
            needToCheckFlight = true;
        }

        // queue up change_cipher_spec or encrypted message
        void queueUpFragment(RecordFragment rf) {
            if ((nextRecordEpoch > rf.recordEpoch) ||
                    (nextRecordSeq > rf.recordSeq)) {
                // too old, discard this record
                return;
            }

            // Is it the first message of next flight?
            if (expectCCSFlight &&
                    (rf.contentType == Record.ct_change_cipher_spec)) {

                flightType = (byte)0xFE;
                flightTopEpoch = rf.recordEpoch;
                flightTopRecordSeq = rf.recordSeq;
            }

            // append this fragment
            bufferedFragments.add(rf);

            if (flightIsReady) {
                flightIsReady = false;
            }
            needToCheckFlight = true;
        }

        boolean isEmpty() {
            return (bufferedFragments.isEmpty() ||
                    (!flightIsReady && !needToCheckFlight) ||
                    (needToCheckFlight && !flightIsReady()));
        }

        Plaintext acquirePlaintext() {
            if (bufferedFragments.isEmpty()) {
                // reset the flight
                if (flightIsReady) {
                    flightIsReady = false;
                    needToCheckFlight = false;
                }

                return null;
            }

            if (!flightIsReady && needToCheckFlight) {
                // check the fligth status
                flightIsReady = flightIsReady();

                // set for next flight
                if (flightIsReady) {
                    flightTopMessageSeq = lastHandshakeFragment.messageSeq + 1;
                    flightTopRecordSeq = -1;
                }

                needToCheckFlight = false;
            }

            if (!flightIsReady) {
                return null;
            }

            RecordFragment rFrag = bufferedFragments.first();
            if (!rFrag.isCiphertext) {
                // handshake message, or ChangeCipherSpec message
                return acquireHandshakeMessage();
            } else {
                // a Finished message or other ciphertexts
                return acquireCachedMessage();
            }
        }

        private Plaintext acquireCachedMessage() {

            RecordFragment rFrag = bufferedFragments.first();
            if (readEpoch != rFrag.recordEpoch) {
                if (readEpoch > rFrag.recordEpoch) {
                    // discard old records
                    bufferedFragments.remove(rFrag);    // popup the fragment
                }

                // reset the flight
                if (flightIsReady) {
                    flightIsReady = false;
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
                if (debug != null && Debug.isOn("ssl")) {
                    System.out.println(Thread.currentThread().getName() +
                            " discard invalid record: " + bpe);
                }

                // invalid, discard this record [section 4.1.2.7, RFC 6347]
                return null;
            }

            // The ciphtext handshake message can only be Finished (the
            // end of this flight), ClinetHello or HelloRequest (the
            // beginning of the next flight) message.  Need not to check
            // any ChangeCipherSpec message.
            if (rFrag.contentType == Record.ct_handshake) {
                HandshakeFragment finFrag = null;
                while (plaintextFragment.remaining() > 0) {
                    HandshakeFragment hsFrag = parseHandshakeMessage(
                            rFrag.contentType,
                            rFrag.majorVersion, rFrag.minorVersion,
                            rFrag.recordEnS, rFrag.recordEpoch, rFrag.recordSeq,
                            plaintextFragment);

                    if (hsFrag == null) {
                        // invalid, discard this record
                        return null;
                    }

                    if (hsFrag.handshakeType == HandshakeMessage.ht_finished) {
                        finFrag = hsFrag;

                        // reset for the next flight
                        this.flightType = (byte)0xFF;
                        this.flightTopEpoch = rFrag.recordEpoch;
                        this.flightTopMessageSeq = hsFrag.messageSeq + 1;
                        this.flightTopRecordSeq = -1;
                    } else {
                        // reset the flight
                        if (flightIsReady) {
                            flightIsReady = false;
                        }
                        queueUpHandshake(hsFrag);
                    }
                }

                this.nextRecordSeq = rFrag.recordSeq + 1;
                this.nextMessageSeq = 0;

                if (finFrag != null) {
                    this.nextRecordEpoch = finFrag.recordEpoch;
                    this.nextRecordSeq = finFrag.recordSeq + 1;
                    this.nextMessageSeq = finFrag.messageSeq + 1;

                    // Finished message does not fragment.
                    byte[] recordFrag = new byte[finFrag.messageLength + 4];
                    Plaintext plaintext = new Plaintext(finFrag.contentType,
                            finFrag.majorVersion, finFrag.minorVersion,
                            finFrag.recordEpoch, finFrag.recordSeq,
                            ByteBuffer.wrap(recordFrag));

                    // fill the handshake fragment of the record
                    recordFrag[0] = finFrag.handshakeType;
                    recordFrag[1] =
                            (byte)((finFrag.messageLength >>> 16) & 0xFF);
                    recordFrag[2] =
                            (byte)((finFrag.messageLength >>> 8) & 0xFF);
                    recordFrag[3] = (byte)(finFrag.messageLength & 0xFF);

                    System.arraycopy(finFrag.fragment, 0,
                            recordFrag, 4, finFrag.fragmentLength);

                    // handshake hashing
                    handshakeHashing(finFrag, plaintext);

                    // input handshake finished
                    handshakeFinished = true;

                    return plaintext;
                } else {
                    return acquirePlaintext();
                }
            } else {
                return new Plaintext(rFrag.contentType,
                        rFrag.majorVersion, rFrag.minorVersion,
                        rFrag.recordEpoch, rFrag.recordSeq,
                        plaintextFragment);
            }
        }

        private Plaintext acquireHandshakeMessage() {

            RecordFragment rFrag = bufferedFragments.first();
            if (rFrag.contentType == Record.ct_change_cipher_spec) {
                this.nextRecordEpoch = rFrag.recordEpoch + 1;
                this.nextRecordSeq = 0;
                // no change on next handshake message sequence number

                bufferedFragments.remove(rFrag);        // popup the fragment

                // Reload if this message has been reserved for handshake hash.
                handshakeHash.reload();

                return new Plaintext(rFrag.contentType,
                        rFrag.majorVersion, rFrag.minorVersion,
                        rFrag.recordEpoch, rFrag.recordSeq,
                        ByteBuffer.wrap(rFrag.fragment));
            } else {    // rFrag.contentType == Record.ct_handshake
                HandshakeFragment hsFrag = (HandshakeFragment)rFrag;
                if ((hsFrag.messageLength == hsFrag.fragmentLength) &&
                    (hsFrag.fragmentOffset == 0)) {     // no fragmentation

                    bufferedFragments.remove(rFrag);    // popup the fragment

                    // this.nextRecordEpoch = hsFrag.recordEpoch;
                    this.nextRecordSeq = hsFrag.recordSeq + 1;
                    this.nextMessageSeq = hsFrag.messageSeq + 1;

                    // Note: may try to avoid byte array copy in the future.
                    byte[] recordFrag = new byte[hsFrag.messageLength + 4];
                    Plaintext plaintext = new Plaintext(hsFrag.contentType,
                            hsFrag.majorVersion, hsFrag.minorVersion,
                            hsFrag.recordEpoch, hsFrag.recordSeq,
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
                            hsFrag.recordEpoch, hsFrag.recordSeq,
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
                    this.nextMessageSeq = msgSeq + 1;

                    return plaintext;
                }
            }
        }

        boolean flightIsReady() {

            //
            // the ChangeCipherSpec/Finished flight
            //
            if (expectCCSFlight) {
                // Have the ChangeCipherSpec/Finished messages been received?
                return hasFinisedMessage(bufferedFragments);
            }

            if (flightType == (byte)0xFF) {
                return false;
            }

            if ((flightType == HandshakeMessage.ht_client_hello) ||
                (flightType == HandshakeMessage.ht_hello_request) ||
                (flightType == HandshakeMessage.ht_hello_verify_request)) {

                // single handshake message flight
                return hasCompleted(holesMap.get(flightType));
            }

            //
            // the ServerHello flight
            //
            if (flightType == HandshakeMessage.ht_server_hello) {
                // Firstly, check the first flight handshake message.
                if (!hasCompleted(holesMap.get(flightType))) {
                    return false;
                }

                //
                // an abbreviated handshake
                //
                if (isAbbreviatedHandshake) {
                    // Ready to use the flight if received the
                    // ChangeCipherSpec and Finished messages.
                    return hasFinisedMessage(bufferedFragments);
                }

                //
                // a full handshake
                //
                if (lastHandshakeFragment.handshakeType !=
                        HandshakeMessage.ht_server_hello_done) {
                    // Not yet got the final message of the flight.
                    return false;
                }

                // Have all handshake message been received?
                return hasCompleted(bufferedFragments,
                    flightTopMessageSeq, lastHandshakeFragment.messageSeq);
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
                if (!hasCompleted(holesMap.get(flightType))) {
                    return false;
                }

                if (!hasFinisedMessage(bufferedFragments)) {
                    // not yet got the ChangeCipherSpec/Finished messages
                    return false;
                }

                if (flightType == HandshakeMessage.ht_client_key_exchange) {
                    // single handshake message flight
                    return true;
                }

                //
                // flightType == HandshakeMessage.ht_certificate
                //
                // We don't support certificates containing fixed
                // Diffie-Hellman parameters.  Therefore, CertificateVerify
                // message is required if client Certificate message presents.
                //
                if (lastHandshakeFragment.handshakeType !=
                        HandshakeMessage.ht_certificate_verify) {
                    // Not yet got the final message of the flight.
                    return false;
                }

                // Have all handshake message been received?
                return hasCompleted(bufferedFragments,
                    flightTopMessageSeq, lastHandshakeFragment.messageSeq);
            }

            //
            // Otherwise, need to receive more handshake messages.
            //
            return false;
        }

        private boolean isSessionResuming(
                byte[] fragment, byte[] prevSid) throws SSLException {

            // As the first fragment of ServerHello should be big enough
            // to hold the session_id field, need not to worry about the
            // fragmentation here.
            if ((fragment == null) || (fragment.length < 38)) {
                                    // 38: the minimal ServerHello body length
                throw new SSLException(
                        "Invalid ServerHello message: no sufficient data");
            }

            int sidLen = fragment[34];          // 34: the length field
            if (sidLen > 32) {                  // opaque SessionID<0..32>
                throw new SSLException(
                        "Invalid ServerHello message: invalid session id");
            }

            if (fragment.length < 38 + sidLen) {
                throw new SSLException(
                        "Invalid ServerHello message: no sufficient data");
            }

            if (sidLen != 0 && (prevSid.length == sidLen)) {
                // may be a session-resuming handshake
                for (int i = 0; i < sidLen; i++) {
                    if (prevSid[i] != fragment[35 + i]) {
                                                // 35: the session identifier
                        return false;
                    }
                }

                return true;
            }

            return false;
        }

        private byte[] getSessionID(byte[] fragment) {
            // The validity has been checked in the call to isSessionResuming().
            int sidLen = fragment[34];      // 34: the sessionID length field

            byte[] temporary = new byte[sidLen];
            System.arraycopy(fragment, 35, temporary, 0, sidLen);

            return temporary;
        }

        // Looking for the ChangeCipherSpec and Finished messages.
        //
        // As the cached Finished message should be a ciphertext, we don't
        // exactly know a ciphertext is a Finished message or not.  According
        // to the spec of TLS/DTLS handshaking, a Finished message is always
        // sent immediately after a ChangeCipherSpec message.  The first
        // ciphertext handshake message should be the expected Finished message.
        private boolean hasFinisedMessage(
                Set<RecordFragment> fragments) {

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

        private boolean hasCompleted(List<HoleDescriptor> holes) {
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
                    if (!hasCompleted(holesMap.get(hsFrag.handshakeType))) {
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

