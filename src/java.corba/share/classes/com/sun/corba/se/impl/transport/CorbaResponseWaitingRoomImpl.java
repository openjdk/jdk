/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.SystemException;

import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.transport.CorbaResponseWaitingRoom;

import com.sun.corba.se.impl.encoding.BufferManagerReadStream;
import com.sun.corba.se.impl.encoding.CDRInputObject;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateReplyOrReplyMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage;

/**
 * @author Harold Carr
 */
public class CorbaResponseWaitingRoomImpl
    implements
        CorbaResponseWaitingRoom
{
    final static class OutCallDesc
    {
        java.lang.Object done = new java.lang.Object();
        Thread thread;
        MessageMediator messageMediator;
        SystemException exception;
        InputObject inputObject;
    }

    private ORB orb;
    private ORBUtilSystemException wrapper ;

    private CorbaConnection connection;
    // Maps requestId to an OutCallDesc.
    final private Map<Integer, OutCallDesc> out_calls;

    public CorbaResponseWaitingRoomImpl(ORB orb, CorbaConnection connection)
    {
        this.orb = orb;
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_TRANSPORT ) ;
        this.connection = connection;
        out_calls =
            Collections.synchronizedMap(new HashMap<Integer, OutCallDesc>());
    }

    ////////////////////////////////////////////////////
    //
    // pept.transport.ResponseWaitingRoom
    //

    public void registerWaiter(MessageMediator mediator)
    {
        CorbaMessageMediator messageMediator = (CorbaMessageMediator) mediator;

        if (orb.transportDebugFlag) {
            dprint(".registerWaiter: " + opAndId(messageMediator));
        }

        Integer requestId = messageMediator.getRequestIdInteger();

        OutCallDesc call = new OutCallDesc();
        call.thread = Thread.currentThread();
        call.messageMediator = messageMediator;
        out_calls.put(requestId, call);
    }

    public void unregisterWaiter(MessageMediator mediator)
    {
        CorbaMessageMediator messageMediator = (CorbaMessageMediator) mediator;

        if (orb.transportDebugFlag) {
            dprint(".unregisterWaiter: " + opAndId(messageMediator));
        }

        Integer requestId = messageMediator.getRequestIdInteger();

        out_calls.remove(requestId);
    }

    public InputObject waitForResponse(MessageMediator mediator)
    {
      CorbaMessageMediator messageMediator = (CorbaMessageMediator) mediator;

      try {

        InputObject returnStream = null;

        if (orb.transportDebugFlag) {
            dprint(".waitForResponse->: " + opAndId(messageMediator));
        }

        Integer requestId = messageMediator.getRequestIdInteger();

        if (messageMediator.isOneWay()) {
            // The waiter is removed in releaseReply in the same
            // way as a normal request.

            if (orb.transportDebugFlag) {
                dprint(".waitForResponse: one way - not waiting: "
                       + opAndId(messageMediator));
            }

            return null;
        }

        OutCallDesc call = out_calls.get(requestId);
        if (call == null) {
            throw wrapper.nullOutCall(CompletionStatus.COMPLETED_MAYBE);
        }

        synchronized(call.done) {

            while (call.inputObject == null && call.exception == null) {
                // Wait for the reply from the server.
                // The ReaderThread reads in the reply IIOP message
                // and signals us.
                try {
                    if (orb.transportDebugFlag) {
                        dprint(".waitForResponse: waiting: "
                               + opAndId(messageMediator));
                    }
                    call.done.wait();
                } catch (InterruptedException ie) {};
            }

            if (call.exception != null) {
                if (orb.transportDebugFlag) {
                    dprint(".waitForResponse: exception: "
                           + opAndId(messageMediator));
                }
                throw call.exception;
            }

            returnStream = call.inputObject;
        }

        // REVISIT -- exceptions from unmarshaling code will
        // go up through this client thread!

        if (returnStream != null) {
            // On fragmented streams the header MUST be unmarshaled here
            // (in the client thread) in case it blocks.
            // If the header was already unmarshaled, this won't
            // do anything
            // REVISIT: cast - need interface method.
            ((CDRInputObject)returnStream).unmarshalHeader();
        }

        return returnStream;

      } finally {
        if (orb.transportDebugFlag) {
            dprint(".waitForResponse<-: " + opAndId(messageMediator));
        }
      }
    }

    public void responseReceived(InputObject is)
    {
        CDRInputObject inputObject = (CDRInputObject) is;
        LocateReplyOrReplyMessage header = (LocateReplyOrReplyMessage)
            inputObject.getMessageHeader();
        Integer requestId = new Integer(header.getRequestId());
        OutCallDesc call = out_calls.get(requestId);

        if (orb.transportDebugFlag) {
            dprint(".responseReceived: id/"
                   + requestId  + ": "
                   + header);
        }

        // This is an interesting case.  It could mean that someone sent us a
        // reply message, but we don't know what request it was for.  That
        // would probably call for an error.  However, there's another case
        // that's normal and we should think about --
        //
        // If the unmarshaling thread does all of its work inbetween the time
        // the ReaderThread gives it the last fragment and gets to the
        // out_calls.get line, then it will also be null, so just return;
        if (call == null) {
            if (orb.transportDebugFlag) {
                dprint(".responseReceived: id/"
                       + requestId
                       + ": no waiter: "
                       + header);
            }
            return;
        }

        // Set the reply InputObject and signal the client thread
        // that the reply has been received.
        // The thread signalled will remove outcall descriptor if appropriate.
        // Otherwise, it'll be removed when last fragment for it has been put on
        // BufferManagerRead's queue.
        synchronized (call.done) {
            CorbaMessageMediator messageMediator = (CorbaMessageMediator)
                call.messageMediator;

            if (orb.transportDebugFlag) {
                dprint(".responseReceived: "
                       + opAndId(messageMediator)
                       + ": notifying waiters");
            }

            messageMediator.setReplyHeader(header);
            messageMediator.setInputObject(is);
            inputObject.setMessageMediator(messageMediator);
            call.inputObject = is;
            call.done.notify();
        }
    }

    public int numberRegistered()
    {
        return out_calls.size();
    }

    //////////////////////////////////////////////////
    //
    // CorbaResponseWaitingRoom
    //

    public void signalExceptionToAllWaiters(SystemException systemException)
    {

        if (orb.transportDebugFlag) {
            dprint(".signalExceptionToAllWaiters: " + systemException);
        }

        synchronized (out_calls) {
            if (orb.transportDebugFlag) {
                dprint(".signalExceptionToAllWaiters: out_calls size :" +
                       out_calls.size());
            }

            for (OutCallDesc call : out_calls.values()) {
                if (orb.transportDebugFlag) {
                    dprint(".signalExceptionToAllWaiters: signaling " +
                            call);
                }
                synchronized(call.done) {
                    try {
                        // anything waiting for BufferManagerRead's fragment queue
                        // needs to be cancelled
                        CorbaMessageMediator corbaMsgMediator =
                                     (CorbaMessageMediator)call.messageMediator;
                        CDRInputObject inputObject =
                                   (CDRInputObject)corbaMsgMediator.getInputObject();
                        // IMPORTANT: If inputObject is null, then no need to tell
                        //            BufferManagerRead to cancel request processing.
                        if (inputObject != null) {
                            BufferManagerReadStream bufferManager =
                                (BufferManagerReadStream)inputObject.getBufferManager();
                            int requestId = corbaMsgMediator.getRequestId();
                            bufferManager.cancelProcessing(requestId);
                        }
                    } catch (Exception e) {
                    } finally {
                        // attempt to wake up waiting threads in all cases
                        call.inputObject = null;
                        call.exception = systemException;
                        call.done.notifyAll();
                    }
                }
            }
        }
    }

    public MessageMediator getMessageMediator(int requestId)
    {
        Integer id = new Integer(requestId);
        OutCallDesc call = out_calls.get(id);
        if (call == null) {
            // This can happen when getting early reply fragments for a
            // request which has completed (e.g., client marshaling error).
            return null;
        }
        return call.messageMediator;
    }

    ////////////////////////////////////////////////////
    //
    // Implementation.
    //

    protected void dprint(String msg)
    {
        ORBUtility.dprint("CorbaResponseWaitingRoomImpl", msg);
    }

    protected String opAndId(CorbaMessageMediator mediator)
    {
        return ORBUtility.operationNameAndRequestId(mediator);
    }
}

// End of file.
