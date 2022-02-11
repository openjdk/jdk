/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class HttpHeaderParser {
    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final char HT = '\t';
    private static final char SP = ' ';
    // ABNF primitives defined in RFC 7230
    private static boolean[] tchar      = new boolean[256];
    private static boolean[] fieldvchar = new boolean[256];

    static {
        char[] allowedTokenChars =
                ("!#$%&'*+-.^_`|~0123456789" +
                        "abcdefghijklmnopqrstuvwxyz" +
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();
        for (char c : allowedTokenChars) {
            tchar[c] = true;
        }
        for (char c = 0x21; c <= 0xFF; c++) {
            fieldvchar[c] = true;
        }
        fieldvchar[0x7F] = false; // a little hole (DEL) in the range
    }

    private StringBuilder sb = new StringBuilder();

    private Map <String, List<String>>  headerMap = new LinkedHashMap<>();
    private List <String> keyList = new ArrayList<>();
    private String requestOrStatusLine;
    private int responseCode;



    enum State { INITIAL,
        STATUS_LINE,
        STATUS_LINE_FOUND_CR,
        STATUS_LINE_FOUND_LF,
        STATUS_LINE_END,
        STATUS_LINE_END_CR,
        STATUS_LINE_END_LF,
        HEADER,
        HEADER_FOUND_CR,
        HEADER_FOUND_LF,
        HEADER_FOUND_CR_LF,
        HEADER_FOUND_CR_LF_CR,
        FINISHED }

    private HttpHeaderParser.State state = HttpHeaderParser.State.INITIAL;

    public HttpHeaderParser() {
    }


    public HttpHeaderParser(InputStream is) throws IOException, ProtocolException {
        parse(is);
    }

    public Map<String, List<String>> getHeaderMap() {
        return headerMap;
    }

    public List<String> getHeaderValue(String key) {
        if(headerMap.containsKey(key.toLowerCase())) {
            return headerMap.get(key.toLowerCase());
        }
        return null;
    }
    public List<String> getValue(int id) {
        String key = keyList.get(id);
        return headerMap.get(key);
    }

    public String getRequestDetails() {
        return requestOrStatusLine;
    }

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
    public boolean parse(InputStream input) throws IOException {
        requireNonNull(input, "null input");
        while (canContinueParsing(input)) {
            switch (state) {
                case INITIAL                                    ->  state = HttpHeaderParser.State.STATUS_LINE;
                case STATUS_LINE                                ->  readResumeStatusLine(input);
                case STATUS_LINE_FOUND_CR, STATUS_LINE_FOUND_LF ->  readStatusLineFeed(input);
                case STATUS_LINE_END                            ->  maybeStartHeaders(input);
                case STATUS_LINE_END_CR, STATUS_LINE_END_LF     ->  maybeEndHeaders(input);
                case HEADER                                     ->  readResumeHeader(input);
                case HEADER_FOUND_CR, HEADER_FOUND_LF           ->  resumeOrLF(input);
                case HEADER_FOUND_CR_LF                         ->  resumeOrSecondCR(input);
                case HEADER_FOUND_CR_LF_CR                      ->  resumeOrEndHeaders(input);
                default -> throw new InternalError("Unexpected state: " + state);
            }
        }
        return state == HttpHeaderParser.State.FINISHED;
    }

    private boolean canContinueParsing(InputStream buffer) throws IOException {
        // some states don't require any input to transition
        // to the next state.
        return switch (state) {
            case FINISHED -> false;
            case STATUS_LINE_FOUND_LF, STATUS_LINE_END_LF, HEADER_FOUND_LF -> true;
            default -> buffer.available() >= 0;
        };
    }

    /**
     * Returns a character (char) corresponding to the next byte in the
     * input, interpreted as an ISO-8859-1 encoded character.
     * <p>
     * The ISO-8859-1 encoding is a 8-bit character coding that
     * corresponds to the first 256 Unicode characters - from U+0000 to
     * U+00FF. UTF-16 is backward compatible with ISO-8859-1 - which
     * means each byte in the input should be interpreted as an unsigned
     * value from [0, 255] representing the character code.
     *
     * @param input a {@code ByteBuffer} containing a partial input
     * @return the next byte in the input, interpreted as an ISO-8859-1
     * encoded char
     * @throws BufferUnderflowException
     *          if the input buffer's current position is not smaller
     *          than its limit
     */
    private char get(InputStream input) throws IOException {
        return (char)(input.read() & 0xFF);
    }

    private void readResumeStatusLine(InputStream input) throws IOException {
        char c = 0;
        while (input.available() > 0 && (c = get(input)) != CR) {
            if (c == LF) break;
            sb.append(c);
        }
        if (c == CR) {
            state = HttpHeaderParser.State.STATUS_LINE_FOUND_CR;
        } else if (c == LF) {
            state = HttpHeaderParser.State.STATUS_LINE_FOUND_LF;
        }
    }

    private void readStatusLineFeed(InputStream input) throws IOException {
        char c = state == HttpHeaderParser.State.STATUS_LINE_FOUND_LF ? LF : get(input);
        if (c != LF) {
            throw protocolException("Bad trailing char, \"%s\", when parsing status line, \"%s\"",
                    c, sb.toString());
        }

        requestOrStatusLine = sb.toString();
        sb = new StringBuilder();
        if (!requestOrStatusLine.startsWith("HTTP/1.")) {
            if(!requestOrStatusLine.startsWith("GET") && !requestOrStatusLine.startsWith("POST") &&
                    !requestOrStatusLine.startsWith("PUT") && !requestOrStatusLine.startsWith("DELETE") &&
                    !requestOrStatusLine.startsWith("OPTION") && !requestOrStatusLine.startsWith("HEAD")
            && !requestOrStatusLine.startsWith("CONNECT")) {
                throw protocolException("Invalid request Or Status line: \"%s\"", requestOrStatusLine);
            } else { //This is request
                System.out.println("THIS IS REQUEST :"+requestOrStatusLine);

            }
        } else { //This is response
            if (requestOrStatusLine.length() < 12) {
                throw protocolException("Invalid status line: \"%s\"", requestOrStatusLine);
            }
            try {
                responseCode = Integer.parseInt(requestOrStatusLine.substring(9, 12));
            } catch (NumberFormatException nfe) {
                throw protocolException("Invalid status line: \"%s\"", requestOrStatusLine);
            }
            // response code expected to be a 3-digit integer (RFC-2616, section 6.1.1)
            if (responseCode < 100) {
                throw protocolException("Invalid status line: \"%s\"", requestOrStatusLine);
            }
        }
        state = HttpHeaderParser.State.STATUS_LINE_END;
    }

    private void maybeStartHeaders(InputStream input) throws IOException {
        assert state == HttpHeaderParser.State.STATUS_LINE_END;
        assert sb.length() == 0;
        char c = get(input);
        if (c == CR) {
            state = HttpHeaderParser.State.STATUS_LINE_END_CR;
        } else if (c == LF) {
            state = HttpHeaderParser.State.STATUS_LINE_END_LF;
        } else {
            sb.append(c);
            state = HttpHeaderParser.State.HEADER;
        }
    }

    private void maybeEndHeaders(InputStream input) throws IOException {
        assert state == HttpHeaderParser.State.STATUS_LINE_END_CR || state == HttpHeaderParser.State.STATUS_LINE_END_LF;
        assert sb.length() == 0;
        char c = state == HttpHeaderParser.State.STATUS_LINE_END_LF ? LF : get(input);
        if (c == LF) {
            state = HttpHeaderParser.State.FINISHED;  // no headers
        } else {
            throw protocolException("Unexpected \"%s\", after status line CR", c);
        }
    }

    private void readResumeHeader(InputStream input) throws IOException {
        assert state == HttpHeaderParser.State.HEADER;
        assert input.available() > 0;
        while (input.available() > 0) {
            char c = get(input);
            if (c == CR) {
                state = HttpHeaderParser.State.HEADER_FOUND_CR;
                break;
            } else if (c == LF) {
                state = HttpHeaderParser.State.HEADER_FOUND_LF;
                break;
            }
            if (c == HT)
                c = SP;
            sb.append(c);
        }
    }

    private void addHeaderFromString(String headerString) throws ProtocolException {
        assert sb.length() == 0;
        int idx = headerString.indexOf(':');
        if (idx == -1)
            return;
        String name = headerString.substring(0, idx);

        // compatibility with HttpURLConnection;
        if (name.isEmpty()) return;

        if (!isValidName(name)) {
            throw protocolException("Invalid header name \"%s\"", name);
        }
        String value = headerString.substring(idx + 1).trim();
        if (!isValidValue(value)) {
            throw protocolException("Invalid header value \"%s: %s\"", name, value);
        }

        keyList.add(name);
        headerMap.computeIfAbsent(name.toLowerCase(Locale.US),
                k -> new ArrayList<>()).add(value);
    }

    private void resumeOrLF(InputStream input) throws IOException {
        assert state == HttpHeaderParser.State.HEADER_FOUND_CR || state == HttpHeaderParser.State.HEADER_FOUND_LF;
        char c = state == HttpHeaderParser.State.HEADER_FOUND_LF ? LF : get(input);
        if (c == LF) {
            state = HttpHeaderParser.State.HEADER_FOUND_CR_LF;
        } else if (c == SP || c == HT) {
            sb.append(SP); // parity with MessageHeaders
            state = HttpHeaderParser.State.HEADER;
        } else {
            sb = new StringBuilder();
            sb.append(c);
            state = HttpHeaderParser.State.HEADER;
        }
    }

    private void resumeOrSecondCR(InputStream input) throws IOException {
        assert state == HttpHeaderParser.State.HEADER_FOUND_CR_LF;
        char c = get(input);
        if (c == CR || c == LF) {
            if (sb.length() > 0) {
                // no continuation line - flush
                // previous header value.
                String headerString = sb.toString();
                sb = new StringBuilder();
                addHeaderFromString(headerString);
            }
            if (c == CR) {
                state = HttpHeaderParser.State.HEADER_FOUND_CR_LF_CR;
            } else {
                state = HttpHeaderParser.State.FINISHED;
            }
        } else if (c == SP || c == HT) {
            assert sb.length() != 0;
            sb.append(SP); // continuation line
            state = HttpHeaderParser.State.HEADER;
        } else {
            if (sb.length() > 0) {
                // no continuation line - flush
                // previous header value.
                String headerString = sb.toString();
                sb = new StringBuilder();
                addHeaderFromString(headerString);
            }
            sb.append(c);
            state = HttpHeaderParser.State.HEADER;
        }
    }

    private void resumeOrEndHeaders(InputStream input) throws IOException {
        assert state == HttpHeaderParser.State.HEADER_FOUND_CR_LF_CR;
        char c = get(input);
        if (c == LF) {
            state = HttpHeaderParser.State.FINISHED;
        } else {
            throw protocolException("Unexpected \"%s\", after CR LF CR", c);
        }
    }

    private ProtocolException protocolException(String format, Object ... args) {
        return new ProtocolException(String.format(format, args));
    }

    /*
     * Validates a RFC 7230 field-name.
     */
    public boolean isValidName(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255 || !tchar[c]) {
                return false;
            }
        }
        return !token.isEmpty();
    }

    /*
     * Validates a RFC 7230 field-value.
     *
     * "Obsolete line folding" rule
     *
     *     obs-fold = CRLF 1*( SP / HTAB )
     *
     * is not permitted!
     */
    public boolean isValidValue(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255) {
                return false;
            }
            if (c == ' ' || c == '\t') {
                continue;
            } else if (!fieldvchar[c]) {
                return false; // forbidden byte
            }
        }
        return true;
    }
}
