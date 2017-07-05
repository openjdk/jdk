/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.corba.se.impl.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Map;

import org.omg.CORBA.COMM_FAILURE;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.DATA_CONVERSION;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.MARSHAL;
import org.omg.CORBA.OBJECT_NOT_EXIST;
import org.omg.CORBA.SystemException;

import com.sun.org.omg.SendingContext.CodeBase;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.transport.Acceptor;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.ConnectionCache;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.corba.se.pept.transport.EventHandler;
import com.sun.corba.se.pept.transport.InboundConnectionCache;
import com.sun.corba.se.pept.transport.OutboundConnectionCache;
import com.sun.corba.se.pept.transport.ResponseWaitingRoom;
import com.sun.corba.se.pept.transport.Selector;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchThreadPoolException;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.transport.CorbaResponseWaitingRoom;
import com.sun.corba.se.spi.transport.ReadTimeouts;

import com.sun.corba.se.impl.encoding.CachedCodeBase;
import com.sun.corba.se.impl.encoding.CDRInputStream_1_0;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.encoding.CDROutputStream_1_0;
import com.sun.corba.se.impl.encoding.CodeSetComponentInfo;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase;
import com.sun.corba.se.impl.transport.CorbaResponseWaitingRoomImpl;

/**
 * @author Harold Carr
 */
