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

import static jdk.internal.net.quic.QuicVersion.QUIC_V2;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * @test
 * @library /test/lib
 * @modules java.base/sun.security.ssl
 *          java.base/jdk.internal.net.quic
 * @build java.base/sun.security.ssl.QuicTLSEngineImplAccessor
 * @summary known-answer test for packet encryption and decryption with Quic v2
 * @run testng/othervm Quicv2PacketEncryptionTest
 */
public class Quicv2PacketEncryptionTest {

    // RFC 9369, appendix A
    private static final String INITIAL_DCID = "8394c8f03e515708";
    // section A.2
    // header includes 4-byte packet number 2
    private static final String INITIAL_C_HEADER = "d36b3343cf088394c8f03e5157080000449e00000002";
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
            "d76b3343cf088394c8f03e5157080000"+"449ea0c95e82ffe67b6abcdb4298b485" +
            "dd04de806071bf03dceebfa162e75d6c"+"96058bdbfb127cdfcbf903388e99ad04" +
            "9f9a3dd4425ae4d0992cfff18ecf0fdb"+"5a842d09747052f17ac2053d21f57c5d" +
            "250f2c4f0e0202b70785b7946e992e58"+"a59ac52dea6774d4f03b55545243cf1a" +
            "12834e3f249a78d395e0d18f4d766004"+"f1a2674802a747eaa901c3f10cda5500" +
            "cb9122faa9f1df66c392079a1b40f0de"+"1c6054196a11cbea40afb6ef5253cd68" +
            "18f6625efce3b6def6ba7e4b37a40f77"+"32e093daa7d52190935b8da58976ff33" +
            "12ae50b187c1433c0f028edcc4c2838b"+"6a9bfc226ca4b4530e7a4ccee1bfa2a3" +
            "d396ae5a3fb512384b2fdd851f784a65"+"e03f2c4fbe11a53c7777c023462239dd" +
            "6f7521a3f6c7d5dd3ec9b3f233773d4b"+"46d23cc375eb198c63301c21801f6520" +
            "bcfb7966fc49b393f0061d974a2706df"+"8c4a9449f11d7f3d2dcbb90c6b877045" +
            "636e7c0c0fe4eb0f697545460c806910"+"d2c355f1d253bc9d2452aaa549e27a1f" +
            "ac7cf4ed77f322e8fa894b6a83810a34"+"b361901751a6f5eb65a0326e07de7c12" +
            "16ccce2d0193f958bb3850a833f7ae43"+"2b65bc5a53975c155aa4bcb4f7b2c4e5" +
            "4df16efaf6ddea94e2c50b4cd1dfe060"+"17e0e9d02900cffe1935e0491d77ffb4" +
            "fdf85290fdd893d577b1131a610ef6a5"+"c32b2ee0293617a37cbb08b847741c3b" +
            "8017c25ca9052ca1079d8b78aebd4787"+"6d330a30f6a8c6d61dd1ab5589329de7" +
            "14d19d61370f8149748c72f132f0fc99"+"f34d766c6938597040d8f9e2bb522ff9" +
            "9c63a344d6a2ae8aa8e51b7b90a4a806"+"105fcbca31506c446151adfeceb51b91" +
            "abfe43960977c87471cf9ad4074d30e1"+"0d6a7f03c63bd5d4317f68ff325ba3bd" +
            "80bf4dc8b52a0ba031758022eb025cdd"+"770b44d6d6cf0670f4e990b22347a7db" +
            "848265e3e5eb72dfe8299ad7481a4083"+"22cac55786e52f633b2fb6b614eaed18" +
            "d703dd84045a274ae8bfa73379661388"+"d6991fe39b0d93debb41700b41f90a15" +
            "c4d526250235ddcd6776fc77bc97e7a4"+"17ebcb31600d01e57f32162a8560cacc" +
            "7e27a096d37a1a86952ec71bd89a3e9a"+"30a2a26162984d7740f81193e8238e61" +
            "f6b5b984d4d3dfa033c1bb7e4f0037fe"+"bf406d91c0dccf32acf423cfa1e70710" +
            "10d3f270121b493ce85054ef58bada42"+"310138fe081adb04e2bd901f2f13458b" +
            "3d6758158197107c14ebb193230cd115"+"7380aa79cae1374a7c1e5bbcb80ee23e" +
            "06ebfde206bfb0fcbc0edc4ebec30966"+"1bdd908d532eb0c6adc38b7ca7331dce" +
            "8dfce39ab71e7c32d318d136b6100671"+"a1ae6a6600e3899f31f0eed19e3417d1" +
            "34b90c9058f8632c798d4490da498730"+"7cba922d61c39805d072b589bd52fdf1" +
            "e86215c2d54e6670e07383a27bbffb5a"+"ddf47d66aa85a0c6f9f32e59d85a44dd" +
            "5d3b22dc2be80919b490437ae4f36a0a"+"e55edf1d0b5cb4e9a3ecabee93dfc6e3" +
            "8d209d0fa6536d27a5d6fbb17641cde2"+"7525d61093f1b28072d111b2b4ae5f89" +
            "d5974ee12e5cf7d5da4d6a31123041f3"+"3e61407e76cffcdcfd7e19ba58cf4b53" +
            "6f4c4938ae79324dc402894b44faf8af"+"bab35282ab659d13c93f70412e85cb19" +
            "9a37ddec600545473cfb5a05e08d0b20"+"9973b2172b4d21fb69745a262ccde96b" +
            "a18b2faa745b6fe189cf772a9f84cbfc";
    // section A.3
    // header includes 2-byte packet number 1
    private static final String INITIAL_S_HEADER = "d16b3343cf0008f067a5502a4262b50040750001";
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
            "dc6b3343cf0008f067a5502a4262b500"+"4075d92faaf16f05d8a4398c47089698" +
            "baeea26b91eb761d9b89237bbf872630"+"17915358230035f7fd3945d88965cf17" +
            "f9af6e16886c61bfc703106fbaf3cb4c"+"fa52382dd16a393e42757507698075b2" +
            "c984c707f0a0812d8cd5a6881eaf21ce"+"da98f4bd23f6fe1a3e2c43edd9ce7ca8" +
            "4bed8521e2e140";

