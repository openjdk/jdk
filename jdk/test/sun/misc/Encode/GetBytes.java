/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 5031097
 * @summary sun.misc.CharacterEncoder(ByteBuffer) is dumping too
 *      much information
 * @author Brad Wetmore
 */

import java.nio.*;
import sun.misc.*;

public class GetBytes {

    public static void main(String args[]) throws Exception {

        ByteBuffer bb = ByteBuffer.wrap(new byte [26 + 2]);

        for (int i = 'a'; i < 'a' + bb.capacity(); i++) {
            bb.put((byte)i);
        }

        /*
         * Slice a subbuffer out of the original buffer.
         */
        bb.position(1);
        bb.limit(bb.capacity() - 1);

        ByteBuffer src = bb.slice();

        CharacterEncoder e = new BASE64Encoder();
        CharacterDecoder d = new BASE64Decoder();

        String encoded = e.encodeBuffer(src);
        ByteBuffer dst = d.decodeBufferToByteBuffer(encoded);

        src.rewind();
        dst.rewind();

        if (src.compareTo(dst) != 0) {
            throw new Exception("Didn't encode/decode correctly");
        }
    }
}
