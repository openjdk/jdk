/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package sun.io;

import sun.nio.cs.ext.HKSCS;
import sun.nio.cs.ext.MS950_HKSCS;
import static sun.nio.cs.CharsetMapping.*;

public class ByteToCharMS950_HKSCS extends ByteToCharDBCS_ASCII {

    private static HKSCS.Decoder dec =
        (HKSCS.Decoder)new MS950_HKSCS().newDecoder();

    public String getCharacterEncoding() {
        return "MS950_HKSCS";
    }

    public ByteToCharMS950_HKSCS() {
        super(dec);
    }

    protected char decodeDouble(int byte1, int byte2) {
        char c = dec.decodeDouble(byte1, byte2);
        if (c == UNMAPPABLE_DECODING)
            c = dec.decodeBig5(byte1, byte2);
        return c;
    }
}
