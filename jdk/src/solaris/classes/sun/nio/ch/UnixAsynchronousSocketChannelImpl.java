/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.io.FileDescriptor;
import java.security.AccessController;
import sun.net.NetHooks;
import sun.security.action.GetPropertyAction;

/**
 * Unix implementation of AsynchronousSocketChannel
 */

class UnixAsynchronousSocketChannelImpl
    extends AsynchronousSocketChannelImpl implements Port.PollableChannel
{
    private final static NativeDispatcher nd = new SocketDispatcher();
    private static enum OpType { CONNECT, READ, WRITE };

    private static final boolean disableSynchronousRead;
    static {
        String propValue = AccessController.doPrivileged(
            new GetPropertyAction("sun.nio.ch.disableSynchronousRead", "false"));
        disableSynchronousRead = (propValue.length() == 0) ?
            true : Boolean.valueOf(propValue);
    }

    private final Port port;
    private final int fdVal;

    // used to ensure that the context for I/O operations that complete
    // ascynrhonously is visible to the pooled threads handling I/O events.
    private final Object updateLock = new Object();

    // pending connect (updateLock)
    private PendingFuture<Void,Object> pendingConnect;

    // pending remote address (statLock)
    private SocketAddress pendingRemote;

    // pending read (updateLock)
    private ByteBuffer[] readBuffers;
    private boolean scatteringRead;
    private PendingFuture<Number,Object> pendingRead;

    // pending write (updateLock)
    private ByteBuffer[] writeBuffers;
    private boolean gatheringWrite;
    private PendingFuture<Number,Object> pendingWrite;


    UnixAsynchronousSocketChannelImpl(Port port)
        throws IOException
    {
        super(port);

        // set non-blocking
        try {
            IOUtil.configureBlocking(fd, false);
        } catch (IOException x) {
            nd.close(fd);
            throw x;
        }

        this.port = port;
        this.fdVal = IOUtil.fdVal(fd);

        // add mapping from file descriptor to this channel
        port.register(fdVal, this);
    }

    // Constructor for sockets created by UnixAsynchronousServerSocketChannelImpl
    UnixAsynchronousSocketChannelImpl(Port port,
                                      FileDescriptor fd,
                                      InetSocketAddress remote)
        throws IOException
    {
        super(port, fd, remote);

        this.fdVal = IOUtil.fdVal(fd);
        IOUtil.configureBlocking(fd, false);

        try {
            port.register(fdVal, this);
        } catch (ShutdownChannelGroupException x) {
            // ShutdownChannelGroupException thrown if we attempt to register a
            // new channel after the group is shutdown
            throw new IOException(x);
        }

        this.port = port;
    }

    @Override
    public AsynchronousChannelGroupImpl group() {
        return port;
    }

    // register for events if there are outstanding I/O operations
    private void updateEvents() {
        assert Thread.holdsLock(updateLock);
        int events = 0;
        if (pendingRead != null)
            events |= Port.POLLIN;
        if (pendingConnect != null || pendingWrite != null)
            events |= Port.POLLOUT;
        if (events != 0)
            port.startPoll(fdVal, events);
    }

    /**
     * Invoked by event handler thread when file descriptor is polled
     */
    @Override
    public void onEvent(int events) {
        boolean readable = (events & Port.POLLIN) > 0;
        boolean writable = (events & Port.POLLOUT) > 0;
        if ((events & (Port.POLLERR | Port.POLLHUP)) > 0) {
            readable = true;
            writable = true;
        }

        PendingFuture<Void,Object> connectResult = null;
        PendingFuture<Number,Object> readResult = null;
        PendingFuture<Number,Object> writeResult = null;

        // map event to pending result
        synchronized (updateLock) {
            if (readable && (pendingRead != null)) {
                readResult = pendingRead;
                pendingRead = null;
            }
            if (writable) {
                if (pendingWrite != null) {
                    writeResult = pendingWrite;
                    pendingWrite = null;
                } else if (pendingConnect != null) {
                    connectResult = pendingConnect;
                    pendingConnect = null;
                }
            }
        }

        // complete the I/O operation. Special case for when channel is
        // ready for both reading and writing. In that case, submit task to
        // complete write if write operation has a completion handler.
        if (readResult != null) {
            if (writeResult != null)
                finishWrite(writeResult, false);
            finishRead(readResult, true);
            return;
        }
        if (writeResult != null) {
            finishWrite(writeResult, true);
        }
        if (connectResult != null) {
            finishConnect(connectResult, true);
        }
    }

    // returns and clears the result of a pending read
    PendingFuture<Number,Object> grabPendingRead() {
        synchronized (updateLock) {
            PendingFuture<Number,Object> result = pendingRead;
            pendingRead = null;
            return result;
        }
    }

    // returns and clears the result of a pending write
    PendingFuture<Number,Object> grabPendingWrite() {
        synchronized (updateLock) {
            PendingFuture<Number,Object> result = pendingWrite;
            pendingWrite = null;
            return result;
        }
    }

    @Override
    void implClose() throws IOException {
        // remove the mapping
        port.unregister(fdVal);

        // close file descriptor
        nd.close(fd);

        // All outstanding I/O operations are required to fail
        final PendingFuture<Void,Object> readyToConnect;
        final PendingFuture<Number,Object> readyToRead;
        final PendingFuture<Number,Object> readyToWrite;
        synchronized (updateLock) {
            readyToConnect = pendingConnect;
            pendingConnect = null;
            readyToRead = pendingRead;
            pendingRead = null;
            readyToWrite = pendingWrite;
            pendingWrite = null;
        }
        if (readyToConnect != null) {
            finishConnect(readyToConnect, false);
        }
        if (readyToRead != null) {
            finishRead(readyToRead, false);
        }
        if (readyToWrite != null) {
            finishWrite(readyToWrite, false);
        }
    }

    @Override
    public void onCancel(PendingFuture<?,?> task) {
        if (task.getContext() == OpType.CONNECT)
            killConnect();
        if (task.getContext() == OpType.READ)
            killConnect();
        if (task.getContext() == OpType.WRITE)
            killConnect();
    }

    // -- connect --

    private void setConnected() throws IOException {
        synchronized (stateLock) {
            state = ST_CONNECTED;
            localAddress = Net.localAddress(fd);
            remoteAddress = pendingRemote;
        }
    }

    private void finishConnect(PendingFuture<Void,Object> result,
                               boolean invokeDirect)
    {
        Throwable e = null;
        try {
            begin();
            checkConnect(fdVal);
            setConnected();
            result.setResult(null);
        } catch (Throwable x) {
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            e = x;
        } finally {
            end();
        }
        if (e != null) {
            // close channel if connection cannot be established
            try {
                close();
            } catch (IOException ignore) { }
            result.setFailure(e);
        }
        if (invokeDirect) {
            Invoker.invoke(result.handler(), result);
        } else {
            Invoker.invokeIndirectly(result.handler(), result);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> Future<Void> connect(SocketAddress remote,
                                    A attachment,
                                    CompletionHandler<Void,? super A> handler)
    {
        if (!isOpen()) {
            CompletedFuture<Void,A> result = CompletedFuture
                .withFailure(this, new ClosedChannelException(), attachment);
            Invoker.invoke(handler, result);
            return result;
        }

        InetSocketAddress isa = Net.checkAddress(remote);

        // permission check
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkConnect(isa.getAddress().getHostAddress(), isa.getPort());

        // check and set state
        boolean notifyBeforeTcpConnect;
        synchronized (stateLock) {
            if (state == ST_CONNECTED)
                throw new AlreadyConnectedException();
            if (state == ST_PENDING)
                throw new ConnectionPendingException();
            state = ST_PENDING;
            pendingRemote = remote;
            notifyBeforeTcpConnect = (localAddress == null);
        }

        AbstractFuture<Void,A> result = null;
        Throwable e = null;
        try {
            begin();
            // notify hook if unbound
            if (notifyBeforeTcpConnect)
                NetHooks.beforeTcpConnect(fd, isa.getAddress(), isa.getPort());
            int n = Net.connect(fd, isa.getAddress(), isa.getPort());
            if (n == IOStatus.UNAVAILABLE) {
                // connection could not be established immediately
                result = new PendingFuture<Void,A>(this, handler, attachment, OpType.CONNECT);
                synchronized (updateLock) {
                    this.pendingConnect = (PendingFuture<Void,Object>)result;
                    updateEvents();
                }
                return result;
            }
            setConnected();
            result = CompletedFuture.withResult(this, null, attachment);
        } catch (Throwable x) {
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            e = x;
        } finally {
            end();
        }

        // close channel if connect fails
        if (e != null) {
            try {
                close();
            } catch (IOException ignore) { }
            result = CompletedFuture.withFailure(this, e, attachment);
        }

        Invoker.invoke(handler, result);
        return result;
    }

    // -- read --

    @SuppressWarnings("unchecked")
    private void finishRead(PendingFuture<Number,Object> result,
                            boolean invokeDirect)
    {
        int n = -1;
        PendingFuture<Number,Object> pending = null;
        try {
            begin();

            ByteBuffer[] dsts = readBuffers;
            if (dsts.length == 1) {
                n = IOUtil.read(fd, dsts[0], -1, nd, null);
            } else {
                n = (int)IOUtil.read(fd, dsts, nd);
            }
            if (n == IOStatus.UNAVAILABLE) {
                // spurious wakeup, is this possible?
                pending = result;
                return;
            }

            // allow buffer(s) to be GC'ed.
            readBuffers = null;

            // allow another read to be initiated
            boolean wasScatteringRead = scatteringRead;
            enableReading();

            // result is Integer or Long
            if (wasScatteringRead) {
                result.setResult(Long.valueOf(n));
            } else {
                result.setResult(Integer.valueOf(n));
            }

        } catch (Throwable x) {
            enableReading();
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            result.setFailure(x);
        } finally {
            // restart poll in case of concurrent write
            synchronized (updateLock) {
                if (pending != null)
                    this.pendingRead = pending;
                updateEvents();
            }
            end();
        }

        if (invokeDirect) {
            Invoker.invoke(result.handler(), result);
        } else {
            Invoker.invokeIndirectly(result.handler(), result);
        }
    }

    private Runnable readTimeoutTask = new Runnable() {
        public void run() {
            PendingFuture<Number,Object> result = grabPendingRead();
            if (result == null)
                return;     // already completed

            // kill further reading before releasing waiters
            enableReading(true);

            // set completed and invoke handler
            result.setFailure(new InterruptedByTimeoutException());
            Invoker.invokeIndirectly(result.handler(), result);
        }
    };

    /**
     * Initiates a read or scattering read operation
     */
    @Override
    @SuppressWarnings("unchecked")
    <V extends Number,A> Future<V> readImpl(ByteBuffer[] dsts,
                                            boolean isScatteringRead,
                                            long timeout,
                                            TimeUnit unit,
                                            A attachment,
                                            CompletionHandler<V,? super A> handler)
    {
        // A synchronous read is not attempted if disallowed by system property
        // or, we are using a fixed thread pool and the completion handler may
        // not be invoked directly (because the thread is not a pooled thread or
        // there are too many handlers on the stack).
        Invoker.GroupAndInvokeCount myGroupAndInvokeCount = null;
        boolean invokeDirect = false;
        boolean attemptRead = false;
        if (!disableSynchronousRead) {
            myGroupAndInvokeCount = Invoker.getGroupAndInvokeCount();
            invokeDirect = Invoker.mayInvokeDirect(myGroupAndInvokeCount, port);
            attemptRead = (handler == null) || invokeDirect ||
                !port.isFixedThreadPool();  // okay to attempt read with user thread pool
        }

        AbstractFuture<V,A> result;
        try {
            begin();

            int n;
            if (attemptRead) {
                if (isScatteringRead) {
                    n = (int)IOUtil.read(fd, dsts, nd);
                } else {
                    n = IOUtil.read(fd, dsts[0], -1, nd, null);
                }
            } else {
                n = IOStatus.UNAVAILABLE;
            }

            if (n == IOStatus.UNAVAILABLE) {
                result = new PendingFuture<V,A>(this, handler, attachment, OpType.READ);

                // update evetns so that read will complete asynchronously
                synchronized (updateLock) {
                    this.readBuffers = dsts;
                    this.scatteringRead = isScatteringRead;
                    this.pendingRead = (PendingFuture<Number,Object>)result;
                    updateEvents();
                }

                // schedule timeout
                if (timeout > 0L) {
                    Future<?> timeoutTask =
                        port.schedule(readTimeoutTask, timeout, unit);
                    ((PendingFuture<V,A>)result).setTimeoutTask(timeoutTask);
                }
                return result;
            }

            // data available
            enableReading();

            // result type is Long or Integer
            if (isScatteringRead) {
                result = (CompletedFuture<V,A>)CompletedFuture
                    .withResult(this, Long.valueOf(n), attachment);
            } else {
                result = (CompletedFuture<V,A>)CompletedFuture
                    .withResult(this, Integer.valueOf(n), attachment);
            }
        } catch (Throwable x) {
            enableReading();
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            result = CompletedFuture.withFailure(this, x, attachment);
        } finally {
            end();
        }

        if (invokeDirect) {
            Invoker.invokeDirect(myGroupAndInvokeCount, handler, result);
        } else {
            Invoker.invokeIndirectly(handler, result);
        }
        return result;
    }

    // -- write --

    private void finishWrite(PendingFuture<Number,Object> result,
                             boolean invokeDirect)
    {
        PendingFuture<Number,Object> pending = null;
        try {
            begin();

            ByteBuffer[] srcs = writeBuffers;
            int n;
            if (srcs.length == 1) {
                n = IOUtil.write(fd, srcs[0], -1, nd, null);
            } else {
                n = (int)IOUtil.write(fd, srcs, nd);
            }
            if (n == IOStatus.UNAVAILABLE) {
                // spurious wakeup, is this possible?
                pending = result;
                return;
            }

            // allow buffer(s) to be GC'ed.
            writeBuffers = null;

            // allow another write to be initiated
            boolean wasGatheringWrite = gatheringWrite;
            enableWriting();

            // result is a Long or Integer
            if (wasGatheringWrite) {
                result.setResult(Long.valueOf(n));
            } else {
                result.setResult(Integer.valueOf(n));
            }

        } catch (Throwable x) {
            enableWriting();
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            result.setFailure(x);
        } finally {
            // restart poll in case of concurrent read
            synchronized (this) {
                if (pending != null)
                    this.pendingWrite = pending;
                updateEvents();
            }
            end();
        }
        if (invokeDirect) {
            Invoker.invoke(result.handler(), result);
        } else {
            Invoker.invokeIndirectly(result.handler(), result);
        }
    }

    private Runnable writeTimeoutTask = new Runnable() {
        public void run() {
            PendingFuture<Number,Object> result = grabPendingWrite();
            if (result == null)
                return;     // already completed

            // kill further writing before releasing waiters
            enableWriting(true);

            // set completed and invoke handler
            result.setFailure(new InterruptedByTimeoutException());
            Invoker.invokeIndirectly(result.handler(), result);
        }
    };

    /**
     * Initiates a read or scattering read operation
     */
    @Override
    @SuppressWarnings("unchecked")
    <V extends Number,A> Future<V> writeImpl(ByteBuffer[] srcs,
                                             boolean isGatheringWrite,
                                             long timeout,
                                             TimeUnit unit,
                                             A attachment,
                                             CompletionHandler<V,? super A> handler)
    {
        Invoker.GroupAndInvokeCount myGroupAndInvokeCount =
            Invoker.getGroupAndInvokeCount();
        boolean invokeDirect = Invoker.mayInvokeDirect(myGroupAndInvokeCount, port);
        boolean attemptWrite = (handler == null) || invokeDirect ||
            !port.isFixedThreadPool();  // okay to attempt read with user thread pool

        AbstractFuture<V,A> result;
        try {
            begin();

            int n;
            if (attemptWrite) {
                if (isGatheringWrite) {
                    n = (int)IOUtil.write(fd, srcs, nd);
                } else {
                    n = IOUtil.write(fd, srcs[0], -1, nd, null);
                }
            } else {
                n = IOStatus.UNAVAILABLE;
            }

            if (n == IOStatus.UNAVAILABLE) {
                result = new PendingFuture<V,A>(this, handler, attachment, OpType.WRITE);

                // update evetns so that read will complete asynchronously
                synchronized (updateLock) {
                    this.writeBuffers = srcs;
                    this.gatheringWrite = isGatheringWrite;
                    this.pendingWrite = (PendingFuture<Number,Object>)result;
                    updateEvents();
                }

                // schedule timeout
                if (timeout > 0L) {
                    Future<?> timeoutTask =
                        port.schedule(writeTimeoutTask, timeout, unit);
                    ((PendingFuture<V,A>)result).setTimeoutTask(timeoutTask);
                }
                return result;
            }

            // data available
            enableWriting();
            if (isGatheringWrite) {
                result = (CompletedFuture<V,A>)CompletedFuture
                    .withResult(this, Long.valueOf(n), attachment);
            } else {
                result = (CompletedFuture<V,A>)CompletedFuture
                    .withResult(this, Integer.valueOf(n), attachment);
            }
        } catch (Throwable x) {
            enableWriting();
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            result = CompletedFuture.withFailure(this, x, attachment);
        } finally {
            end();
        }
        if (invokeDirect) {
            Invoker.invokeDirect(myGroupAndInvokeCount, handler, result);
        } else {
            Invoker.invokeIndirectly(handler, result);
        }
        return result;
    }

    // -- Native methods --

    private static native void checkConnect(int fdVal) throws IOException;

    static {
        Util.load();
    }
}
