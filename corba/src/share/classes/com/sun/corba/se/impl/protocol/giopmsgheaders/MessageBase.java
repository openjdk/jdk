/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.Class;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.Iterator;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.MARSHAL;
import org.omg.CORBA.Principal;
import org.omg.CORBA.SystemException;
import org.omg.IOP.TaggedProfile;

import com.sun.corba.se.pept.transport.ByteBufferPool;

import com.sun.corba.se.spi.ior.ObjectKey;
import com.sun.corba.se.spi.ior.ObjectId;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.ObjectKeyFactory;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile;
import com.sun.corba.se.spi.ior.iiop.IIOPFactories;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate ;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.ior.iiop.RequestPartitioningComponent;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.transport.ReadTimeouts;

import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.encoding.CDRInputStream_1_0;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.protocol.AddressingDispositionException;

import sun.corba.SharedSecrets;

/**
 * This class acts as the base class for the various GIOP message types. This
 * also serves as a factory to create various message types. We currently
 * support GIOP 1.0, 1.1 and 1.2 message types.
 *
 * @author Ram Jeyaraman 05/14/2000
 */

public abstract class MessageBase implements Message{

    // This is only used when the giopDebug flag is
    // turned on.
    public byte[] giopHeader;
    private ByteBuffer byteBuffer;
    private int threadPoolToUse;

    // (encodingVersion == 0x00) implies CDR encoding,
    // (encodingVersion >  0x00) implies Java serialization version.
    byte encodingVersion = (byte) Message.CDR_ENC_VERSION;

    private static ORBUtilSystemException wrapper =
        ORBUtilSystemException.get( CORBALogDomains.RPC_PROTOCOL ) ;

    // Static methods

    public static String typeToString(int type)
    {
        return typeToString((byte)type);
    }

    public static String typeToString(byte type)
    {
        String result = type + "/";
        switch (type) {
        case GIOPRequest         : result += "GIOPRequest";         break;
        case GIOPReply           : result += "GIOPReply";           break;
        case GIOPCancelRequest   : result += "GIOPCancelRequest";   break;
        case GIOPLocateRequest   : result += "GIOPLocateRequest";   break;
        case GIOPLocateReply     : result += "GIOPLocateReply";     break;
        case GIOPCloseConnection : result += "GIOPCloseConnection"; break;
        case GIOPMessageError    : result += "GIOPMessageError";    break;
        case GIOPFragment        : result += "GIOPFragment";        break;
        default                  : result += "Unknown";             break;
        }
        return result;
    }

    public static MessageBase readGIOPMessage(ORB orb, CorbaConnection connection)
    {
        MessageBase msg = readGIOPHeader(orb, connection);
        msg = (MessageBase)readGIOPBody(orb, connection, (Message)msg);
        return msg;
    }

