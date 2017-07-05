/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nio.zipfs;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;

/**
 * Utility class for zipfile name and comment decoding and encoding
 *
 * @author  Xueming Shen
 */

final class ZipCoder {

    String toString(byte[] ba, int length) {
        CharsetDecoder cd = decoder().reset();
        int len = (int)(length * cd.maxCharsPerByte());
        char[] ca = new char[len];
        if (len == 0)
            return new String(ca);
        ByteBuffer bb = ByteBuffer.wrap(ba, 0, length);
        CharBuffer cb = CharBuffer.wrap(ca);
        CoderResult cr = cd.decode(bb, cb, true);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        cr = cd.flush(cb);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        return new String(ca, 0, cb.position());
    }

    String toString(byte[] ba) {
        return toString(ba, ba.length);
    }

    byte[] getBytes(String s) {
        CharsetEncoder ce = encoder().reset();
        char[] ca = s.toCharArray();
        int len = (int)(ca.length * ce.maxBytesPerChar());
        byte[] ba = new byte[len];
        if (len == 0)
            return ba;
        ByteBuffer bb = ByteBuffer.wrap(ba);
        CharBuffer cb = CharBuffer.wrap(ca);
        CoderResult cr = ce.encode(cb, bb, true);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        cr = ce.flush(bb);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        if (bb.position() == ba.length)  // defensive copy?
            return ba;
        else
            return Arrays.copyOf(ba, bb.position());
    }

    // assume invoked only if "this" is not utf8
    byte[] getBytesUTF8(String s) {
        if (isutf8)
            return getBytes(s);
        if (utf8 == null)
            utf8 = new ZipCoder(Charset.forName("UTF-8"));
        return utf8.getBytes(s);
    }

    String toStringUTF8(byte[] ba, int len) {
        if (isutf8)
            return toString(ba, len);
        if (utf8 == null)
            utf8 = new ZipCoder(Charset.forName("UTF-8"));
        return utf8.toString(ba, len);
    }

    boolean isUTF8() {
        return isutf8;
    }

    private Charset cs;
    private boolean isutf8;
    private ZipCoder utf8;

    private ZipCoder(Charset cs) {
        this.cs = cs;
        this.isutf8 = cs.name().equals("UTF-8");
    }

    static ZipCoder get(Charset charset) {
        return new ZipCoder(charset);
    }

    static ZipCoder get(String csn) {
        try {
            return new ZipCoder(Charset.forName(csn));
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return new ZipCoder(Charset.defaultCharset());
    }

    private final ThreadLocal<CharsetDecoder> decTL = new ThreadLocal<>();
    private final ThreadLocal<CharsetEncoder> encTL = new ThreadLocal<>();

    private CharsetDecoder decoder() {
        CharsetDecoder dec = decTL.get();
        if (dec == null) {
            dec = cs.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
            decTL.set(dec);
        }
        return dec;
    }

    private CharsetEncoder encoder() {
        CharsetEncoder enc = encTL.get();
        if (enc == null) {
            enc = cs.newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
            encTL.set(enc);
        }
        return enc;
    }
}
