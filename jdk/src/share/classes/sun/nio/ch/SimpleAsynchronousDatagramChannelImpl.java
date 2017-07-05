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

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;

/**
 * A prototype implementation of AsynchronousDatagramChannel, used to aid
 * test and spec development.
 */

class SimpleAsynchronousDatagramChannelImpl
    extends AsynchronousDatagramChannel implements Groupable, Cancellable
{
    private final DatagramChannel dc;
    private final AsynchronousChannelGroupImpl group;
    private final Object attachKey;
    private boolean closed;

    // used to coordinate timed and blocking reads
    private final Object readLock = new Object();

    // channel blocking mode (requires readLock)
    private boolean isBlocking = true;

    // number of blocking readers (requires readLock)
    private int blockingReaderCount;

    // true if timed read attempted while blocking read in progress (requires readLock)
    private boolean transitionToNonBlocking;

    // true if a blocking read is cancelled (requires readLock)
    private boolean blockingReadKilledByCancel;

    // temporary Selectors used by timed reads (requires readLock)
    private Selector firstReader;
    private Set<Selector> otherReaders;

    SimpleAsynchronousDatagramChannelImpl(ProtocolFamily family,
                                          AsynchronousChannelGroupImpl group)
        throws IOException
    {
        super(group.provider());
        this.dc = (family == null) ?
            DatagramChannel.open() : DatagramChannel.open(family);
        this.group = group;

        // attach this channel to the group as foreign channel
        boolean registered = false;
        try {
            if (!(dc instanceof DatagramChannelImpl))
                throw new UnsupportedOperationException();
            attachKey = group
                .attachForeignChannel(this, ((DatagramChannelImpl)dc).getFD());
            registered = true;
        } finally {
            if (!registered)
                dc.close();
        }
    }

    // throws RuntimeException if blocking read has been cancelled
    private void ensureBlockingReadNotKilled() {
        assert Thread.holdsLock(readLock);
        if (blockingReadKilledByCancel)
            throw new RuntimeException("Reading not allowed due to cancellation");
    }

    // invoke prior to non-timed read/receive
    private void beginNoTimeoutRead() {
        synchronized (readLock) {
            ensureBlockingReadNotKilled();
            if (isBlocking)
                blockingReaderCount++;
        }
    }

    // invoke after non-timed read/receive has completed
    private void endNoTimeoutRead() {
        synchronized (readLock) {
            if (isBlocking) {
                if (--blockingReaderCount == 0 && transitionToNonBlocking) {
                    // notify any threads waiting to make channel non-blocking
                    readLock.notifyAll();
                }
            }
        }
    }

    // invoke prior to timed read
    // returns the timeout remaining
    private long prepareForTimedRead(PendingFuture<?,?> result, long timeout)
        throws IOException
    {
        synchronized (readLock) {
            ensureBlockingReadNotKilled();
            if (isBlocking) {
                transitionToNonBlocking = true;
                while (blockingReaderCount > 0 &&
                       timeout > 0L &&
                       !result.isCancelled())
                {
                    long st = System.currentTimeMillis();
                    try {
                        readLock.wait(timeout);
                    } catch (InterruptedException e) { }
                    timeout -= System.currentTimeMillis() - st;
                }
                if (blockingReaderCount == 0) {
                    // re-check that blocked read wasn't cancelled
                    ensureBlockingReadNotKilled();
                    // no blocking reads so change channel to non-blocking
                    dc.configureBlocking(false);
                    isBlocking = false;
                }
            }
            return timeout;
        }
    }

    // returns a temporary Selector
    private Selector getSelector() throws IOException {
        Selector sel = Util.getTemporarySelector(dc);
        synchronized (readLock) {
            if (firstReader == null) {
                firstReader = sel;
            } else {
                if (otherReaders == null)
                    otherReaders = new HashSet<Selector>();
                otherReaders.add(sel);
            }
        }
        return sel;
    }

    // releases a temporary Selector
    private void releaseSelector(Selector sel) throws IOException {
        synchronized (readLock) {
            if (firstReader == sel) {
                firstReader = null;
            } else {
                otherReaders.remove(sel);
            }
        }
        Util.releaseTemporarySelector(sel);
    }

    // wakeup all Selectors currently in use
    private void wakeupSelectors() {
        synchronized (readLock) {
            if (firstReader != null)
                firstReader.wakeup();
            if (otherReaders != null) {
                for (Selector sel: otherReaders) {
                    sel.wakeup();
                }
            }
        }
    }

    @Override
    public AsynchronousChannelGroupImpl group() {
        return group;
    }

    @Override
    public boolean isOpen() {
        return dc.isOpen();
    }

    @Override
    public void onCancel(PendingFuture<?,?> task) {
        synchronized (readLock) {
            if (blockingReaderCount > 0) {
                blockingReadKilledByCancel = true;
                readLock.notifyAll();
                return;
            }
        }
        wakeupSelectors();
    }

    @Override
    public void close() throws IOException {
        synchronized (dc) {
            if (closed)
                return;
            closed = true;
        }
        // detach from group and close underlying channel
        group.detachForeignChannel(attachKey);
        dc.close();

        // wakeup any threads blocked in timed read/receives
        wakeupSelectors();
    }

    @Override
    public AsynchronousDatagramChannel connect(SocketAddress remote)
        throws IOException
    {
        dc.connect(remote);
        return this;
    }

    @Override
    public AsynchronousDatagramChannel disconnect() throws IOException {
        dc.disconnect();
        return this;
    }

    private static class WrappedMembershipKey extends MembershipKey {
        private final MulticastChannel channel;
        private final MembershipKey key;

        WrappedMembershipKey(MulticastChannel channel, MembershipKey key) {
            this.channel = channel;
            this.key = key;
        }

        @Override
        public boolean isValid() {
            return key.isValid();
        }

        @Override
        public void drop() {
            key.drop();
        }

        @Override
        public MulticastChannel channel() {
            return channel;
        }

        @Override
        public InetAddress group() {
            return key.group();
        }

        @Override
        public NetworkInterface networkInterface() {
            return key.networkInterface();
        }

        @Override
        public InetAddress sourceAddress() {
            return key.sourceAddress();
        }

        @Override
        public MembershipKey block(InetAddress toBlock) throws IOException {
            key.block(toBlock);
            return this;
        }

        @Override
        public MembershipKey unblock(InetAddress toUnblock) {
            key.unblock(toUnblock);
            return this;
        }

        @Override
        public String toString() {
            return key.toString();
        }
    }

    @Override
    public MembershipKey join(InetAddress group,
                              NetworkInterface interf)
        throws IOException
    {
        MembershipKey key = ((MulticastChannel)dc).join(group, interf);
        return new WrappedMembershipKey(this, key);
    }

    @Override
    public MembershipKey join(InetAddress group,
                              NetworkInterface interf,
                              InetAddress source)
        throws IOException
    {
        MembershipKey key = ((MulticastChannel)dc).join(group, interf, source);
        return new WrappedMembershipKey(this, key);
    }

    private <A> Future<Integer> implSend(ByteBuffer src,
                                         SocketAddress target,
                                         A attachment,
                                         CompletionHandler<Integer,? super A> handler)
    {
        int n = 0;
        Throwable exc = null;
        try {
            n = dc.send(src, target);
        } catch (IOException ioe) {
            exc = ioe;
        }
        if (handler == null)
            return CompletedFuture.withResult(n, exc);
        Invoker.invoke(this, handler, attachment, n, exc);
        return null;
    }

    @Override
    public Future<Integer> send(ByteBuffer src, SocketAddress target) {
        return implSend(src, target, null, null);
    }

    @Override
    public <A> void send(ByteBuffer src,
                         SocketAddress target,
                         A attachment,
                         CompletionHandler<Integer,? super A> handler)
    {
        if (handler == null)
            throw new NullPointerException("'handler' is null");
        implSend(src, target, attachment, handler);
    }

    private <A> Future<Integer> implWrite(ByteBuffer src,
                                          A attachment,
                                          CompletionHandler<Integer,? super A> handler)
    {
        int n = 0;
        Throwable exc = null;
        try {
            n = dc.write(src);
        } catch (IOException ioe) {
            exc = ioe;
        }
        if (handler == null)
            return CompletedFuture.withResult(n, exc);
        Invoker.invoke(this, handler, attachment, n, exc);
        return null;

    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return implWrite(src, null, null);
    }

    @Override
    public <A> void write(ByteBuffer src,
                          A attachment,
                          CompletionHandler<Integer,? super A> handler)
    {
        if (handler == null)
            throw new NullPointerException("'handler' is null");
        implWrite(src, attachment, handler);
    }

    /**
     * Receive into the given buffer with privileges enabled and restricted by
     * the given AccessControlContext (can be null).
     */
    private SocketAddress doRestrictedReceive(final ByteBuffer dst,
                                              AccessControlContext acc)
        throws IOException
    {
        if (acc == null) {
            return dc.receive(dst);
        } else {
            try {
                return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<SocketAddress>() {
                        public SocketAddress run() throws IOException {
                            return dc.receive(dst);
                        }}, acc);
            } catch (PrivilegedActionException pae) {
                Exception cause = pae.getException();
                if (cause instanceof SecurityException)
                    throw (SecurityException)cause;
                throw (IOException)cause;
            }
        }
    }

    private <A> Future<SocketAddress> implReceive(final ByteBuffer dst,
                                                  final long timeout,
                                                  final TimeUnit unit,
                                                  A attachment,
                                                  final CompletionHandler<SocketAddress,? super A> handler)
    {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        if (timeout < 0L)
            throw new IllegalArgumentException("Negative timeout");
        if (unit == null)
            throw new NullPointerException();

        // complete immediately if channel closed
        if (!isOpen()) {
            Throwable exc = new ClosedChannelException();
            if (handler == null)
                return CompletedFuture.withFailure(exc);
            Invoker.invoke(this, handler, attachment, null, exc);
            return null;
        }

        final AccessControlContext acc = (System.getSecurityManager() == null) ?
            null : AccessController.getContext();
        final PendingFuture<SocketAddress,A> result =
            new PendingFuture<SocketAddress,A>(this, handler, attachment);
        Runnable task = new Runnable() {
            public void run() {
                try {
                    SocketAddress remote = null;
                    long to;
                    if (timeout == 0L) {
                        beginNoTimeoutRead();
                        try {
                            remote = doRestrictedReceive(dst, acc);
                        } finally {
                            endNoTimeoutRead();
                        }
                        to = 0L;
                    } else {
                        to = prepareForTimedRead(result, unit.toMillis(timeout));
                        if (to <= 0L)
                            throw new InterruptedByTimeoutException();
                        remote = doRestrictedReceive(dst, acc);
                    }
                    if (remote == null) {
                        Selector sel = getSelector();
                        SelectionKey sk = null;
                        try {
                            sk = dc.register(sel, SelectionKey.OP_READ);
                            for (;;) {
                                if (!dc.isOpen())
                                    throw new AsynchronousCloseException();
                                if (result.isCancelled())
                                    break;
                                long st = System.currentTimeMillis();
                                int ns = sel.select(to);
                                if (ns > 0) {
                                    remote = doRestrictedReceive(dst, acc);
                                    if (remote != null)
                                        break;
                                }
                                sel.selectedKeys().remove(sk);
                                if (timeout != 0L) {
                                    to -= System.currentTimeMillis() - st;
                                    if (to <= 0)
                                        throw new InterruptedByTimeoutException();
                                }
                            }
                        } finally {
                            if (sk != null)
                                sk.cancel();
                            releaseSelector(sel);
                        }
                    }
                    result.setResult(remote);
                } catch (Throwable x) {
                    if (x instanceof ClosedChannelException)
                        x = new AsynchronousCloseException();
                    result.setFailure(x);
                }
                Invoker.invokeUnchecked(result);
            }
        };
        try {
            group.executeOnPooledThread(task);
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
        return result;
    }

    @Override
    public Future<SocketAddress> receive(ByteBuffer dst) {
        return implReceive(dst, 0L, TimeUnit.MILLISECONDS, null, null);
    }

    @Override
    public <A> void receive(ByteBuffer dst,
                            long timeout,
                            TimeUnit unit,
                            A attachment,
                            CompletionHandler<SocketAddress,? super A> handler)
    {
        if (handler == null)
            throw new NullPointerException("'handler' is null");
        implReceive(dst, timeout, unit, attachment, handler);
    }

    private <A> Future<Integer> implRead(final ByteBuffer dst,
                                         final long timeout,
                                         final TimeUnit unit,
                                         A attachment,
                                         final CompletionHandler<Integer,? super A> handler)
    {
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");
        if (timeout < 0L)
            throw new IllegalArgumentException("Negative timeout");
        if (unit == null)
            throw new NullPointerException();

        // complete immediately if channel closed
        if (!isOpen()) {
            Throwable exc = new ClosedChannelException();
            if (handler == null)
                return CompletedFuture.withFailure(exc);
            Invoker.invoke(this, handler, attachment, null, exc);
            return null;
        }

        // another thread may disconnect before read is initiated
        if (!dc.isConnected())
            throw new NotYetConnectedException();

        final PendingFuture<Integer,A> result =
            new PendingFuture<Integer,A>(this, handler, attachment);
        Runnable task = new Runnable() {
            public void run() {
                try {
                    int n = 0;
                    long to;
                    if (timeout == 0L) {
                        beginNoTimeoutRead();
                        try {
                            n = dc.read(dst);
                        } finally {
                            endNoTimeoutRead();
                        }
                        to = 0L;
                    } else {
                        to = prepareForTimedRead(result, unit.toMillis(timeout));
                        if (to <= 0L)
                            throw new InterruptedByTimeoutException();
                        n = dc.read(dst);
                    }
                    if (n == 0) {
                        Selector sel = getSelector();
                        SelectionKey sk = null;
                        try {
                            sk = dc.register(sel, SelectionKey.OP_READ);
                            for (;;) {
                                if (!dc.isOpen())
                                    throw new AsynchronousCloseException();
                                if (result.isCancelled())
                                    break;
                                long st = System.currentTimeMillis();
                                int ns = sel.select(to);
                                if (ns > 0) {
                                    if ((n = dc.read(dst)) != 0)
                                        break;
                                }
                                sel.selectedKeys().remove(sk);
                                if (timeout != 0L) {
                                    to -= System.currentTimeMillis() - st;
                                    if (to <= 0)
                                        throw new InterruptedByTimeoutException();
                                }
                            }
                        } finally {
                            if (sk != null)
                                sk.cancel();
                            releaseSelector(sel);
                        }
                    }
                    result.setResult(n);
                } catch (Throwable x) {
                    if (x instanceof ClosedChannelException)
                        x = new AsynchronousCloseException();
                    result.setFailure(x);
                }
                Invoker.invokeUnchecked(result);
            }
        };
        try {
            group.executeOnPooledThread(task);
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
        return result;
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        return implRead(dst, 0L, TimeUnit.MILLISECONDS, null, null);
    }

    @Override
    public <A> void read(ByteBuffer dst,
                            long timeout,
                            TimeUnit unit,
                            A attachment,
                            CompletionHandler<Integer,? super A> handler)
    {
        if (handler == null)
            throw new NullPointerException("'handler' is null");
        implRead(dst, timeout, unit, attachment, handler);
    }

    @Override
    public  AsynchronousDatagramChannel bind(SocketAddress local)
        throws IOException
    {
        dc.bind(local);
        return this;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return dc.getLocalAddress();
    }

    @Override
    public <T> AsynchronousDatagramChannel setOption(SocketOption<T> name, T value)
        throws IOException
    {
        dc.setOption(name, value);
        return this;
    }

    @Override
    public  <T> T getOption(SocketOption<T> name) throws IOException {
        return dc.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return dc.supportedOptions();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return dc.getRemoteAddress();
    }
}
