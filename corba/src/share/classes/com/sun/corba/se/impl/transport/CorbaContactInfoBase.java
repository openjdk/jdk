/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.transport;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.omg.CORBA.INTERNAL;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.ClientRequestDispatcher;
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.corba.se.pept.transport.ContactInfoList;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.EventHandler;
import com.sun.corba.se.pept.transport.OutboundConnectionCache;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.ior.iiop.IIOPAddress ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.CorbaContactInfo;

import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.encoding.CDRInputObject;
import com.sun.corba.se.impl.encoding.CDRInputStream_1_0;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.protocol.CorbaMessageMediatorImpl;
import com.sun.corba.se.impl.protocol.giopmsgheaders.KeyAddr;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase;
import com.sun.corba.se.impl.orbutil.ORBUtility;

/**
 * @author Harold Carr
 */
public abstract class CorbaContactInfoBase
    implements
        CorbaContactInfo
{
    protected ORB orb;
    protected CorbaContactInfoList contactInfoList;
    // NOTE: This may be different from same named one in CorbaContactInfoList.
    protected IOR effectiveTargetIOR;
    protected short addressingDisposition;
    protected OutboundConnectionCache connectionCache;

    ////////////////////////////////////////////////////
    //
    // pept.transport.ContactInfo
    //

    public Broker getBroker()
    {
        return orb;
    }

    public ContactInfoList getContactInfoList()
    {
        return contactInfoList;
    }

    public ClientRequestDispatcher getClientRequestDispatcher()
    {
        int scid =
            getEffectiveProfile().getObjectKeyTemplate().getSubcontractId() ;
        RequestDispatcherRegistry scr = orb.getRequestDispatcherRegistry() ;
        return scr.getClientRequestDispatcher( scid ) ;
    }

    // Note: not all derived classes will use a connection cache.
    // These are convenience methods that may not be used.
    public void setConnectionCache(OutboundConnectionCache connectionCache)
    {
        this.connectionCache = connectionCache;
    }

    public OutboundConnectionCache getConnectionCache()
    {
        return connectionCache;
    }

    // Called when client making an invocation.
    public MessageMediator createMessageMediator(Broker broker,
                                                 ContactInfo contactInfo,
                                                 Connection connection,
                                                 String methodName,
                                                 boolean isOneWay)
    {
        // REVISIT: Would like version, ior, requestid, etc., decisions
        // to be in client subcontract.  Cannot pass these to this
        // factory method because it breaks generic abstraction.
        // Maybe set methods on mediator called from subcontract
        // after creation?
        CorbaMessageMediator messageMediator =
            new CorbaMessageMediatorImpl(
                (ORB) broker,
                contactInfo,
                connection,
                GIOPVersion.chooseRequestVersion( (ORB)broker,
                     effectiveTargetIOR),
                effectiveTargetIOR,
                ((CorbaConnection)connection).getNextRequestId(),
                getAddressingDisposition(),
                methodName,
                isOneWay);

        return messageMediator;
    }

    // Called when connection handling a read event.
    public MessageMediator createMessageMediator(Broker broker,Connection conn)
    {
        ORB orb = (ORB) broker;
        CorbaConnection connection = (CorbaConnection) conn;

        if (orb.transportDebugFlag) {
            if (connection.shouldReadGiopHeaderOnly()) {
                dprint(
                ".createMessageMediator: waiting for message header on connection: "
                + connection);
            } else {
                dprint(
                ".createMessageMediator: waiting for message on connection: "
                + connection);
            }
        }

        Message msg = null;

        if (connection.shouldReadGiopHeaderOnly()) {
            // read giop header only
            msg = MessageBase.readGIOPHeader(orb, connection);
        } else {
            // read entire giop message
            msg = MessageBase.readGIOPMessage(orb, connection);
        }

        ByteBuffer byteBuffer = msg.getByteBuffer();
        msg.setByteBuffer(null);
        CorbaMessageMediator messageMediator =
            new CorbaMessageMediatorImpl(orb, connection, msg, byteBuffer);

        return messageMediator;
    }

    // Called when connection reading message body
    public MessageMediator finishCreatingMessageMediator(Broker broker,
                               Connection conn, MessageMediator messageMediator)
    {
        ORB orb = (ORB) broker;
        CorbaConnection connection = (CorbaConnection) conn;
        CorbaMessageMediator corbaMessageMediator =
                      (CorbaMessageMediator)messageMediator;

        if (orb.transportDebugFlag) {
            dprint(
            ".finishCreatingMessageMediator: waiting for message body on connection: "
                + connection);
        }

        Message msg = corbaMessageMediator.getDispatchHeader();
        msg.setByteBuffer(corbaMessageMediator.getDispatchBuffer());

        // read giop body only
        msg = MessageBase.readGIOPBody(orb, connection, msg);

        ByteBuffer byteBuffer = msg.getByteBuffer();
        msg.setByteBuffer(null);
        corbaMessageMediator.setDispatchHeader(msg);
        corbaMessageMediator.setDispatchBuffer(byteBuffer);

        return corbaMessageMediator;
    }

    public OutputObject createOutputObject(MessageMediator messageMediator)
    {
        CorbaMessageMediator corbaMessageMediator = (CorbaMessageMediator)
            messageMediator;

        OutputObject outputObject =
            sun.corba.OutputStreamFactory.newCDROutputObject(orb, messageMediator,
                                corbaMessageMediator.getRequestHeader(),
                                corbaMessageMediator.getStreamFormatVersion());

        messageMediator.setOutputObject(outputObject);
        return outputObject;
    }

    public InputObject createInputObject(Broker broker,
                                         MessageMediator messageMediator)
    {
        // REVISIT: Duplicate of acceptor code.
        CorbaMessageMediator corbaMessageMediator = (CorbaMessageMediator)
            messageMediator;
        return new CDRInputObject((ORB)broker,
                                  (CorbaConnection)messageMediator.getConnection(),
                                  corbaMessageMediator.getDispatchBuffer(),
                                  corbaMessageMediator.getDispatchHeader());
    }

    ////////////////////////////////////////////////////
    //
    // spi.transport.CorbaContactInfo
    //

    public short getAddressingDisposition()
    {
        return addressingDisposition;
    }

    public void setAddressingDisposition(short addressingDisposition)
    {
        this.addressingDisposition = addressingDisposition;
    }

    // REVISIT - remove this.
    public IOR getTargetIOR()
    {
        return  contactInfoList.getTargetIOR();
    }

    public IOR getEffectiveTargetIOR()
    {
        return effectiveTargetIOR ;
    }

    public IIOPProfile getEffectiveProfile()
    {
        return effectiveTargetIOR.getProfile();
    }

    ////////////////////////////////////////////////////
    //
    // java.lang.Object
    //

    public String toString()
    {
        return
            "CorbaContactInfoBase["
            + "]";
    }


    ////////////////////////////////////////////////////
    //
    // Implementation
    //

    protected void dprint(String msg)
    {
        ORBUtility.dprint("CorbaContactInfoBase", msg);
    }
}

// End of file.
