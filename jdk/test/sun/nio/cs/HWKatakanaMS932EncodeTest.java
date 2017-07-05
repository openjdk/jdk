/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
   @bug 4715330
   @summary Check MS932/windows-31j encoding (char->byte) for halfwidth katakana chars
 */

/*
 * Tests encodeability of the Unicode defined Halfwidth Katakana
 * characters using the MS932/windows-31j encoder
 */

public class HWKatakanaMS932EncodeTest {
   public static void main(String[] args) throws Exception {

        char[] testChars = new char[1];
        byte[] testBytes = new byte[1];
        int offset = 0;
        String encoding = "windows-31j";

        // Halfwidth Katakana chars run from U+FF61 --> U+FF9F
        // and their native equivalents in Code page 932 run
        // sequentially from 0xa1 --> 0xdf

        for (int lsByte = 0x61 ; lsByte <= 0x9F; lsByte++, offset++) {
            testChars[0] = (char) (lsByte | 0xFF00);
            String s = new String(testChars);
            testBytes = s.getBytes(encoding);
            if ( testBytes[0] != (byte)(0xa1 + offset))
                throw new Exception("failed Test");
        }
    }
}
