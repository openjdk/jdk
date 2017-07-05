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
import java.util.concurrent.*;
import java.io.IOException;
import java.io.FileDescriptor;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Unix implementation of AsynchronousServerSocketChannel
 */

class UnixAsynchronousServerSocketChannelImpl
    extends AsynchronousServerSocketChannelImpl
    implements Port.PollableChannel
{
    private final static NativeDispatcher nd = new SocketDispatcher();

    private final Port port;
    private final int fdVal;

    // flag to indicate an accept is outstanding
    private final AtomicBoolean accepting = new AtomicBoolean();
    private void enableAccept() {
        accepting.set(false);
    }

    // used to ensure that the context for an asynchronous accept is visible
    // the pooled thread that handles the I/O event
    private final Object updateLock = new Object();

    // pending accept
    private PendingFuture<AsynchronousSocketChannel,Object> pendingAccept;

    // context for permission check when security manager set
    private AccessControlContext acc;


    UnixAsynchronousServerSocketChannelImpl(Port port)
        throws IOException
    {
        super(port);

        try {
            IOUtil.configureBlocking(fd, false);
        } catch (IOException x) {
            nd.close(fd);  // prevent leak
            throw x;
        }
        this.port = port;
        this.fdVal = IOUtil.fdVal(fd);

        // add mapping from file descriptor to this channel
        port.register(fdVal, this);
    }

    // returns and clears the result of a pending accept
    private PendingFuture<AsynchronousSocketChannel,Object> grabPendingAccept() {
        synchronized (updateLock) {
            PendingFuture<AsynchronousSocketChannel,Object> result = pendingAccept;
            pendingAccept = null;
            return result;
        }
    }

    @Override
    void implClose() throws IOException {
        // remove the mapping
        port.unregister(fdVal);

        // close file descriptor
        nd.close(fd);

        // if there is a pending accept then complete it
        final PendingFuture<AsynchronousSocketChannel,Object> result =
            grabPendingAccept();
        if (result != null) {
            // discard the stack trace as otherwise it may appear that implClose
            // has thrown the exception.
            AsynchronousCloseException x = new AsynchronousCloseException();
            x.setStackTrace(new StackTraceElement[0]);
            result.setFailure(x);

            // invoke by submitting task rather than directly
            Invoker.invokeIndirectly(result.handler(), result);
        }
    }

    @Override
    public AsynchronousChannelGroupImpl group() {
        return port;
    }

    /**
     * Invoked by event handling thread when listener socket is polled
     */
    @Override
    public void onEvent(int events) {
        PendingFuture<AsynchronousSocketChannel,Object> result = grabPendingAccept();
        if (result == null)
            return; // may have been grabbed by asynchronous close

        // attempt to accept connection
        FileDescriptor newfd = new FileDescriptor();
        InetSocketAddress[] isaa = new InetSocketAddress[1];
        boolean accepted = false;
        try {
            begin();
            int n = accept0(this.fd, newfd, isaa);

            // spurious wakeup, is this possible?
            if (n == IOStatus.UNAVAILABLE) {
                synchronized (updateLock) {
                    this.pendingAccept = result;
                }
                port.startPoll(fdVal, Port.POLLIN);
                return;
            }

            // connection accepted
            accepted = true;

        } catch (Throwable x) {
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            enableAccept();
            result.setFailure(x);
        } finally {
            end();
        }

        // Connection accepted so finish it when not holding locks.
        AsynchronousSocketChannel child = null;
        if (accepted) {
            try {
                child = finishAccept(newfd, isaa[0], acc);
                enableAccept();
                result.setResult(child);
            } catch (Throwable x) {
                enableAccept();
                if (!(x instanceof IOException) && !(x instanceof SecurityException))
                    x = new IOException(x);
                result.setFailure(x);
            }
        }

        // if an async cancel has already cancelled the operation then
        // close the new channel so as to free resources
        if (child != null && result.isCancelled()) {
            try {
                child.close();
            } catch (IOException ignore) { }
        }

        // invoke the handler
        Invoker.invoke(result.handler(), result);
    }

    /**
     * Completes the accept by creating the AsynchronousSocketChannel for
     * the given file descriptor and remote address. If this method completes
     * with an IOException or SecurityException then the channel/file descriptor
     * will be closed.
     */
    private AsynchronousSocketChannel finishAccept(FileDescriptor newfd,
                                                   final InetSocketAddress remote,
                                                   AccessControlContext acc)
        throws IOException, SecurityException
    {
        AsynchronousSocketChannel ch = null;
        try {
            ch = new UnixAsynchronousSocketChannelImpl(port, newfd, remote);
        } catch (IOException x) {
            nd.close(newfd);
            throw x;
        }

        // permission check must always be in initiator's context
        try {
            if (acc != null) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        SecurityManager sm = System.getSecurityManager();
                        if (sm != null) {
                            sm.checkAccept(remote.getAddress().getHostAddress(),
                                           remote.getPort());
                        }
                        return null;
                    }
                }, acc);
            } else {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    sm.checkAccept(remote.getAddress().getHostAddress(),
                                   remote.getPort());
                }
            }
        } catch (SecurityException x) {
            try {
                ch.close();
            } catch (IOException ignore) { }
            throw x;
        }
        return ch;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> Future<AsynchronousSocketChannel> accept(A attachment,
        final CompletionHandler<AsynchronousSocketChannel,? super A> handler)
    {
        // complete immediately if channel is closed
        if (!isOpen()) {
            CompletedFuture<AsynchronousSocketChannel,A> result = CompletedFuture
                .withFailure(this, new ClosedChannelException(), attachment);
            Invoker.invokeIndirectly(handler, result);
            return result;
        }
        if (localAddress == null)
            throw new NotYetBoundException();

        // cancel was invoked with pending accept so connection may have been
        // dropped.
        if (isAcceptKilled())
            throw new RuntimeException("Accept not allowed due cancellation");

        // check and set flag to prevent concurrent accepting
        if (!accepting.compareAndSet(false, true))
            throw new AcceptPendingException();

        // attempt accept
        AbstractFuture<AsynchronousSocketChannel,A> result = null;
        FileDescriptor newfd = new FileDescriptor();
        InetSocketAddress[] isaa = new InetSocketAddress[1];
        try {
            begin();

            int n = accept0(this.fd, newfd, isaa);
            if (n == IOStatus.UNAVAILABLE) {
                // no connection to accept
                result = new PendingFuture<AsynchronousSocketChannel,A>(this, handler, attachment);

                // need calling context when there is security manager as
                // permission check may be done in a different thread without
                // any application call frames on the stack
                synchronized (this) {
                    this.acc = (System.getSecurityManager() == null) ?
                        null : AccessController.getContext();
                    this.pendingAccept =
                        (PendingFuture<AsynchronousSocketChannel,Object>)result;
                }

                // register for connections
                port.startPoll(fdVal, Port.POLLIN);
                return result;
            }
        } catch (Throwable x) {
            // accept failed
            if (x instanceof ClosedChannelException)
                x = new AsynchronousCloseException();
            result = CompletedFuture.withFailure(this, x, attachment);
        } finally {
            end();
        }

        // connection accepted immediately
        if (result == null) {
            try {
                AsynchronousSocketChannel ch = finishAccept(newfd, isaa[0], null);
                result = CompletedFuture.withResult(this, ch, attachment);
            } catch (Throwable x) {
                result = CompletedFuture.withFailure(this, x, attachment);
            }
        }

        // re-enable accepting and invoke handler
        enableAccept();
        Invoker.invokeIndirectly(handler, result);
        return result;
    }

    // -- Native methods --

    private static native void initIDs();

    // Accepts a new connection, setting the given file descriptor to refer to
    // the new socket and setting isaa[0] to the socket's remote address.
    // Returns 1 on success, or IOStatus.UNAVAILABLE.
    //
    private native int accept0(FileDescriptor ssfd, FileDescriptor newfd,
                               InetSocketAddress[] isaa)
        throws IOException;

    static {
        Util.load();
        initIDs();
    }
}
