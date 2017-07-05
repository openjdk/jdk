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
 */
package java.net.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads response headers off channel, in blocking mode. Entire header
 * block is collected in a byte[]. The offset location of the start of
 * each header name is recorded in an array to facilitate later searching.
 *
 * The location of "Content-length" is recorded explicitly. Similar approach
 * could be taken for other common headers.
 *
 * This class is not thread-safe
 */
class ResponseHeaders implements HttpHeaders1 {

    static final int DATA_SIZE = 16 * 1024;  // initial space for headers
    static final int NUM_HEADERS = 50; // initial expected max number of headers

    final HttpConnection connection;
    byte[] data;
    int contentlen = -2; // means not initialized
    ByteBuffer buffer;

    /**
     * Following used for scanning the array looking for:
     *      - well known headers
     *      - end of header block
     */
    int[] headerOffsets; // index into data
    int numHeaders;
    int count;

    ByteBuffer residue; // after headers processed, data may be here

    ResponseHeaders(HttpConnection connection, ByteBuffer buffer) {
        this.connection = connection;
        initOffsets();
        this.buffer = buffer;
        data = new byte[DATA_SIZE];
    }

    int getContentLength() throws IOException {
        if (contentlen != -2) {
            return contentlen;
        }
        int[] search = findHeaderValue("Content-length");
        if (search[0] == -1) {
            contentlen = -1;
            return -1;
        }

        int i = search[0];

        while (data[i] == ' ' || data[i] == '\t') {
            i++;
            if (i == data.length || data[i] == CR || data[i] == LF) {
                throw new IOException("Bad header");
            }
        }
        contentlen = 0;
        int digit = data[i++] - 0x30;
        while (digit >= 0 && digit <= 9) {
            contentlen = contentlen * 10 + digit;
            digit = data[i++] - 0x30;
        }
        return contentlen;
    }

    void log() {
        populateMap(false);
    }

    void  populateMap(boolean clearOffsets) {
        StringBuilder sb;

        for (int i = 0; i < numHeaders; i++) {
            sb = new StringBuilder(32);
            int offset = headerOffsets[i];
            if (offset == -1) {
                continue;
            }
            int j;
            for (j=0; data[offset+j] != ':'; j++) {
                // byte to char promotion ok for US-ASCII
                sb.append((char)data[offset+j]);
            }
            String name = sb.toString();
            List<String> l = getOrCreate(name);
            addEntry(l, name, offset + j + 1);
            // clear the offset
            if (clearOffsets)
                headerOffsets[i] = -1;
        }
    }

    void addEntry(List<String> l, String name, int j) {

        while (data[j] == ' ' || data[j] == '\t') {
            j++;
        }

        int vstart = j;
            // TODO: back slash ??

        while (data[j] != CR) {
            j++;
        }
        try {
            String value = new String(data, vstart, j - vstart, "US-ASCII");
            l.add(value);
        } catch (UnsupportedEncodingException e) {
            // can't happen
            throw new InternalError(e);
        }
    }

    // returns an int[2]: [0] = offset of value in data[]
    // [1] = offset in headerOffsets. Both are -1 in error

    private int[] findHeaderValue(String name) {
        int[] result = new int[2];
        byte[] namebytes = getBytes(name);

 outer: for (int i = 0; i < numHeaders; i++) {
            int offset = headerOffsets[i];
            if (offset == -1) {
                continue;
            }

            for (int j=0; j<namebytes.length; j++) {
                if (namebytes[j] != lowerCase(data[offset+j])) {
                    continue outer;
                }
            }
            // next char must be ':'
            if (data[offset+namebytes.length] != ':') {
                continue;
            }
            result[0] = offset+namebytes.length + 1;
            result[1] = i;
            return result;
        }
        result[0] = -1;
        result[1] = -1;
        return result;
    }

    /**
     * Populates the map for header values with the given name.
     * The offsets are cleared for any that are found, so they don't
     * get repeatedly searched.
     */
    List<String> populateMapEntry(String name) {
        List<String> l = getOrCreate(name);
        int[] search = findHeaderValue(name);
        if (search[0] != -1) {
            addEntry(l, name, search[0]);
            // clear the offset
            headerOffsets[search[1]] = -1;
        }
        return l;
    }

    static final Locale usLocale = Locale.US;
    static final Charset ascii = StandardCharsets.US_ASCII;

    private byte[] getBytes(String name) {
        return name.toLowerCase(usLocale).getBytes(ascii);
    }

    /*
     * We read buffers in a loop until we detect end of headers
     * CRLFCRLF. Each byte received is copied into the byte[] data
     * The position of the first byte of each header (after a CRLF)
     * is recorded in a separate array showing the location of
     * each header name.
     */
    void initHeaders() throws IOException {

        inHeaderName = true;
        endOfHeader = true;

        for (int numBuffers = 0; true; numBuffers++) {

            if (numBuffers > 0) {
                buffer = connection.read();
            }

            if (buffer == null) {
                throw new IOException("Error reading headers");
            }

            if (!buffer.hasRemaining()) {
                continue;
            }

            // Position set to first byte
            int start = buffer.position();
            byte[] backing = buffer.array();
            int len = buffer.limit() - start;

            for (int i = 0; i < len; i++) {
                byte b = backing[i + start];
                if (inHeaderName) {
                    b = lowerCase(b);
                }
                if (b == ':') {
                    inHeaderName = false;
                }
                data[count++] = b;
                checkByte(b);
                if (firstChar) {
                    recordHeaderOffset(count-1);
                    firstChar = false;
                }
                if (endOfHeader && numHeaders == 0) {
                    // empty headers
                    endOfAllHeaders = true;
                }
                if (endOfAllHeaders) {
                    int newposition = i + 1 + start;
                    if (newposition <= buffer.limit()) {
                        buffer.position(newposition);
                        residue = buffer;
                    } else {
                        residue = null;
                    }
                    return;
                }

                if (count == data.length) {
                    resizeData();
                }
            }
        }
    }

