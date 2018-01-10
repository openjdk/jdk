/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * A tagging interface that all TLS communication parameters must implement.
 */
public interface Parameter { }

/* The followings are TLS communication parameters. */

enum Protocol implements Parameter {

    SSLV3_0(3, "SSLv3"),
    TLSV1_0(4, "TLSv1"),
    TLSV1_1(5, "TLSv1.1"),
    TLSV1_2(6, "TLSv1.2");

    public final int sequence;
    public final String version;

    private Protocol(int sequence, String version) {
        this.sequence = sequence;
        this.version = version;
    }

    static Protocol getProtocol(String version) {
        for (Protocol protocol : values()) {
            if (protocol.version.equals(version)) {
                return protocol;
            }
        }

        return null;
    }

    static Protocol[] getMandatoryValues() {
        return new Protocol[] { TLSV1_0, TLSV1_1, TLSV1_2 };
    }
}

enum CipherSuite implements Parameter {

    TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_RSA_WITH_AES_256_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK6),
    TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_DHE_RSA_WITH_AES_256_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK6),
    TLS_DHE_DSS_WITH_AES_256_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK6),
    TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA(),
    TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA(),
    TLS_RSA_WITH_AES_256_CBC_SHA(),
    TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA(),
    TLS_ECDH_RSA_WITH_AES_256_CBC_SHA(),
    TLS_DHE_RSA_WITH_AES_256_CBC_SHA(),
    TLS_DHE_DSS_WITH_AES_256_CBC_SHA(),
    TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_RSA_WITH_AES_128_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK6),
    TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK7),
    TLS_DHE_RSA_WITH_AES_128_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK6),
    TLS_DHE_DSS_WITH_AES_128_CBC_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK6),
    TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA(),
    TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA(),
    TLS_RSA_WITH_AES_128_CBC_SHA(),
    TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA(),
    TLS_ECDH_RSA_WITH_AES_128_CBC_SHA(
            Protocol.SSLV3_0, JdkRelease.JDK7),
    TLS_DHE_RSA_WITH_AES_128_CBC_SHA(),
    TLS_DHE_DSS_WITH_AES_128_CBC_SHA(),
    TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_RSA_WITH_AES_256_GCM_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_DHE_RSA_WITH_AES_256_GCM_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_DHE_DSS_WITH_AES_256_GCM_SHA384(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_RSA_WITH_AES_128_GCM_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_DHE_RSA_WITH_AES_128_GCM_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_DHE_DSS_WITH_AES_128_GCM_SHA256(
            Protocol.TLSV1_2, JdkRelease.JDK8),
    TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA(),
    TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA(),
    TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA(),
    TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA(),
    TLS_ECDHE_ECDSA_WITH_RC4_128_SHA(),
    TLS_ECDHE_RSA_WITH_RC4_128_SHA(),
    TLS_ECDH_ECDSA_WITH_RC4_128_SHA(),
    TLS_ECDH_RSA_WITH_RC4_128_SHA(),
    SSL_RSA_WITH_RC4_128_SHA(),
    SSL_RSA_WITH_3DES_EDE_CBC_SHA(),
    SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA(
            Protocol.SSLV3_0, JdkRelease.JDK6),
    SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA(
            Protocol.SSLV3_0, JdkRelease.JDK6),
    SSL_RSA_WITH_RC4_128_MD5(
            Protocol.SSLV3_0, JdkRelease.JDK6);

    private static final boolean FULL_CIPHER_SUITES
            = Utils.getBoolProperty("fullCipherSuites");

    final Protocol startProtocol;
    final Protocol endProtocol;

    final JdkRelease startJdk;
    final JdkRelease endJdk;

    private CipherSuite(
            Protocol startProtocol, Protocol endProtocol,
            JdkRelease startJdk, JdkRelease endJdk) {
        this.startProtocol = startProtocol;
        this.endProtocol = endProtocol;

        this.startJdk = startJdk;
        this.endJdk = endJdk;
    }

    private CipherSuite(Protocol startProtocol, JdkRelease startJdk) {
        this(startProtocol, null, startJdk, null);
    }

    private CipherSuite() {
        this(Protocol.TLSV1_0, null, JdkRelease.JDK6, null);
    }

    boolean supportedByProtocol(Protocol protocol) {
        return startProtocol.sequence <= protocol.sequence
                && (endProtocol == null || endProtocol.sequence >= protocol.sequence);
    }

    static CipherSuite[] getMandatoryValues() {
        return FULL_CIPHER_SUITES
               ? values()
               : new CipherSuite[] {
                       TLS_RSA_WITH_AES_128_CBC_SHA,
                       TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
                       TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                       TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                       TLS_RSA_WITH_AES_256_CBC_SHA256,
                       TLS_DHE_DSS_WITH_AES_256_CBC_SHA256,
                       TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
                       TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384 };
    }

    static CipherSuite getCipherSuite(String name) {
        for (CipherSuite cipherSuite : values()) {
            if (cipherSuite.name().equals(name)) {
                return cipherSuite;
            }
        }

        return null;
    }
}

enum ClientAuth implements Parameter {

    FALSE,
    TRUE;

    static ClientAuth[] getMandatoryValues() {
        return new ClientAuth[] { TRUE };
    }
}

enum ServerName implements Parameter {

    NONE(null),
    EXAMPLE("www.example.com");

    final String name;

    private ServerName(String name) {
        this.name = name;
    }

    static ServerName[] getMandatoryValues() {
        return new ServerName[] { EXAMPLE };
    }
}

enum AppProtocol implements Parameter {

    NONE(null, null),
    EXAMPLE(new String[] { Utils.HTTP_2, Utils.HTTP_1_1 }, Utils.HTTP_2);

    final String[] appProtocols;

    // Expected negotiated application protocol
    final String negoAppProtocol;

    private AppProtocol(String[] appProtocols, String negoAppProtocol) {
        this.appProtocols = appProtocols;
        this.negoAppProtocol = negoAppProtocol;
    }

    static AppProtocol[] getMandatoryValues() {
        return new AppProtocol[] { EXAMPLE };
    }
}
