/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportException;
import org.testng.annotations.Test;
import sun.security.ssl.QuicTLSEngineImpl;
import sun.security.ssl.QuicTLSEngineImplAccessor;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.IntFunction;

import static jdk.internal.net.quic.QuicVersion.QUIC_V1;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * @test
 * @library /test/lib
 * @modules java.base/sun.security.ssl
 *          java.base/jdk.internal.net.quic
 * @build java.base/sun.security.ssl.QuicTLSEngineImplAccessor
 * @summary known-answer test for packet encryption and decryption
 * @run testng/othervm PacketEncryptionTest
 */
public class PacketEncryptionTest {

    // RFC 9001, appendix A
    private static final String INITIAL_DCID = "8394c8f03e515708";
    // section A.2
    // header includes 4-byte packet number 2
    private static final String INITIAL_C_HEADER = "c300000001088394c8f03e5157080000449e00000002";
    private static final int INITIAL_C_PAYLOAD_OFFSET = INITIAL_C_HEADER.length() / 2;
    private static final int INITIAL_C_PN_OFFSET = INITIAL_C_PAYLOAD_OFFSET - 4;
    private static final int INITIAL_C_PN = 2;
    // payload is zero-padded to 1162 bytes, not shown here
    private static final String INITIAL_C_PAYLOAD =
            "060040f1010000ed0303ebf8fa56f129"+"39b9584a3896472ec40bb863cfd3e868" +
            "04fe3a47f06a2b69484c000004130113"+"02010000c000000010000e00000b6578" +
            "616d706c652e636f6dff01000100000a"+"00080006001d00170018001000070005" +
            "04616c706e0005000501000000000033"+"00260024001d00209370b2c9caa47fba" +
            "baf4559fedba753de171fa71f50f1ce1"+"5d43e994ec74d748002b000302030400" +
            "0d0010000e0403050306030203080408"+"050806002d00020101001c0002400100" +
            "3900320408ffffffffffffffff050480"+"00ffff07048000ffff08011001048000" +
            "75300901100f088394c8f03e51570806"+"048000ffff";
    private static final int INITIAL_C_PAYLOAD_LENGTH = 1162;
    private static final String ENCRYPTED_C_PAYLOAD =
            "c000000001088394c8f03e5157080000"+"449e7b9aec34d1b1c98dd7689fb8ec11" +
            "d242b123dc9bd8bab936b47d92ec356c"+"0bab7df5976d27cd449f63300099f399" +
            "1c260ec4c60d17b31f8429157bb35a12"+"82a643a8d2262cad67500cadb8e7378c" +
            "8eb7539ec4d4905fed1bee1fc8aafba1"+"7c750e2c7ace01e6005f80fcb7df6212" +
            "30c83711b39343fa028cea7f7fb5ff89"+"eac2308249a02252155e2347b63d58c5" +
            "457afd84d05dfffdb20392844ae81215"+"4682e9cf012f9021a6f0be17ddd0c208" +
            "4dce25ff9b06cde535d0f920a2db1bf3"+"62c23e596d11a4f5a6cf3948838a3aec" +
            "4e15daf8500a6ef69ec4e3feb6b1d98e"+"610ac8b7ec3faf6ad760b7bad1db4ba3" +
            "485e8a94dc250ae3fdb41ed15fb6a8e5"+"eba0fc3dd60bc8e30c5c4287e53805db" +
            "059ae0648db2f64264ed5e39be2e20d8"+"2df566da8dd5998ccabdae053060ae6c" +
            "7b4378e846d29f37ed7b4ea9ec5d82e7"+"961b7f25a9323851f681d582363aa5f8" +
            "9937f5a67258bf63ad6f1a0b1d96dbd4"+"faddfcefc5266ba6611722395c906556" +
            "be52afe3f565636ad1b17d508b73d874"+"3eeb524be22b3dcbc2c7468d54119c74" +
            "68449a13d8e3b95811a198f3491de3e7"+"fe942b330407abf82a4ed7c1b311663a" +
            "c69890f4157015853d91e923037c227a"+"33cdd5ec281ca3f79c44546b9d90ca00" +
            "f064c99e3dd97911d39fe9c5d0b23a22"+"9a234cb36186c4819e8b9c5927726632" +
            "291d6a418211cc2962e20fe47feb3edf"+"330f2c603a9d48c0fcb5699dbfe58964" +
            "25c5bac4aee82e57a85aaf4e2513e4f0"+"5796b07ba2ee47d80506f8d2c25e50fd" +
            "14de71e6c418559302f939b0e1abd576"+"f279c4b2e0feb85c1f28ff18f58891ff" +
            "ef132eef2fa09346aee33c28eb130ff2"+"8f5b766953334113211996d20011a198" +
            "e3fc433f9f2541010ae17c1bf202580f"+"6047472fb36857fe843b19f5984009dd" +
            "c324044e847a4f4a0ab34f719595de37"+"252d6235365e9b84392b061085349d73" +
            "203a4a13e96f5432ec0fd4a1ee65accd"+"d5e3904df54c1da510b0ff20dcc0c77f" +
            "cb2c0e0eb605cb0504db87632cf3d8b4"+"dae6e705769d1de354270123cb11450e" +
            "fc60ac47683d7b8d0f811365565fd98c"+"4c8eb936bcab8d069fc33bd801b03ade" +
            "a2e1fbc5aa463d08ca19896d2bf59a07"+"1b851e6c239052172f296bfb5e724047" +
            "90a2181014f3b94a4e97d117b4381303"+"68cc39dbb2d198065ae3986547926cd2" +
            "162f40a29f0c3c8745c0f50fba3852e5"+"66d44575c29d39a03f0cda721984b6f4" +
            "40591f355e12d439ff150aab7613499d"+"bd49adabc8676eef023b15b65bfc5ca0" +
            "6948109f23f350db82123535eb8a7433"+"bdabcb909271a6ecbcb58b936a88cd4e" +
            "8f2e6ff5800175f113253d8fa9ca8885"+"c2f552e657dc603f252e1a8e308f76f0" +
            "be79e2fb8f5d5fbbe2e30ecadd220723"+"c8c0aea8078cdfcb3868263ff8f09400" +
            "54da48781893a7e49ad5aff4af300cd8"+"04a6b6279ab3ff3afb64491c85194aab" +
            "760d58a606654f9f4400e8b38591356f"+"bf6425aca26dc85244259ff2b19c41b9" +
            "f96f3ca9ec1dde434da7d2d392b905dd"+"f3d1f9af93d1af5950bd493f5aa731b4" +
            "056df31bd267b6b90a079831aaf579be"+"0a39013137aac6d404f518cfd4684064" +
            "7e78bfe706ca4cf5e9c5453e9f7cfd2b"+"8b4c8d169a44e55c88d4a9a7f9474241" +
            "e221af44860018ab0856972e194cd934";
    // section A.3
    // header includes 2-byte packet number 1
    private static final String INITIAL_S_HEADER = "c1000000010008f067a5502a4262b50040750001";
    private static final int INITIAL_S_PAYLOAD_OFFSET = INITIAL_S_HEADER.length() / 2;
    private static final int INITIAL_S_PN_OFFSET = INITIAL_S_PAYLOAD_OFFSET - 2;
    private static final int INITIAL_S_PN = 1;
    // complete packet, no padding
    private static final String INITIAL_S_PAYLOAD =
            "02000000000600405a020000560303ee"+"fce7f7b37ba1d1632e96677825ddf739" +
            "88cfc79825df566dc5430b9a045a1200"+"130100002e00330024001d00209d3c94" +
            "0d89690b84d08a60993c144eca684d10"+"81287c834d5311bcf32bb9da1a002b00" +
            "020304";
    private static final int INITIAL_S_PAYLOAD_LENGTH = INITIAL_S_PAYLOAD.length() / 2;
    private static final String ENCRYPTED_S_PAYLOAD =
            "cf000000010008f067a5502a4262b500"+"4075c0d95a482cd0991cd25b0aac406a" +
            "5816b6394100f37a1c69797554780bb3"+"8cc5a99f5ede4cf73c3ec2493a1839b3" +
            "dbcba3f6ea46c5b7684df3548e7ddeb9"+"c3bf9c73cc3f3bded74b562bfb19fb84" +
            "022f8ef4cdd93795d77d06edbb7aaf2f"+"58891850abbdca3d20398c276456cbc4" +
            "2158407dd074ee";

