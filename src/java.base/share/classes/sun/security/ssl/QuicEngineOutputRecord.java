/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.quic.QuicTLSEngine;
import sun.security.ssl.SSLCipher.SSLWriteCipher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * {@code OutputRecord} implementation for {@code QuicTLSEngineImpl}.
 */
final class QuicEngineOutputRecord extends OutputRecord implements SSLRecord {

    private final HandshakeFragment fragmenter = new HandshakeFragment();

    private volatile boolean isCloseWaiting;

    private Alert alert;

    QuicEngineOutputRecord(HandshakeHash handshakeHash) {
        super(handshakeHash, SSLWriteCipher.nullTlsWriteCipher());

        this.packetSize = SSLRecord.maxRecordSize;
        this.protocolVersion = ProtocolVersion.NONE;
    }

    @Override
    public void close() throws IOException {
        recordLock.lock();
        try {
            if (!isClosed) {
                if (!fragmenter.isEmpty()) {
                    isCloseWaiting = true;
                } else {
                    super.close();
                }
            }
        } finally {
            recordLock.unlock();
        }
    }

    boolean isClosed() {
        return isClosed || isCloseWaiting;
    }

    @Override
    void encodeAlert(byte level, byte description) throws IOException {
        recordLock.lock();
        try {
            if (isClosed()) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("outbound has closed, ignore outbound " +
                            "alert message: " + Alert.nameOf(description));
                }
                return;
            }
            if (level == Alert.Level.WARNING.level) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("Suppressing warning-level " +
                            "alert message: " + Alert.nameOf(description));
                }
                return;
            }

            if (alert != null) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("Suppressing subsequent alert: " +
                            description + ", original: " + alert.id);
                }
                return;
            }

            alert = Alert.valueOf(description);
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    void encodeHandshake(byte[] source,
            int offset, int length) throws IOException {
        recordLock.lock();
        try {
            if (isClosed()) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("outbound has closed, ignore outbound " +
                                    "handshake message",
                            ByteBuffer.wrap(source, offset, length));
                }
                return;
            }

            firstMessage = false;

            byte handshakeType = source[offset];
            if (handshakeHash.isHashable(handshakeType)) {
                handshakeHash.deliver(source, offset, length);
            }

            fragmenter.queueUpFragment(source, offset, length);
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    void encodeChangeCipherSpec() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    void changeWriteCiphers(SSLWriteCipher writeCipher, boolean useChangeCipherSpec) throws IOException {
        recordLock.lock();
        try {
            fragmenter.changePacketSpace();
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    void changeWriteCiphers(SSLWriteCipher writeCipher, byte keyUpdateRequest) throws IOException {
        throw new UnsupportedOperationException("Should not call this");
    }

    @Override
    byte[] getHandshakeMessage() {
        recordLock.lock();
        try {
            return fragmenter.acquireCiphertext();
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    QuicTLSEngine.KeySpace getHandshakeMessageKeySpace() {
        recordLock.lock();
        try {
            return switch (fragmenter.currentPacketSpace) {
                case 0-> QuicTLSEngine.KeySpace.INITIAL;
                case 1-> QuicTLSEngine.KeySpace.HANDSHAKE;
                case 2-> QuicTLSEngine.KeySpace.ONE_RTT;
                default -> throw new IllegalStateException("Unexpected state");
            };
        } finally {
            recordLock.unlock();
        }
    }

    @Override
    boolean isEmpty() {
        recordLock.lock();
        try {
            return fragmenter.isEmpty();
        } finally {
            recordLock.unlock();
        }
    }

    Alert getAlert() {
        recordLock.lock();
        try {
            return alert;
        } finally {
            recordLock.unlock();
        }
    }

    // buffered record fragment
    private static class HandshakeMemo {
        boolean changeSpace;
        byte[] fragment;
    }

    static final class HandshakeFragment {
        private final LinkedList<HandshakeMemo> handshakeMemos =
                new LinkedList<>();

        private int currentPacketSpace;

        void queueUpFragment(byte[] source,
                int offset, int length) throws IOException {
            HandshakeMemo memo = new HandshakeMemo();

            memo.fragment = new byte[length];
            assert Record.getInt24(ByteBuffer.wrap(source, offset + 1, 3))
                    == length - 4 : "Invalid handshake message length";
            System.arraycopy(source, offset, memo.fragment, 0, length);

            handshakeMemos.add(memo);
        }

        void changePacketSpace() {
            HandshakeMemo lastMemo = handshakeMemos.peekLast();
            if (lastMemo != null) {
                lastMemo.changeSpace = true;
            } else {
                currentPacketSpace++;
            }
        }

        byte[] acquireCiphertext() {
            HandshakeMemo hsMemo = handshakeMemos.pollFirst();
            if (hsMemo == null) {
                return null;
            }
            if (hsMemo.changeSpace) {
                currentPacketSpace++;
            }
            return hsMemo.fragment;
        }

        boolean isEmpty() {
            return handshakeMemos.isEmpty();
        }
    }
}
