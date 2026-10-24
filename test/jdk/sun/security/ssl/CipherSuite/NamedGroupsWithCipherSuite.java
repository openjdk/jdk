/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import jdk.test.lib.security.SecurityUtils;

/*
  * @test
  * @bug 8224650 8242929 8314323
  * @library /javax/net/ssl/templates
  *          /javax/net/ssl/TLSCommon
  *          /test/lib
  * @summary Test TLS ciphersuite with each individual supported group
  * @run main/othervm NamedGroupsWithCipherSuite x25519
  * @run main/othervm NamedGroupsWithCipherSuite X448
  * @run main/othervm NamedGroupsWithCipherSuite secp256r1
  * @run main/othervm NamedGroupsWithCipherSuite secP384r1
  * @run main/othervm NamedGroupsWithCipherSuite SECP521R1
  * @run main/othervm NamedGroupsWithCipherSuite ffDhe2048
  * @run main/othervm NamedGroupsWithCipherSuite FFDHE3072
  * @run main/othervm NamedGroupsWithCipherSuite ffdhe4096
  * @run main/othervm NamedGroupsWithCipherSuite ffdhe6144
  * @run main/othervm NamedGroupsWithCipherSuite ffdhe8192
  * @run main/othervm NamedGroupsWithCipherSuite X25519MLKEM768
  * @run main/othervm NamedGroupsWithCipherSuite SecP256r1MLKEM768
  * @run main/othervm NamedGroupsWithCipherSuite SecP384r1MLKEM1024
 */
public class NamedGroupsWithCipherSuite extends SSLSocketTemplate {

    private static final List<Protocol> PROTOCOLS = List.of(
            Protocol.TLSV1_3,
            Protocol.TLSV1_2,
            Protocol.TLSV1_1,
            Protocol.TLSV1
    );

    private static final List<CipherSuite> CIPHER_SUITES = List.of(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,

            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,

            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,

            CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_DHE_DSS_WITH_AES_256_CBC_SHA256,

            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256
    );

    private static final List<String> HYBRID_NAMEDGROUPS = List.of(
            "X25519MLKEM768",
            "SecP256r1MLKEM768",
            "SecP384r1MLKEM1024"
    );

    private static final List<Protocol> HYBRID_PROTOCOL = List.of(
            Protocol.TLSV1_3
    );

    private static final List<CipherSuite> HYBRID_CIPHER_SUITES = List.of(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256
    );

    private String protocol;
    private String cipher;

    private SSLSocketTemplate.Cert[] trustedCerts = TRUSTED_CERTS;
    private SSLSocketTemplate.Cert[] endEntityCerts = END_ENTITY_CERTS;

    NamedGroupsWithCipherSuite(
            Protocol protocol,
            CipherSuite cipher,
            String namedGroup) {
        this.protocol = protocol.name;
        this.cipher = cipher.name();

        if (cipher.keyExAlgorithm == KeyExAlgorithm.ECDHE_ECDSA) {
            switch (namedGroup) {
            case "secp256r1":
                trustedCerts = new SSLSocketTemplate.Cert[] {
                        SSLSocketTemplate.Cert.CA_ECDSA_SECP256R1 };
                endEntityCerts = new SSLSocketTemplate.Cert[] {
                        SSLSocketTemplate.Cert.EE_ECDSA_SECP256R1 };
                break;
            case "secp384r1":
                trustedCerts = new SSLSocketTemplate.Cert[] {
                        SSLSocketTemplate.Cert.CA_ECDSA_SECP384R1 };
                endEntityCerts = new SSLSocketTemplate.Cert[] {
                        SSLSocketTemplate.Cert.EE_ECDSA_SECP384R1 };
                break;
            case "secp521r1":
                trustedCerts = new SSLSocketTemplate.Cert[] {
                        SSLSocketTemplate.Cert.CA_ECDSA_SECP521R1 };
                endEntityCerts = new SSLSocketTemplate.Cert[] {
                        SSLSocketTemplate.Cert.EE_ECDSA_SECP521R1 };
            }
        } else if (protocol.id < Protocol.TLSV1_2.id
                && cipher.keyExAlgorithm == KeyExAlgorithm.DHE_DSS) {
            trustedCerts = new SSLSocketTemplate.Cert[] {
                    SSLSocketTemplate.Cert.CA_DSA_1024 };
            endEntityCerts = new SSLSocketTemplate.Cert[] {
                    SSLSocketTemplate.Cert.EE_DSA_1024 };
        }
    }