    // section A.4
    private static final String SIGNED_RETRY =
            "ff000000010008f067a5502a4262b574"+"6f6b656e04a265ba2eff4d829058fb3f" +
            "0f2496ba";

    // section A.5
    public static final String ONERTT_SECRET = "9ac312a7f877468ebe69422748ad00a1" +
            "5443f18203a07d6060f688f30f21632b";
    private static final String ONERTT_HEADER = "4200bff4";
    private static final int ONERTT_PAYLOAD_OFFSET = ONERTT_HEADER.length() / 2;
    private static final int ONERTT_PN_OFFSET = 1;
    private static final int ONERTT_PN = 654360564;
    // payload is zero-padded to 1162 bytes, not shown here
    private static final String ONERTT_PAYLOAD =
            "01";
    private static final int ONERTT_PAYLOAD_LENGTH =
            ONERTT_PAYLOAD.length() / 2;
    private static final String ENCRYPTED_ONERTT_PAYLOAD =
            "4cfe4189655e5cd55c41f69080575d7999c25a5bfb";

    private static final class FixedHeaderContent implements IntFunction<ByteBuffer> {
        private final ByteBuffer header;
        private FixedHeaderContent(ByteBuffer header) {
            this.header = header;
        }

        @Override
        public ByteBuffer apply(final int keyphase) {
            // ignore keyphase
            return this.header;
        }
    }