    // section A.4
    private static final String SIGNED_RETRY =
            "cf6b3343cf0008f067a5502a4262b574"+"6f6b656ec8646ce8bfe33952d9555436" +
            "65dcc7b6";

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
            "5558b1c60ae7b6b932bc27d786f4bc2bb20f2162ba";

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
        QuicTLSEngine clientEngine = getQuicV2Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        clientEngine.deriveInitialKeys(QUIC_V2, dcid);
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
        QuicTLSEngine serverEngine = getQuicV2Engine(SSLContext.getDefault(), false);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        serverEngine.deriveInitialKeys(QUIC_V2, dcid);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_C_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_C_PN_OFFSET, INITIAL_C_PAYLOAD_OFFSET - INITIAL_C_PN_OFFSET, serverEngine, 0x0f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_C_PAYLOAD_OFFSET);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_C_PN, -1, src, INITIAL_C_PAYLOAD_OFFSET, packet);

        String expectedContents = INITIAL_C_HEADER + INITIAL_C_PAYLOAD;

        assertEquals(HexFormat.of().formatHex(packet.array()).substring(0, expectedContents.length()), expectedContents);
    }

    @Test(expectedExceptions = AEADBadTagException.class)
    public void testDecryptClientInitialPacketBadTag() throws Exception {
        QuicTLSEngine serverEngine = getQuicV2Engine(SSLContext.getDefault(), false);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        serverEngine.deriveInitialKeys(QUIC_V2, dcid);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_C_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_C_PN_OFFSET, INITIAL_C_PAYLOAD_OFFSET - INITIAL_C_PN_OFFSET, serverEngine, 0x0f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_C_PAYLOAD_OFFSET);

        // change one byte of AEAD tag
        packet.put(packet.limit() - 1, (byte)0);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_C_PN, -1, src, INITIAL_C_PAYLOAD_OFFSET, packet);
        fail("Decryption should have failed");
    }

    @Test
    public void testEncryptServerInitialPacket() throws Exception {
        QuicTLSEngine serverEngine = getQuicV2Engine(SSLContext.getDefault(), false);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        serverEngine.deriveInitialKeys(QUIC_V2, dcid);

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
        QuicTLSEngine clientEngine = getQuicV2Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        clientEngine.deriveInitialKeys(QUIC_V2, dcid);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_S_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.INITIAL, packet, INITIAL_S_PN_OFFSET, INITIAL_S_PAYLOAD_OFFSET - INITIAL_S_PN_OFFSET, clientEngine, 0x0f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(INITIAL_S_PAYLOAD_OFFSET);

        clientEngine.decryptPacket(QuicTLSEngine.KeySpace.INITIAL, INITIAL_S_PN, -1, src, INITIAL_S_PAYLOAD_OFFSET, packet);

        String expectedContents = INITIAL_S_HEADER + INITIAL_S_PAYLOAD;

        assertEquals(HexFormat.of().formatHex(packet.array()).substring(0, expectedContents.length()), expectedContents);
    }

    @Test
    public void testDecryptServerInitialPacketTwice() throws Exception {
        // verify that decrypting the same packet twice does not throw
        QuicTLSEngine clientEngine = getQuicV2Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));
        clientEngine.deriveInitialKeys(QUIC_V2, dcid);

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
        QuicTLSEngine clientEngine = getQuicV2Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));

        ByteBuffer packet = ByteBuffer.allocate(SIGNED_RETRY.length() / 2);
        packet.put(HexFormat.of().parseHex(SIGNED_RETRY), 0, SIGNED_RETRY.length() / 2 - 16);

        ByteBuffer src = packet.asReadOnlyBuffer();
        src.limit(src.position());
        src.position(0);

        clientEngine.signRetryPacket(QUIC_V2, dcid, src, packet);

        assertEquals(HexFormat.of().formatHex(packet.array()), SIGNED_RETRY);
    }

    @Test
    public void testVerifyRetry() throws NoSuchAlgorithmException, AEADBadTagException, QuicTransportException {
        QuicTLSEngine clientEngine = getQuicV2Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(SIGNED_RETRY));

        clientEngine.verifyRetryPacket(QUIC_V2, dcid, packet);
    }

    @Test(expectedExceptions = AEADBadTagException.class)
    public void testVerifyBadRetry() throws NoSuchAlgorithmException, AEADBadTagException, QuicTransportException {
        QuicTLSEngine clientEngine = getQuicV2Engine(SSLContext.getDefault(), true);
        ByteBuffer dcid = ByteBuffer.wrap(HexFormat.of().parseHex(INITIAL_DCID));

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(SIGNED_RETRY));

        // change one byte of AEAD tag
        packet.put(packet.limit() - 1, (byte)0);
        clientEngine.verifyRetryPacket(QUIC_V2, dcid, packet);
        fail("Verification should have failed");
    }

    @Test
    public void testEncryptChaCha() throws Exception {
        QuicTLSEngineImpl clientEngine = (QuicTLSEngineImpl) getQuicV2Engine(SSLContext.getDefault(), true);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V2, clientEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", true);

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
        QuicTLSEngineImpl serverEngine = (QuicTLSEngineImpl) getQuicV2Engine(SSLContext.getDefault(), false);
        // mark the TLS handshake as FINISHED
        QuicTLSEngineImplAccessor.completeHandshake(serverEngine);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V2, serverEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", false);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, 0, src, ONERTT_PAYLOAD_OFFSET, packet);

        String expectedContents = ONERTT_HEADER + ONERTT_PAYLOAD;

        assertEquals(HexFormat.of().formatHex(packet.array()).substring(0, expectedContents.length()), expectedContents);
    }

    @Test
    public void testDecryptChaChaTwice() throws Exception {
        // verify that decrypting the same packet twice does not throw
        QuicTLSEngineImpl serverEngine = (QuicTLSEngineImpl) getQuicV2Engine(SSLContext.getDefault(), false);
        // mark the TLS handshake as FINISHED
        QuicTLSEngineImplAccessor.completeHandshake(serverEngine);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V2, serverEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", false);

        // attempt 1
        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);
        final int keyphase = 0;
        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, keyphase, src, ONERTT_PAYLOAD_OFFSET, packet);

        // attempt 2
        packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        // must not throw
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);
        // must not throw
        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, keyphase, src, ONERTT_PAYLOAD_OFFSET, packet);
    }

    @Test(expectedExceptions = AEADBadTagException.class)
    public void testDecryptChaChaBadTag() throws Exception {
        QuicTLSEngineImpl serverEngine = (QuicTLSEngineImpl) getQuicV2Engine(SSLContext.getDefault(), false);
        // mark the TLS handshake as FINISHED
        QuicTLSEngineImplAccessor.completeHandshake(serverEngine);
        SecretKey key = new SecretKeySpec(HexFormat.of().parseHex(ONERTT_SECRET), 0, 32, "ChaCha20-Poly1305");
        QuicTLSEngineImplAccessor.testDeriveOneRTTKeys(QUIC_V2, serverEngine, key, key, "TLS_CHACHA20_POLY1305_SHA256", false);

        ByteBuffer packet = ByteBuffer.wrap(HexFormat.of().parseHex(ENCRYPTED_ONERTT_PAYLOAD));
        unprotect(QuicTLSEngine.KeySpace.ONE_RTT, packet, ONERTT_PN_OFFSET, ONERTT_PAYLOAD_OFFSET - ONERTT_PN_OFFSET, serverEngine, 0x1f);
        ByteBuffer src = packet.asReadOnlyBuffer();
        packet.position(ONERTT_PAYLOAD_OFFSET);

        // change one byte of AEAD tag
        packet.put(packet.limit() - 1, (byte)0);

        serverEngine.decryptPacket(QuicTLSEngine.KeySpace.ONE_RTT, ONERTT_PN, 0, src, ONERTT_PAYLOAD_OFFSET, packet);
        fail("Decryption should have failed");
    }


    private void protect(QuicTLSEngine.KeySpace space, ByteBuffer buffer, int packetNumberStart,
                         int packetNumberLength, QuicTLSEngine tlsEngine, int headersMask)
            throws QuicKeyUnavailableException, QuicTransportException {
        ByteBuffer sample = buffer.slice(packetNumberStart + 4, 16);
        ByteBuffer encryptedSample = tlsEngine.computeHeaderProtectionMask(space, false, sample);
        byte headers = buffer.get(0);
        headers ^= encryptedSample.get() & headersMask;
        buffer.put(0, headers);
        maskPacketNumber(buffer, packetNumberStart, packetNumberLength, encryptedSample);
    }

    private void unprotect(QuicTLSEngine.KeySpace keySpace, ByteBuffer buffer, int packetNumberStart,
                           int packetNumberLength, QuicTLSEngine tlsEngine, int headersMask)
            throws QuicKeyUnavailableException, QuicTransportException {
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

    // returns a QuicTLSEngine with only Quic version 2 enabled
    private QuicTLSEngine getQuicV2Engine(SSLContext context, boolean mode) {
        final QuicTLSContext quicTLSContext = new QuicTLSContext(context);
        final QuicTLSEngine engine = quicTLSContext.createEngine();
        engine.setUseClientMode(mode);
        return engine;
    }
}