    public static MessageBase readGIOPHeader(ORB orb, CorbaConnection connection)
    {
        MessageBase msg = null;
        ReadTimeouts readTimeouts =
                           orb.getORBData().getTransportTCPReadTimeouts();

        ByteBuffer buf = null;

        try {
            buf = connection.read(GIOPMessageHeaderLength,
                          0, GIOPMessageHeaderLength,
                          readTimeouts.get_max_giop_header_time_to_wait());
        } catch (IOException e) {
            throw wrapper.ioexceptionWhenReadingConnection(e);
        }

        if (orb.giopDebugFlag) {
            // Since this is executed in debug mode only the overhead of
            // using a View Buffer is not an issue. We'll also use a
            // read-only View Buffer so we don't disturb the state of
            // byteBuffer.
            dprint(".readGIOPHeader: " + typeToString(buf.get(7)));
            dprint(".readGIOPHeader: GIOP header is: ");
            ByteBuffer viewBuffer = buf.asReadOnlyBuffer();
            viewBuffer.position(0).limit(GIOPMessageHeaderLength);
            ByteBufferWithInfo bbwi = new ByteBufferWithInfo(orb,viewBuffer);
            bbwi.buflen = GIOPMessageHeaderLength;
            CDRInputStream_1_0.printBuffer(bbwi);
        }

        // Sanity checks

        /*
         * check for magic corruption
         * check for version incompatibility
         * check if fragmentation is allowed based on mesg type.
            . 1.0 fragmentation disallowed; FragmentMessage is non-existent.
            . 1.1 only {Request, Reply} msgs maybe fragmented.
            . 1.2 only {Request, Reply, LocateRequest, LocateReply} msgs
              maybe fragmented.
        */

        int b1, b2, b3, b4;

        b1 = (buf.get(0) << 24) & 0xFF000000;
        b2 = (buf.get(1) << 16) & 0x00FF0000;
        b3 = (buf.get(2) << 8)  & 0x0000FF00;
        b4 = (buf.get(3) << 0)  & 0x000000FF;
        int magic = (b1 | b2 | b3 | b4);

        if (magic != GIOPBigMagic) {
            // If Magic is incorrect, it is an error.
            // ACTION : send MessageError and close the connection.
            throw wrapper.giopMagicError( CompletionStatus.COMPLETED_MAYBE);
        }

        // Extract the encoding version from the request GIOP Version,
        // if it contains an encoding, and set GIOP version appropriately.
        // For Java serialization, we use GIOP Version 1.2 message format.
        byte requestEncodingVersion = Message.CDR_ENC_VERSION;
        if ((buf.get(4) == 0x0D) &&
            (buf.get(5) <= Message.JAVA_ENC_VERSION) &&
            (buf.get(5) > Message.CDR_ENC_VERSION) &&
            orb.getORBData().isJavaSerializationEnabled()) {
            // Entering this block means the request is using Java encoding,
            // and the encoding version is <= this ORB's Java encoding version.
            requestEncodingVersion = buf.get(5);
            buf.put(4, (byte) 0x01);
            buf.put(5, (byte) 0x02);
        }

        GIOPVersion orbVersion = orb.getORBData().getGIOPVersion();

        if (orb.giopDebugFlag) {
            dprint(".readGIOPHeader: Message GIOP version: "
                              + buf.get(4) + '.' + buf.get(5));
            dprint(".readGIOPHeader: ORB Max GIOP Version: "
                              + orbVersion);
        }

        if ( (buf.get(4) > orbVersion.getMajor()) ||
             ( (buf.get(4) == orbVersion.getMajor()) && (buf.get(5) > orbVersion.getMinor()) )
            ) {
            // For requests, sending ORB should use the version info
            // published in the IOR or may choose to use a <= version
            // for requests. If the version is greater than published version,
            // it is an error.

            // For replies, the ORB should always receive a version it supports
            // or less, but never greater (except for MessageError)

            // ACTION : Send back a MessageError() with the the highest version
            // the server ORB supports, and close the connection.
            if ( buf.get(7) != GIOPMessageError ) {
                throw wrapper.giopVersionError( CompletionStatus.COMPLETED_MAYBE);
            }
        }

        AreFragmentsAllowed(buf.get(4), buf.get(5), buf.get(6), buf.get(7));

        // create appropriate messages types

        switch (buf.get(7)) {

        case GIOPRequest:
            if (orb.giopDebugFlag) {
                dprint(".readGIOPHeader: creating RequestMessage");
            }
            //msg = new RequestMessage(orb.giopDebugFlag);
            if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
                msg = new RequestMessage_1_0(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x01) ) { // 1.1
                msg = new RequestMessage_1_1(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x02) ) { // 1.2
                msg = new RequestMessage_1_2(orb);
            } else {
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }
            break;

        case GIOPLocateRequest:
            if (orb.giopDebugFlag) {
                dprint(".readGIOPHeader: creating LocateRequestMessage");
            }
            //msg = new LocateRequestMessage(orb.giopDebugFlag);
            if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
                msg = new LocateRequestMessage_1_0(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x01) ) { // 1.1
                msg = new LocateRequestMessage_1_1(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x02) ) { // 1.2
                msg = new LocateRequestMessage_1_2(orb);
            } else {
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }
            break;

