/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.http3.frames;
import java.util.stream.Stream;

import static jdk.internal.net.http.quic.VariableLengthEncoder.MAX_ENCODED_INTEGER;
import static jdk.internal.net.http.quic.VariableLengthEncoder.MAX_INTEGER_LENGTH;

/**
 * An enum to model HTTP/3 frame types.
 */
public enum Http3FrameType {

    /**
     * Used to identify an HTTP/3 frame whose type is unknown
     */
    UNKNOWN(-1, MAX_ENCODED_INTEGER),
    /**
     * Used to identify an HTTP/3 DATA frame
     */
    DATA(TYPE.DATA_FRAME, MAX_ENCODED_INTEGER),
    /**
     * Used to identify an HTTP/3 HEADERS frame
     */
    HEADERS(TYPE.HEADERS_FRAME, MAX_ENCODED_INTEGER),
    /**
     * Used to identify an HTTP/3 CANCEL_PUSH frame
     */
    CANCEL_PUSH(TYPE.CANCEL_PUSH_FRAME, MAX_INTEGER_LENGTH),
    /**
     * Used to identify an HTTP/3 SETTINGS frame
     */
    SETTINGS(TYPE.SETTINGS_FRAME, TYPE.MAX_SETTINGS_LENGTH),
    /**
     * Used to identify an HTTP/3 PUSH_PROMISE frame
     */
    PUSH_PROMISE(TYPE.PUSH_PROMISE_FRAME, MAX_ENCODED_INTEGER),
    /**
     * Used to identify an HTTP/3 GOAWAY frame
     */
    GOAWAY(TYPE.GOAWAY_FRAME, MAX_INTEGER_LENGTH),
    /**
     * Used to identify an HTTP/3 MAX_PUSH_ID_FRAME frame
     */
    MAX_PUSH_ID(TYPE.MAX_PUSH_ID_FRAME, MAX_INTEGER_LENGTH);

    /**
     * A class to hold type constants
     */
    static final class TYPE {
        private TYPE() { throw new InternalError(); }

        // Frames types
        public static final int DATA_FRAME = 0x00;
        public static final int HEADERS_FRAME = 0x01;
        public static final int CANCEL_PUSH_FRAME = 0x03;
        public static final int SETTINGS_FRAME = 0x04;
        public static final int PUSH_PROMISE_FRAME = 0x05;
        public static final int GOAWAY_FRAME = 0x07;
        public static final int MAX_PUSH_ID_FRAME = 0x0d;

        // The maximum size a settings frame can have.
        // This is a limit imposed by our implementation.
        // There are only 7 settings defined in the current
        // specification, but we will allow for a frame to
        // contain up to 80. Past that limit, we will consider
        // the frame to be malformed:
        // 8 x 10 x (max sizeof(id) + max sizeof(value)) = 80 x 16 bytes
        public static final long MAX_SETTINGS_LENGTH =
                10L * 8L * MAX_INTEGER_LENGTH * 2L;
    }


    // This is one of the values defined in TYPE above, or
    // -1 for the UNKNOWN frame types.
    private final int type;
    private final long maxLength;
    private Http3FrameType(int type, long maxLength) {
        this.type = type;
        this.maxLength = maxLength;
    }

    /**
     * {@return the frame type, as defined by HTTP/3}
     */
    public long type() { return type;}

    /**
     * {@return the maximum length a frame of this type
     *  can take}
     */
    public long maxLength() {
        return maxLength;
    }

    /**
     * {@return the HTTP/3 frame type, as an int}
     *
     * @apiNote
     * HTTP/3 defines frames type as variable length integers
     * in the range [0, 2^62-1]. However, the few standard frame
     * types registered for HTTP/3 and modeled by this enum
     * class can be coded as an int.
     * This method provides a convenient way to access the frame
     * type as an int, which avoids having to cast when using
     * the value in switch statements.
     */
     public int intType() { return type;}

    /**
     * {@return the {@link Http3FrameType} corresponding to the given
     * {@code type}, or {@link #UNKNOWN} if no corresponding
     * {@link Http3FrameType} instance is found}
     * @param type an HTTP/3 frame type identifier read from an HTTP/3 frame
     */
    public static Http3FrameType forType(long type) {
        return Stream.of(values())
                .filter(x -> x.type == type)
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * {@return a string representation of the given type, suited for inclusion
     * in log messages, exceptions, etc...}
     * @param type an HTTP/3 frame type identifier read from an HTTP/3 frame
     */
    public static String asString(long type) {
        String str = null;
        if (type >= Integer.MIN_VALUE && type <= Integer.MAX_VALUE) {
            str = switch ((int)type) {
                case TYPE.DATA_FRAME         -> DATA.name();         // 0x00
                case TYPE.HEADERS_FRAME      -> HEADERS.name();      // 0x01
                case 0x02                    -> "RESERVED(0x02)";
                case TYPE.CANCEL_PUSH_FRAME  -> CANCEL_PUSH.name();  // 0x03
                case TYPE.SETTINGS_FRAME     -> SETTINGS.name();     // 0x04
                case TYPE.PUSH_PROMISE_FRAME -> PUSH_PROMISE.name(); // 0x05
                case 0x06                    -> "RESERVED(0x06)";
                case TYPE.GOAWAY_FRAME       -> GOAWAY.name();       // 0x07
                case 0x08                    -> "RESERVED(0x08)";
                case 0x09                    -> "RESERVED(0x09)";
                case TYPE.MAX_PUSH_ID_FRAME  -> MAX_PUSH_ID.name();  // 0x0d
                default -> null;
            };
        }
        if (str != null) return str;
        if (isReservedType(type)) {
            return "RESERVED(type=" + type + ")";
        }
        return "UNKNOWN(type=" + type + ")";
    }

    /**
     * {@return whether this frame type is illegal}
     * This corresponds to HTTP/2 frame types that have no equivalent in
     * HTTP/3.
     * @param type the frame type
     */
    public static boolean isIllegalType(long type) {
        return type == 0x02 || type == 0x06 || type == 0x08 || type == 0x09;
    }

    /**
     * Whether the given type is one of the reserved frame
     * types defined by HTTP/3. For any non-negative integer N:
     * {@code 0x21 + 0x1f * N }
     * is a reserved frame type that has no meaning.
     *
     * @param type an HTTP/3 frame type identifier read from an HTTP/3 frame
     *
     * @return true if the given type matches the {@code 0x21 + 0x1f * N}
     *         pattern
     */
    public static boolean isReservedType(long type) {
        return type >= 0x21L && type <= MAX_ENCODED_INTEGER
                && (type - 0x21L) % 0x1f == 0;
    }
}
