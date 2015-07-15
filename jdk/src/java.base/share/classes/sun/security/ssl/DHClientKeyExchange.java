/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import javax.net.ssl.SSLHandshakeException;

/*
 * Message used by clients to send their Diffie-Hellman public
 * keys to servers.
 *
 * @author David Brownell
 */
final class DHClientKeyExchange extends HandshakeMessage {

    @Override
    int messageType() {
        return ht_client_key_exchange;
    }

    /*
     * This value may be empty if it was included in the
     * client's certificate ...
     */
    private byte[] dh_Yc;               // 1 to 2^16 -1 bytes

    BigInteger getClientPublicKey() {
        return dh_Yc == null ? null : new BigInteger(1, dh_Yc);
    }

    /*
     * Either pass the client's public key explicitly (because it's
     * using DHE or DH_anon), or implicitly (the public key was in the
     * certificate).
     */
    DHClientKeyExchange(BigInteger publicKey) {
        dh_Yc = toByteArray(publicKey);
    }

    DHClientKeyExchange() {
        dh_Yc = null;
    }

    /*
     * Get the client's public key either explicitly or implicitly.
     * (It's ugly to have an empty record be sent in the latter case,
     * but that's what the protocol spec requires.)
     */
    DHClientKeyExchange(HandshakeInStream input) throws IOException {
        if (input.available() >= 2) {
            dh_Yc = input.getBytes16();
        } else {
            // currently, we don't support cipher suites that requires
            // implicit public key of client.
            throw new SSLHandshakeException(
                    "Unsupported implicit client DiffieHellman public key");
        }
    }

    @Override
    int messageLength() {
        if (dh_Yc == null) {
            return 0;
        } else {
            return dh_Yc.length + 2;
        }
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        if (dh_Yc != null && dh_Yc.length != 0) {
            s.putBytes16(dh_Yc);
        }
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** ClientKeyExchange, DH");

        if (debug != null && Debug.isOn("verbose")) {
            Debug.println(s, "DH Public key", dh_Yc);
        }
    }
}