    static final int CR = 13;
    static final int LF = 10;
    int crlfCount = 0;

    // results of checkByte()
    boolean endOfHeader; // just seen LF after CR before
    boolean endOfAllHeaders; // just seen LF after CRLFCR before
    boolean firstChar; //
    boolean inHeaderName; // examining header name

    void checkByte(byte b) throws IOException {
        if (endOfHeader &&  b != CR && b != LF)
            firstChar = true;
        endOfHeader = false;
        endOfAllHeaders = false;
        switch (crlfCount) {
            case 0:
                crlfCount = b == CR ? 1 : 0;
                break;
            case 1:
                crlfCount = b == LF ? 2 : 0;
                endOfHeader = true;
                inHeaderName = true;
                break;
            case 2:
                crlfCount = b == CR ? 3 : 0;
                break;
            case 3:
                if (b != LF) {
                    throw new IOException("Bad header block termination");
                }
                endOfAllHeaders = true;
                break;
        }
    }

    byte lowerCase(byte b) {
        if (b >= 0x41 && b <= 0x5A)
            b = (byte)(b + 32);
        return b;
    }

    void resizeData() {
        int oldlen = data.length;
        int newlen = oldlen * 2;
        byte[] newdata = new byte[newlen];
        System.arraycopy(data, 0, newdata, 0, oldlen);
        data = newdata;
    }

    final void initOffsets() {
        headerOffsets = new int[NUM_HEADERS];
        numHeaders = 0;
    }

    ByteBuffer getResidue() {
        return residue;
    }

    void recordHeaderOffset(int index) {
        if (numHeaders >= headerOffsets.length) {
            int oldlen = headerOffsets.length;
            int newlen = oldlen * 2;
            int[] new1 = new int[newlen];
            System.arraycopy(headerOffsets, 0, new1, 0, oldlen);
            headerOffsets = new1;
        }
        headerOffsets[numHeaders++] = index;
    }

    /**
     * As entries are read from the byte[] they are placed in here
     * So we always check this map first
     */
    Map<String,List<String>> headers = new HashMap<>();

    @Override
    public Optional<String> firstValue(String name) {
        List<String> l =  allValues(name);
        if (l == null || l.isEmpty()) {
            return Optional.ofNullable(null);
        } else {
            return Optional.of(l.get(0));
        }
    }

    @Override
    public List<String> allValues(String name) {
        name = name.toLowerCase(usLocale);
        List<String> l = headers.get(name);
        if (l == null) {
            l = populateMapEntry(name);
        }
        return Collections.unmodifiableList(l);
    }

    @Override
    public void makeUnmodifiable() {
    }

    // Delegates map to HashMap but converts keys to lower case

    static class HeaderMap implements Map<String,List<String>> {
        Map<String,List<String>> inner;

        HeaderMap(Map<String,List<String>> inner) {
            this.inner = inner;
        }
        @Override
        public int size() {
            return inner.size();
        }

        @Override
        public boolean isEmpty() {
            return inner.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            if (!(key instanceof String)) {
                return false;
            }
            String s = ((String)key).toLowerCase(usLocale);
            return inner.containsKey(s);
        }

        @Override
        public boolean containsValue(Object value) {
            return inner.containsValue(value);
        }

        @Override
        public List<String> get(Object key) {
            String s = ((String)key).toLowerCase(usLocale);
            return inner.get(s);
        }

        @Override
        public List<String> put(String key, List<String> value) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public List<String> remove(Object key) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void putAll(Map<? extends String, ? extends List<String>> m) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Set<String> keySet() {
            return inner.keySet();
        }

        @Override
        public Collection<List<String>> values() {
            return inner.values();
        }

        @Override
        public Set<Entry<String, List<String>>> entrySet() {
            return inner.entrySet();
        }
    }

    @Override
    public Map<String, List<String>> map() {
        populateMap(true);
        return new HeaderMap(headers);
    }

    Map<String, List<String>> mapInternal() {
        populateMap(false);
        return new HeaderMap(headers);
    }

    private List<String> getOrCreate(String name) {
        List<String> l = headers.get(name);
        if (l == null) {
            l = new LinkedList<>();
            headers.put(name, l);
        }
        return l;
    }

    @Override
    public Optional<Long> firstValueAsLong(String name) {
        List<String> l =  allValues(name);
        if (l == null) {
            return Optional.ofNullable(null);
        } else {
            String v = l.get(0);
            Long lv = Long.parseLong(v);
            return Optional.of(lv);
        }
    }
}
