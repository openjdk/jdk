/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.net.http.WebSocket.CloseCode;

import static java.net.http.WebSocket.CloseCode.PROTOCOL_ERROR;
import static java.util.Objects.requireNonNull;

//
// Special kind of exception closed from the outside world.
//
// Used as a "marker exception" for protocol issues in the incoming data, so the
// implementation could close the connection and specify an appropriate status
// code.
//
// A separate 'section' argument makes it more uncomfortable to be lazy and to
// leave a relevant spec reference empty :-) As a bonus all messages have the
// same style.
//
final class WSProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final CloseCode closeCode;
    private final String section;

    WSProtocolException(String section, String detail) {
        this(section, detail, PROTOCOL_ERROR);
    }

    WSProtocolException(String section, String detail, Throwable cause) {
        this(section, detail, PROTOCOL_ERROR, cause);
    }

    private WSProtocolException(String section, String detail, CloseCode code) {
        super(formatMessage(section, detail));
        this.closeCode = requireNonNull(code);
        this.section = section;
    }

    WSProtocolException(String section, String detail, CloseCode code,
                        Throwable cause) {
        super(formatMessage(section, detail), cause);
        this.closeCode = requireNonNull(code);
        this.section = section;
    }

    private static String formatMessage(String section, String detail) {
        if (requireNonNull(section).isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (requireNonNull(detail).isEmpty()) {
            throw new IllegalArgumentException();
        }
        return WSUtils.webSocketSpecViolation(section, detail);
    }

    CloseCode getCloseCode() {
        return closeCode;
    }

    public String getSection() {
        return section;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + closeCode + "]";
    }
}
