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

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

/**
 * This implements the GIOP 1.2 Fragment header.
 *
 * @author Ram Jeyaraman 05/14/2000
 */

public final class FragmentMessage_1_2 extends Message_1_2
        implements FragmentMessage {

    // Constructors

    FragmentMessage_1_2() {}

    // This is currently never called.
    FragmentMessage_1_2(int _request_id) {
        super(Message.GIOPBigMagic, GIOPVersion.V1_2, FLAG_NO_FRAG_BIG_ENDIAN,
            Message.GIOPFragment, 0);
        this.message_type = GIOPFragment;
        request_id = _request_id;
    }

    FragmentMessage_1_2(Message_1_1 msg12) {
        this.magic = msg12.magic;
        this.GIOP_version = msg12.GIOP_version;
        this.flags = msg12.flags;
        this.message_type = GIOPFragment;
        this.message_size = 0;

        switch (msg12.message_type) {
        case GIOPRequest :
            this.request_id = ((RequestMessage) msg12).getRequestId();
            break;
        case GIOPReply :
            this.request_id = ((ReplyMessage) msg12).getRequestId();
            break;
        case GIOPLocateRequest :
            this.request_id = ((LocateRequestMessage) msg12).getRequestId();
            break;
        case GIOPLocateReply :
            this.request_id = ((LocateReplyMessage) msg12).getRequestId();
            break;
        case GIOPFragment :
            this.request_id = ((FragmentMessage) msg12).getRequestId();
            break;
        }
    }

    // Accessor methods

    public int getRequestId() {
        return this.request_id;
    }

    public int getHeaderLength() {
        return GIOPMessageHeaderLength + 4;
    }

    // IO methods

    /* This will never be called, since we do not currently read the
     * request_id from an CDRInputStream. Instead we use the
     * readGIOP_1_2_requestId to read the requestId from a byte buffer.
     */
    public void read(org.omg.CORBA.portable.InputStream istream) {
        super.read(istream);
        this.request_id = istream.read_ulong();
    }

    public void write(org.omg.CORBA.portable.OutputStream ostream) {
        super.write(ostream);
        ostream.write_ulong(this.request_id);
    }

    public void callback(MessageHandler handler)
        throws java.io.IOException
    {
        handler.handleInput(this);
    }
} // class FragmentMessage_1_2
