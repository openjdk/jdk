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

import org.omg.CORBA.Principal;
import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.ObjectKey;
import com.sun.corba.se.impl.encoding.CDRInputStream;
import com.sun.corba.se.impl.encoding.CDROutputStream;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.encoding.CDRInputStream_1_2;
import com.sun.corba.se.impl.encoding.CDROutputStream_1_2;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

/**
 * This implements the GIOP 1.2 Request header.
 *
 * @author Ram Jeyaraman 05/14/2000
 */

public final class RequestMessage_1_2 extends Message_1_2
        implements RequestMessage {

    // Instance variables

    private ORB orb = null;
    private ORBUtilSystemException wrapper = null ;
    private byte response_flags = (byte) 0;
    private byte reserved[] = null;
    private TargetAddress target = null;
    private String operation = null;
    private ServiceContexts service_contexts = null;
    private ObjectKey objectKey = null;

    // Constructors

    RequestMessage_1_2(ORB orb) {
        this.orb = orb;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
    }

    RequestMessage_1_2(ORB orb, int _request_id, byte _response_flags,
            byte[] _reserved, TargetAddress _target,
            String _operation, ServiceContexts _service_contexts) {
        super(Message.GIOPBigMagic, GIOPVersion.V1_2, FLAG_NO_FRAG_BIG_ENDIAN,
            Message.GIOPRequest, 0);
        this.orb = orb;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        request_id = _request_id;
        response_flags = _response_flags;
        reserved = _reserved;
        target = _target;
        operation = _operation;
        service_contexts = _service_contexts;
    }

    // Accessor methods (RequestMessage interface)

    public int getRequestId() {
        return this.request_id;
    }

    public boolean isResponseExpected() {
        /*
        case 1: LSBit[1] == 1
            not a oneway call (DII flag INV_NO_RESPONSE is false)  // Ox03
            LSBit[0] must be 1.
        case 2: LSBit[1] == 0
            if (LSB[0] == 0) // Ox00
                oneway call
            else if (LSB[0] == 1) // 0x01
                oneway call; but server may provide
                a location forward response or system exception response.
        */

        if ( (this.response_flags & RESPONSE_EXPECTED_BIT) == RESPONSE_EXPECTED_BIT ) {
            return true;
        }

        return false;
    }

    public byte[] getReserved() {
        return this.reserved;
    }

    public ObjectKey getObjectKey() {
        if (this.objectKey == null) {
            // this will raise a MARSHAL exception upon errors.
            this.objectKey = MessageBase.extractObjectKey(target, orb);
        }

        return this.objectKey;
    }

    public String getOperation() {
        return this.operation;
    }

    public Principal getPrincipal() {
        // REVISIT Should we throw an exception or return null ?
        return null;
    }

    public ServiceContexts getServiceContexts() {
        return this.service_contexts;
    }

    // IO methods

    public void read(org.omg.CORBA.portable.InputStream istream) {
        super.read(istream);
        this.request_id = istream.read_ulong();
        this.response_flags = istream.read_octet();
        this.reserved = new byte[3];
        for (int _o0 = 0;_o0 < (3); ++_o0) {
            this.reserved[_o0] = istream.read_octet();
        }
        this.target = TargetAddressHelper.read(istream);
        getObjectKey(); // this does AddressingDisposition check
        this.operation = istream.read_string();
        this.service_contexts
            = new ServiceContexts((org.omg.CORBA_2_3.portable.InputStream) istream);

        // CORBA formal 00-11-0 15.4.2.2 GIOP 1.2 body must be
        // aligned on an 8 octet boundary.
        // Ensures that the first read operation called from the stub code,
        // during body deconstruction, would skip the header padding, that was
        // inserted to ensure that the body was aligned on an 8-octet boundary.
        ((CDRInputStream)istream).setHeaderPadding(true);

    }

    public void write(org.omg.CORBA.portable.OutputStream ostream) {
        super.write(ostream);
        ostream.write_ulong(this.request_id);
        ostream.write_octet(this.response_flags);
        nullCheck(this.reserved);
        if (this.reserved.length != (3)) {
            throw wrapper.badReservedLength(
                org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);
        }
        for (int _i0 = 0;_i0 < (3); ++_i0) {
            ostream.write_octet(this.reserved[_i0]);
        }
        nullCheck(this.target);
        TargetAddressHelper.write(ostream, this.target);
        ostream.write_string(this.operation);
        if (this.service_contexts != null) {
                service_contexts.write(
                (org.omg.CORBA_2_3.portable.OutputStream) ostream,
                GIOPVersion.V1_2);
            } else {
                ServiceContexts.writeNullServiceContext(
                (org.omg.CORBA_2_3.portable.OutputStream) ostream);
        }

        // CORBA formal 00-11-0 15.4.2.2 GIOP 1.2 body must be
        // aligned on an 8 octet boundary.
        // Ensures that the first write operation called from the stub code,
        // during body construction, would insert a header padding, such that
        // the body is aligned on an 8-octet boundary.
        ((CDROutputStream)ostream).setHeaderPadding(true);
    }

    public void callback(MessageHandler handler)
        throws java.io.IOException
    {
        handler.handleInput(this);
    }
} // class RequestMessage_1_2
