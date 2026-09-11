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

/*
 * @test
 * @bug 8383608
 * @summary check that InternalBinaryEncodable exists
 * @enablePreview
 * @modules java.base/sun.security.internal
 * @run main CheckIBE
 */

import javax.crypto.EncryptedPrivateKeyInfo;
import java.security.AsymmetricKey;
import java.security.BinaryEncodable;
import java.security.KeyPair;
import java.security.PEM;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import sun.security.internal.InternalBinaryEncodable;

/*
 * This test verifies that BinaryEncodable has the expected set of permitted
 * subtypes, including InternalBinaryEncodable. If this switch stops compiling,
 * update the cases to match the BinaryEncodable permits list.
 */

public class CheckIBE {
    public static void main(String[] args) {
        BinaryEncodable be = new PEM("TEST", "TEST");

        switch (be) {
            case AsymmetricKey ignored -> {}
            case KeyPair ignored -> {}
            case PKCS8EncodedKeySpec ignored -> {}
            case X509EncodedKeySpec ignored -> {}
            case EncryptedPrivateKeyInfo ignored -> {}
            case X509Certificate ignored -> {}
            case X509CRL ignored -> {}
            case PEM ignored -> {}
            case InternalBinaryEncodable ignored -> {}
        }
    }
}
