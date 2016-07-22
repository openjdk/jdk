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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import static java.nio.charset.StandardCharsets.UTF_8;

// The purpose of this class is to separate charset-related tasks from the main
// WebSocket logic, simplifying where possible.
//
//     * Coders hide the differences between coding and flushing stages on the
//       API level
//     * Verifier abstracts the way the verification is performed
//       (spoiler: it's a decoding into a throw-away buffer)
//
// Coding methods throw exceptions instead of returning coding result denoting
// errors, since any kind of handling and recovery is not expected.
final class CharsetToolkit {

    private CharsetToolkit() { }

    static final class Verifier {

        private final CharsetDecoder decoder = UTF_8.newDecoder();
        // A buffer used to check validity of UTF-8 byte stream by decoding it.
        // The contents of this buffer are never used.
        // The size is arbitrary, though it should probably be chosen from the
        // performance perspective since it affects the total number of calls to
        // decoder.decode() and amount of work in each of these calls
        private final CharBuffer blackHole = CharBuffer.allocate(1024);

        void verify(ByteBuffer in, boolean endOfInput)
                throws CharacterCodingException {
            while (true) {
                // Since decoder.flush() cannot produce an error, it's not
                // helpful for verification. Therefore this step is skipped.
                CoderResult r = decoder.decode(in, blackHole, endOfInput);
                if (r.isOverflow()) {
                    blackHole.clear();
                } else if (r.isUnderflow()) {
                    break;
                } else if (r.isError()) {
                    r.throwException();
                } else {
                    // Should not happen
                    throw new InternalError();
                }
            }
        }

        Verifier reset() {
            decoder.reset();
            return this;
        }
    }

    static final class Encoder {

        private final CharsetEncoder encoder = UTF_8.newEncoder();
        private boolean coding = true;

        CoderResult encode(CharBuffer in, ByteBuffer out, boolean endOfInput)
                throws CharacterCodingException {

            if (coding) {
                CoderResult r = encoder.encode(in, out, endOfInput);
                if (r.isOverflow()) {
                    return r;
                } else if (r.isUnderflow()) {
                    if (endOfInput) {
                        coding = false;
                    } else {
                        return r;
                    }
                } else if (r.isError()) {
                    r.throwException();
                } else {
                    // Should not happen
                    throw new InternalError();
                }
            }
            assert !coding;
            return encoder.flush(out);
        }

        Encoder reset() {
            coding = true;
            encoder.reset();
            return this;
        }
    }

    static CharBuffer decode(ByteBuffer in) throws CharacterCodingException {
        return UTF_8.newDecoder().decode(in);
    }

    static final class Decoder {

        private final CharsetDecoder decoder = UTF_8.newDecoder();
        private boolean coding = true; // Either coding or flushing

        CoderResult decode(ByteBuffer in, CharBuffer out, boolean endOfInput)
                throws CharacterCodingException {

            if (coding) {
                CoderResult r = decoder.decode(in, out, endOfInput);
                if (r.isOverflow()) {
                    return r;
                } else if (r.isUnderflow()) {
                    if (endOfInput) {
                        coding = false;
                    } else {
                        return r;
                    }
                } else if (r.isError()) {
                    r.throwException();
                } else {
                    // Should not happen
                    throw new InternalError();
                }
            }
            assert !coding;
            return decoder.flush(out);
        }

        Decoder reset() {
            coding = true;
            decoder.reset();
            return this;
        }
    }
}