    protected SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(trustedCerts, endEntityCerts,
                getClientContextParameters());
    }

    protected SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(trustedCerts, endEntityCerts,
                getServerContextParameters());
    }

    // Servers are configured before clients, increment test case after.
    @Override
    protected void configureClientSocket(SSLSocket socket) {
        socket.setEnabledProtocols(new String[] { protocol });
        socket.setEnabledCipherSuites(new String[] { cipher });
    }

    @Override
    protected void configureServerSocket(SSLServerSocket serverSocket) {
        serverSocket.setEnabledProtocols(new String[] { protocol });
        serverSocket.setEnabledCipherSuites(new String[] { cipher });
    }

    public static void main(String[] args) throws Exception {
        String namedGroup = args[0];
        // Named group is set as per run argument with no change in it's alphabet
        System.setProperty("jdk.tls.namedGroups", namedGroup);
        System.out.println("NamedGroup: " + namedGroup);

        // Re-enable TLSv1 and TLSv1.1 since test depends on it.
        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1", "TLSv1.1");

        boolean hybridGroup = HYBRID_NAMEDGROUPS.contains(namedGroup);
        List<Protocol> protocolList = hybridGroup ?
                HYBRID_PROTOCOL : PROTOCOLS;
        List<CipherSuite> cipherList = hybridGroup ?
                HYBRID_CIPHER_SUITES : CIPHER_SUITES;

        // non-Hybrid named group converted to lower case just
        // to satisfy Test condition
        String normalizedGroup = hybridGroup ?
                namedGroup : namedGroup.toLowerCase();

        for (Protocol protocol : protocolList) {
            for (CipherSuite cipherSuite : cipherList) {
                if (cipherSuite.supportedByProtocol(protocol)
                        && groupSupportedByCipher(normalizedGroup,
                        cipherSuite)) {
                    System.out.printf("Protocol: %s, cipher suite: %s%n",
                            protocol, cipherSuite);
                    new NamedGroupsWithCipherSuite(protocol,
                            cipherSuite, normalizedGroup).run();
                }
            }
        }
    }

    private static boolean groupSupportedByCipher(String group,
            CipherSuite cipherSuite) {
        if (HYBRID_NAMEDGROUPS.contains(group)) {
            return cipherSuite.keyExAlgorithm == null;
        }

        return (group.startsWith("x")
                        && xdhGroupSupportedByCipher(cipherSuite))
                || (group.startsWith("secp")
                        && ecdhGroupSupportedByCipher(cipherSuite))
                || (group.startsWith("ffdhe")
                        && ffdhGroupSupportedByCipher(cipherSuite));
    }

    private static boolean xdhGroupSupportedByCipher(
            CipherSuite cipherSuite) {
        return cipherSuite.keyExAlgorithm == null
                || cipherSuite.keyExAlgorithm == KeyExAlgorithm.ECDHE_RSA;
    }

    private static boolean ecdhGroupSupportedByCipher(
            CipherSuite cipherSuite) {
        return cipherSuite.keyExAlgorithm == null
                || cipherSuite.keyExAlgorithm == KeyExAlgorithm.ECDHE_RSA
                || cipherSuite.keyExAlgorithm == KeyExAlgorithm.ECDHE_ECDSA;
    }

    private static boolean ffdhGroupSupportedByCipher(
            CipherSuite cipherSuite) {
        return cipherSuite.keyExAlgorithm == null
                || cipherSuite.keyExAlgorithm == KeyExAlgorithm.DHE_DSS
                || cipherSuite.keyExAlgorithm == KeyExAlgorithm.DHE_RSA;
    }
}
