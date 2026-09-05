/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

// Test data class, not a test.
final class NamedGroupTestData {

    static final String[] DEFAULT_SUPPORTED_NG = new String[]{
            "X25519MLKEM768",
            "x25519",
            "secp256r1",
            "secp384r1",
            "secp521r1",
            "x448",
            "ffdhe2048",
            "ffdhe3072",
            "ffdhe4096",
            "sect233k1",
            "sect233r1",
            "sect239k1",
            "sect283k1",
            "sect283r1",
            "sect409k1",
            "sect409r1",
            "sect571k1",
            "sect571r1",
            "secp224k1",
            "secp224r1",
            "secp256k1",
            "ffdhe6144",
            "ffdhe8192",
            "SecP256r1MLKEM768",
            "SecP384r1MLKEM1024"
    };

    // X25519MLKEM768, SecP256r1MLKEM768 and SecP384r1MLKEM1024 are not
    // supported in DTLSv1.2
    static final String[] DTLS12_SUPPORTED_NG = new String[]{
            "x25519",
            "secp256r1",
            "secp384r1",
            "secp521r1",
            "x448",
            "ffdhe2048",
            "ffdhe3072",
            "ffdhe4096",
            "sect233k1",
            "sect233r1",
            "sect239k1",
            "sect283k1",
            "sect283r1",
            "sect409k1",
            "sect409r1",
            "sect571k1",
            "sect571r1",
            "secp224k1",
            "secp224r1",
            "secp256k1",
            "ffdhe6144",
            "ffdhe8192"
    };

    static final String[][] TEST_VALUES = new String[][]{
            /*
             * Test values format as follows:
             *   Requested Named Group(s), Negotiated Named Group, Protocol
             */

            // Use default named group
            {null, "X25519MLKEM768", "TLSv1.3"},
            {null, "x25519", "TLSv1.2"},
            {null, "x25519", "DTLSv1.2"},

            // Request a single named group
            {"secp384r1", "secp384r1", "TLSv1.3"},
            {"secp384r1", "secp384r1", "TLSv1.2"},
            {"secp384r1", "secp384r1", "DTLSv1.2"},

            // Request multiple named groups
            {"secp256r1,secp384r1", "secp256r1", "TLSv1.3"},
            {"secp256r1,secp384r1", "secp256r1", "TLSv1.2"},
            {"secp256r1,secp384r1", "secp256r1", "DTLSv1.2"},
    };
}
