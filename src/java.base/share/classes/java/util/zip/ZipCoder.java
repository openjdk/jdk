/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.util.zip;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;

import sun.nio.cs.UTF_8;

/**
 * Utility class for zipfile name and comment decoding and encoding
 */
class ZipCoder {

    private static final jdk.internal.access.JavaLangAccess JLA =
        jdk.internal.access.SharedSecrets.getJavaLangAccess();

    static final class UTF8ZipCoder extends ZipCoder {

        // Encoding/decoding is stateless, so make it singleton.
        static final ZipCoder INSTANCE = new UTF8ZipCoder(UTF_8.INSTANCE);

        private UTF8ZipCoder(Charset utf8) {
            super(utf8);
        }

        @Override
        boolean isUTF8() {
            return true;
        }

        @Override
        String toString(byte[] ba, int off, int length) {
            return JLA.newStringUTF8NoRepl(ba, off, length);
        }

        @Override
        byte[] getBytes(String s) {
            return JLA.getBytesUTF8NoRepl(s);
        }

        @Override
        int hashN(byte[] a, int off, int len) {
            // Performance optimization: when UTF8-encoded, ZipFile.getEntryPos
            // assume that the hash of a name remains unchanged when appending a
            // trailing '/', which allows lookups to avoid rehashing
            int end = off + len;
            if (len > 0 && a[end - 1] == '/') {
                end--;
            }

            int h = 1;
            for (int i = off; i < end; i++) {
                h = 31 * h + a[i];
            }
            return h;
        }
    }

    public static ZipCoder get(Charset charset) {
        if (charset == UTF_8.INSTANCE)
            return UTF8ZipCoder.INSTANCE;
        return new ZipCoder(charset);
    }

    String toString(byte[] ba, int off, int length) {
        try {
            return decoder().decode(ByteBuffer.wrap(ba, off, length)).toString();
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
    }

    String toString(byte[] ba, int length) {
        return toString(ba, 0, length);
    }

    String toString(byte[] ba) {
        return toString(ba, 0, ba.length);
    }

    byte[] getBytes(String s) {
        try {
            ByteBuffer bb = encoder().encode(CharBuffer.wrap(s));
            int pos = bb.position();
            int limit = bb.limit();
            if (bb.hasArray() && pos == 0 && limit == bb.capacity()) {
                return bb.array();
            }
            byte[] bytes = new byte[bb.limit() - bb.position()];
            bb.get(bytes);
            return bytes;
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
    }

    // assume invoked only if "this" is not utf8
    byte[] getBytesUTF8(String s) {
        return UTF8ZipCoder.INSTANCE.getBytes(s);
    }

    String toStringUTF8(byte[] ba, int len) {
        return UTF8ZipCoder.INSTANCE.toString(ba, 0, len);
    }

    String toStringUTF8(byte[] ba, int off, int len) {
        return UTF8ZipCoder.INSTANCE.toString(ba, off, len);
    }

    boolean isUTF8() {
        return false;
    }

    int hashN(byte[] a, int off, int len) {
        int h = 1;
        while (len-- > 0) {
            h = 31 * h + a[off++];
        }
        return h;
    }

    private Charset cs;
    private CharsetDecoder dec;
    private CharsetEncoder enc;

    private ZipCoder(Charset cs) {
        this.cs = cs;
    }

    private CharsetDecoder decoder() {
        if (dec == null) {
            dec = cs.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return dec;
    }

    private CharsetEncoder encoder() {
        if (enc == null) {
            enc = cs.newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return enc;
    }
}