        case GIOPCancelRequest:
            if (orb.giopDebugFlag) {
                dprint(".readGIOPHeader: creating CancelRequestMessage");
            }
            //msg = new CancelRequestMessage(orb.giopDebugFlag);
            if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
                msg = new CancelRequestMessage_1_0();
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x01) ) { // 1.1
                msg = new CancelRequestMessage_1_1();
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x02) ) { // 1.2
                msg = new CancelRequestMessage_1_2();
            } else {
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }
            break;

        case GIOPReply:
            if (orb.giopDebugFlag) {
                dprint(".readGIOPHeader: creating ReplyMessage");
            }
            //msg = new ReplyMessage(orb.giopDebugFlag);
            if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
                msg = new ReplyMessage_1_0(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x01) ) { // 1.1
                msg = new ReplyMessage_1_1(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x02) ) { // 1.2
                msg = new ReplyMessage_1_2(orb);
            } else {
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }
            break;

        case GIOPLocateReply:
            if (orb.giopDebugFlag) {
                dprint(".readGIOPHeader: creating LocateReplyMessage");
            }
            //msg = new LocateReplyMessage(orb.giopDebugFlag);
            if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
                msg = new LocateReplyMessage_1_0(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x01) ) { // 1.1
                msg = new LocateReplyMessage_1_1(orb);
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x02) ) { // 1.2
                msg = new LocateReplyMessage_1_2(orb);
            } else {
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }
            break;

        case GIOPCloseConnection:
        case GIOPMessageError:
            if (orb.giopDebugFlag) {
                dprint(".readGIOPHeader: creating Message for CloseConnection or MessageError");
            }
            // REVISIT a MessageError  may contain the highest version server
            // can support. In such a case, a new request may be made with the
            // correct version or the connection be simply closed. Note the
            // connection may have been closed by the server.
            //msg = new Message(orb.giopDebugFlag);
            if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
                msg = new Message_1_0();
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x01) ) { // 1.1
                msg = new Message_1_1();
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x02) ) { // 1.2
                msg = new Message_1_1();
            } else {
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }
            break;

        case GIOPFragment:
            if (orb.giopDebugFlag) {
                dprint(".readGIOPHeader: creating FragmentMessage");
            }
            //msg = new FragmentMessage(orb.giopDebugFlag);
            if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
                // not possible (error checking done already)
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x01) ) { // 1.1
                msg = new FragmentMessage_1_1();
            } else if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x02) ) { // 1.2
                msg = new FragmentMessage_1_2();
            } else {
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }
            break;

        default:
            if (orb.giopDebugFlag)
                dprint(".readGIOPHeader: UNKNOWN MESSAGE TYPE: "
                       + buf.get(7));
            // unknown message type ?
            // ACTION : send MessageError and close the connection
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }

        //
        // Initialize the generic GIOP header instance variables.
        //

        if ( (buf.get(4) == 0x01) && (buf.get(5) == 0x00) ) { // 1.0
            Message_1_0 msg10 = (Message_1_0) msg;
            msg10.magic = magic;
            msg10.GIOP_version = new GIOPVersion(buf.get(4), buf.get(5));
            msg10.byte_order = (buf.get(6) == LITTLE_ENDIAN_BIT);
            // 'request partitioning' not supported on GIOP version 1.0
            // so just use the default thread pool, 0.
            msg.threadPoolToUse = 0;
            msg10.message_type = buf.get(7);
            msg10.message_size = readSize(buf.get(8), buf.get(9), buf.get(10), buf.get(11),
                                          msg10.isLittleEndian()) +
                                 GIOPMessageHeaderLength;
        } else { // 1.1 & 1.2
            Message_1_1 msg11 = (Message_1_1) msg;
            msg11.magic = magic;
            msg11.GIOP_version = new GIOPVersion(buf.get(4), buf.get(5));
            msg11.flags = (byte)(buf.get(6) & TRAILING_TWO_BIT_BYTE_MASK);
            // IMPORTANT: For 'request partitioning', the thread pool to use
            //            information is stored in the leading 6 bits of byte 6.
            //
            // IMPORTANT: Request partitioning is a PROPRIETARY EXTENSION !!!
            //
            // NOTE: Bitwise operators will promote a byte to an int before
            //       performing a bitwise operation and bytes, ints, longs, etc
            //       are signed types in Java. Thus, the need for the
            //       THREAD_POOL_TO_USE_MASK operation.
            msg.threadPoolToUse = (buf.get(6) >>> 2) & THREAD_POOL_TO_USE_MASK;
            msg11.message_type = buf.get(7);
            msg11.message_size =
                      readSize(buf.get(8), buf.get(9), buf.get(10), buf.get(11),
                              msg11.isLittleEndian()) + GIOPMessageHeaderLength;
        }


        if (orb.giopDebugFlag) {
            // Since this is executed in debug mode only the overhead of
            // using a View Buffer is not an issue. We'll also use a
            // read-only View Buffer so we don't disturb the state of
            // byteBuffer.
            dprint(".readGIOPHeader: header construction complete.");

            // For debugging purposes, save the 12 bytes of the header
            ByteBuffer viewBuf = buf.asReadOnlyBuffer();
            byte[] msgBuf = new byte[GIOPMessageHeaderLength];
            viewBuf.position(0).limit(GIOPMessageHeaderLength);
            viewBuf.get(msgBuf,0,msgBuf.length);
            // REVISIT: is giopHeader still used?
            ((MessageBase)msg).giopHeader = msgBuf;
        }

        msg.setByteBuffer(buf);
        msg.setEncodingVersion(requestEncodingVersion);

        return msg;
    }

    public static Message readGIOPBody(ORB orb,
                                       CorbaConnection connection,
                                       Message msg)
    {
        ReadTimeouts readTimeouts =
                           orb.getORBData().getTransportTCPReadTimeouts();
        ByteBuffer buf = msg.getByteBuffer();

        buf.position(MessageBase.GIOPMessageHeaderLength);
        int msgSizeMinusHeader =
            msg.getSize() - MessageBase.GIOPMessageHeaderLength;
        try {
            buf = connection.read(buf,
                          GIOPMessageHeaderLength, msgSizeMinusHeader,
                          readTimeouts.get_max_time_to_wait());
        } catch (IOException e) {
            throw wrapper.ioexceptionWhenReadingConnection(e);
        }

        msg.setByteBuffer(buf);

        if (orb.giopDebugFlag) {
            dprint(".readGIOPBody: received message:");
            ByteBuffer viewBuffer = buf.asReadOnlyBuffer();
            viewBuffer.position(0).limit(msg.getSize());
            ByteBufferWithInfo bbwi = new ByteBufferWithInfo(orb, viewBuffer);
            CDRInputStream_1_0.printBuffer(bbwi);
        }

        return msg;
    }

    private static RequestMessage createRequest(
            ORB orb, GIOPVersion gv, byte encodingVersion, int request_id,
            boolean response_expected, byte[] object_key, String operation,
            ServiceContexts service_contexts, Principal requesting_principal) {

        if (gv.equals(GIOPVersion.V1_0)) { // 1.0
            return new RequestMessage_1_0(orb, service_contexts, request_id,
                                         response_expected, object_key,
                                         operation, requesting_principal);
        } else if (gv.equals(GIOPVersion.V1_1)) { // 1.1
            return new RequestMessage_1_1(orb, service_contexts, request_id,
                response_expected, new byte[] { 0x00, 0x00, 0x00 },
                object_key, operation, requesting_principal);
        } else if (gv.equals(GIOPVersion.V1_2)) { // 1.2
            // Note: Currently we use response_expected flag to decide if the
            // call is oneway or not. Ideally, it is possible to expect a
            // response on a oneway call too, but we do not support it now.
            byte response_flags = 0x03;
            if (response_expected) {
                response_flags = 0x03;
            } else {
                response_flags = 0x00;
            }
            /*
            // REVISIT The following is the correct way to do it. This gives
            // more flexibility.
            if ((DII::INV_NO_RESPONSE == false) && response_expected) {
                response_flags = 0x03; // regular two-way
            } else if ((DII::INV_NO_RESPONSE == false) && !response_expected) {
                // this condition is not possible
            } else if ((DII::INV_NO_RESPONSE == true) && response_expected) {
                // oneway, but we need response for LocationForwards or
                // SystemExceptions.
                response_flags = 0x01;
            } else if ((DII::INV_NO_RESPONSE == true) && !response_expected) {
                // oneway, no response required
                response_flags = 0x00;
            }
            */
            TargetAddress target = new TargetAddress();
            target.object_key(object_key);
            RequestMessage msg =
                new RequestMessage_1_2(orb, request_id, response_flags,
                                       new byte[] { 0x00, 0x00, 0x00 },
                                       target, operation, service_contexts);
            msg.setEncodingVersion(encodingVersion);
            return msg;
        } else {
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public static RequestMessage createRequest(
            ORB orb, GIOPVersion gv, byte encodingVersion, int request_id,
            boolean response_expected, IOR ior,
            short addrDisp, String operation,
            ServiceContexts service_contexts, Principal requesting_principal) {

        RequestMessage requestMessage = null;
        IIOPProfile profile = ior.getProfile();

        if (addrDisp == KeyAddr.value) {
            // object key will be used for target addressing
            profile = ior.getProfile();
            ObjectKey objKey = profile.getObjectKey();
            byte[] object_key = objKey.getBytes(orb);
            requestMessage =
                   createRequest(orb, gv, encodingVersion, request_id,
                                 response_expected, object_key,
                                 operation, service_contexts,
                                 requesting_principal);
        } else {

            if (!(gv.equals(GIOPVersion.V1_2))) {
                // only object_key based target addressing is allowed for
                // GIOP 1.0 & 1.1
                throw wrapper.giopVersionError(
                    CompletionStatus.COMPLETED_MAYBE);
            }

            // Note: Currently we use response_expected flag to decide if the
            // call is oneway or not. Ideally, it is possible to expect a
            // response on a oneway call too, but we do not support it now.
            byte response_flags = 0x03;
            if (response_expected) {
                response_flags = 0x03;
            } else {
                response_flags = 0x00;
            }

            TargetAddress target = new TargetAddress();
            if (addrDisp == ProfileAddr.value) { // iop profile will be used
                profile = ior.getProfile();
                target.profile(profile.getIOPProfile());
            } else if (addrDisp == ReferenceAddr.value) {  // ior will be used
                IORAddressingInfo iorInfo =
                    new IORAddressingInfo( 0, // profile index
                        ior.getIOPIOR());
                target.ior(iorInfo);
            } else {
                // invalid target addressing disposition value
                throw wrapper.illegalTargetAddressDisposition(
                    CompletionStatus.COMPLETED_NO);
            }

            requestMessage =
                   new RequestMessage_1_2(orb, request_id, response_flags,
                                  new byte[] { 0x00, 0x00, 0x00 }, target,
                                  operation, service_contexts);
            requestMessage.setEncodingVersion(encodingVersion);
        }

        if (gv.supportsIORIIOPProfileComponents()) {
            // add request partitioning thread pool to use info
            int poolToUse = 0; // default pool
            IIOPProfileTemplate temp =
                (IIOPProfileTemplate)profile.getTaggedProfileTemplate();
            Iterator iter =
                temp.iteratorById(ORBConstants.TAG_REQUEST_PARTITIONING_ID);
            if (iter.hasNext()) {
                poolToUse =
                    ((RequestPartitioningComponent)iter.next()).getRequestPartitioningId();
            }

            if (poolToUse < ORBConstants.REQUEST_PARTITIONING_MIN_THREAD_POOL_ID ||
                poolToUse > ORBConstants.REQUEST_PARTITIONING_MAX_THREAD_POOL_ID) {
                throw wrapper.invalidRequestPartitioningId(new Integer(poolToUse),
                      new Integer(ORBConstants.REQUEST_PARTITIONING_MIN_THREAD_POOL_ID),
                      new Integer(ORBConstants.REQUEST_PARTITIONING_MAX_THREAD_POOL_ID));
            }
            requestMessage.setThreadPoolToUse(poolToUse);
        }

        return requestMessage;
    }

    public static ReplyMessage createReply(
            ORB orb, GIOPVersion gv, byte encodingVersion, int request_id,
            int reply_status, ServiceContexts service_contexts, IOR ior) {

        if (gv.equals(GIOPVersion.V1_0)) { // 1.0
            return new ReplyMessage_1_0(orb, service_contexts, request_id,
                                        reply_status, ior);
        } else if (gv.equals(GIOPVersion.V1_1)) { // 1.1
            return new ReplyMessage_1_1(orb, service_contexts, request_id,
                                        reply_status, ior);
        } else if (gv.equals(GIOPVersion.V1_2)) { // 1.2
            ReplyMessage msg =
                new ReplyMessage_1_2(orb, request_id, reply_status,
                                     service_contexts, ior);
            msg.setEncodingVersion(encodingVersion);
            return msg;
        } else {
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public static LocateRequestMessage createLocateRequest(
            ORB orb, GIOPVersion gv, byte encodingVersion,
            int request_id, byte[] object_key) {

        if (gv.equals(GIOPVersion.V1_0)) { // 1.0
            return new LocateRequestMessage_1_0(orb, request_id, object_key);
        } else if (gv.equals(GIOPVersion.V1_1)) { // 1.1
            return new LocateRequestMessage_1_1(orb, request_id, object_key);
        } else if (gv.equals(GIOPVersion.V1_2)) { // 1.2
            TargetAddress target = new TargetAddress();
            target.object_key(object_key);
            LocateRequestMessage msg =
                new LocateRequestMessage_1_2(orb, request_id, target);
            msg.setEncodingVersion(encodingVersion);
            return msg;
        } else {
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public static LocateReplyMessage createLocateReply(
            ORB orb, GIOPVersion gv, byte encodingVersion,
            int request_id, int locate_status, IOR ior) {

        if (gv.equals(GIOPVersion.V1_0)) { // 1.0
            return new LocateReplyMessage_1_0(orb, request_id,
                                              locate_status, ior);
        } else if (gv.equals(GIOPVersion.V1_1)) { // 1.1
            return new LocateReplyMessage_1_1(orb, request_id,
                                              locate_status, ior);
        } else if (gv.equals(GIOPVersion.V1_2)) { // 1.2
            LocateReplyMessage msg =
                new LocateReplyMessage_1_2(orb, request_id,
                                           locate_status, ior);
            msg.setEncodingVersion(encodingVersion);
            return msg;
        } else {
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public static CancelRequestMessage createCancelRequest(
            GIOPVersion gv, int request_id) {

        if (gv.equals(GIOPVersion.V1_0)) { // 1.0
            return new CancelRequestMessage_1_0(request_id);
        } else if (gv.equals(GIOPVersion.V1_1)) { // 1.1
            return new CancelRequestMessage_1_1(request_id);
        } else if (gv.equals(GIOPVersion.V1_2)) { // 1.2
            return new CancelRequestMessage_1_2(request_id);
        } else {
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public static Message createCloseConnection(GIOPVersion gv) {
        if (gv.equals(GIOPVersion.V1_0)) { // 1.0
            return new Message_1_0(Message.GIOPBigMagic, false,
                                   Message.GIOPCloseConnection, 0);
        } else if (gv.equals(GIOPVersion.V1_1)) { // 1.1
            return new Message_1_1(Message.GIOPBigMagic, GIOPVersion.V1_1,
                                   FLAG_NO_FRAG_BIG_ENDIAN,
                                   Message.GIOPCloseConnection, 0);
        } else if (gv.equals(GIOPVersion.V1_2)) { // 1.2
            return new Message_1_1(Message.GIOPBigMagic, GIOPVersion.V1_2,
                                   FLAG_NO_FRAG_BIG_ENDIAN,
                                   Message.GIOPCloseConnection, 0);
        } else {
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public static Message createMessageError(GIOPVersion gv) {
        if (gv.equals(GIOPVersion.V1_0)) { // 1.0
            return new Message_1_0(Message.GIOPBigMagic, false,
                                   Message.GIOPMessageError, 0);
        } else if (gv.equals(GIOPVersion.V1_1)) { // 1.1
            return new Message_1_1(Message.GIOPBigMagic, GIOPVersion.V1_1,
                                   FLAG_NO_FRAG_BIG_ENDIAN,
                                   Message.GIOPMessageError, 0);
        } else if (gv.equals(GIOPVersion.V1_2)) { // 1.2
            return new Message_1_1(Message.GIOPBigMagic, GIOPVersion.V1_2,
                                   FLAG_NO_FRAG_BIG_ENDIAN,
                                   Message.GIOPMessageError, 0);
        } else {
            throw wrapper.giopVersionError(
                CompletionStatus.COMPLETED_MAYBE);
        }
    }

    public static FragmentMessage createFragmentMessage(GIOPVersion gv) {
        // This method is not currently used.
        // New fragment messages are always created from existing messages.
        // Creating a FragmentMessage from InputStream is done in
        // createFromStream(..)
        return null;
    }

    public static int getRequestId(Message msg) {
        switch (msg.getType()) {
        case GIOPRequest :
            return ((RequestMessage) msg).getRequestId();
        case GIOPReply :
            return ((ReplyMessage) msg).getRequestId();
        case GIOPLocateRequest :
            return ((LocateRequestMessage) msg).getRequestId();
        case GIOPLocateReply :
            return ((LocateReplyMessage) msg).getRequestId();
        case GIOPCancelRequest :
            return ((CancelRequestMessage) msg).getRequestId();
        case GIOPFragment :
            return ((FragmentMessage) msg).getRequestId();
        }

        throw wrapper.illegalGiopMsgType(
            CompletionStatus.COMPLETED_MAYBE);
    }

    /**
     * Set a flag in the given buffer (fragment bit, byte order bit, etc)
     */
    public static void setFlag(ByteBuffer byteBuffer, int flag) {
        byte b = byteBuffer.get(6);
        b |= flag;
        byteBuffer.put(6,b);
    }

    /**
     * Clears a flag in the given buffer
     */
    public static void clearFlag(byte[] buf, int flag) {
        buf[6] &= (0xFF ^ flag);
    }

    private static void AreFragmentsAllowed(byte major, byte minor, byte flag,
            byte msgType) {

        if ( (major == 0x01) && (minor == 0x00) ) { // 1.0
            if (msgType == GIOPFragment) {
                throw wrapper.fragmentationDisallowed(
                    CompletionStatus.COMPLETED_MAYBE);
            }
        }

        if ( (flag & MORE_FRAGMENTS_BIT) == MORE_FRAGMENTS_BIT ) {
            switch (msgType) {
            case GIOPCancelRequest :
            case GIOPCloseConnection :
            case GIOPMessageError :
                throw wrapper.fragmentationDisallowed(
                    CompletionStatus.COMPLETED_MAYBE);
            case GIOPLocateRequest :
            case GIOPLocateReply :
                if ( (major == 0x01) && (minor == 0x01) ) { // 1.1
                    throw wrapper.fragmentationDisallowed(
                        CompletionStatus.COMPLETED_MAYBE);
                }
                break;
            }
        }
    }

    /**
     * Construct an ObjectKey from a byte[].
     *
     * @return ObjectKey the object key.
     */
    static ObjectKey extractObjectKey(byte[] objKey, ORB orb) {

        try {
            if (objKey != null) {
                ObjectKey objectKey =
                    orb.getObjectKeyFactory().create(objKey);
                if (objectKey != null) {
                    return objectKey;
                }
            }
        } catch (Exception e) {
            // XXX log this exception
        }

        // This exception is thrown if any exceptions are raised while
        // extracting the object key or if the object key is empty.
        throw wrapper.invalidObjectKey();
    }

    /**
     * Extract the object key from TargetAddress.
     *
     * @return ObjectKey the object key.
     */
    static ObjectKey extractObjectKey(TargetAddress target, ORB orb) {

        short orbTargetAddrPref = orb.getORBData().getGIOPTargetAddressPreference();
        short reqAddrDisp = target.discriminator();

        switch (orbTargetAddrPref) {
        case ORBConstants.ADDR_DISP_OBJKEY :
            if (reqAddrDisp != KeyAddr.value) {
                throw new AddressingDispositionException(KeyAddr.value);
            }
            break;
        case ORBConstants.ADDR_DISP_PROFILE :
            if (reqAddrDisp != ProfileAddr.value) {
                throw new AddressingDispositionException(ProfileAddr.value);
            }
            break;
        case ORBConstants.ADDR_DISP_IOR :
            if (reqAddrDisp != ReferenceAddr.value) {
                throw new AddressingDispositionException(ReferenceAddr.value);
            }
            break;
        case ORBConstants.ADDR_DISP_HANDLE_ALL :
            break;
        default :
            throw wrapper.orbTargetAddrPreferenceInExtractObjectkeyInvalid() ;
        }

        try {
            switch (reqAddrDisp) {
            case KeyAddr.value :
                byte[] objKey = target.object_key();
                if (objKey != null) { // AddressingDisposition::KeyAddr
                    ObjectKey objectKey =
                        orb.getObjectKeyFactory().create(objKey);
                    if (objectKey != null) {
                       return objectKey;
                   }
                }
                break;
            case ProfileAddr.value :
                IIOPProfile iiopProfile = null;
                TaggedProfile profile = target.profile();
                if (profile != null) { // AddressingDisposition::ProfileAddr
                   iiopProfile = IIOPFactories.makeIIOPProfile(orb, profile);
                   ObjectKey objectKey = iiopProfile.getObjectKey();
                   if (objectKey != null) {
                       return objectKey;
                   }
                }
                break;
            case ReferenceAddr.value :
                IORAddressingInfo iorInfo = target.ior();
                if (iorInfo != null) { // AddressingDisposition::IORAddr
                    profile = iorInfo.ior.profiles[iorInfo.selected_profile_index];
                    iiopProfile = IIOPFactories.makeIIOPProfile(orb, profile);
                    ObjectKey objectKey = iiopProfile.getObjectKey();
                    if (objectKey != null) {
                       return objectKey;
                   }
                }
                break;
            default : // this cannot happen
                // There is no need for a explicit exception, since the
                // TargetAddressHelper.read() would have raised a BAD_OPERATION
                // exception by now.
                break;
            }
        } catch (Exception e) {}

        // This exception is thrown if any exceptions are raised while
        // extracting the object key from the TargetAddress or if all the
        // the valid TargetAddress::AddressingDispositions are empty.
        throw wrapper.invalidObjectKey() ;
    }

    private static int readSize(byte b1, byte b2, byte b3, byte b4,
            boolean littleEndian) {

        int a1, a2, a3, a4;

        if (!littleEndian) {
            a1 = (b1 << 24) & 0xFF000000;
            a2 = (b2 << 16) & 0x00FF0000;
            a3 = (b3 << 8)  & 0x0000FF00;
            a4 = (b4 << 0)  & 0x000000FF;
        } else {
            a1 = (b4 << 24) & 0xFF000000;
            a2 = (b3 << 16) & 0x00FF0000;
            a3 = (b2 << 8)  & 0x0000FF00;
            a4 = (b1 << 0)  & 0x000000FF;
        }

        return (a1 | a2 | a3 | a4);
    }

    static void nullCheck(Object obj) {
        if (obj == null) {
            throw wrapper.nullNotAllowed() ;
        }
    }

    static SystemException getSystemException(
        String exClassName, int minorCode, CompletionStatus completionStatus,
        String message, ORBUtilSystemException wrapper)
    {
        SystemException sysEx = null;

        try {
            Class<?> clazz =
                SharedSecrets.getJavaCorbaAccess().loadClass(exClassName);
            if (message == null) {
                sysEx = (SystemException) clazz.newInstance();
            } else {
                Class[] types = { String.class };
                Constructor constructor = clazz.getConstructor(types);
                Object[] args = { message };
                sysEx = (SystemException)constructor.newInstance(args);
            }
        } catch (Exception someEx) {
            throw wrapper.badSystemExceptionInReply(
                CompletionStatus.COMPLETED_MAYBE, someEx );
        }

        sysEx.minor = minorCode;
        sysEx.completed = completionStatus;

        return sysEx;
    }

    public void callback(MessageHandler handler)
        throws java.io.IOException
    {
        handler.handleInput(this);
    }

    public ByteBuffer getByteBuffer()
    {
        return byteBuffer;
    }

    public void setByteBuffer(ByteBuffer byteBuffer)
    {
        this.byteBuffer = byteBuffer;
    }

    public int getThreadPoolToUse()
    {
        return threadPoolToUse;
    }

    public byte getEncodingVersion() {
        return this.encodingVersion;
    }

    public void setEncodingVersion(byte version) {
        this.encodingVersion = version;
    }

    private static void dprint(String msg)
    {
        ORBUtility.dprint("MessageBase", msg);
    }
}