    @Test
    public void testEncryptClientInitialPacket() throws Exception {
        QuicTLSEngine clientEngine = getQuicV1Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        clientEngine.deriveInitialKeys(QUIC_V1, dcid);

        final int packetLen = INITIAL_C_PAYLOAD_OFFSET + INITIAL_C_PAYLOAD_LENGTH + 16;
        final ByteBuffer packet = ByteBuffer.allocate(packetLen);
        packet.put(HexFormat.of().parseHex(INITIAL_C_HEADER));
        packet.put(HexFormat.of().parseHex(INITIAL_C_PAYLOAD));

        final ByteBuffer header = packet.slice(0, INITIAL_C_PAYLOAD_OFFSET).asReadOnlyBuffer();
        final ByteBuffer payload = packet.slice(INITIAL_C_PAYLOAD_OFFSET, INITIAL_C_PAYLOAD_LENGTH).asReadOnlyBuffer();

        packet.position(INITIAL_C_PAYLOAD_OFFSET);
        clientEngine.encryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_C_PN, new FixedHeaderContent(header), payload, packet);
        protect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_C_PN_OFFSET, INITIAL_C_PAYLOAD_OFFSET - INITIAL_C_PN_OFFSET, clientEngine, 0x0f);

        assertEquals(HexFormat.of().formatHex(packet.array()), ENCRYPTED_C_PAYLOAD);
    }

    @Test
    public void testDecryptClientInitialPacket() throws Exception {
        QuicTLSEngine serverEngine = getQuicV1Engine(SSLContext.getDefault(), false);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        serverEngine.deriveInitialKeys(QUIC_V1, dcid);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_C_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_C_PN_OFFSET, INITIAL_C_PAYLOAD_OFFSET - INITIAL_C_PN_OFFSET, serverEngine, 0x0f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_C_PAYLOAD_OFFSET);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_C_PN, -1,
                src, INITIAL_C_PAYLOAD_OFFSET, packet);

        String expectedContents = INITIAL_C_HEADER + INITIAL_C_PAYLOAD;

        assertEquals(HexFormat.of().formatHex(packet.array()).substring(0, expectedContents.length()), expectedContents);
    }

    @Test(expectedExceptions = AEADBadTagException.class)
    public void testDecryptClientInitialPacketBadTag() throws Exception {
        QuicTLSEngine serverEngine = getQuicV1Engine(SSLContext.getDefault(), false);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        serverEngine.deriveInitialKeys(QUIC_V1, dcid);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_C_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_C_PN_OFFSET, INITIAL_C_PAYLOAD_OFFSET - INITIAL_C_PN_OFFSET, serverEngine, 0x0f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_C_PAYLOAD_OFFSET);

        // change one byte of AEAD tag
        packet.put(packet.limit() - 1, (byte)0);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_C_PN, -1,
                src, INITIAL_C_PAYLOAD_OFFSET, packet);
        fail("Decryption should have failed");
    }

    @Test
    public void testEncryptServerInitialPacket() throws Exception {
        QuicTLSEngine serverEngine = getQuicV1Engine(SSLContext.getDefault(), false);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        serverEngine.deriveInitialKeys(QUIC_V1, dcid);

        final int packetLen = INITIAL_S_PAYLOAD_OFFSET + INITIAL_S_PAYLOAD_LENGTH + 16;
        final ByteBuffer packet = ByteBuffer.allocate(packetLen);
        packet.put(HexFormat.of().parseHex(INITIAL_S_HEADER));
        packet.put(HexFormat.of().parseHex(INITIAL_S_PAYLOAD));

        final ByteBuffer header = packet.slice(0, INITIAL_S_PAYLOAD_OFFSET).asReadOnlyBuffer();
        final ByteBuffer payload = packet.slice(INITIAL_S_PAYLOAD_OFFSET, INITIAL_S_PAYLOAD_LENGTH).asReadOnlyBuffer();

        packet.position(INITIAL_S_PAYLOAD_OFFSET);
        serverEngine.encryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_S_PN, new FixedHeaderContent(header), payload, packet);
        protect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_S_PN_OFFSET, INITIAL_S_PAYLOAD_OFFSET - INITIAL_S_PN_OFFSET, serverEngine, 0x0f);

        assertEquals(HexFormat.of().formatHex(packet.array()), ENCRYPTED_S_PAYLOAD);
    }

    @Test
    public void testDecryptServerInitialPacket() throws Exception {
        QuicTLSEngine clientEngine = getQuicV1Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        clientEngine.deriveInitialKeys(QUIC_V1, dcid);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_S_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_S_PN_OFFSET, INITIAL_S_PAYLOAD_OFFSET - INITIAL_S_PN_OFFSET, clientEngine, 0x0f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_S_PAYLOAD_OFFSET);

        clientEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_S_PN, -1,
                src, INITIAL_S_PAYLOAD_OFFSET, packet);

        String expectedContents = INITIAL_S_HEADER + INITIAL_S_PAYLOAD;

        assertEquals(HexFormat.of().formatHex(packet.array()).substring(0, expectedContents.length()), expectedContents);
    }

    @Test
    public void testDecryptServerInitialPacketTwice() throws Exception {
        // verify that decrypting the same packet twice does not throw
        QuicTLSEngine clientEngine = getQuicV1Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        clientEngine.deriveInitialKeys(QUIC_V1, dcid);

        // attempt 1
        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_S_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_S_PN_OFFSET, INITIAL_S_PAYLOAD_OFFSET - INITIAL_S_PN_OFFSET, clientEngine, 0x0f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_S_PAYLOAD_OFFSET);
        clientEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_S_PN, -1, src, INITIAL_S_PAYLOAD_OFFSET, packet);

        // attempt 2
        packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_S_PAYLOAD));
        // must not throw
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_S_PN_OFFSET, INITIAL_S_PAYLOAD_OFFSET - INITIAL_S_PN_OFFSET, clientEngine, 0x0f);
        src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_S_PAYLOAD_OFFSET);
        // must not throw
        clientEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_S_PN, -1, src, INITIAL_S_PAYLOAD_OFFSET, packet);
    }

    @Test
    public void testSignRetry() throws NoSuchAlgorithmException, ShortBufferException, QuicTransportException {
        QuicTLSEngine clientEngine = getQuicV1Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));

        ByteBuffer packet = ByteBuffer.allocate(SIGNED_RETRY.length() / 2);
        packet.put(HexFormat.of().parseHex(SIGNED_RETRY), 0, SIGNED_RETRY.length() / 2 - 16);

        ByteBuffer src = packet.asReadOnlyBuffer();
        src.limit(src.position());
        src.position(0);

        clientEngine.signRetryPacket(QUIC_V1, dcid, src, packet);

        assertEquals(HexFormat.of().formatHex(packet.array()), SIGNED_RETRY);
    }

    @Test
    public void testVerifyRetry() throws NoSuchAlgorithmException, AEADBadTagException, QuicTransportException {
        QuicTLSEngine clientEngine = getQuicV1Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(SIGNED_RETRY));

        clientEngine.verifyRetryPacket(QUIC_V1, dcid, packet);
    }

    @Test(expectedExceptions = AEADBadTagException.class)
    public void testVerifyBadRetry() throws NoSuchAlgorithmException, AEADBadTagException, QuicTransportException {
        QuicTLSEngine clientEngine = getQuicV1Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(SIGNED_RETRY));

        // change one byte of AEAD tag
        packet.put(packet.limit() - 1, (byte)0);
        clientEngine.verifyRetryPacket(QUIC_V1, dcid, packet);
        fail("Verification should have failed");
    }

    @Test
    public void testEncryptChaCha() throws Exception {
        QuicTLSEngineImpl clientEngine = (QuicTLSEngineImpl) getQuicV1Engine(SSLContext.getDefault(), true);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V1, clientEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", true);

        final int packetLen = ONERTT_PAYLOAD_OFFSET + ONERTT_PAYLOAD_LENGTH + 16;
        final ByteBuffer packet = ByteBuffer.allocate(packetLen);
        packet.put(HexFormat.of().parseHex(ONERTT_HEADER));
        packet.put(HexFormat.of().parseHex(ONERTT_PAYLOAD));

        final ByteBuffer header = packet.slice(0, ONERTT_PAYLOAD_OFFSET).asReadOnlyBuffer();
        final ByteBuffer payload = packet.slice(ONERTT_PAYLOAD_OFFSET, ONERTT_PAYLOAD_LENGTH).asReadOnlyBuffer();

        packet.position(ONERTT_PAYLOAD_OFFSET);
        clientEngine.encryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN , new FixedHeaderContent(header), payload, packet);
        protect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, clientEngine, 0x1f);

        assertEquals(HexFormat.of().formatHex(packet.array()), ENCRYPTED_ONERTT_PAYLOAD);
    }

    @Test
    public void testDecryptChaCha() throws Exception {
        QuicTLSEngineImpl serverEngine = (QuicTLSEngineImpl) getQuicV1Engine(SSLContext.getDefault(), false);
        // mark the TLS handshake as FINISHED
        QuicTLSEngineImplAccessor.completeHandshake(serverEngine);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V1, serverEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", false);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, (byte) 0,
                src, ONERTT_PAYLOAD_OFFSET, packet);

        String expectedContents = ONERTT_HEADER + ONERTT_PAYLOAD;

        assertEquals(HexFormat.of().formatHex(packet.array()).substring(0, expectedContents.length()), expectedContents);
    }

    @Test
    public void testDecryptChaChaTwice() throws Exception {
        // verify that decrypting the same packet twice does not throw
        QuicTLSEngineImpl serverEngine = (QuicTLSEngineImpl) getQuicV1Engine(SSLContext.getDefault(), false);
        // mark the TLS handshake as FINISHED
        QuicTLSEngineImplAccessor.completeHandshake(serverEngine);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V1, serverEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", false);

        final int keyPhase = 0;
        // attempt 1
        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);
        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, keyPhase, src, ONERTT_PAYLOAD_OFFSET, packet);

        // attempt 2
        packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        // must not throw
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);
        // must not throw
        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, keyPhase, src, ONERTT_PAYLOAD_OFFSET, packet);
    }

    @Test(expectedExceptions = AEADBadTagException.class)
    public void testDecryptChaChaBadTag() throws Exception {
        QuicTLSEngineImpl serverEngine = (QuicTLSEngineImpl) getQuicV1Engine(SSLContext.getDefault(), false);
        // mark the TLS handshake as FINISHED
        QuicTLSEngineImplAccessor.completeHandshake(serverEngine);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V1, serverEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", false);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);

        // change one byte of AEAD tag
        packet.put(packet.limit() - 1, (byte)0);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, (byte) 0,
                src, ONERTT_PAYLOAD_OFFSET, packet);
        fail("Decryption should have failed");
    }


    private void protect(QuicTLSEngine.KeySpace space, ByteBuffer buffer,
                         int packetNumberStart, int packetNumberLength, QuicTLSEngine tlsEngine,
                         int headersMask) throws QuicKeyUnavailableException, QuicTransportException {
        ByteBuffer sample = buffer.slice(packetNumberStart + 4, 16);
        ByteBuffer encryptedSample = tlsEngine.computeHeaderProtectionMask(space, false, sample);
        byte headers = buffer.get(0);
        headers ^= encryptedSample.get() & headersMask;
        buffer.put(0, headers);
        maskPacketNumber(buffer, packetNumberStart, packetNumberLength, encryptedSample);
    }

    private void unprotect(QuicTLSEngine.KeySpace keySpace, ByteBuffer buffer,
                           int packetNumberStart, int packetNumberLength,
                           QuicTLSEngine tlsEngine, int headersMask) throws QuicKeyUnavailableException, QuicTransportException {
        ByteBuffer sample = buffer.slice(packetNumberStart + 4, 16);
        ByteBuffer encryptedSample = tlsEngine.computeHeaderProtectionMask(keySpace, true, sample);
        byte headers = buffer.get(0);
        headers ^= encryptedSample.get() & headersMask;
        buffer.put(0, headers);
        maskPacketNumber(buffer, packetNumberStart, packetNumberLength, encryptedSample);
    }

    private void maskPacketNumber(ByteBuffer buffer, int packetNumberStart, int packetNumberLength, ByteBuffer mask) {
        for (int i = 0; i < packetNumberLength; i++) {
            buffer.put(packetNumberStart + i, (byte)(buffer.get(packetNumberStart + i) ^ mask.get()));
        }
    }

    // returns a QuicTLSEngine with only Quic version 1 enabled
    private QuicTLSEngine getQuicV1Engine(SSLContext context, boolean mode) {
        final QuicTLSContext quicTLSContext = new QuicTLSContext(context);
        final QuicTLSEngine engine = quicTLSContext.createEngine();
        engine.setUseClientMode(mode);
        return engine;
    }
}
