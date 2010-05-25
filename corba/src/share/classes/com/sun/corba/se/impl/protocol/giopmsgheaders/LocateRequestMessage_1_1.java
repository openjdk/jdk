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

package com.sun.corba.se.impl.protocol.giopmsgheaders;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.ior.ObjectKey;

/**
 * This implements the GIOP 1.1 LocateRequest header.
 *
 * @author Ram Jeyaraman 05/14/2000
 */

public final class LocateRequestMessage_1_1 extends Message_1_1
        implements LocateRequestMessage {

    // Instance variables

    private ORB orb = null;
    private int request_id = (int) 0;
    private byte[] object_key = null;
    private ObjectKey objectKey = null;

    // Constructors

    LocateRequestMessage_1_1(ORB orb) {
        this.orb = orb;
    }

    LocateRequestMessage_1_1(ORB orb, int _request_id, byte[] _object_key) {
        super(Message.GIOPBigMagic, GIOPVersion.V1_1, FLAG_NO_FRAG_BIG_ENDIAN,
            Message.GIOPLocateRequest, 0);
        this.orb = orb;
        request_id = _request_id;
        object_key = _object_key;
    }

    // Accessor methods (LocateRequestMessage interface)

    public int getRequestId() {
        return this.request_id;
    }

    public ObjectKey getObjectKey() {
        if (this.objectKey == null) {
            // this will raise a MARSHAL exception upon errors.
            this.objectKey = MessageBase.extractObjectKey(object_key, orb);
        }

        return this.objectKey;
    }

    // IO methods

    public void read(org.omg.CORBA.portable.InputStream istream) {
        super.read(istream);
        this.request_id = istream.read_ulong();
        int _len1 = istream.read_long();
        this.object_key = new byte[_len1];
        istream.read_octet_array(this.object_key, 0, _len1);
    }

    public void write(org.omg.CORBA.portable.OutputStream ostream) {
        super.write(ostream);
        ostream.write_ulong(this.request_id);
        nullCheck(this.object_key);
        ostream.write_long(this.object_key.length);
        ostream.write_octet_array(this.object_key, 0, this.object_key.length);
    }

    public void callback(MessageHandler handler)
        throws java.io.IOException
    {
        handler.handleInput(this);
    }
} // class LocateRequestMessage_1_1
