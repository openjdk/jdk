/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import javax.net.ssl.SSLProtocolException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import sun.security.ssl.HandshakeMessage.ClientHello;

/*
 * HelloVerifyRequest cookie manager
 */
final class HelloCookieManager {
    // the cookie secret life time
    private static long COOKIE_TIMING_WINDOW = 3600000;     // in milliseconds
    private static int  COOKIE_MAX_LENGTH_DTLS10 = 32;      // 32 bytes
    private static int  COOKIE_MAX_LENGTH_DTLS12 = 0xFF;    // 2^8 -1 bytes

    private final SecureRandom          secureRandom;
    private final MessageDigest         cookieDigest;

    private int                         cookieVersion;      // allow to wrap
    private long                        secretLifetime;
    private byte[]                      cookieSecret;

    private int                         prevCookieVersion;
    private byte[]                      prevCookieSecret;

    HelloCookieManager(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
        this.cookieDigest = JsseJce.getMessageDigest("SHA-256");

        this.cookieVersion = secureRandom.nextInt();
        this.secretLifetime = 0;
        this.cookieSecret = null;

        this.prevCookieVersion = 0;
        this.prevCookieSecret = null;
    }

    // Used by server side to generate cookies in HelloVerifyRequest message.
    synchronized byte[] getCookie(ClientHello clientHelloMsg) {
        if (secretLifetime < System.currentTimeMillis()) {
            if (cookieSecret != null) {
                prevCookieVersion = cookieVersion;
                prevCookieSecret = cookieSecret.clone();
            } else {
                cookieSecret = new byte[32];
            }

            cookieVersion++;
            secureRandom.nextBytes(cookieSecret);
            secretLifetime = System.currentTimeMillis() + COOKIE_TIMING_WINDOW;
        }

        clientHelloMsg.updateHelloCookie(cookieDigest);
        byte[] cookie = cookieDigest.digest(cookieSecret);      // 32 bytes
        cookie[0] = (byte)((cookieVersion >> 24) & 0xFF);
        cookie[1] = (byte)((cookieVersion >> 16) & 0xFF);
        cookie[2] = (byte)((cookieVersion >> 8) & 0xFF);
        cookie[3] = (byte)(cookieVersion & 0xFF);

        return cookie;
    }

    // Used by server side to check the cookie in ClientHello message.
    synchronized boolean isValid(ClientHello clientHelloMsg) {
        byte[] cookie = clientHelloMsg.cookie;

        // no cookie exchange or not a valid cookie length
        if ((cookie == null) || (cookie.length != 32)) {
            return false;
        }

        int version = ((cookie[0] & 0xFF) << 24) |
                      ((cookie[1] & 0xFF) << 16) |
                      ((cookie[2] & 0xFF) << 8) |
                       (cookie[3] & 0xFF);

        byte[] secret;
        if (version == cookieVersion) {
            secret = cookieSecret;
        } else if (version == prevCookieVersion) {
            secret = prevCookieSecret;
        } else {
            return false;       // may be out of the timing window
        }

        clientHelloMsg.updateHelloCookie(cookieDigest);
        byte[] target = cookieDigest.digest(secret);            // 32 bytes

        for (int i = 4; i < 32; i++) {
            if (cookie[i] != target[i]) {
                return false;
            }
        }

        return true;
    }

    // Used by client side to check the cookie in HelloVerifyRequest message.
    static void checkCookie(ProtocolVersion protocolVersion,
            byte[] cookie) throws IOException {
        if (cookie != null && cookie.length != 0) {
            int limit = COOKIE_MAX_LENGTH_DTLS12;
            if (protocolVersion.v == ProtocolVersion.DTLS10.v) {
                limit = COOKIE_MAX_LENGTH_DTLS10;
            }

            if (cookie.length > COOKIE_MAX_LENGTH_DTLS10) {
                throw new SSLProtocolException(
                        "Invalid HelloVerifyRequest.cookie (length = " +
                         cookie.length + " bytes)");
            }
        }

        // Otherwise, no cookie exchange.
    }
}
