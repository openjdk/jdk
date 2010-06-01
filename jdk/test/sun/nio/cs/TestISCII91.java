/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 6431650
   @summary Check charset ISCII91 and C2B/B2CISCII91 yield same encoding/decoding result
 */


import java.nio.*;
import java.nio.charset.*;
import sun.io.*;

public class TestISCII91 {
    public static void main(String[] args) throws Throwable{
        CharToByteConverter c2b = new CharToByteISCII91();
        ByteToCharConverter b2c = new ByteToCharISCII91();
        Charset cs = Charset.forName("ISCII91");
        String charsToEncode = getCharsForEncoding("ISCII91");

        byte [] c2bBytes = c2b.convertAll(charsToEncode.toCharArray());
        byte [] csBytes = cs.encode(charsToEncode).array();
        for (int i = 0; i < c2bBytes.length; ++i) {
            if (c2bBytes[i] != csBytes[i])
                throw new RuntimeException("ISCII91 encoding failed!");
        }

        char[] c2bChars = b2c.convertAll(c2bBytes);
        char[] csChars = cs.decode(ByteBuffer.wrap(csBytes)).array();
        for (int i = 0; i < c2bChars.length; ++i) {
            if (c2bChars[i] != csChars[i])
                throw new RuntimeException("ISCII91 decoding failed!");
        }
    }


    static String getCharsForEncoding(String encodingName)
        throws CharacterCodingException{
        Charset set = Charset.forName(encodingName);
        CharBuffer chars = CharBuffer.allocate(300);
        CharsetEncoder encoder = set.newEncoder();
        for (int c = 0; chars.remaining() > 0 && c < Character.MAX_VALUE; ++c) {
            if (Character.isDefined((char) c) && !Character.isISOControl((char) c) && encoder.canEncode((char) c)) {
                chars.put((char) c);
            }
        }
        chars.limit(chars.position());
        chars.rewind();
        return chars.toString();
    }
}
