/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.impl.orbutil.ORBConstants;

public class CDRInputStream_1_2 extends CDRInputStream_1_1
{
    // Indicates whether the header is padded. In GIOP 1.2 and above,
    // the body must be aligned on an 8-octet boundary, and so the header is
    // padded appropriately. However, if there is no body to a request or reply
    // message, there is no header padding, in the unfragmented case.
    protected boolean headerPadding;

    // used to remember headerPadding flag when mark() and restore() are used.
    protected boolean restoreHeaderPadding;

    // Called by RequestMessage_1_2 or ReplyMessage_1_2 classes only.
    void setHeaderPadding(boolean headerPadding) {
        this.headerPadding = headerPadding;
    }

    // the mark and reset methods have been overridden to remember the
    // headerPadding flag.

    public void mark(int readlimit) {
        super.mark(readlimit);
        restoreHeaderPadding = headerPadding;
    }

    public void reset() {
        super.reset();
        headerPadding = restoreHeaderPadding;
        restoreHeaderPadding = false;
    }

    // Template method
    // This method has been overriden to ensure that the duplicated stream
    // inherits the headerPadding flag, in case of GIOP 1.2 and above, streams.
    public CDRInputStreamBase dup() {
        CDRInputStreamBase result = super.dup();
        ((CDRInputStream_1_2)result).headerPadding = this.headerPadding;
        return result;
    }

    protected void alignAndCheck(int align, int n) {

        // headerPadding bit is set by read method of the RequestMessage_1_2
        // or ReplyMessage_1_2 classes. When set, the very first body read
        // operation (from the stub code) would trigger an alignAndCheck
        // method call, that would in turn skip the header padding that was
        // inserted during the earlier write operation by the sender. The
        // padding ensures that the body is aligned on an 8-octet boundary,
        // for GIOP versions 1.2 and beyond.
        if (headerPadding == true) {
            headerPadding = false;
            alignOnBoundary(ORBConstants.GIOP_12_MSG_BODY_ALIGNMENT);
        }

        checkBlockLength(align, n);

        // WARNING: Must compute real alignment after calling
        // checkBlockLength since it may move the position

        // In GIOP 1.2, a fragment may end with some alignment
        // padding (which leads to all fragments ending perfectly
        // on evenly divisible 8 byte boundaries).  A new fragment
        // never requires alignment with the header since it ends
        // on an 8 byte boundary.

        int alignIncr = computeAlignment(bbwi.position(),align);
        bbwi.position(bbwi.position() + alignIncr);

        if (bbwi.position() + n > bbwi.buflen) {
            grow(1, n);
        }
    }

    public GIOPVersion getGIOPVersion() {
        return GIOPVersion.V1_2;
    }

    public char read_wchar() {
        // In GIOP 1.2, a wchar is encoded as an unsigned octet length
        // followed by the octets of the converted wchar.
        int numBytes = read_octet();

        char[] result = getConvertedChars(numBytes, getWCharConverter());

        // Did the provided bytes convert to more than one
        // character?  This may come up as more unicode values are
        // assigned, and a single 16 bit Java char isn't enough.
        // Better to use strings for i18n purposes.
        if (getWCharConverter().getNumChars() > 1)
            throw wrapper.btcResultMoreThanOneChar() ;

        return result[0];
    }

    public String read_wstring() {
        // In GIOP 1.2, wstrings are not terminated by a null.  The
        // length is the number of octets in the converted format.
        // A zero length string is represented with the 4 byte length
        // value of 0.

        int len = read_long();

        //
        // IMPORTANT: Do not replace 'new String("")' with "", it may result
        // in a Serialization bug (See serialization.zerolengthstring) and
        // bug id: 4728756 for details
        if (len == 0)
            return new String("");

        checkForNegativeLength(len);

        return new String(getConvertedChars(len, getWCharConverter()),
                          0,
                          getWCharConverter().getNumChars());
    }
}
