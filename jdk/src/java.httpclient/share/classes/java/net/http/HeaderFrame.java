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
 */

package java.net.http;

import java.nio.ByteBuffer;

/**
 * Either a HeadersFrame or a ContinuationFrame
 */
abstract class HeaderFrame extends Http2Frame {

    int offset;
    int number;
    int headerLength;
    ByteBuffer[] headerBlocks;

    public static final int END_HEADERS = 0x4;

    @Override
    String flagAsString(int flag) {
        switch (flag) {
        case END_HEADERS:
            return "END_HEADERS";
        }
        return super.flagAsString(flag);
    }

    /**
     * Sets the array of hpack encoded ByteBuffers
     */
    public void setHeaderBlock(ByteBuffer bufs[], int offset, int number) {
        this.headerBlocks = bufs;
        this.offset = offset;
        this.number = number;
        int length = 0;
        for (int i=offset; i<offset+number; i++) {
            length += headerBlocks[i].remaining();
        }
        this.headerLength = length;
    }

    public void setHeaderBlock(ByteBuffer bufs[]) {
        setHeaderBlock(bufs, 0, bufs.length);
    }

    public ByteBuffer[] getHeaderBlock() {
        return headerBlocks;
    }

    /**
     * Returns true if this block is the final block of headers
     */
    public abstract boolean endHeaders();

}
