/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.io;

import sun.nio.cs.ext.Big5_HKSCS;
import sun.nio.cs.ext.HKSCS;
import static sun.nio.cs.CharsetMapping.*;

public class ByteToCharBig5_HKSCS extends ByteToCharDBCS_ASCII {

    protected static HKSCS.Decoder dec =
        (HKSCS.Decoder)new Big5_HKSCS().newDecoder();


    public String getCharacterEncoding() {
        return "Big5_HKSCS";
    }

    public ByteToCharBig5_HKSCS() {
        super(dec);
    }

    protected char decodeDouble(int byte1, int byte2) {
        char c = dec.decodeDouble(byte1, byte2);
        if (c == UNMAPPABLE_DECODING)
            c = dec.decodeBig5(byte1, byte2);
        return c;
    }
}
