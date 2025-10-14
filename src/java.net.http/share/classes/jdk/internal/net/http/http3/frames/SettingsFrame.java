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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import static jdk.internal.net.http.quic.VariableLengthEncoder.MAX_ENCODED_INTEGER;

/**
 * This class models an HTTP/3 SETTINGS frame
 */
public class SettingsFrame extends AbstractHttp3Frame {

    // An array of setting parameters.
    // The index is the parameter id, minus 1, the value is the parameter value
    private final long[] parameters;
    // HTTP/3 specifies some reserved identifier for which the parameter
    // has no semantics and the value is undefined and should be ignored.
    // It's excepted that at least one such parameter should be included
    // in the settings frame to exercise the fact that undefined parameters
    // should be ignored
    private long undefinedId;
    private long undefinedValue;

    /**
     * The SETTINGS frame type, as defined by HTTP/3
     */
    public static final int TYPE = Http3FrameType.TYPE.SETTINGS_FRAME;


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
          .append(" Settings: ");

        for (int i = 0; i < MAX_PARAM; i++) {
            if (parameters[i] != -1) {
                sb.append(name(i+1))
                  .append("=")
                  .append(parameters[i])
                  .append(' ');
            }
        }
        if (undefinedId != -1) {
            sb.append(name(undefinedId)).append("=")
                    .append(undefinedValue).append(' ');
        }
        return sb.toString();
    }

    // TODO: should we use an enum instead?
    // HTTP/2 only Parameters - receiving one of those should be
    // considered as a protocol error of type SETTINGS_ERROR
    public static final int ENABLE_PUSH = 0x2;
    public static final int MAX_CONCURRENT_STREAMS = 0x3;
    public static final int INITIAL_WINDOW_SIZE = 0x4;
    public static final int MAX_FRAME_SIZE = 0x5;
    // HTTP/3 Parameters
    // This parameter was defined as HEADER_TABLE_SIZE in HTTP/2
    public static final int SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x1;
    public static final int DEFAULT_SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0;
    // This parameter was defined as MAX_HEADER_LIST_SIZE in HTTP/2
    public static final int SETTINGS_MAX_FIELD_SECTION_SIZE = 0x6;
    public static final long DEFAULT_SETTINGS_MAX_FIELD_SECTION_SIZE = -1;
    // Allow compression efficiency by allowing referencing dynamic table entries
    // that are still in transit. This parameter specifies the number of streams
    // that could become blocked.
    public static final int SETTINGS_QPACK_BLOCKED_STREAMS = 0x7;
    public static final int DEFAULT_SETTINGS_QPACK_BLOCKED_STREAMS = 0;

    public static final int MAX_PARAM = 0x7;

    // maps a parameter id to a parameter name
    private String name(long i) {
        if (i <= MAX_PARAM) {
            return switch ((int)i) {
                case SETTINGS_QPACK_MAX_TABLE_CAPACITY -> "SETTINGS_QPACK_MAX_TABLE_CAPACITY"; // 0x01
                case ENABLE_PUSH -> "ENABLE_PUSH"; // 0x02
                case MAX_CONCURRENT_STREAMS -> "MAX_CONCURRENT_STREAMS"; // 0x03
                case INITIAL_WINDOW_SIZE -> "INITIAL_WINDOW_SIZE"; // 0x04
                case MAX_FRAME_SIZE -> "MAX_FRAME_SIZE"; // 0x05
                case SETTINGS_MAX_FIELD_SECTION_SIZE -> "SETTINGS_MAX_FIELD_SECTION_SIZE"; // 0x06
                case SETTINGS_QPACK_BLOCKED_STREAMS -> "SETTINGS_QPACK_BLOCKED_STREAMS"; // 0x07
                default -> "UNKNOWN(0x00)"; // 0x00 ?
            };
        } else if (isReservedId(i)) {
            return "RESERVED(" + i + ")";
        } else {
            return "UNKNOWN(" + i +")";
        }
    }

    /**
     * Creates a new HTTP/3 SETTINGS frame, including the given
     * reserved identifier id and value pair.
     *
     * @implNote
     *   We only keep one reserved id/value pair - there's no
     *   reason to keep more...
     *
     * @param undefinedId     the id of an undefined (reserved) parameter
     * @param undefinedValue  a random value for the undefined parameter
     */
    public SettingsFrame(long undefinedId, long undefinedValue) {
        super(TYPE);
        parameters = new long [MAX_PARAM];
        Arrays.fill(parameters, -1);
        assert undefinedId == -1 || isReservedId(undefinedId);
        assert undefinedId != -1 || undefinedValue == -1;
        this.undefinedId = undefinedId;
        this.undefinedValue = undefinedValue;
    }

    /**
     * Creates a new empty SETTINGS frame, and allocate a random
     * reserved id and value pair.
     */
    public SettingsFrame() {
        this(nextRandomReservedParameterId(), nextRandomParameterValue());
    }

    /**
     * Get the parameter value for the given parameter id
     *
     * @param paramID the parameter id
     *
     * @return the value of the given parameter, if present,
     * {@code -1}, if absent
     *
     * @throws IllegalArgumentException if the parameter id is negative or
     *         {@linkplain #isIllegal(long) illegal}
     *
     */
    public synchronized long getParameter(int paramID) {
        if (isIllegal(paramID)) {
            throw new IllegalArgumentException("illegal parameter: " + paramID);
        }
        if (undefinedId != -1 && paramID == undefinedId)
            return undefinedValue;
        if (paramID > MAX_PARAM) return -1;
        return parameters[paramID - 1];
    }

    /**
     * Sets the given parameter to the given value.
     *
     * @param paramID  the parameter id
     * @param value    the parameter value
     *
     * @return this
     *
     * @throws IllegalArgumentException if the parameter id is negative or
     *         {@linkplain #isIllegal(long) illegal}
     */
    public synchronized SettingsFrame setParameter(long paramID, long value) {
        // subclasses can override this to actually send
        // an illegal parameter
        if (isIllegal(paramID) || paramID < 1 || paramID > MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException("illegal parameter: " + paramID);
        }
        if (paramID <= MAX_PARAM) {
            parameters[(int)paramID - 1] = value;
        } else if (isReservedId(paramID)) {
            this.undefinedId = paramID;
            this.undefinedValue = value;
        }
        return this;
    }

    @Override
    public long length() {
        int len = 0;
        int i = 0;
        for (long p : parameters) {
            if (p != -1) {
                len  += VariableLengthEncoder.getEncodedSize(i+1);
                len  += VariableLengthEncoder.getEncodedSize(p);
            }
        }
        if (undefinedId != -1) {
            assert isReservedId(undefinedId);
            len += VariableLengthEncoder.getEncodedSize(undefinedId);
            len += VariableLengthEncoder.getEncodedSize(undefinedValue);
        }
        return len;
    }

    /**
     * Writes this frame to the given buffer.
     *
     * @param buf a byte buffer to write this frame into
     *
     * @throws java.nio.BufferUnderflowException if the buffer
     * doesn't have enough space
     */
    public void writeFrame(ByteBuffer buf) {
        long size = size();
        long len = length();
        int pos0 = buf.position();
        VariableLengthEncoder.encode(buf, TYPE);
        VariableLengthEncoder.encode(buf, len);
        int pos1 = buf.position();
        for (int i = 0; i < MAX_PARAM; i++) {
            if (parameters[i] != -1) {
                VariableLengthEncoder.encode(buf, i+1);
                VariableLengthEncoder.encode(buf, parameters[i]);
            }
        }
        if (undefinedId != -1) {
            // Setting identifiers of the format 0x1f * N + 0x21 for
            // non-negative integer values of N are reserved to exercise
            // the requirement that unknown identifiers be ignored.
            // Such settings have no defined meaning. Endpoints SHOULD
            // include at least one such setting in their SETTINGS frame
            assert isReservedId(undefinedId);
            VariableLengthEncoder.encode(buf, undefinedId);
            VariableLengthEncoder.encode(buf, undefinedValue);
        }
        assert buf.position() - pos1 == len;
        assert buf.position() == pos0 + size;
    }

    /**
     * Decodes a SETTINGS frame from the given reader.
     * This method is expected to be called when the reader
     * contains enough bytes to decode the frame.
     *
     * @param reader a reader containing bytes
     *
     * @return a new SettingsFrame frame, or a MalformedFrame.
     *
     * @throws BufferUnderflowException if the reader doesn't contain
     *         enough bytes to decode the frame
     */
    public static AbstractHttp3Frame decodeFrame(BuffersReader reader, Logger debug) {
        final long pos = reader.position();
        decodeRequiredType(reader, TYPE);
        final SettingsFrame frame = new SettingsFrame(-1, -1);
        long length = VariableLengthEncoder.decode(reader);

         // is that OK? Find what's the actual limit for
        // a frame length...
        if (length > reader.remaining()) {
            reader.position(pos);
            throw new BufferUnderflowException();
        }

        // position before reading payload
        long start = reader.position();

        while (length > reader.position() - start) {
            long id = VariableLengthEncoder.decode(reader);
            long value = VariableLengthEncoder.decode(reader);
            if (id == -1 || value == -1) {
                return new MalformedFrame(TYPE,
                        Http3Error.H3_FRAME_ERROR.code(),
                        "Invalid SETTINGS frame contents.");
            }
            try {
                frame.setParameter(id, value);
            } catch (IllegalArgumentException iae) {
                String msg = "H3_SETTINGS_ERROR: " + iae.getMessage();
                if (debug.on()) debug.log(msg, iae);
                reader.position(start + length);
                reader.release();
                return new MalformedFrame(TYPE,
                        Http3Error.H3_SETTINGS_ERROR.code(),
                        iae.getMessage(),
                        iae);
            }
        }

        // check position after reading payload
        var malformed = checkPayloadSize(TYPE, reader, start, length);
        if (malformed != null) return malformed;

        reader.release();
        return frame;
    }

    public static SettingsFrame defaultRFCSettings() {
        SettingsFrame f = new SettingsFrame()
                .setParameter(SETTINGS_MAX_FIELD_SECTION_SIZE,
                        DEFAULT_SETTINGS_MAX_FIELD_SECTION_SIZE)
                .setParameter(SETTINGS_QPACK_MAX_TABLE_CAPACITY,
                        DEFAULT_SETTINGS_QPACK_MAX_TABLE_CAPACITY)
                .setParameter(SETTINGS_QPACK_BLOCKED_STREAMS,
                        DEFAULT_SETTINGS_QPACK_BLOCKED_STREAMS);
        return f;
    }

    public boolean isIllegal(long parameterId) {
        // Parameters with 0x0, 0x2, 0x3, 0x4 and 0x5 ids are reserved,
        // 0x6 is the legal one:
        // https://www.rfc-editor.org/rfc/rfc9114.html#name-settings-parameters
        // 0x1 and 0x7 defined by QPACK as a legal one:
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-configuration
        return parameterId < SETTINGS_MAX_FIELD_SECTION_SIZE &&
               parameterId != SETTINGS_QPACK_MAX_TABLE_CAPACITY;
    }

    public static long nextRandomParameterValue() {
        long value = RANDOM.nextLong(0, MAX_ENCODED_INTEGER + 1);
        assert value >= 0 && value <= MAX_ENCODED_INTEGER;
        return value;
    }

    private static final long MAX_N = (MAX_ENCODED_INTEGER - 0x21L) / 0x1fL;
    public static long nextRandomReservedParameterId() {
        long N = RANDOM.nextLong(0, MAX_N + 1);
        long id = 0x1fL * N + 0x21L;
        assert id <= MAX_ENCODED_INTEGER;
        assert id >= 0x21L;
        assert isReservedId(id) : "generated id is not undefined: " + id;
        return id;
    }

    /**
     * Tells whether the given id is one of the undefined parameter ids that
     * are reserved and have no meaning.
     *
     * @apiNote
     * Setting identifiers of the format 0x1f * N + 0x21
     * for non-negative integer values of N are reserved to
     * exercise the requirement that unknown identifiers be
     * ignored
     *
     * @param  id the parameter id
     *
     * @return true if this is one of the reserved identifiers
     */
    public static boolean isReservedId(long id) {
        return id >= 0x21 && id < MAX_ENCODED_INTEGER && (id - 0x21) % 0x1f == 0;
    }
}
