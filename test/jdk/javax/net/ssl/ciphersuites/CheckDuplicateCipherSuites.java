
/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8217408
 * @summary Verify that setting multiple instances of the same cipher suite does
 * not result in extra reported suites
 * @run main/othervm CheckDuplicateCipherSuites
 * @run main/othervm -Djdk.tls.namedGroups=ffdhe2048,secp256r1,ffdhe2048,secp256r1,ffdhe2048,secp256r1,ffdhe2048,secp256r1 CheckDuplicateCipherSuites
 * @run main/othervm -Djdk.tls.client.SignatureSchemes=ecdsa_secp256r1_sha256,ed25519,ed448,ecdsa_secp256r1_sha256,ed25519,ed448,ecdsa_secp256r1_sha256,ed25519,ed448,rsa_pkcs1_sha256 CheckDuplicateCipherSuites
 */

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;

public class CheckDuplicateCipherSuites {
    enum CipherSuite {
        TLS_AES_256_GCM_SHA384
                (0x1302,    "TLS_AES_256_GCM_SHA384"),
        TLS_AES_128_GCM_SHA256
                (0x1301,    "TLS_AES_128_GCM_SHA256"),
        TLS_CHACHA20_POLY1305_SHA256
                (0x1303,    "TLS_CHACHA20_POLY1305_SHA256"),
        TLS_DHE_RSA_WITH_AES_128_CBC_SHA
                (0x0033,    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA"),
        TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
                (0x0067,    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256"),
        TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
                (0x009E,    "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"),
        TLS_DHE_RSA_WITH_AES_256_CBC_SHA
                (0x0039,    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"),
        TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
                (0x006B,    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256"),
        TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
                (0x009F,    "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384");

        final int id;
        final String name;

        CipherSuite(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Get a list of the ciphersuite names only
         *
         * @param orig a list of CipherSuite enums
         *
         * @return a list of ciphersuite String names
         */
        private static List<String> names(List<CipherSuite> orig) {
            List<String> names = new ArrayList<>();
            orig.forEach(cs -> names.add(cs.name));
            return names;
        }

        /**
         * Get a list of the ciphersuite ids only
         *
         * @param orig a list of CipherSuite enums
         *
         * @return a list of ciphersuite hex IDs
         */
        private static List<Integer> ids(List<CipherSuite> orig) {
            List<Integer> ids = new ArrayList<>();
            orig.forEach(cs -> ids.add(cs.id));
            return ids;
        }

        private static String nameOf(int id) {
            for (CipherSuite cs : CipherSuite.values()) {
                if (cs.id == id) {
                    return cs.name;
                }
            }
            return "UNKNOWN-CIPHER-SUITE(" + id + ")";
        }
    }

    enum ProtocolVersion {
        TLS13(0x0304, "TLSv1.3"),
        TLS12(0x0303, "TLSv1.2"),
        TLS11(0x0302, "TLSv1.1"),
        TLS10(0x0301, "TLSv1"),
        SSL30(0x0300, "SSLv3"),
        SSL20Hello(0x0002, "SSLv2Hello"),

        DTLS12(0xFEFD, "DTLSv1.2"),
        DTLS10(0xFEFF, "DTLSv1.0");

        final int id;
        final String name;

        ProtocolVersion(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Get a list of the protocol version names only
         *
         * @param orig a list of ProtocolVersion enums
         *
         * @return a list of protocol version String names
         */
        private static List<String> names(List<ProtocolVersion> orig) {
            List<String> names = new ArrayList<>();
            orig.forEach(cs -> names.add(cs.name));
            return names;
        }

        /**
         * Get a list of the protocol version ids only
         *
         * @param orig a list of ProtocolVersion enums
         *
         * @return a list of protocol version hex IDs
         */
        private static List<Integer> ids(List<ProtocolVersion> orig) {
            List<Integer> ids = new ArrayList<>();
            orig.forEach(cs -> ids.add(cs.id));
            return ids;
        }

        private static String nameOf(int id) {
            for (ProtocolVersion pv : ProtocolVersion.values()) {
                if (pv.id == id) {
                    return pv.name;
                }
            }
            return "UNKNOWN-PROTOCOL-VERSION(" + id + ")";
        }
    }

    enum NamedGroup {
        FFDHE_2048(0x0100, "ffdhe2048"),
        SECP256_R1(0x0017, "secp256r1"),
        X25519(0x001D, "x25519"),
        X448(0x001E, "x448"),
        SECP384_R1(0x0018, "secp384r1"),
        SECP521_R1(0x0019, "secp521r1"),
        FFDHE_3072(0x0101, "ffdhe3072"),
        FFDHE_4096(0x0102, "ffdhe4096"),
        FFDHE_6144(0x0103, "ffdhe6144"),
        FFDHE_8192(0x0104, "ffdhe8192");

        final int id;           // hash + signature
        final String name;      // literal name

        NamedGroup(int id, String name) {
            this.id = id;
            this.name = name;
        }

        private static List<String> names(List<NamedGroup> orig) {
            List<String> names = new ArrayList<>();
            orig.forEach(ng -> names.add(ng.name));
            return names;
        }

        private static List<Integer> ids(List<NamedGroup> orig) {
            List<Integer> ids = new ArrayList<>();
            orig.forEach(ng -> ids.add(ng.id));
            return ids;
        }

        private static String nameOf(int id) {
            for (NamedGroup ng : NamedGroup.values()) {
                if (ng.id == id) {
                    return ng.name;
                }
            }
            return "UNKNOWN-NAMED-GROUP(" + id + ")";
        }
    }

    enum SignatureScheme {
        ECDSA_SECP256R1_SHA256  (0x0403, "ecdsa_secp256r1_sha256"),
        ED25519                 (0x0807, "ed25519"),
        ED448                   (0x0808, "ed448"),
        RSA_PKCS1_SHA256        (0x0401, "rsa_pkcs1_sha256"),
        ECDSA_SECP384R1_SHA384  (0x0503, "ecdsa_secp384r1_sha384"),
        ECDSA_SECP521R1_SHA512  (0x0603, "ecdsa_secp521r1_sha512"),
        RSA_PSS_RSAE_SHA256     (0x0804, "rsa_pss_rsae_sha256"),
        RSA_PSS_RSAE_SHA384     (0x0805, "rsa_pss_rsae_sha384"),
        RSA_PSS_RSAE_SHA512     (0x0806, "rsa_pss_rsae_sha512"),
        RSA_PSS_PSS_SHA256      (0x0809, "rsa_pss_pss_sha256"),
        RSA_PSS_PSS_SHA384      (0x080A, "rsa_pss_pss_sha384"),
        RSA_PSS_PSS_SHA512      (0x080B, "rsa_pss_pss_sha512"),
        RSA_PKCS1_SHA384        (0x0501, "rsa_pkcs1_sha384"),
        RSA_PKCS1_SHA512        (0x0601, "rsa_pkcs1_sha512"),
        DSA_SHA256              (0x0402, "dsa_sha256"),
        ECDSA_SHA224            (0x0303, "ecdsa_sha224"),
        RSA_SHA224              (0x0301, "rsa_sha224"),
        DSA_SHA224              (0x0302, "dsa_sha224"),
        ECDSA_SHA1              (0x0203, "ecdsa_sha1"),
        RSA_PKCS1_SHA1          (0x0201, "rsa_pkcs1_sha1"),
        DSA_SHA1                (0x0202, "dsa_sha1");

        final int id;           // hash + signature
        final String name;      // literal name

        SignatureScheme(int id, String name) {
            this.id = id;
            this.name = name;
        }

        private static List<String> names(List<SignatureScheme> orig) {
            List<String> names = new ArrayList<>();
            orig.forEach(ss -> names.add(ss.name));
            return names;
        }

        private static List<Integer> ids(List<SignatureScheme> orig) {
            List<Integer> ids = new ArrayList<>();
            orig.forEach(ss -> ids.add(ss.id));
            return ids;
        }

        private static String nameOf(int id) {
            for (SignatureScheme ss : SignatureScheme.values()) {
                if (ss.id == id) {
                    return ss.name;
                }
            }
            return "UNKNOWN-SIGNATURE-SCHEME(" + id + ")";
        }
    }

    static final int TLS_HANDSHAKE_CLIHELLO = 1;
    static final int HELLO_EXT_SUPP_GROUPS = 10;
    static final int HELLO_EXT_SIG_ALGS = 13;
    static final int HELLO_EXT_ALPN_NEGOT = 16;
    static final int TLS_RECORD_HANDSHAKE = 22;
    static final int HELLO_EXT_SUPP_VERS = 43;
    static final int HELLO_EXT_SIG_ALG_CERTS = 50;
    static final List<CipherSuite> malfCS = List.of(
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256);
    static final List<ProtocolVersion> malfPV = List.of(
            ProtocolVersion.TLS11,
            ProtocolVersion.SSL20Hello,
            ProtocolVersion.DTLS12,
            ProtocolVersion.TLS13,
            ProtocolVersion.DTLS10,
            ProtocolVersion.TLS12,
            ProtocolVersion.SSL30,
            ProtocolVersion.TLS10,
            ProtocolVersion.TLS13,
            ProtocolVersion.TLS13,
            ProtocolVersion.TLS13);
    static final List<String> malfALPN = List.of("http/1.1", "spdy/2", "spdy/3",
            "stun.turn", "stun.nat-discovery", "h2c", "c-webrtc", "sunrpc",
            "irc", "http/1.1", "http/1.1", "http/1.1", "http/1.1");
    static SSLContext defaultCtx;

    public static void main(String[] args) throws Exception {
        defaultCtx = SSLContext.getDefault();
        ByteBuffer transitData = clientHelloEnv();
        checkClientHello(transitData);
    }

    /**
     * Create an SSLEngine from the default context and initiate a
     * ClientHello to be evaluated.
     */
    private static ByteBuffer clientHelloEnv() throws Exception {
        SSLEngine eng = defaultCtx.createSSLEngine();
        SSLParameters sslp = new SSLParameters(
                CipherSuite.names(malfCS).toArray(new String[0]),
                ProtocolVersion.names(malfPV).toArray(new String[0]));
        sslp.setApplicationProtocols(malfALPN.toArray(new String[0]));
        eng.setSSLParameters(sslp);
        eng.setUseClientMode(true);
        SSLSession session = eng.getSession();
        ByteBuffer clientOut = ByteBuffer.wrap("Client".getBytes());
        ByteBuffer cTOs =
                ByteBuffer.allocateDirect(session.getPacketBufferSize());

        // Create and check the ClientHello message
        SSLEngineResult clientResult = eng.wrap(clientOut, cTOs);
        if (clientResult.getStatus() != SSLEngineResult.Status.OK) {
            throw new RuntimeException("Client wrap status: "
                    + clientResult.getStatus());
        }

        cTOs.flip();
        return cTOs;
    }

    /**
     * Examine the ClientHello to check the agreed upon cipher suites
     * for handling of duplicate entries.
     *
     * @param data the ByteBuffer containing the ClientHello bytes
     */
    private static void checkClientHello(ByteBuffer data) {
        Objects.requireNonNull(data);
        data.mark();

        // Process the TLS record header
        int type = Byte.toUnsignedInt(data.get());
        int ver = Short.toUnsignedInt(data.getShort());
        int recLen = Short.toUnsignedInt(data.getShort());

        // Sanity checks on the record header
        if (type != TLS_RECORD_HANDSHAKE) {
            throw new RuntimeException("Not a handshake, type = " + type);
        } else if (recLen > data.remaining()) {
            throw new RuntimeException("Buffer record is incomplete. " +
                    "Record length = " + recLen + ", remaining = " +
                    data.remaining());
        }

        // Extract the handshake message header
        int msgHdr = data.getInt();
        int msgType = msgHdr >>> 24;
        int msgLen = msgHdr & 0x00FFFFFF;

        // Sanity check on the message type
        if (msgType != TLS_HANDSHAKE_CLIHELLO) {
            throw new RuntimeException("Not a ClientHello, type = " + msgType);
        }

        // Skip protocol version and client random
        data.position(data.position() + 34);

        // Jump past session ID if it exists
        int sessLen = Byte.toUnsignedInt(data.get());
        if (sessLen != 0) {
            data.position(data.position() + sessLen);
        }

        // Extract the cipher suites and put them in a separate List
        List<String> transitCSs = new ArrayList<>();
        int csLen = Short.toUnsignedInt(data.getShort());
        for (int i=0; i < csLen; i+=2) {
            transitCSs.add(
                    CipherSuite.nameOf(Short.toUnsignedInt(data.getShort())));
        }

        // Jump past compression algorithms
        int compLen = Byte.toUnsignedInt(data.get());
        if (compLen != 0) {
            data.position(data.position() + compLen);
        }

        // Go through the extensions and look for supported_versions and ALPNs,
        // then add each entry to a list to be checked later
        int extsLen = Short.toUnsignedInt(data.getShort());
        List<String> transitPVs = new ArrayList<>();
        List<String> transitALPNs = new ArrayList<>();
        List<String> transitNmdGrps = new ArrayList<>();
        List<String> transitSigSchms = new ArrayList<>();
        List<String> transitCertSigAlgs = new ArrayList<>();
        while (data.hasRemaining()) {
            int extType = Short.toUnsignedInt(data.getShort());
            int extLen = Short.toUnsignedInt(data.getShort());
            switch (extType) {
                case HELLO_EXT_SUPP_VERS:
                    int supVerLen = Byte.toUnsignedInt(data.get());
                    for (int i=0; i < supVerLen; i+=2) {
                        transitPVs.add(ProtocolVersion.
                                nameOf(Short.toUnsignedInt(data.getShort())));
                    }
                    break;
                case HELLO_EXT_ALPN_NEGOT:
                    int alpnListLen = Short.toUnsignedInt(data.getShort());
                    while (alpnListLen > 0) {
                        byte[] alpnBytes = new byte[Byte.toUnsignedInt(data.get())];
                        data.get(alpnBytes);
                        transitALPNs.add(new String(alpnBytes, StandardCharsets.UTF_8));
                        alpnListLen -= (1 + alpnBytes.length);
                    }
                    break;
                case HELLO_EXT_SUPP_GROUPS:
                    int supGrpLen = Short.toUnsignedInt(data.getShort());
                    for (int i=0; i<supGrpLen; i+=2) {
                        transitNmdGrps.add(NamedGroup.nameOf(
                                Short.toUnsignedInt(data.getShort())));
                    }
                    break;
                case HELLO_EXT_SIG_ALGS:
                    int sigAlgLen = Short.toUnsignedInt(data.getShort());
                    for (int i=0; i<sigAlgLen; i+=2) {
                        transitSigSchms.add(SignatureScheme.nameOf(
                                Short.toUnsignedInt(data.getShort())));
                    }
                    break;
                case HELLO_EXT_SIG_ALG_CERTS:
                    int certSigAlgLen = Short.toUnsignedInt(data.getShort());
                    for (int i=0; i<certSigAlgLen; i+=2) {
                        transitCertSigAlgs.add(SignatureScheme.nameOf(
                                Short.toUnsignedInt(data.getShort())));
                    }
                    break;
                default:
                    data.position(data.position() + extLen);
                    break;
            }
        }

        System.out.println("Transmitted CipherSuites: " + transitCSs);
        System.out.println("Transmitted ProtocolVersions : " + transitPVs);
        System.out.println("Transmitted ALPNs: " + transitALPNs);
        System.out.println("Transmitted NamedGroups: " + transitNmdGrps);
        System.out.println("Transmitted SignatureSchemes: " + transitSigSchms);
        System.out.println("Transmitted CertificateSignatureAlgorithms: " +
                transitCertSigAlgs);

        if (containsDups(transitCSs)) {
            throw new RuntimeException("CipherSuite list contains duplicates");
        }
        if (containsDups(transitPVs)) {
            throw new RuntimeException("ProtocolVersion list contains duplicates");
        }
        if (containsDups(transitALPNs)) {
            throw new RuntimeException("ALPN list contains duplicates");
        }
        if (containsDups(transitNmdGrps)) {
            throw new RuntimeException("NamedGroup list contains duplicates");
        }
        if (containsDups(transitSigSchms)) {
            throw new RuntimeException("SignatureScheme list contains duplicates");
        }
        if (containsDups(transitCertSigAlgs)) {
            throw new RuntimeException("CertificateSignatureAlgorithm list " +
                    "contains duplicates");
        }

        // move ByteBuffer location back to the beginning point saved earlier
        data.reset();
    }

    /**
     * Eliminate duplicates from a generic List while preserving order.
     *
     * @param src an unsanitized array
     *
     * @return a list with the same elements and order, but without duplicates
     */
    private static <T> List<T> clearDups(List<T> src) {
        Set<T> setVers = new LinkedHashSet<>();
        setVers.addAll(src);
        List<T> clean = new ArrayList<>(setVers);

        return clean;
    }

    /**
     * Check if a generic list contains duplicates. Set.add(...) returns true
     * if the element is not present in the set, and false otherwise, so the
     * return value is reversed.
     *
     * @param src list to be checked
     *
     * @return true if duplicates are found, false otherwise
     */
    private static <T> boolean containsDups(List<T> src) {
        Set<T> setVers = new LinkedHashSet<>();
        for (T entry : src) {
            if (!setVers.add(entry)) {
                return true;
            }
        }
        return false;
    }
}
