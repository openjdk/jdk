/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.impl.encoding;

import org.omg.CORBA.CompletionStatus;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.impl.encoding.CodeSetConversion;

public class CDROutputStream_1_1 extends CDROutputStream_1_0
{
    // This is used to keep indirections working across fragments.  When added
    // to the current bbwi.position(), the result is the current position
    // in the byte stream without any fragment headers.
    //
    // It is equal to the following:
    //
    // n = number of buffers (0 is original buffer, 1 is first fragment, etc)
    //
    // n == 0, fragmentOffset = 0
    //
    // n > 0, fragmentOffset
    //          = sum i=[1,n] { bbwi_i-1_.size - buffer i header length }
    //
    protected int fragmentOffset = 0;

    protected void alignAndReserve(int align, int n) {

        // Notice that in 1.1, we won't end a fragment with
        // alignment padding.  We also won't guarantee that
        // our fragments end on evenly divisible 8 byte
        // boundaries.  There may be alignment
        // necessary with the header of the next fragment
        // since the header isn't aligned on an 8 byte
        // boundary, so we have to calculate it twice.

        int alignment = computeAlignment(align);

        if (bbwi.position() + n + alignment > bbwi.buflen) {
            grow(align, n);

            // Must recompute the alignment after a grow.
            // In the case of fragmentation, the alignment
            // calculation may no longer be correct.

            // People shouldn't be able to set their fragment
            // sizes so small that the fragment header plus
            // this alignment fills the entire buffer.
            alignment = computeAlignment(align);
        }

        bbwi.position(bbwi.position() + alignment);
    }

    protected void grow(int align, int n) {
        // Save the current size for possible post-fragmentation calculation
        int oldSize = bbwi.position();

        super.grow(align, n);

        // At this point, if we fragmented, we should have a ByteBufferWithInfo
        // with the fragment header already marshalled.  The size and length fields
        // should be updated accordingly, and the fragmented flag should be set.
        if (bbwi.fragmented) {

            // Clear the flag
            bbwi.fragmented = false;

            // Update fragmentOffset so indirections work properly.
            // At this point, oldSize is the entire length of the
            // previous buffer.  bbwi.position() is the length of the
            // fragment header of this buffer.
            fragmentOffset += (oldSize - bbwi.position());
        }
    }

    public int get_offset() {
        return bbwi.position() + fragmentOffset;
    }

    public GIOPVersion getGIOPVersion() {
        return GIOPVersion.V1_1;
    }

    public void write_wchar(char x)
    {
        // In GIOP 1.1, interoperability with wchar is limited
        // to 2 byte fixed width encodings.  CORBA formal 99-10-07 15.3.1.6.
        // Note that the following code prohibits UTF-16 with a byte
        // order marker (which would result in 4 bytes).
        CodeSetConversion.CTBConverter converter = getWCharConverter();

        converter.convert(x);

        if (converter.getNumBytes() != 2)
            throw wrapper.badGiop11Ctb(CompletionStatus.COMPLETED_MAYBE);

        alignAndReserve(converter.getAlignment(),
                        converter.getNumBytes());

        parent.write_octet_array(converter.getBytes(),
                                 0,
                                 converter.getNumBytes());
    }

    public void write_wstring(String value)
    {
        if (value == null) {
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);
        }

        // The length is the number of code points (which are 2 bytes each)
        // including the 2 byte null.  See CORBA formal 99-10-07 15.3.2.7.

        int len = value.length() + 1;

        write_long(len);

        CodeSetConversion.CTBConverter converter = getWCharConverter();

        converter.convert(value);

        internalWriteOctetArray(converter.getBytes(), 0, converter.getNumBytes());

        // Write the 2 byte null ending
        write_short((short)0);
    }
}
