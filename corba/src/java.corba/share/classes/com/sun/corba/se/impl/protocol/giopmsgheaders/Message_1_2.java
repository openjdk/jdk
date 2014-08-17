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
package com.sun.corba.se.impl.protocol.giopmsgheaders;

import java.nio.ByteBuffer;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

public class Message_1_2 extends Message_1_1
{
    protected int request_id = (int) 0;

    Message_1_2() {}

    Message_1_2(int _magic, GIOPVersion _GIOP_version, byte _flags,
            byte _message_type, int _message_size) {

        super(_magic,
              _GIOP_version,
              _flags,
              _message_type,
              _message_size);
    }

    /**
     * The byteBuffer is presumed to have contents of the message already
     * read in.  It must have 12 bytes of space at the beginning for the GIOP header,
     * but the header doesn't have to be copied in.
     */
    public void unmarshalRequestID(ByteBuffer byteBuffer) {
        int b1, b2, b3, b4;

        if (!isLittleEndian()) {
            b1 = (byteBuffer.get(GIOPMessageHeaderLength+0) << 24) & 0xFF000000;
            b2 = (byteBuffer.get(GIOPMessageHeaderLength+1) << 16) & 0x00FF0000;
            b3 = (byteBuffer.get(GIOPMessageHeaderLength+2) << 8)  & 0x0000FF00;
            b4 = (byteBuffer.get(GIOPMessageHeaderLength+3) << 0)  & 0x000000FF;
        } else {
            b1 = (byteBuffer.get(GIOPMessageHeaderLength+3) << 24) & 0xFF000000;
            b2 = (byteBuffer.get(GIOPMessageHeaderLength+2) << 16) & 0x00FF0000;
            b3 = (byteBuffer.get(GIOPMessageHeaderLength+1) << 8)  & 0x0000FF00;
            b4 = (byteBuffer.get(GIOPMessageHeaderLength+0) << 0)  & 0x000000FF;
        }

        this.request_id = (b1 | b2 | b3 | b4);
    }

    public void write(org.omg.CORBA.portable.OutputStream ostream) {
        if (this.encodingVersion == Message.CDR_ENC_VERSION) {
            super.write(ostream);
            return;
        }
        GIOPVersion gv = this.GIOP_version; // save
        this.GIOP_version = GIOPVersion.getInstance((byte)13,
                                                    this.encodingVersion);
        super.write(ostream);
        this.GIOP_version = gv; // restore
    }
}
