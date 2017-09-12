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

import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.CompletionStatus;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.IORFactories ;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.impl.encoding.CDRInputStream;
import com.sun.corba.se.impl.encoding.CDROutputStream;
import com.sun.corba.se.impl.encoding.CDRInputStream_1_2;
import com.sun.corba.se.impl.encoding.CDROutputStream_1_2;

import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.orbutil.ORBConstants;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

/**
 * This implements the GIOP 1.2 Reply header.
 *
 * @author Ram Jeyaraman 05/14/2000
 */

public final class ReplyMessage_1_2 extends Message_1_2
        implements ReplyMessage {

    // Instance variables

    private ORB orb = null;
    private ORBUtilSystemException wrapper = null ;
    private int reply_status = (int) 0;
    private ServiceContexts service_contexts = null;
    private IOR ior = null;
    private String exClassName = null;
    private int minorCode = (int) 0;
    private CompletionStatus completionStatus = null;
    private short addrDisposition = KeyAddr.value; // default;

    // Constructors

    ReplyMessage_1_2(ORB orb) {
        this.orb = orb;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
    }

    ReplyMessage_1_2(ORB orb, int _request_id, int _reply_status,
            ServiceContexts _service_contexts, IOR _ior) {
        super(Message.GIOPBigMagic, GIOPVersion.V1_2, FLAG_NO_FRAG_BIG_ENDIAN,
            Message.GIOPReply, 0);
        this.orb = orb;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        request_id = _request_id;
        reply_status = _reply_status;
        service_contexts = _service_contexts;
        ior = _ior;
    }

    // Accessor methods

    public int getRequestId() {
        return this.request_id;
    }

    public int getReplyStatus() {
        return this.reply_status;
    }

    public short getAddrDisposition() {
        return this.addrDisposition;
    }

    public ServiceContexts getServiceContexts() {
        return this.service_contexts;
    }

    public void setServiceContexts( ServiceContexts sc ) {
        this.service_contexts = sc;
    }

    public SystemException getSystemException(String message) {
        return MessageBase.getSystemException(
            exClassName, minorCode, completionStatus, message, wrapper);
    }

    public IOR getIOR() {
        return this.ior;
    }

    public void setIOR( IOR ior ) {
        this.ior = ior;
    }

    // IO methods

    public void read(org.omg.CORBA.portable.InputStream istream) {
        super.read(istream);
        this.request_id = istream.read_ulong();
        this.reply_status = istream.read_long();
        isValidReplyStatus(this.reply_status); // raises exception on error
        this.service_contexts
            = new ServiceContexts((org.omg.CORBA_2_3.portable.InputStream) istream);

        // CORBA formal 00-11-0 15.4.2.2 GIOP 1.2 body must be
        // aligned on an 8 octet boundary.
        // Ensures that the first read operation called from the stub code,
        // during body deconstruction, would skip the header padding, that was
        // inserted to ensure that the body was aligned on an 8-octet boundary.
        ((CDRInputStream)istream).setHeaderPadding(true);

        // The code below reads the reply body in some cases
        // SYSTEM_EXCEPTION & LOCATION_FORWARD & LOCATION_FORWARD_PERM &
        // NEEDS_ADDRESSING_MODE
        if (this.reply_status == SYSTEM_EXCEPTION) {

            String reposId = istream.read_string();
            this.exClassName = ORBUtility.classNameOf(reposId);
            this.minorCode = istream.read_long();
            int status = istream.read_long();

            switch (status) {
            case CompletionStatus._COMPLETED_YES:
                this.completionStatus = CompletionStatus.COMPLETED_YES;
                break;
            case CompletionStatus._COMPLETED_NO:
                this.completionStatus = CompletionStatus.COMPLETED_NO;
                break;
            case CompletionStatus._COMPLETED_MAYBE:
                this.completionStatus = CompletionStatus.COMPLETED_MAYBE;
                break;
            default:
                throw wrapper.badCompletionStatusInReply(
                    CompletionStatus.COMPLETED_MAYBE, new Integer(status) );
            }

        } else if (this.reply_status == USER_EXCEPTION) {
            // do nothing. The client stub will read the exception from body.
        } else if ( (this.reply_status == LOCATION_FORWARD) ||
                (this.reply_status == LOCATION_FORWARD_PERM) ){
            CDRInputStream cdr = (CDRInputStream) istream;
            this.ior = IORFactories.makeIOR( cdr ) ;
        }  else if (this.reply_status == NEEDS_ADDRESSING_MODE) {
            // read GIOP::AddressingDisposition from body and resend the
            // original request using the requested addressing mode. The
            // resending is transparent to the client program.
            this.addrDisposition = AddressingDispositionHelper.read(istream);
        }
    }

    // Note, this writes only the header information. SystemException or
    // IOR or GIOP::AddressingDisposition may be written afterwards into the
    // reply mesg body.
    public void write(org.omg.CORBA.portable.OutputStream ostream) {
        super.write(ostream);
        ostream.write_ulong(this.request_id);
        ostream.write_long(this.reply_status);
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

    // Static methods

    public static void isValidReplyStatus(int replyStatus) {
        switch (replyStatus) {
        case NO_EXCEPTION :
        case USER_EXCEPTION :
        case SYSTEM_EXCEPTION :
        case LOCATION_FORWARD :
        case LOCATION_FORWARD_PERM :
        case NEEDS_ADDRESSING_MODE :
            break;
        default :
            ORBUtilSystemException localWrapper = ORBUtilSystemException.get(
                CORBALogDomains.RPC_PROTOCOL ) ;
            throw localWrapper.illegalReplyStatus( CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public void callback(MessageHandler handler)
        throws java.io.IOException
    {
        handler.handleInput(this);
    }
} // class ReplyMessage_1_2
