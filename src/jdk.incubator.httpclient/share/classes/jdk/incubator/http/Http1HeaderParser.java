/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

class Http1HeaderParser {

    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final char HT = '\t';
    private static final char SP = ' ';

    private StringBuilder sb = new StringBuilder();
    private String statusLine;
    private int responseCode;
    private HttpHeaders headers;
    private Map<String,List<String>> privateMap = new HashMap<>();

    enum State { STATUS_LINE,
                 STATUS_LINE_FOUND_CR,
                 STATUS_LINE_END,
                 STATUS_LINE_END_CR,
                 HEADER,
                 HEADER_FOUND_CR,
                 HEADER_FOUND_LF,
                 HEADER_FOUND_CR_LF,
                 HEADER_FOUND_CR_LF_CR,
                 FINISHED }

    private State state = State.STATUS_LINE;

    /** Returns the status-line. */
    String statusLine() { return statusLine; }

    /** Returns the response code. */
    int responseCode() { return responseCode; }

    /** Returns the headers, possibly empty. */
    HttpHeaders headers() { assert state == State.FINISHED; return headers; }

    /**
     * Parses HTTP/1.X status-line and headers from the given bytes. Must be
     * called successive times, with additional data, until returns true.
     *
     * All given ByteBuffers will be consumed, until ( possibly ) the last one
     * ( when true is returned ), which may not be fully consumed.
     *
     * @param input the ( partial ) header data
     * @return true iff the end of the headers block has been reached
     */
    boolean parse(ByteBuffer input) throws ProtocolException {
        requireNonNull(input, "null input");

        while (input.hasRemaining() && state != State.FINISHED) {
            switch (state) {
                case STATUS_LINE:
                    readResumeStatusLine(input);
                    break;
                case STATUS_LINE_FOUND_CR:
                    readStatusLineFeed(input);
                    break;
                case STATUS_LINE_END:
                    maybeStartHeaders(input);
                    break;
                case STATUS_LINE_END_CR:
                    maybeEndHeaders(input);
                    break;
                case HEADER:
                    readResumeHeader(input);
                    break;
                // fallthrough
                case HEADER_FOUND_CR:
                case HEADER_FOUND_LF:
                    resumeOrLF(input);
                    break;
                case HEADER_FOUND_CR_LF:
                    resumeOrSecondCR(input);
                    break;
                case HEADER_FOUND_CR_LF_CR:
                    resumeOrEndHeaders(input);
                    break;
                default:
                    throw new InternalError(
                            "Unexpected state: " + String.valueOf(state));
            }
        }

        return state == State.FINISHED;
    }

    private void readResumeStatusLine(ByteBuffer input) {
        char c = 0;
        while (input.hasRemaining() && (c =(char)input.get()) != CR) {
            sb.append(c);
        }

        if (c == CR) {
            state = State.STATUS_LINE_FOUND_CR;
        }
    }

    private void readStatusLineFeed(ByteBuffer input) throws ProtocolException {
        char c = (char)input.get();
        if (c != LF) {
            throw protocolException("Bad trailing char, \"%s\", when parsing status-line, \"%s\"",
                                    c, sb.toString());
        }

        statusLine = sb.toString();
        sb = new StringBuilder();
        if (!statusLine.startsWith("HTTP/1.")) {
            throw protocolException("Invalid status line: \"%s\"", statusLine);
        }
        if (statusLine.length() < 12) {
            throw protocolException("Invalid status line: \"%s\"", statusLine);
        }
        responseCode = Integer.parseInt(statusLine.substring(9, 12));

        state = State.STATUS_LINE_END;
    }

    private void maybeStartHeaders(ByteBuffer input) {
        assert state == State.STATUS_LINE_END;
        assert sb.length() == 0;
        char c = (char)input.get();
        if (c == CR) {
            state = State.STATUS_LINE_END_CR;
        } else {
            sb.append(c);
            state = State.HEADER;
        }
    }

    private void maybeEndHeaders(ByteBuffer input) throws ProtocolException {
        assert state == State.STATUS_LINE_END_CR;
        assert sb.length() == 0;
        char c = (char)input.get();
        if (c == LF) {
            headers = ImmutableHeaders.of(privateMap);
            privateMap = null;
            state = State.FINISHED;  // no headers
        } else {
            throw protocolException("Unexpected \"%s\", after status-line CR", c);
        }
    }

    private void readResumeHeader(ByteBuffer input) {
        assert state == State.HEADER;
        assert input.hasRemaining();
        while (input.hasRemaining()) {
            char c = (char)input.get();
            if (c == CR) {
                state = State.HEADER_FOUND_CR;
                break;
            } else if (c == LF) {
                state = State.HEADER_FOUND_LF;
                break;
            }

            if (c == HT)
                c = SP;
            sb.append(c);
        }
    }

    private void addHeaderFromString(String headerString) {
        assert sb.length() == 0;
        int idx = headerString.indexOf(':');
        if (idx == -1)
            return;
        String name = headerString.substring(0, idx).trim();
        if (name.isEmpty())
            return;
        String value = headerString.substring(idx + 1, headerString.length()).trim();

        privateMap.computeIfAbsent(name.toLowerCase(Locale.US),
                                   k -> new ArrayList<>()).add(value);
    }

    private void resumeOrLF(ByteBuffer input) {
        assert state == State.HEADER_FOUND_CR || state == State.HEADER_FOUND_LF;
        char c = (char)input.get();
        if (c == LF && state == State.HEADER_FOUND_CR) {
            // header value will be flushed by
            // resumeOrSecondCR if next line does not
            // begin by SP or HT
            state = State.HEADER_FOUND_CR_LF;
        } else if (c == SP || c == HT) {
            sb.append(SP); // parity with MessageHeaders
            state = State.HEADER;
        } else {
            sb = new StringBuilder();
            sb.append(c);
            state = State.HEADER;
        }
    }

    private void resumeOrSecondCR(ByteBuffer input) {
        assert state == State.HEADER_FOUND_CR_LF;
        char c = (char)input.get();
        if (c == CR) {
            if (sb.length() > 0) {
                // no continuation line - flush
                // previous header value.
                String headerString = sb.toString();
                sb = new StringBuilder();
                addHeaderFromString(headerString);
            }
            state = State.HEADER_FOUND_CR_LF_CR;
        } else if (c == SP || c == HT) {
            assert sb.length() != 0;
            sb.append(SP); // continuation line
            state = State.HEADER;
        } else {
            if (sb.length() > 0) {
                // no continuation line - flush
                // previous header value.
                String headerString = sb.toString();
                sb = new StringBuilder();
                addHeaderFromString(headerString);
            }
            sb.append(c);
            state = State.HEADER;
        }
    }

    private void resumeOrEndHeaders(ByteBuffer input) throws ProtocolException {
        assert state == State.HEADER_FOUND_CR_LF_CR;
        char c = (char)input.get();
        if (c == LF) {
            state = State.FINISHED;
            headers = ImmutableHeaders.of(privateMap);
            privateMap = null;
        } else {
            throw protocolException("Unexpected \"%s\", after CR LF CR", c);
        }
    }

    private ProtocolException protocolException(String format, Object... args) {
        return new ProtocolException(format(format, args));
    }
}
