/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.websocket;

import static jdk.incubator.http.WebSocket.CLOSED_ABNORMALLY;

/*
 * Utilities and common constants for WebSocket status codes. For more details
 * on status codes and their meaning see:
 *
 *     1. https://tools.ietf.org/html/rfc6455#section-7.4
 *     2. http://www.iana.org/assignments/websocket/websocket.xhtml#close-code-number
 */
final class StatusCodes {

    static final int PROTOCOL_ERROR        = 1002;
    static final int CANNOT_ACCEPT         = 1003;
    static final int NO_STATUS_CODE        = 1005;
    static final int NOT_CONSISTENT        = 1007;
    static final int TOO_BIG               = 1009;
    static final int NO_EXTENSION          = 1010;
    static final int SERVICE_RESTART       = 1012;
    static final int TRY_AGAIN_LATER       = 1013;
    static final int TLS_HANDSHAKE_FAILURE = 1015;

    private StatusCodes() { }

    /*
     * Returns the given code if it doesn't violate any rules for outgoing
     * codes, otherwise throws a CFE with a detailed description.
     */
    static int checkOutgoingCode(int code) {
        checkCommon(code);
        if (code > 4999) {
            throw new CheckFailedException("Unspecified: " + code);
        }
        if (isNotUserSettable(code)) {
            throw new CheckFailedException("Cannot set: " + code);
        }
        return code;
    }

    /*
     * Returns the given code if it doesn't violate any rules for incoming
     * codes, otherwise throws a CFE with a detailed description.
     */
    static int checkIncomingCode(int code) {
        checkCommon(code);
        if (code == NO_EXTENSION) {
            throw new CheckFailedException("Bad server code: " + code);
        }
        return code;
    }

    private static int checkCommon(int code) {
        if (isOutOfRange(code)) {
            throw new CheckFailedException("Out of range: " + code);
        }
        if (isForbidden(code)) {
            throw new CheckFailedException("Forbidden: " + code);
        }
        if (isUnassigned(code)) {
            throw new CheckFailedException("Unassigned: " + code);
        }
        return code;
    }

    /*
     * Returns true if the given code cannot be set by a user of the WebSocket
     * API. e.g. this code means something which only a WebSocket implementation
     * is responsible for or it doesn't make sense to be send by a WebSocket
     * client.
     */
    private static boolean isNotUserSettable(int code) {
        switch (code) {
            case PROTOCOL_ERROR:
            case CANNOT_ACCEPT:
            case NOT_CONSISTENT:
            case TOO_BIG:
            case NO_EXTENSION:
            case TRY_AGAIN_LATER:
            case SERVICE_RESTART:
                return true;
            default:
                return false;
        }
    }

    /*
     * Returns true if the given code cannot appear on the wire. It's always an
     * error to send a frame with such a code or to receive one.
     */
    private static boolean isForbidden(int code) {
        switch (code) {
            case NO_STATUS_CODE:
            case CLOSED_ABNORMALLY:
            case TLS_HANDSHAKE_FAILURE:
                return true;
            default:
                return false;
        }
    }

    /*
     * Returns true if the given code has no known meaning under the WebSocket
     * specification (i.e. unassigned/undefined).
     */
    private static boolean isUnassigned(int code) {
        return (code >= 1016 && code <= 2999) || code == 1004 || code == 1014;
    }

    /*
     * Returns true if the given code is not in domain of status codes:
     *
     * 2-byte unsigned integer minus first 1000 numbers from the range [0, 999]
     * that are never used.
     */
    private static boolean isOutOfRange(int code) {
        return code < 1000 || code > 65535;
    }
}