public class SocketOrChannelConnectionImpl
    extends
        EventHandlerBase
    implements
        CorbaConnection,
        Work
{
    public static boolean dprintWriteLocks = false;

    //
    // New transport.
    //

    protected long enqueueTime;

    protected SocketChannel socketChannel;
    public SocketChannel getSocketChannel()
    {
        return socketChannel;
    }

    // REVISIT:
    // protected for test: genericRPCMSGFramework.IIOPConnection constructor.
    protected CorbaContactInfo contactInfo;
    protected Acceptor acceptor;
    protected ConnectionCache connectionCache;

    //
    // From iiop.Connection.java
    //

    protected Socket socket;    // The socket used for this connection.
    protected long timeStamp = 0;
    protected boolean isServer = false;

    // Start at some value other than zero since this is a magic
    // value in some protocols.
    protected int requestId = 5;
    protected CorbaResponseWaitingRoom responseWaitingRoom;
    protected int state;
    protected java.lang.Object stateEvent = new java.lang.Object();
    protected java.lang.Object writeEvent = new java.lang.Object();
    protected boolean writeLocked;
    protected int serverRequestCount = 0;

    // Server request map: used on the server side of Connection
    // Maps request ID to IIOPInputStream.
    Map serverRequestMap = null;

    // This is a flag associated per connection telling us if the
    // initial set of sending contexts were sent to the receiver
    // already...
    protected boolean postInitialContexts = false;

    // Remote reference to CodeBase server (supplies
    // FullValueDescription, among other things)
    protected IOR codeBaseServerIOR;

    // CodeBase cache for this connection.  This will cache remote operations,
    // handle connecting, and ensure we don't do any remote operations until
    // necessary.
    protected CachedCodeBase cachedCodeBase = new CachedCodeBase(this);

    protected ORBUtilSystemException wrapper ;

    // transport read timeout values
    protected ReadTimeouts readTimeouts;

    protected boolean shouldReadGiopHeaderOnly;

    // A message mediator used when shouldReadGiopHeaderOnly is
    // true to maintain request message state across execution in a
    // SelectorThread and WorkerThread.
    protected CorbaMessageMediator partialMessageMediator = null;

    // Used in genericRPCMSGFramework test.
    protected SocketOrChannelConnectionImpl(ORB orb)
    {
        this.orb = orb;
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_TRANSPORT ) ;

        setWork(this);
        responseWaitingRoom = new CorbaResponseWaitingRoomImpl(orb, this);
        setReadTimeouts(orb.getORBData().getTransportTCPReadTimeouts());
    }

    // Both client and servers.
    protected SocketOrChannelConnectionImpl(ORB orb,
                                            boolean useSelectThreadToWait,
                                            boolean useWorkerThread)
    {
        this(orb) ;
        setUseSelectThreadToWait(useSelectThreadToWait);
        setUseWorkerThreadForEvent(useWorkerThread);
    }

    // Client constructor.
    public SocketOrChannelConnectionImpl(ORB orb,
                                         CorbaContactInfo contactInfo,
                                         boolean useSelectThreadToWait,
                                         boolean useWorkerThread,
                                         String socketType,
                                         String hostname,
                                         int port)
    {
        this(orb, useSelectThreadToWait, useWorkerThread);

        this.contactInfo = contactInfo;

        try {
            socket = orb.getORBData().getSocketFactory()
                .createSocket(socketType,
                              new InetSocketAddress(hostname, port));
            socketChannel = socket.getChannel();

            if (socketChannel != null) {
                boolean isBlocking = !useSelectThreadToWait;
                socketChannel.configureBlocking(isBlocking);
            } else {
                // IMPORTANT: non-channel-backed sockets must use
                // dedicated reader threads.
                setUseSelectThreadToWait(false);
            }
            if (orb.transportDebugFlag) {
                dprint(".initialize: connection created: " + socket);
            }
        } catch (Throwable t) {
            throw wrapper.connectFailure(t, socketType, hostname,
                                         Integer.toString(port));
        }
        state = OPENING;
    }

    // Client-side convenience.
    public SocketOrChannelConnectionImpl(ORB orb,
                                         CorbaContactInfo contactInfo,
                                         String socketType,
                                         String hostname,
                                         int port)
    {
        this(orb, contactInfo,
             orb.getORBData().connectionSocketUseSelectThreadToWait(),
             orb.getORBData().connectionSocketUseWorkerThreadForEvent(),
             socketType, hostname, port);
    }

    // Server-side constructor.
    public SocketOrChannelConnectionImpl(ORB orb,
                                         Acceptor acceptor,
                                         Socket socket,
                                         boolean useSelectThreadToWait,
                                         boolean useWorkerThread)
    {
        this(orb, useSelectThreadToWait, useWorkerThread);

        this.socket = socket;
        socketChannel = socket.getChannel();
        if (socketChannel != null) {
            // REVISIT
            try {
                boolean isBlocking = !useSelectThreadToWait;
                socketChannel.configureBlocking(isBlocking);
            } catch (IOException e) {
                RuntimeException rte = new RuntimeException();
                rte.initCause(e);
                throw rte;
            }
        }
        this.acceptor = acceptor;

        serverRequestMap = Collections.synchronizedMap(new HashMap());
        isServer = true;

        state = ESTABLISHED;
    }

    // Server-side convenience
    public SocketOrChannelConnectionImpl(ORB orb,
                                         Acceptor acceptor,
                                         Socket socket)
    {
        this(orb, acceptor, socket,
             (socket.getChannel() == null
              ? false
              : orb.getORBData().connectionSocketUseSelectThreadToWait()),
             (socket.getChannel() == null
              ? false
              : orb.getORBData().connectionSocketUseWorkerThreadForEvent()));
    }

    ////////////////////////////////////////////////////
    //
    // framework.transport.Connection
    //

    public boolean shouldRegisterReadEvent()
    {
        return true;
    }

    public boolean shouldRegisterServerReadEvent()
    {
        return true;
    }

    public boolean read()
    {
        try {
            if (orb.transportDebugFlag) {
                dprint(".read->: " + this);
            }
            CorbaMessageMediator messageMediator = readBits();
            if (messageMediator != null) {
                // Null can happen when client closes stream
                // causing purgecalls.
                return dispatch(messageMediator);
            }
            return true;
        } finally {
            if (orb.transportDebugFlag) {
                dprint(".read<-: " + this);
            }
        }
    }

    protected CorbaMessageMediator readBits()
    {
        try {

            if (orb.transportDebugFlag) {
                dprint(".readBits->: " + this);
            }

            MessageMediator messageMediator;
            // REVISIT - use common factory base class.
            if (contactInfo != null) {
                messageMediator =
                    contactInfo.createMessageMediator(orb, this);
            } else if (acceptor != null) {
                messageMediator = acceptor.createMessageMediator(orb, this);
            } else {
                throw
                    new RuntimeException("SocketOrChannelConnectionImpl.readBits");
            }
            return (CorbaMessageMediator) messageMediator;

        } catch (ThreadDeath td) {
            if (orb.transportDebugFlag) {
                dprint(".readBits: " + this + ": ThreadDeath: " + td, td);
            }
            try {
                purgeCalls(wrapper.connectionAbort(td), false, false);
            } catch (Throwable t) {
                if (orb.transportDebugFlag) {
                    dprint(".readBits: " + this + ": purgeCalls: Throwable: " + t, t);
                }
            }
            throw td;
        } catch (Throwable ex) {
            if (orb.transportDebugFlag) {
                dprint(".readBits: " + this + ": Throwable: " + ex, ex);
            }

            try {
                if (ex instanceof INTERNAL) {
                    sendMessageError(GIOPVersion.DEFAULT_VERSION);
                }
            } catch (IOException e) {
                if (orb.transportDebugFlag) {
                    dprint(".readBits: " + this +
                           ": sendMessageError: IOException: " + e, e);
                }
            }
            // REVISIT - make sure reader thread is killed.
            orb.getTransportManager().getSelector(0).unregisterForEvent(this);
            // Notify anyone waiting.
            purgeCalls(wrapper.connectionAbort(ex), true, false);
            // REVISIT
            //keepRunning = false;
            // REVISIT - if this is called after purgeCalls then
            // the state of the socket is ABORT so the writeLock
            // in close throws an exception.  It is ignored but
            // causes IBM (screen scraping) tests to fail.
            //close();
        } finally {
            if (orb.transportDebugFlag) {
                dprint(".readBits<-: " + this);
            }
        }
        return null;
    }

    protected CorbaMessageMediator finishReadingBits(MessageMediator messageMediator)
    {
        try {

            if (orb.transportDebugFlag) {
                dprint(".finishReadingBits->: " + this);
            }

            // REVISIT - use common factory base class.
            if (contactInfo != null) {
                messageMediator =
                    contactInfo.finishCreatingMessageMediator(orb, this, messageMediator);
            } else if (acceptor != null) {
                messageMediator =
                    acceptor.finishCreatingMessageMediator(orb, this, messageMediator);
            } else {
                throw
                    new RuntimeException("SocketOrChannelConnectionImpl.finishReadingBits");
            }
            return (CorbaMessageMediator) messageMediator;

        } catch (ThreadDeath td) {
            if (orb.transportDebugFlag) {
                dprint(".finishReadingBits: " + this + ": ThreadDeath: " + td, td);
            }
            try {
                purgeCalls(wrapper.connectionAbort(td), false, false);
            } catch (Throwable t) {
                if (orb.transportDebugFlag) {
                    dprint(".finishReadingBits: " + this + ": purgeCalls: Throwable: " + t, t);
                }
            }
            throw td;
        } catch (Throwable ex) {
            if (orb.transportDebugFlag) {
                dprint(".finishReadingBits: " + this + ": Throwable: " + ex, ex);
            }

            try {
                if (ex instanceof INTERNAL) {
                    sendMessageError(GIOPVersion.DEFAULT_VERSION);
                }
            } catch (IOException e) {
                if (orb.transportDebugFlag) {
                    dprint(".finishReadingBits: " + this +
                           ": sendMessageError: IOException: " + e, e);
                }
            }
            // REVISIT - make sure reader thread is killed.
            orb.getTransportManager().getSelector(0).unregisterForEvent(this);
            // Notify anyone waiting.
            purgeCalls(wrapper.connectionAbort(ex), true, false);
            // REVISIT
            //keepRunning = false;
            // REVISIT - if this is called after purgeCalls then
            // the state of the socket is ABORT so the writeLock
            // in close throws an exception.  It is ignored but
            // causes IBM (screen scraping) tests to fail.
            //close();
        } finally {
            if (orb.transportDebugFlag) {
                dprint(".finishReadingBits<-: " + this);
            }
        }
        return null;
    }

    protected boolean dispatch(CorbaMessageMediator messageMediator)
    {
        try {
            if (orb.transportDebugFlag) {
                dprint(".dispatch->: " + this);
            }

            //
            // NOTE:
            //
            // This call is the transition from the tranport block
            // to the protocol block.
            //

            boolean result =
                messageMediator.getProtocolHandler()
                .handleRequest(messageMediator);

            return result;

        } catch (ThreadDeath td) {
            if (orb.transportDebugFlag) {
                dprint(".dispatch: ThreadDeath", td );
            }
            try {
                purgeCalls(wrapper.connectionAbort(td), false, false);
            } catch (Throwable t) {
                if (orb.transportDebugFlag) {
                    dprint(".dispatch: purgeCalls: Throwable", t);
                }
            }
            throw td;
        } catch (Throwable ex) {
            if (orb.transportDebugFlag) {
                dprint(".dispatch: Throwable", ex ) ;
            }

            try {
                if (ex instanceof INTERNAL) {
                    sendMessageError(GIOPVersion.DEFAULT_VERSION);
                }
            } catch (IOException e) {
                if (orb.transportDebugFlag) {
                    dprint(".dispatch: sendMessageError: IOException", e);
                }
            }
            purgeCalls(wrapper.connectionAbort(ex), false, false);
            // REVISIT
            //keepRunning = false;
        } finally {
            if (orb.transportDebugFlag) {
                dprint(".dispatch<-: " + this);
            }
        }

        return true;
    }

    public boolean shouldUseDirectByteBuffers()
    {
        return getSocketChannel() != null;
    }

    public ByteBuffer read(int size, int offset, int length, long max_wait_time)
        throws IOException
    {
        if (shouldUseDirectByteBuffers()) {

            ByteBuffer byteBuffer =
                orb.getByteBufferPool().getByteBuffer(size);

            if (orb.transportDebugFlag) {
                // print address of ByteBuffer gotten from pool
                int bbAddress = System.identityHashCode(byteBuffer);
                StringBuffer sb = new StringBuffer(80);
                sb.append(".read: got ByteBuffer id (");
                sb.append(bbAddress).append(") from ByteBufferPool.");
                String msgStr = sb.toString();
                dprint(msgStr);
            }

            byteBuffer.position(offset);
            byteBuffer.limit(size);

            readFully(byteBuffer, length, max_wait_time);

            return byteBuffer;
        }

        byte[] buf = new byte[size];
        readFully(getSocket().getInputStream(), buf,
                  offset, length, max_wait_time);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        byteBuffer.limit(size);
        return byteBuffer;
    }

    public ByteBuffer read(ByteBuffer byteBuffer, int offset,
                           int length, long max_wait_time)
        throws IOException
    {
        int size = offset + length;
        if (shouldUseDirectByteBuffers()) {

            if (! byteBuffer.isDirect()) {
                throw wrapper.unexpectedNonDirectByteBufferWithChannelSocket();
            }
            if (size > byteBuffer.capacity()) {
                if (orb.transportDebugFlag) {
                    // print address of ByteBuffer being released
                    int bbAddress = System.identityHashCode(byteBuffer);
                    StringBuffer bbsb = new StringBuffer(80);
                    bbsb.append(".read: releasing ByteBuffer id (")
                        .append(bbAddress).append(") to ByteBufferPool.");
                    String bbmsg = bbsb.toString();
                    dprint(bbmsg);
                }
                orb.getByteBufferPool().releaseByteBuffer(byteBuffer);
                byteBuffer = orb.getByteBufferPool().getByteBuffer(size);
            }
            byteBuffer.position(offset);
            byteBuffer.limit(size);
            readFully(byteBuffer, length, max_wait_time);
            byteBuffer.position(0);
            byteBuffer.limit(size);
            return byteBuffer;
        }
        if (byteBuffer.isDirect()) {
            throw wrapper.unexpectedDirectByteBufferWithNonChannelSocket();
        }
        byte[] buf = new byte[size];
        readFully(getSocket().getInputStream(), buf,
                  offset, length, max_wait_time);
        return ByteBuffer.wrap(buf);
    }

    public void readFully(ByteBuffer byteBuffer, int size, long max_wait_time)
        throws IOException
    {
        int n = 0;
        int bytecount = 0;
        long time_to_wait = readTimeouts.get_initial_time_to_wait();
        long total_time_in_wait = 0;

        // The reading of data incorporates a strategy to detect a
        // rogue client. The strategy is implemented as follows. As
        // long as data is being read, at least 1 byte or more, we
        // assume we have a well behaved client. If no data is read,
        // then we sleep for a time to wait, re-calculate a new time to
        // wait which is lengthier than the previous time spent waiting.
        // Then, if the total time spent waiting does not exceed a
        // maximum time we are willing to wait, we attempt another
        // read. If the maximum amount of time we are willing to
        // spend waiting for more data is exceeded, we throw an
        // IOException.

        // NOTE: Reading of GIOP headers are treated with a smaller
        //       maximum time to wait threshold. Based on extensive
        //       performance testing, all GIOP headers are being
        //       read in 1 read access.

        do {
            bytecount = getSocketChannel().read(byteBuffer);

            if (bytecount < 0) {
                throw new IOException("End-of-stream");
            }
            else if (bytecount == 0) {
                try {
                    Thread.sleep(time_to_wait);
                    total_time_in_wait += time_to_wait;
                    time_to_wait =
                        (long)(time_to_wait*readTimeouts.get_backoff_factor());
                }
                catch (InterruptedException ie) {
                    // ignore exception
                    if (orb.transportDebugFlag) {
                        dprint("readFully(): unexpected exception "
                                + ie.toString());
                    }
                }
            }
            else {
                n += bytecount;
            }
        }
        while (n < size && total_time_in_wait < max_wait_time);

        if (n < size && total_time_in_wait >= max_wait_time)
        {
            // failed to read entire message
            throw wrapper.transportReadTimeoutExceeded(new Integer(size),
                                      new Integer(n), new Long(max_wait_time),
                                      new Long(total_time_in_wait));
        }

        getConnectionCache().stampTime(this);
    }

    // To support non-channel connections.
    public void readFully(java.io.InputStream is, byte[] buf,
                          int offset, int size, long max_wait_time)
        throws IOException
    {
        int n = 0;
        int bytecount = 0;
        long time_to_wait = readTimeouts.get_initial_time_to_wait();
        long total_time_in_wait = 0;

        // The reading of data incorporates a strategy to detect a
        // rogue client. The strategy is implemented as follows. As
        // long as data is being read, at least 1 byte or more, we
        // assume we have a well behaved client. If no data is read,
        // then we sleep for a time to wait, re-calculate a new time to
        // wait which is lengthier than the previous time spent waiting.
        // Then, if the total time spent waiting does not exceed a
        // maximum time we are willing to wait, we attempt another
        // read. If the maximum amount of time we are willing to
        // spend waiting for more data is exceeded, we throw an
        // IOException.

        // NOTE: Reading of GIOP headers are treated with a smaller
        //       maximum time to wait threshold. Based on extensive
        //       performance testing, all GIOP headers are being
        //       read in 1 read access.

        do {
            bytecount = is.read(buf, offset + n, size - n);
            if (bytecount < 0) {
                throw new IOException("End-of-stream");
            }
            else if (bytecount == 0) {
                try {
                    Thread.sleep(time_to_wait);
                    total_time_in_wait += time_to_wait;
                    time_to_wait =
                        (long)(time_to_wait*readTimeouts.get_backoff_factor());
                }
                catch (InterruptedException ie) {
                    // ignore exception
                    if (orb.transportDebugFlag) {
                        dprint("readFully(): unexpected exception "
                                + ie.toString());
                    }
                }
            }
            else {
                n += bytecount;
            }
        }
        while (n < size && total_time_in_wait < max_wait_time);

        if (n < size && total_time_in_wait >= max_wait_time)
        {
            // failed to read entire message
            throw wrapper.transportReadTimeoutExceeded(new Integer(size),
                                      new Integer(n), new Long(max_wait_time),
                                      new Long(total_time_in_wait));
        }

        getConnectionCache().stampTime(this);
    }

    public void write(ByteBuffer byteBuffer)
        throws IOException
    {
        if (shouldUseDirectByteBuffers()) {
            /* NOTE: cannot perform this test.  If one ask for a
               ByteBuffer from the pool which is bigger than the size
               of ByteBuffers managed by the pool, then the pool will
               return a HeapByteBuffer.
            if (byteBuffer.hasArray()) {
                throw wrapper.unexpectedNonDirectByteBufferWithChannelSocket();
            }
            */
            // IMPORTANT: For non-blocking SocketChannels, there's no guarantee
            //            all bytes are written on first write attempt.
            do {
                getSocketChannel().write(byteBuffer);
            }
            while (byteBuffer.hasRemaining());

        } else {
            if (! byteBuffer.hasArray()) {
                throw wrapper.unexpectedDirectByteBufferWithNonChannelSocket();
            }
            byte[] tmpBuf = byteBuffer.array();
            getSocket().getOutputStream().write(tmpBuf, 0, byteBuffer.limit());
            getSocket().getOutputStream().flush();
        }

        // TimeStamp connection to indicate it has been used
        // Note granularity of connection usage is assumed for
        // now to be that of a IIOP packet.
        getConnectionCache().stampTime(this);
    }

    /**
     * Note:it is possible for this to be called more than once
     */
    public synchronized void close()
    {
        try {
            if (orb.transportDebugFlag) {
                dprint(".close->: " + this);
            }
            writeLock();

            // REVISIT It will be good to have a read lock on the reader thread
            // before we proceed further, to avoid the reader thread (server side)
            // from processing requests. This avoids the risk that a new request
            // will be accepted by ReaderThread while the ListenerThread is
            // attempting to close this connection.

            if (isBusy()) { // we are busy!
                writeUnlock();
                if (orb.transportDebugFlag) {
                    dprint(".close: isBusy so no close: " + this);
                }
                return;
            }

            try {
                try {
                    sendCloseConnection(GIOPVersion.V1_0);
                } catch (Throwable t) {
                    wrapper.exceptionWhenSendingCloseConnection(t);
                }

                synchronized ( stateEvent ){
                    state = CLOSE_SENT;
                    stateEvent.notifyAll();
                }

                // stop the reader without causing it to do purgeCalls
                //Exception ex = new Exception();
                //reader.stop(ex); // REVISIT

                // NOTE: !!!!!!
                // This does writeUnlock().
                purgeCalls(wrapper.connectionRebind(), false, true);

            } catch (Exception ex) {
                if (orb.transportDebugFlag) {
                    dprint(".close: exception: " + this, ex);
                }
            }
            try {
                Selector selector = orb.getTransportManager().getSelector(0);
                selector.unregisterForEvent(this);
                if (socketChannel != null) {
                    socketChannel.close();
                }
                socket.close();
            } catch (IOException e) {
                if (orb.transportDebugFlag) {
                    dprint(".close: " + this, e);
                }
            }
        } finally {
            if (orb.transportDebugFlag) {
                dprint(".close<-: " + this);
            }
        }
    }

    public Acceptor getAcceptor()
    {
        return acceptor;
    }

    public ContactInfo getContactInfo()
    {
        return contactInfo;
    }

    public EventHandler getEventHandler()
    {
        return this;
    }

    public OutputObject createOutputObject(MessageMediator messageMediator)
    {
        // REVISIT - remove this method from Connection and all it subclasses.
        throw new RuntimeException("*****SocketOrChannelConnectionImpl.createOutputObject - should not be called.");
    }

    // This is used by the GIOPOutputObject in order to
    // throw the correct error when handling code sets.
    // Can we determine if we are on the server side by
    // other means?  XREVISIT
    public boolean isServer()
    {
        return isServer;
    }

    public boolean isBusy()
    {
        if (serverRequestCount > 0 ||
            getResponseWaitingRoom().numberRegistered() > 0)
        {
            return true;
        } else {
            return false;
        }
    }

    public long getTimeStamp()
    {
        return timeStamp;
    }

    public void setTimeStamp(long time)
    {
        timeStamp = time;
    }

    public void setState(String stateString)
    {
        synchronized (stateEvent) {
            if (stateString.equals("ESTABLISHED")) {
                state =  ESTABLISHED;
                stateEvent.notifyAll();
            } else {
                // REVISIT: ASSERT
            }
        }
    }

    /**
     * Sets the writeLock for this connection.
     * If the writeLock is already set by someone else, block till the
     * writeLock is released and can set by us.
     * IMPORTANT: this connection's lock must be acquired before
     * setting the writeLock and must be unlocked after setting the writeLock.
     */
    public void writeLock()
    {
      try {
        if (dprintWriteLocks && orb.transportDebugFlag) {
            dprint(".writeLock->: " + this);
        }
        // Keep looping till we can set the writeLock.
        while ( true ) {
            int localState = state;
            switch ( localState ) {

            case OPENING:
                synchronized (stateEvent) {
                    if (state != OPENING) {
                        // somebody has changed 'state' so be careful
                        break;
                    }
                    try {
                        stateEvent.wait();
                    } catch (InterruptedException ie) {
                        if (orb.transportDebugFlag) {
                            dprint(".writeLock: OPENING InterruptedException: " + this);
                        }
                    }
                }
                // Loop back
                break;

            case ESTABLISHED:
                synchronized (writeEvent) {
                    if (!writeLocked) {
                        writeLocked = true;
                        return;
                    }

                    try {
                        // do not stay here too long if state != ESTABLISHED
                        // Bug 4752117
                        while (state == ESTABLISHED && writeLocked) {
                            writeEvent.wait(100);
                        }
                    } catch (InterruptedException ie) {
                        if (orb.transportDebugFlag) {
                            dprint(".writeLock: ESTABLISHED InterruptedException: " + this);
                        }
                    }
                }
                // Loop back
                break;

                //
                // XXX
                // Need to distinguish between client and server roles
                // here probably.
                //
            case ABORT:
                synchronized ( stateEvent ){
                    if (state != ABORT) {
                        break;
                    }
                    throw wrapper.writeErrorSend() ;
                }

            case CLOSE_RECVD:
                // the connection has been closed or closing
                // ==> throw rebind exception
                synchronized ( stateEvent ){
                    if (state != CLOSE_RECVD) {
                        break;
                    }
                    throw wrapper.connectionCloseRebind() ;
                }

            default:
                if (orb.transportDebugFlag) {
                    dprint(".writeLock: default: " + this);
                }
                // REVISIT
                throw new RuntimeException(".writeLock: bad state");
            }
        }
      } finally {
        if (dprintWriteLocks && orb.transportDebugFlag) {
            dprint(".writeLock<-: " + this);
        }
      }
    }

    public void writeUnlock()
    {
        try {
            if (dprintWriteLocks && orb.transportDebugFlag) {
                dprint(".writeUnlock->: " + this);
            }
            synchronized (writeEvent) {
                writeLocked = false;
                writeEvent.notify(); // wake up one guy waiting to write
            }
        } finally {
            if (dprintWriteLocks && orb.transportDebugFlag) {
                dprint(".writeUnlock<-: " + this);
            }
        }
    }

    // Assumes the caller handles writeLock and writeUnlock
    public void sendWithoutLock(OutputObject outputObject)
    {
        // Don't we need to check for CloseConnection
        // here?  REVISIT

        // XREVISIT - Shouldn't the MessageMediator
        // be the one to handle writing the data here?

        try {

            // Write the fragment/message

            CDROutputObject cdrOutputObject = (CDROutputObject) outputObject;
            cdrOutputObject.writeTo(this);
            // REVISIT - no flush?
            //socket.getOutputStream().flush();

        } catch (IOException e1) {

            /*
             * ADDED(Ram J) 10/13/2000 In the event of an IOException, try
             * sending a CancelRequest for regular requests / locate requests
             */

            // Since IIOPOutputStream's msgheader is set only once, and not
            // altered during sending multiple fragments, the original
            // msgheader will always have the requestId.
            // REVISIT This could be optimized to send a CancelRequest only
            // if any fragments had been sent already.

            /* REVISIT: MOVE TO SUBCONTRACT
            Message msg = os.getMessage();
            if (msg.getType() == Message.GIOPRequest ||
                    msg.getType() == Message.GIOPLocateRequest) {
                GIOPVersion requestVersion = msg.getGIOPVersion();
                int requestId = MessageBase.getRequestId(msg);
                try {
                    sendCancelRequest(requestVersion, requestId);
                } catch (IOException e2) {
                    // most likely an abortive connection closure.
                    // ignore, since nothing more can be done.
                    if (orb.transportDebugFlag) {

                }
            }
            */

            // REVISIT When a send failure happens, purgeCalls() need to be
            // called to ensure that the connection is properly removed from
            // further usage (ie., cancelling pending requests with COMM_FAILURE
            // with an appropriate minor_code CompletionStatus.MAY_BE).

            // Relying on the IIOPOutputStream (as noted below) is not
            // sufficient as it handles COMM_FAILURE only for the final
            // fragment (during invoke processing). Note that COMM_FAILURE could
            // happen while sending the initial fragments.
            // Also the IIOPOutputStream does not properly close the connection.
            // It simply removes the connection from the table. An orderly
            // closure is needed (ie., cancel pending requests on the connection
            // COMM_FAILURE as well.

            // IIOPOutputStream will cleanup the connection info when it
            // sees this exception.
            SystemException exc = wrapper.writeErrorSend(e1);
            purgeCalls(exc, false, true);
            throw exc;
        }
    }

    public void registerWaiter(MessageMediator messageMediator)
    {
        responseWaitingRoom.registerWaiter(messageMediator);
    }

    public void unregisterWaiter(MessageMediator messageMediator)
    {
        responseWaitingRoom.unregisterWaiter(messageMediator);
    }

    public InputObject waitForResponse(MessageMediator messageMediator)
    {
        return responseWaitingRoom.waitForResponse(messageMediator);
    }

    public void setConnectionCache(ConnectionCache connectionCache)
    {
        this.connectionCache = connectionCache;
    }

    public ConnectionCache getConnectionCache()
    {
        return connectionCache;
    }

    ////////////////////////////////////////////////////
    //
    // EventHandler methods
    //

    public void setUseSelectThreadToWait(boolean x)
    {
        useSelectThreadToWait = x;
        // REVISIT - Reading of a GIOP header only is information
        //           that should be passed into the constructor
        //           from the SocketOrChannelConnection factory.
        setReadGiopHeaderOnly(shouldUseSelectThreadToWait());
    }

    public void handleEvent()
    {
        if (orb.transportDebugFlag) {
            dprint(".handleEvent->: " + this);
        }
        getSelectionKey().interestOps(getSelectionKey().interestOps() &
                                      (~ getInterestOps()));

        if (shouldUseWorkerThreadForEvent()) {
            Throwable throwable = null;
            try {
                int poolToUse = 0;
                if (shouldReadGiopHeaderOnly()) {
                    partialMessageMediator = readBits();
                    poolToUse =
                        partialMessageMediator.getThreadPoolToUse();
                }

                if (orb.transportDebugFlag) {
                    dprint(".handleEvent: addWork to pool: " + poolToUse);
                }
                orb.getThreadPoolManager().getThreadPool(poolToUse)
                    .getWorkQueue(0).addWork(getWork());
            } catch (NoSuchThreadPoolException e) {
                throwable = e;
            } catch (NoSuchWorkQueueException e) {
                throwable = e;
            }
            // REVISIT: need to close connection.
            if (throwable != null) {
                if (orb.transportDebugFlag) {
                    dprint(".handleEvent: " + throwable);
                }
                INTERNAL i = new INTERNAL("NoSuchThreadPoolException");
                i.initCause(throwable);
                throw i;
            }
        } else {
            if (orb.transportDebugFlag) {
                dprint(".handleEvent: doWork");
            }
            getWork().doWork();
        }
        if (orb.transportDebugFlag) {
            dprint(".handleEvent<-: " + this);
        }
    }

    public SelectableChannel getChannel()
    {
        return socketChannel;
    }

    public int getInterestOps()
    {
        return SelectionKey.OP_READ;
    }

    //    public Acceptor getAcceptor() - already defined above.

    public Connection getConnection()
    {
        return this;
    }

    ////////////////////////////////////////////////////
    //
    // Work methods.
    //

    public String getName()
    {
        return this.toString();
    }

    public void doWork()
    {
        try {
            if (orb.transportDebugFlag) {
                dprint(".doWork->: " + this);
            }

            // IMPORTANT: Sanity checks on SelectionKeys such as
            //            SelectorKey.isValid() should not be done
            //            here.
            //

            if (!shouldReadGiopHeaderOnly()) {
                read();
            }
            else {
                // get the partialMessageMediator
                // created by SelectorThread
                CorbaMessageMediator messageMediator =
                                         this.getPartialMessageMediator();

                // read remaining info needed in a MessageMediator
                messageMediator = finishReadingBits(messageMediator);

                if (messageMediator != null) {
                    // Null can happen when client closes stream
                    // causing purgecalls.
                    dispatch(messageMediator);
                }
            }
        } catch (Throwable t) {
            if (orb.transportDebugFlag) {
                dprint(".doWork: ignoring Throwable: "
                       + t
                       + " " + this);
            }
        } finally {
            if (orb.transportDebugFlag) {
                dprint(".doWork<-: " + this);
            }
        }
    }

    public void setEnqueueTime(long timeInMillis)
    {
        enqueueTime = timeInMillis;
    }

    public long getEnqueueTime()
    {
        return enqueueTime;
    }

    ////////////////////////////////////////////////////
    //
    // spi.transport.CorbaConnection.
    //

    // IMPORTANT: Reader Threads must NOT read Giop header only.
    public boolean shouldReadGiopHeaderOnly() {
        return shouldReadGiopHeaderOnly;
    }

    protected void setReadGiopHeaderOnly(boolean shouldReadHeaderOnly) {
        shouldReadGiopHeaderOnly = shouldReadHeaderOnly;
    }

    public ResponseWaitingRoom getResponseWaitingRoom()
    {
        return responseWaitingRoom;
    }

    // REVISIT - inteface defines isServer but already defined in
    // higher interface.

    public void serverRequestMapPut(int requestId,
                                    CorbaMessageMediator messageMediator)
    {
        serverRequestMap.put(new Integer(requestId), messageMediator);
    }

    public CorbaMessageMediator serverRequestMapGet(int requestId)
    {
        return (CorbaMessageMediator)
            serverRequestMap.get(new Integer(requestId));
    }

    public void serverRequestMapRemove(int requestId)
    {
        serverRequestMap.remove(new Integer(requestId));
    }


    // REVISIT: this is also defined in:
    // com.sun.corba.se.spi.legacy.connection.Connection
    public java.net.Socket getSocket()
    {
        return socket;
    }

    /** It is possible for a Close Connection to have been
     ** sent here, but we will not check for this. A "lazy"
     ** Exception will be thrown in the Worker thread after the
     ** incoming request has been processed even though the connection
     ** is closed before the request is processed. This is o.k because
     ** it is a boundary condition. To prevent it we would have to add
     ** more locks which would reduce performance in the normal case.
     **/
    public synchronized void serverRequestProcessingBegins()
    {
        serverRequestCount++;
    }

    public synchronized void serverRequestProcessingEnds()
    {
        serverRequestCount--;
    }

    //
    //
    //

    public synchronized int getNextRequestId()
    {
        return requestId++;
    }

    // Negotiated code sets for char and wchar data
    protected CodeSetComponentInfo.CodeSetContext codeSetContext = null;

    public ORB getBroker()
    {
        return orb;
    }

    public CodeSetComponentInfo.CodeSetContext getCodeSetContext() {
        // Needs to be synchronized for the following case when the client
        // doesn't send the code set context twice, and we have two threads
        // in ServerRequestDispatcher processCodeSetContext.
        //
        // Thread A checks to see if there is a context, there is none, so
        //     it calls setCodeSetContext, getting the synch lock.
        // Thread B checks to see if there is a context.  If we didn't synch,
        //     it might decide to outlaw wchar/wstring.
        if (codeSetContext == null) {
            synchronized(this) {
                return codeSetContext;
            }
        }

        return codeSetContext;
    }

    public synchronized void setCodeSetContext(CodeSetComponentInfo.CodeSetContext csc) {
        // Double check whether or not we need to do this
        if (codeSetContext == null) {

            if (OSFCodeSetRegistry.lookupEntry(csc.getCharCodeSet()) == null ||
                OSFCodeSetRegistry.lookupEntry(csc.getWCharCodeSet()) == null) {
                // If the client says it's negotiated a code set that
                // isn't a fallback and we never said we support, then
                // it has a bug.
                throw wrapper.badCodesetsFromClient() ;
            }

            codeSetContext = csc;
        }
    }

    //
    // from iiop.IIOPConnection.java
    //

    // Map request ID to an InputObject.
    // This is so the client thread can start unmarshaling
    // the reply and remove it from the out_calls map while the
    // ReaderThread can still obtain the input stream to give
    // new fragments.  Only the ReaderThread touches the clientReplyMap,
    // so it doesn't incur synchronization overhead.

    public MessageMediator clientRequestMapGet(int requestId)
    {
        return responseWaitingRoom.getMessageMediator(requestId);
    }

    protected MessageMediator clientReply_1_1;

    public void clientReply_1_1_Put(MessageMediator x)
    {
        clientReply_1_1 = x;
    }

    public MessageMediator clientReply_1_1_Get()
    {
        return  clientReply_1_1;
    }

    public void clientReply_1_1_Remove()
    {
        clientReply_1_1 = null;
    }

    protected MessageMediator serverRequest_1_1;

    public void serverRequest_1_1_Put(MessageMediator x)
    {
        serverRequest_1_1 = x;
    }

    public MessageMediator serverRequest_1_1_Get()
    {
        return  serverRequest_1_1;
    }

    public void serverRequest_1_1_Remove()
    {
        serverRequest_1_1 = null;
    }

    protected String getStateString( int state )
    {
        synchronized ( stateEvent ){
            switch (state) {
            case OPENING : return "OPENING" ;
            case ESTABLISHED : return "ESTABLISHED" ;
            case CLOSE_SENT : return "CLOSE_SENT" ;
            case CLOSE_RECVD : return "CLOSE_RECVD" ;
            case ABORT : return "ABORT" ;
            default : return "???" ;
            }
        }
    }

    public synchronized boolean isPostInitialContexts() {
        return postInitialContexts;
    }

    // Can never be unset...
    public synchronized void setPostInitialContexts(){
        postInitialContexts = true;
    }

    /**
     * Wake up the outstanding requests on the connection, and hand them
     * COMM_FAILURE exception with a given minor code.
     *
     * Also, delete connection from connection table and
     * stop the reader thread.

     * Note that this should only ever be called by the Reader thread for
     * this connection.
     *
     * @param minor_code The minor code for the COMM_FAILURE major code.
     * @param die Kill the reader thread (this thread) before exiting.
     */
    public void purgeCalls(SystemException systemException,
                           boolean die, boolean lockHeld)
    {
        int minor_code = systemException.minor;

        try{
            if (orb.transportDebugFlag) {
                dprint(".purgeCalls->: "
                       + minor_code + "/" + die + "/" + lockHeld
                       + " " + this);
            }

            // If this invocation is a result of ThreadDeath caused
            // by a previous execution of this routine, just exit.

            synchronized ( stateEvent ){
                if ((state == ABORT) || (state == CLOSE_RECVD)) {
                    if (orb.transportDebugFlag) {
                        dprint(".purgeCalls: exiting since state is: "
                               + getStateString(state)
                               + " " + this);
                    }
                    return;
                }
            }

            // Grab the writeLock (freeze the calls)
            try {
                if (!lockHeld) {
                    writeLock();
                }
            } catch (SystemException ex) {
                if (orb.transportDebugFlag)
                    dprint(".purgeCalls: SystemException" + ex
                           + "; continuing " + this);
            }

            // Mark the state of the connection
            // and determine the request status
            org.omg.CORBA.CompletionStatus completion_status;
            synchronized ( stateEvent ){
                if (minor_code == ORBUtilSystemException.CONNECTION_REBIND) {
                    state = CLOSE_RECVD;
                    systemException.completed = CompletionStatus.COMPLETED_NO;
                } else {
                    state = ABORT;
                    systemException.completed = CompletionStatus.COMPLETED_MAYBE;
                }
                stateEvent.notifyAll();
            }

            try {
                socket.getInputStream().close();
                socket.getOutputStream().close();
                socket.close();
            } catch (Exception ex) {
                if (orb.transportDebugFlag) {
                    dprint(".purgeCalls: Exception closing socket: " + ex
                           + " " + this);
                }
            }

            // Signal all threads with outstanding requests on this
            // connection and give them the SystemException;

            responseWaitingRoom.signalExceptionToAllWaiters(systemException);

            if (contactInfo != null) {
                ((OutboundConnectionCache)getConnectionCache()).remove(contactInfo);
            } else if (acceptor != null) {
                ((InboundConnectionCache)getConnectionCache()).remove(this);
            }

            //
            // REVISIT: Stop the reader thread
            //

            // Signal all the waiters of the writeLock.
            // There are 4 types of writeLock waiters:
            // 1. Send waiters:
            // 2. SendReply waiters:
            // 3. cleanUp waiters:
            // 4. purge_call waiters:
            //

            writeUnlock();

        } finally {
            if (orb.transportDebugFlag) {
                dprint(".purgeCalls<-: "
                       + minor_code + "/" + die + "/" + lockHeld
                       + " " + this);
            }
        }
    }

    /*************************************************************************
    * The following methods are for dealing with Connection cleaning for
    * better scalability of servers in high network load conditions.
    **************************************************************************/

    public void sendCloseConnection(GIOPVersion giopVersion)
        throws IOException
    {
        Message msg = MessageBase.createCloseConnection(giopVersion);
        sendHelper(giopVersion, msg);
    }

    public void sendMessageError(GIOPVersion giopVersion)
        throws IOException
    {
        Message msg = MessageBase.createMessageError(giopVersion);
        sendHelper(giopVersion, msg);
    }

    /**
     * Send a CancelRequest message. This does not lock the connection, so the
     * caller needs to ensure this method is called appropriately.
     * @exception IOException - could be due to abortive connection closure.
     */
    public void sendCancelRequest(GIOPVersion giopVersion, int requestId)
        throws IOException
    {

        Message msg = MessageBase.createCancelRequest(giopVersion, requestId);
        sendHelper(giopVersion, msg);
    }

    protected void sendHelper(GIOPVersion giopVersion, Message msg)
        throws IOException
    {
        // REVISIT: See comments in CDROutputObject constructor.
        CDROutputObject outputObject =
            new CDROutputObject((ORB)orb, null, giopVersion, this, msg,
                                ORBConstants.STREAM_FORMAT_VERSION_1);
        msg.write(outputObject);

        outputObject.writeTo(this);
    }

    public void sendCancelRequestWithLock(GIOPVersion giopVersion,
                                          int requestId)
        throws IOException
    {
        writeLock();
        try {
            sendCancelRequest(giopVersion, requestId);
        } finally {
            writeUnlock();
        }
    }

    // Begin Code Base methods ---------------------------------------
    //
    // Set this connection's code base IOR.  The IOR comes from the
    // SendingContext.  This is an optional service context, but all
    // JavaSoft ORBs send it.
    //
    // The set and get methods don't need to be synchronized since the
    // first possible get would occur during reading a valuetype, and
    // that would be after the set.

    // Sets this connection's code base IOR.  This is done after
    // getting the IOR out of the SendingContext service context.
    // Our ORBs always send this, but it's optional in CORBA.

    public final void setCodeBaseIOR(IOR ior) {
        codeBaseServerIOR = ior;
    }

    public final IOR getCodeBaseIOR() {
        return codeBaseServerIOR;
    }

    // Get a CodeBase stub to use in unmarshaling.  The CachedCodeBase
    // won't connect to the remote codebase unless it's necessary.
    public final CodeBase getCodeBase() {
        return cachedCodeBase;
    }

    // End Code Base methods -----------------------------------------

    // set transport read thresholds
    protected void setReadTimeouts(ReadTimeouts readTimeouts) {
        this.readTimeouts = readTimeouts;
    }

    protected void setPartialMessageMediator(CorbaMessageMediator messageMediator) {
        partialMessageMediator = messageMediator;
    }

    protected CorbaMessageMediator getPartialMessageMediator() {
        return partialMessageMediator;
    }

    public String toString()
    {
        synchronized ( stateEvent ){
            return
                "SocketOrChannelConnectionImpl[" + " "
                + (socketChannel == null ?
                   socket.toString() : socketChannel.toString()) + " "
                + getStateString( state ) + " "
                + shouldUseSelectThreadToWait() + " "
                + shouldUseWorkerThreadForEvent() + " "
                + shouldReadGiopHeaderOnly()
                + "]" ;
        }
    }

    // Must be public - used in encoding.
    public void dprint(String msg)
    {
        ORBUtility.dprint("SocketOrChannelConnectionImpl", msg);
    }

    protected void dprint(String msg, Throwable t)
    {
        dprint(msg);
        t.printStackTrace(System.out);
    }
}

// End of file.
