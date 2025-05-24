/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package provider;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.spi.CharsetProvider;
import java.util.Collections;
import java.util.Iterator;

// A test charset provider that decodes every input byte into its uppercase
public class UppercasingCharsetProvider extends CharsetProvider {

    @Override
    public Iterator charsets() {
        return Collections.singleton(new UppercasingCharsetProvider.UppercasingCharset()).iterator();
    }

    @Override
    public Charset charsetForName(String charsetName) {
        if (charsetName.equals("Uppercasing")) {
            return new UppercasingCharsetProvider.UppercasingCharset();
        } else {
            return null;
        }
    }

    public static class UppercasingCharset extends Charset {

        public UppercasingCharset() {
            super("Uppercasing", null);
        }

        @Override
        public boolean contains(Charset cs) {
            return false;
        }

        @Override
        public CharsetDecoder newDecoder() {
            return new UppercasingCharsetDecoder(this, 1, 1);
        }

        @Override
        public CharsetEncoder newEncoder() {
            return null;
        }
    }

    private static class UppercasingCharsetDecoder extends CharsetDecoder {
        public UppercasingCharsetDecoder(Charset cs, float averageCharsPerByte, float maxCharsPerByte) {
            super(cs, averageCharsPerByte, maxCharsPerByte);
        }

        @Override
        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            while (in.remaining() > 0) {
                out.put(Character.toUpperCase((char)in.get()));
            }
            return CoderResult.UNDERFLOW;
        }
    }
}
