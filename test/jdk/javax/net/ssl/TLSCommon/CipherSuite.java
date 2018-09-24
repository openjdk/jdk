/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

public enum CipherSuite {

    TLS_AES_256_GCM_SHA384(
            0x1302, Protocol.TLSV1_3, Protocol.TLSV1_3),
    TLS_AES_128_GCM_SHA256(
            0x1301, Protocol.TLSV1_3, Protocol.TLSV1_3),
    TLS_CHACHA20_POLY1305_SHA256(
            0x1303, Protocol.TLSV1_3, Protocol.TLSV1_3),
    TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256(
            0xCCAA, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256(
            0xCCA9, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256(
            0xCCA8, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384(
            0xC032, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256(
            0xC031, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384(
            0xC030, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256(
            0xC02F, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384(
            0xC02E, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256(
            0xC02D, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384(
            0xC02C, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256(
            0xC02B, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384(
            0xC02A, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256(
            0xC029, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384(
            0xC028, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256(
            0xC027, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384(
            0xC026, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA(
            0xC025, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256(
            0xC025, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384(
            0xC024, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256(
            0xC023, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_anon_WITH_AES_256_CBC_SHA(
            0xC019, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_anon_WITH_AES_128_CBC_SHA(
            0xC018, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA(
            0xC017, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_anon_WITH_RC4_128_SHA(
            0xC016, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_anon_WITH_NULL_SHA(
            0xC015, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA(
            0xC014, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA(
            0xC013, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA(
            0xC012, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_RC4_128_SHA(
            0xC011, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_RSA_WITH_NULL_SHA(
            0xC010, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_AES_256_CBC_SHA(
            0xC00F, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_AES_128_CBC_SHA(
            0xC00E, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA(
            0xC00D, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_RC4_128_SHA(
            0xC00C, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_RSA_WITH_NULL_SHA(
            0xC00B, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA(
            0xC00A, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA(
            0xC009, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA(
            0xC008, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_RC4_128_SHA(
            0xC007, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDHE_ECDSA_WITH_NULL_SHA(
            0xC006, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA(
            0xC003, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_RC4_128_SHA(
            0xC002, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_NULL_SHA(
            0xC001, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_EMPTY_RENEGOTIATION_INFO_SCSV(
            0x00FF, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_DH_anon_WITH_AES_256_GCM_SHA384(
            0x00A7, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DH_anon_WITH_AES_128_GCM_SHA256(
            0x00A6, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DHE_DSS_WITH_AES_256_GCM_SHA384(
            0x00A3, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DHE_DSS_WITH_AES_128_GCM_SHA256(
            0x00A2, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DHE_RSA_WITH_AES_256_GCM_SHA384(
            0x009F, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DHE_RSA_WITH_AES_128_GCM_SHA256(
            0x009E, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_RSA_WITH_AES_256_GCM_SHA384(
            0x009D, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_RSA_WITH_AES_128_GCM_SHA256(
            0x009C, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DH_anon_WITH_AES_256_CBC_SHA256(
            0x006D, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DH_anon_WITH_AES_128_CBC_SHA256(
            0x006C, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DHE_RSA_WITH_AES_256_CBC_SHA256(
            0x006B, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DHE_DSS_WITH_AES_256_CBC_SHA256(
            0x006A, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DHE_RSA_WITH_AES_128_CBC_SHA256(
            0x0067, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA(
            0x004C, Protocol.TLSV1, Protocol.TLSV1_2),
    TLS_DHE_DSS_WITH_AES_128_CBC_SHA256(
            0x0040, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_RSA_WITH_AES_256_CBC_SHA256(
            0x003D, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_RSA_WITH_AES_128_CBC_SHA256(
            0x003C, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_RSA_WITH_NULL_SHA256(
            0x003B, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_DH_anon_WITH_AES_256_CBC_SHA(
            0x003A, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_DHE_RSA_WITH_AES_256_CBC_SHA(
            0x0039, Protocol.TLSV1, Protocol.TLSV1_2),
    TLS_DHE_DSS_WITH_AES_256_CBC_SHA(
            0x0038, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_RSA_WITH_AES_256_CBC_SHA(
            0x0035, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_DH_anon_WITH_AES_128_CBC_SHA(
            0x0034, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_DHE_RSA_WITH_AES_128_CBC_SHA(
            0x0033, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_DHE_DSS_WITH_AES_128_CBC_SHA(
            0x0032, Protocol.TLSV1_2, Protocol.TLSV1_2),
    TLS_RSA_WITH_AES_128_CBC_SHA(
            0x002F, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_KRB5_WITH_3DES_EDE_CBC_MD5(
            0x0023, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_KRB5_WITH_DES_CBC_MD5(
            0x0022, Protocol.SSLV3, Protocol.TLSV1_1),
    TLS_KRB5_WITH_3DES_EDE_CBC_SHA(
            0x001F, Protocol.SSLV3, Protocol.TLSV1_2),
    TLS_KRB5_WITH_DES_CBC_SHA(
            0x001E, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_DH_anon_WITH_3DES_EDE_CBC_SHA(
            0x001B, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_DH_anon_WITH_DES_CBC_SHA(
            0x001A, Protocol.SSLV3, Protocol.TLSV1_1),
    SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA(
            0x0019, Protocol.SSLV3, Protocol.TLSV1),
    SSL_DH_anon_WITH_RC4_128_MD5(
            0x0018, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_DH_anon_EXPORT_WITH_RC4_40_MD5(
            0x0017, Protocol.SSLV3, Protocol.TLSV1),
    SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA(
            0x0016, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_DHE_RSA_WITH_DES_CBC_SHA(
            0x0015, Protocol.SSLV3, Protocol.TLSV1_1),
    SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA(
            0x0014, Protocol.SSLV3, Protocol.TLSV1),
    SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA(
            0x0013, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_DHE_DSS_WITH_DES_CBC_SHA(
            0x0012, Protocol.SSLV3, Protocol.TLSV1_1),
    SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA(
            0x0011, Protocol.SSLV3, Protocol.TLSV1),
    SSL_RSA_WITH_3DES_EDE_CBC_SHA(
            0x000A, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_RSA_WITH_DES_CBC_SHA(
            0x0009, Protocol.SSLV3, Protocol.TLSV1_1),
    SSL_RSA_EXPORT_WITH_DES40_CBC_SHA(
            0x0008, Protocol.SSLV3, Protocol.TLSV1),
    SSL_RSA_WITH_RC4_128_SHA(
            0x0005, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_RSA_WITH_RC4_128_MD5(
            0x0004, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_RSA_EXPORT_WITH_RC4_40_MD5(
            0x0003, Protocol.SSLV3, Protocol.TLSV1),
    SSL_RSA_WITH_NULL_SHA(
            0x0002, Protocol.SSLV3, Protocol.TLSV1_2),
    SSL_RSA_WITH_NULL_MD5(
            0x0001, Protocol.SSLV3, Protocol.TLSV1_2);

    public final int id;
    public final Protocol startProtocol;
    public final Protocol endProtocol;

    private CipherSuite(
            int id,
            Protocol startProtocol,
            Protocol endProtocol) {
        this.id = id;
        this.startProtocol = startProtocol;
        this.endProtocol = endProtocol;
    }

    public boolean supportedByProtocol(Protocol protocol) {
        return startProtocol.id <= protocol.id
                && protocol.id <= endProtocol.id;
    }

    public static CipherSuite cipherSuite(String name) {
        return CipherSuite.valueOf(CipherSuite.class, name);
    }
}
