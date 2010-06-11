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

import java.nio.ByteBuffer;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.CompletionStatus;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

/*
 * This implements the GIOP 1.0 Message header.
 *
 * @author Ram Jeyaraman 05/14/2000
 */

public class Message_1_0
        extends com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase {

    private static ORBUtilSystemException wrapper =
        ORBUtilSystemException.get( CORBALogDomains.RPC_PROTOCOL ) ;

    // Instance variables
    int magic = (int) 0;
    GIOPVersion GIOP_version = null;
    boolean byte_order = false;
    byte message_type = (byte) 0;
    int message_size = (int) 0;

    // Constructor

    Message_1_0() {
    }

    Message_1_0(int _magic, boolean _byte_order, byte _message_type,
            int _message_size) {
        magic = _magic;
        GIOP_version = GIOPVersion.V1_0;
        byte_order = _byte_order;
        message_type = _message_type;
        message_size = _message_size;
    }

    // Accessor methods

    public GIOPVersion getGIOPVersion() {
        return this.GIOP_version;
    }

    public int getType() {
        return this.message_type;
    }

    public int getSize() {
            return this.message_size;
    }

    public boolean isLittleEndian() {
        return this.byte_order;
    }

    public boolean moreFragmentsToFollow() {
        return false;
    }

    // Mutator methods

    public void setSize(ByteBuffer byteBuffer, int size) {
            this.message_size = size;

        //
        // Patch the size field in the header.
        //
            int patch = size - GIOPMessageHeaderLength;
        if (!isLittleEndian()) {
            byteBuffer.put(8,  (byte)((patch >>> 24) & 0xFF));
            byteBuffer.put(9,  (byte)((patch >>> 16) & 0xFF));
            byteBuffer.put(10, (byte)((patch >>> 8)  & 0xFF));
            byteBuffer.put(11, (byte)((patch >>> 0)  & 0xFF));
        } else {
            byteBuffer.put(8,  (byte)((patch >>> 0)  & 0xFF));
            byteBuffer.put(9,  (byte)((patch >>> 8)  & 0xFF));
            byteBuffer.put(10, (byte)((patch >>> 16) & 0xFF));
            byteBuffer.put(11, (byte)((patch >>> 24) & 0xFF));
        }
    }

    public FragmentMessage createFragmentMessage() {
        throw wrapper.fragmentationDisallowed(
            CompletionStatus.COMPLETED_MAYBE);
    }

    // IO methods

    // This should do nothing even if it is called. The Message Header already
    // is read off java.io.InputStream (not a CDRInputStream) by IIOPConnection
    // in order to choose the correct CDR Version, msg_type, and msg_size.
    // So, we would never need to read the Message Header off a CDRInputStream.
    public void read(org.omg.CORBA.portable.InputStream istream) {
        /*
        this.magic = istream.read_long();
        this.GIOP_version = (new GIOPVersion()).read(istream);
        this.byte_order = istream.read_boolean();
        this.message_type = istream.read_octet();
        this.message_size = istream.read_ulong();
        */
    }

    public void write(org.omg.CORBA.portable.OutputStream ostream) {
        ostream.write_long(this.magic);
        nullCheck(this.GIOP_version);
        this.GIOP_version.write(ostream);
        ostream.write_boolean(this.byte_order);
        ostream.write_octet(this.message_type);
        ostream.write_ulong(this.message_size);
    }

} // class Message_1_0
