/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.spi.SelectorProvider;
import com.sun.nio.sctp.AbstractNotificationHandler;
import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.HandlerResult;
import com.sun.nio.sctp.IllegalReceiveException;
import com.sun.nio.sctp.InvalidStreamException;
import com.sun.nio.sctp.IllegalUnbindException;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.NotificationHandler;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpSocketOption;
import sun.nio.ch.PollArrayWrapper;
import sun.nio.ch.SelChImpl;
import static com.sun.nio.sctp.SctpStandardSocketOption.*;
import static sun.nio.ch.SctpResultContainer.SEND_FAILED;
import static sun.nio.ch.SctpResultContainer.ASSOCIATION_CHANGED;
import static sun.nio.ch.SctpResultContainer.PEER_ADDRESS_CHANGED;
import static sun.nio.ch.SctpResultContainer.SHUTDOWN;

/**
 * An implementation of an SctpChannel
 */
public class SctpChannelImpl extends SctpChannel
    implements SelChImpl
{
    private final FileDescriptor fd;

    private final int fdVal;

    /* IDs of native threads doing send and receivess, for signalling */
    private volatile long receiverThread = 0;
    private volatile long senderThread = 0;

    /* Lock held by current receiving or connecting thread */
    private final Object receiveLock = new Object();

    /* Lock held by current sending or connecting thread */
    private final Object sendLock = new Object();

    private final ThreadLocal<Boolean> receiveInvoked =
        new ThreadLocal<Boolean>() {
             @Override protected Boolean initialValue() {
                 return Boolean.FALSE;
            }
    };

    /* Lock held by any thread that modifies the state fields declared below
       DO NOT invoke a blocking I/O operation while holding this lock! */
    private final Object stateLock = new Object();

    private enum ChannelState {
        UNINITIALIZED,
        UNCONNECTED,
        PENDING,
        CONNECTED,
        KILLPENDING,
        KILLED,
    }
    /* -- The following fields are protected by stateLock -- */
    private ChannelState state = ChannelState.UNINITIALIZED;

    /* Binding; Once bound the port will remain constant. */
    int port = -1;
    private HashSet<InetSocketAddress> localAddresses = new HashSet<InetSocketAddress>();
    /* Has the channel been bound to the wildcard address */
    private boolean wildcard; /* false */
    //private InetSocketAddress remoteAddress = null;

    /* Input/Output open */
    private boolean readyToConnect;

    /* Shutdown */
    private boolean isShutdown;

    private Association association;

    private Set<SocketAddress> remoteAddresses = Collections.EMPTY_SET;

    /* -- End of fields protected by stateLock -- */

    /**
     * Constructor for normal connecting sockets
     */
    public SctpChannelImpl(SelectorProvider provider) throws IOException {
        //TODO: update provider remove public modifier
        super(provider);
        this.fd = SctpNet.socket(true);
        this.fdVal = IOUtil.fdVal(fd);
        this.state = ChannelState.UNCONNECTED;
    }

    /**
     * Constructor for sockets obtained from server sockets
     */
    public SctpChannelImpl(SelectorProvider provider, FileDescriptor fd)
         throws IOException {
        this(provider, fd, null);
    }

    /**
     * Constructor for sockets obtained from branching
     */
    public SctpChannelImpl(SelectorProvider provider,
                           FileDescriptor fd,
                           Association association)
            throws IOException {
        super(provider);
        this.fd = fd;
        this.fdVal = IOUtil.fdVal(fd);
        this.state = ChannelState.CONNECTED;
        port = (Net.localAddress(fd)).getPort();

        if (association != null) { /* branched */
            this.association = association;
        } else { /* obtained from server channel */
            /* Receive COMM_UP */
            ByteBuffer buf = Util.getTemporaryDirectBuffer(50);
            try {
                receive(buf, null, null, true);
            } finally {
                Util.releaseTemporaryDirectBuffer(buf);
            }
        }
    }

    /**
     * Binds the channel's socket to a local address.
     */
    @Override
    public SctpChannel bind(SocketAddress local) throws IOException {
        synchronized (receiveLock) {
            synchronized (sendLock) {
                synchronized (stateLock) {
                    ensureOpenAndUnconnected();
                    if (isBound())
                        SctpNet.throwAlreadyBoundException();
                    InetSocketAddress isa = (local == null) ?
                        new InetSocketAddress(0) : Net.checkAddress(local);
                    Net.bind(fd, isa.getAddress(), isa.getPort());
                    InetSocketAddress boundIsa = Net.localAddress(fd);
                    port = boundIsa.getPort();
                    localAddresses.add(isa);
                    if (isa.getAddress().isAnyLocalAddress())
                        wildcard = true;
                }
            }
        }
        return this;
    }

    @Override
    public SctpChannel bindAddress(InetAddress address)
            throws IOException {
        bindUnbindAddress(address, true);
        localAddresses.add(new InetSocketAddress(address, port));
        return this;
    }

    @Override
    public SctpChannel unbindAddress(InetAddress address)
            throws IOException {
        bindUnbindAddress(address, false);
        localAddresses.remove(new InetSocketAddress(address, port));
        return this;
    }

    private SctpChannel bindUnbindAddress(InetAddress address, boolean add)
            throws IOException {
        if (address == null)
            throw new IllegalArgumentException();

        synchronized (receiveLock) {
            synchronized (sendLock) {
                synchronized (stateLock) {
                    if (!isOpen())
                        throw new ClosedChannelException();
                    if (!isBound())
                        throw new NotYetBoundException();
                    if (wildcard)
                        throw new IllegalStateException(
                                "Cannot add or remove addresses from a channel that is bound to the wildcard address");
                    if (address.isAnyLocalAddress())
                        throw new IllegalArgumentException(
                                "Cannot add or remove the wildcard address");
                    if (add) {
                        for (InetSocketAddress addr : localAddresses) {
                            if (addr.getAddress().equals(address)) {
                                SctpNet.throwAlreadyBoundException();
                            }
                        }
                    } else { /*removing */
                        /* Verify that there is more than one address
                         * and that address is already bound */
                        if (localAddresses.size() <= 1)
                            throw new IllegalUnbindException("Cannot remove address from a channel with only one address bound");
                        boolean foundAddress = false;
                        for (InetSocketAddress addr : localAddresses) {
                            if (addr.getAddress().equals(address)) {
                                foundAddress = true;
                                break;
                            }
                        }
                        if (!foundAddress )
                            throw new IllegalUnbindException("Cannot remove address from a channel that is not bound to that address");
                    }

                    SctpNet.bindx(fdVal, new InetAddress[]{address}, port, add);

                    /* Update our internal Set to reflect the addition/removal */
                    if (add)
                        localAddresses.add(new InetSocketAddress(address, port));
                    else {
                        for (InetSocketAddress addr : localAddresses) {
                            if (addr.getAddress().equals(address)) {
                                localAddresses.remove(addr);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return this;
    }

    private boolean isBound() {
        synchronized (stateLock) {
            return port == -1 ? false : true;
        }
    }

    private boolean isConnected() {
        synchronized (stateLock) {
            return (state == ChannelState.CONNECTED);
        }
    }

    private void ensureOpenAndUnconnected() throws IOException {
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (isConnected())
                throw new AlreadyConnectedException();
            if (state == ChannelState.PENDING)
                throw new ConnectionPendingException();
        }
    }

    private boolean ensureReceiveOpen() throws ClosedChannelException {
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (!isConnected())
                throw new NotYetConnectedException();
            else
                return true;
        }
    }

    private void ensureSendOpen() throws ClosedChannelException {
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (isShutdown)
                throw new ClosedChannelException();
            if (!isConnected())
                throw new NotYetConnectedException();
        }
    }

    private void receiverCleanup() throws IOException {
        synchronized (stateLock) {
            receiverThread = 0;
            if (state == ChannelState.KILLPENDING)
                kill();
        }
    }

    private void senderCleanup() throws IOException {
        synchronized (stateLock) {
            senderThread = 0;
            if (state == ChannelState.KILLPENDING)
                kill();
        }
    }

    @Override
    public Association association() throws ClosedChannelException {
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (!isConnected())
                return null;

            return association;
        }
    }

    @Override
    public boolean connect(SocketAddress endpoint) throws IOException {
        synchronized (receiveLock) {
            synchronized (sendLock) {
                ensureOpenAndUnconnected();
                InetSocketAddress isa = Net.checkAddress(endpoint);
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkConnect(isa.getAddress().getHostAddress(),
                                    isa.getPort());
                synchronized (blockingLock()) {
                    int n = 0;
                    try {
                        try {
                            begin();
                            synchronized (stateLock) {
                                if (!isOpen()) {
                                    return false;
                                }
                                receiverThread = NativeThread.current();
                            }
                            for (;;) {
                                InetAddress ia = isa.getAddress();
                                if (ia.isAnyLocalAddress())
                                    ia = InetAddress.getLocalHost();
                                n = SctpNet.connect(fdVal, ia, isa.getPort());
                                if (  (n == IOStatus.INTERRUPTED)
                                      && isOpen())
                                    continue;
                                break;
                            }
                        } finally {
                            receiverCleanup();
                            end((n > 0) || (n == IOStatus.UNAVAILABLE));
                            assert IOStatus.check(n);
                        }
                    } catch (IOException x) {
                        /* If an exception was thrown, close the channel after
                         * invoking end() so as to avoid bogus
                         * AsynchronousCloseExceptions */
                        close();
                        throw x;
                    }

                    if (n > 0) {
                        synchronized (stateLock) {
                            /* Connection succeeded */
                            state = ChannelState.CONNECTED;
                            if (!isBound()) {
                                InetSocketAddress boundIsa =
                                        Net.localAddress(fd);
                                port = boundIsa.getPort();
                            }

                            /* Receive COMM_UP */
                            ByteBuffer buf = Util.getTemporaryDirectBuffer(50);
                            try {
                                receive(buf, null, null, true);
                            } finally {
                                Util.releaseTemporaryDirectBuffer(buf);
                            }

                            /* cache remote addresses */
                            try {
                                remoteAddresses = getRemoteAddresses();
                            } catch (IOException unused) { /* swallow exception */ }

                            return true;
                        }
                    } else  {
                        synchronized (stateLock) {
                            /* If nonblocking and no exception then connection
                             * pending; disallow another invocation */
                            if (!isBlocking())
                                state = ChannelState.PENDING;
                            else
                                assert false;
                        }
                    }
                }
                return false;
            }
        }
    }

    @Override
    public boolean connect(SocketAddress endpoint,
                           int maxOutStreams,
                           int maxInStreams)
            throws IOException {
        ensureOpenAndUnconnected();
        return setOption(SCTP_INIT_MAXSTREAMS, InitMaxStreams.
                create(maxInStreams, maxOutStreams)).connect(endpoint);

    }

    @Override
    public boolean isConnectionPending() {
        synchronized (stateLock) {
            return (state == ChannelState.PENDING);
        }
    }

    @Override
    public boolean finishConnect() throws IOException {
        synchronized (receiveLock) {
            synchronized (sendLock) {
                synchronized (stateLock) {
                    if (!isOpen())
                        throw new ClosedChannelException();
                    if (isConnected())
                        return true;
                    if (state != ChannelState.PENDING)
                        throw new NoConnectionPendingException();
                }
                int n = 0;
                try {
                    try {
                        begin();
                        synchronized (blockingLock()) {
                            synchronized (stateLock) {
                                if (!isOpen()) {
                                    return false;
                                }
                                receiverThread = NativeThread.current();
                            }
                            if (!isBlocking()) {
                                for (;;) {
                                    n = checkConnect(fd, false, readyToConnect);
                                    if (  (n == IOStatus.INTERRUPTED)
                                          && isOpen())
                                        continue;
                                    break;
                                }
                            } else {
                                for (;;) {
                                    n = checkConnect(fd, true, readyToConnect);
                                    if (n == 0) {
                                        // Loop in case of
                                        // spurious notifications
                                        continue;
                                    }
                                    if (  (n == IOStatus.INTERRUPTED)
                                          && isOpen())
                                        continue;
                                    break;
                                }
                            }
                        }
                    } finally {
                        synchronized (stateLock) {
                            receiverThread = 0;
                            if (state == ChannelState.KILLPENDING) {
                                kill();
                                /* poll()/getsockopt() does not report
                                 * error (throws exception, with n = 0)
                                 * on Linux platform after dup2 and
                                 * signal-wakeup. Force n to 0 so the
                                 * end() can throw appropriate exception */
                                n = 0;
                            }
                        }
                        end((n > 0) || (n == IOStatus.UNAVAILABLE));
                        assert IOStatus.check(n);
                    }
                } catch (IOException x) {
                    /* If an exception was thrown, close the channel after
                     * invoking end() so as to avoid bogus
                     * AsynchronousCloseExceptions */
                    close();
                    throw x;
                }

                if (n > 0) {
                    synchronized (stateLock) {
                        state = ChannelState.CONNECTED;
                        if (!isBound()) {
                            InetSocketAddress boundIsa =
                                    Net.localAddress(fd);
                            port = boundIsa.getPort();
                        }

                        /* Receive COMM_UP */
                        ByteBuffer buf = Util.getTemporaryDirectBuffer(50);
                        try {
                            receive(buf, null, null, true);
                        } finally {
                            Util.releaseTemporaryDirectBuffer(buf);
                        }

                        /* cache remote addresses */
                        try {
                            remoteAddresses = getRemoteAddresses();
                        } catch (IOException unused) { /* swallow exception */ }

                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        IOUtil.configureBlocking(fd, block);
    }

    @Override
    public void implCloseSelectableChannel() throws IOException {
        synchronized (stateLock) {
            SctpNet.preClose(fdVal);

            if (receiverThread != 0)
                NativeThread.signal(receiverThread);

            if (senderThread != 0)
                NativeThread.signal(senderThread);

            if (!isRegistered())
                kill();
        }
    }

    @Override
    public FileDescriptor getFD() {
        return fd;
    }

    @Override
    public int getFDVal() {
        return fdVal;
    }

    /**
     * Translates native poll revent ops into a ready operation ops
     */
    private boolean translateReadyOps(int ops, int initialOps, SelectionKeyImpl sk) {
        int intOps = sk.nioInterestOps();
        int oldOps = sk.nioReadyOps();
        int newOps = initialOps;

        if ((ops & PollArrayWrapper.POLLNVAL) != 0) {
            /* This should only happen if this channel is pre-closed while a
             * selection operation is in progress
             * ## Throw an error if this channel has not been pre-closed */
            return false;
        }

        if ((ops & (PollArrayWrapper.POLLERR
                    | PollArrayWrapper.POLLHUP)) != 0) {
            newOps = intOps;
            sk.nioReadyOps(newOps);
            /* No need to poll again in checkConnect,
             * the error will be detected there */
            readyToConnect = true;
            return (newOps & ~oldOps) != 0;
        }

        if (((ops & PollArrayWrapper.POLLIN) != 0) &&
            ((intOps & SelectionKey.OP_READ) != 0) &&
            isConnected())
            newOps |= SelectionKey.OP_READ;

        if (((ops & PollArrayWrapper.POLLCONN) != 0) &&
            ((intOps & SelectionKey.OP_CONNECT) != 0) &&
            ((state == ChannelState.UNCONNECTED) || (state == ChannelState.PENDING))) {
            newOps |= SelectionKey.OP_CONNECT;
            readyToConnect = true;
        }

        if (((ops & PollArrayWrapper.POLLOUT) != 0) &&
            ((intOps & SelectionKey.OP_WRITE) != 0) &&
            isConnected())
            newOps |= SelectionKey.OP_WRITE;

        sk.nioReadyOps(newOps);
        return (newOps & ~oldOps) != 0;
    }

    @Override
    public boolean translateAndUpdateReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, sk.nioReadyOps(), sk);
    }

    @Override
    @SuppressWarnings("all")
    public boolean translateAndSetReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, 0, sk);
    }

    @Override
    public void translateAndSetInterestOps(int ops, SelectionKeyImpl sk) {
        int newOps = 0;
        if ((ops & SelectionKey.OP_READ) != 0)
            newOps |= PollArrayWrapper.POLLIN;
        if ((ops & SelectionKey.OP_WRITE) != 0)
            newOps |= PollArrayWrapper.POLLOUT;
        if ((ops & SelectionKey.OP_CONNECT) != 0)
            newOps |= PollArrayWrapper.POLLCONN;
        sk.selector.putEventOps(sk, newOps);
    }

    @Override
    public void kill() throws IOException {
        synchronized (stateLock) {
            if (state == ChannelState.KILLED)
                return;
            if (state == ChannelState.UNINITIALIZED) {
                state = ChannelState.KILLED;
                return;
            }
            assert !isOpen() && !isRegistered();

            /* Postpone the kill if there is a waiting reader
             * or writer thread. */
            if (receiverThread == 0 && senderThread == 0) {
                SctpNet.close(fdVal);
                state = ChannelState.KILLED;
            } else {
                state = ChannelState.KILLPENDING;
            }
        }
    }

    @Override
    public <T> SctpChannel setOption(SctpSocketOption<T> name, T value)
            throws IOException {
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();

            SctpNet.setSocketOption(fdVal, name, value, 0 /*oneToOne*/);
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SctpSocketOption<T> name) throws IOException {
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();

            return (T)SctpNet.getSocketOption(fdVal, name, 0 /*oneToOne*/);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SctpSocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SctpSocketOption<?>> defaultOptions() {
            HashSet<SctpSocketOption<?>> set = new HashSet<SctpSocketOption<?>>(10);
            set.add(SCTP_DISABLE_FRAGMENTS);
            set.add(SCTP_EXPLICIT_COMPLETE);
            set.add(SCTP_FRAGMENT_INTERLEAVE);
            set.add(SCTP_INIT_MAXSTREAMS);
            set.add(SCTP_NODELAY);
            set.add(SCTP_PRIMARY_ADDR);
            set.add(SCTP_SET_PEER_PRIMARY_ADDR);
            set.add(SO_SNDBUF);
            set.add(SO_RCVBUF);
            set.add(SO_LINGER);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SctpSocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    public <T> MessageInfo receive(ByteBuffer buffer,
                                   T attachment,
                                   NotificationHandler<T> handler)
            throws IOException {
        return receive(buffer, attachment, handler, false);
    }

    private <T> MessageInfo receive(ByteBuffer buffer,
                                    T attachment,
                                    NotificationHandler<T> handler,
                                    boolean fromConnect)
            throws IOException {
        if (buffer == null)
            throw new IllegalArgumentException("buffer cannot be null");

        if (buffer.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");

        if (receiveInvoked.get())
            throw new IllegalReceiveException(
                    "cannot invoke receive from handler");
        receiveInvoked.set(Boolean.TRUE);

        try {
            SctpResultContainer resultContainer = new SctpResultContainer();
            do {
                resultContainer.clear();
                synchronized (receiveLock) {
                    if (!ensureReceiveOpen())
                        return null;

                    int n = 0;
                    try {
                        begin();

                        synchronized (stateLock) {
                            if(!isOpen())
                                return null;
                            receiverThread = NativeThread.current();
                        }

                        do {
                            n = receive(fdVal, buffer, resultContainer, fromConnect);
                        } while ((n == IOStatus.INTERRUPTED) && isOpen());
                    } finally {
                        receiverCleanup();
                        end((n > 0) || (n == IOStatus.UNAVAILABLE));
                        assert IOStatus.check(n);
                    }

                    if (!resultContainer.isNotification()) {
                        /* message or nothing */
                        if (resultContainer.hasSomething()) {
                            /* Set the association before returning */
                            SctpMessageInfoImpl info =
                                    resultContainer.getMessageInfo();
                            synchronized (stateLock) {
                                assert association != null;
                                info.setAssociation(association);
                            }
                            return info;
                        } else
                            /* Non-blocking may return null if nothing available*/
                            return null;
                    } else { /* notification */
                        synchronized (stateLock) {
                            handleNotificationInternal(
                                    resultContainer);
                        }
                    }

                    if (fromConnect)  {
                        /* If we reach here, then it was connect that invoked
                         * receive and received the COMM_UP. We have already
                         * handled the COMM_UP with the internal notification
                         * handler. Simply return. */
                        return null;
                    }
                }  /* receiveLock */
            } while (handler == null ? true :
                (invokeNotificationHandler(resultContainer, handler, attachment)
                 == HandlerResult.CONTINUE));

            return null;
        } finally {
            receiveInvoked.set(Boolean.FALSE);
        }
    }

    private int receive(int fd,
                        ByteBuffer dst,
                        SctpResultContainer resultContainer,
                        boolean peek)
            throws IOException {
        int pos = dst.position();
        int lim = dst.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        if (dst instanceof DirectBuffer && rem > 0)
            return receiveIntoNativeBuffer(fd, resultContainer, dst, rem, pos, peek);

        /* Substitute a native buffer */
        int newSize = Math.max(rem, 1);
        ByteBuffer bb = Util.getTemporaryDirectBuffer(newSize);
        try {
            int n = receiveIntoNativeBuffer(fd, resultContainer, bb, newSize, 0, peek);
            bb.flip();
            if (n > 0 && rem > 0)
                dst.put(bb);
            return n;
        } finally {
            Util.releaseTemporaryDirectBuffer(bb);
        }
    }

    private int receiveIntoNativeBuffer(int fd,
                                        SctpResultContainer resultContainer,
                                        ByteBuffer bb,
                                        int rem,
                                        int pos,
                                        boolean peek)
        throws IOException
    {
        int n = receive0(fd, resultContainer, ((DirectBuffer)bb).address() + pos, rem, peek);

        if (n > 0)
            bb.position(pos + n);
        return n;
    }

    private InternalNotificationHandler<?> internalNotificationHandler =
            new InternalNotificationHandler();

    private void handleNotificationInternal(SctpResultContainer resultContainer)
    {
        invokeNotificationHandler(resultContainer,
                internalNotificationHandler, null);
    }

    private class InternalNotificationHandler<T>
            extends AbstractNotificationHandler<T>
    {
        @Override
        public HandlerResult handleNotification(
                AssociationChangeNotification not, T unused) {
            if (not.event().equals(
                    AssociationChangeNotification.AssocChangeEvent.COMM_UP) &&
                    association == null) {
                SctpAssocChange sac = (SctpAssocChange) not;
                association = new SctpAssociationImpl
                       (sac.assocId(), sac.maxInStreams(), sac.maxOutStreams());
            }
            return HandlerResult.CONTINUE;
        }
    }

    private <T> HandlerResult invokeNotificationHandler
                                 (SctpResultContainer resultContainer,
                                  NotificationHandler<T> handler,
                                  T attachment) {
        SctpNotification notification = resultContainer.notification();
        synchronized (stateLock) {
            notification.setAssociation(association);
        }

        if (!(handler instanceof AbstractNotificationHandler)) {
            return handler.handleNotification(notification, attachment);
        }

        /* AbstractNotificationHandler */
        AbstractNotificationHandler absHandler =
                (AbstractNotificationHandler)handler;
        switch(resultContainer.type()) {
            case ASSOCIATION_CHANGED :
                return absHandler.handleNotification(
                        resultContainer.getAssociationChanged(), attachment);
            case PEER_ADDRESS_CHANGED :
                return absHandler.handleNotification(
                        resultContainer.getPeerAddressChanged(), attachment);
            case SEND_FAILED :
                return absHandler.handleNotification(
                        resultContainer.getSendFailed(), attachment);
            case SHUTDOWN :
                return absHandler.handleNotification(
                        resultContainer.getShutdown(), attachment);
            default :
                /* implementation specific handlers */
                return absHandler.handleNotification(
                        resultContainer.notification(), attachment);
        }
    }

    private void checkAssociation(Association sendAssociation) {
        synchronized (stateLock) {
            if (sendAssociation != null && !sendAssociation.equals(association)) {
                throw new IllegalArgumentException(
                        "Cannot send to another association");
            }
        }
    }

    private void checkStreamNumber(int streamNumber) {
        synchronized (stateLock) {
            if (association != null) {
                if (streamNumber < 0 ||
                      streamNumber >= association.maxOutboundStreams())
                    throw new InvalidStreamException();
            }
        }
    }

    /* TODO: Add support for ttl and isComplete to both 121 12M
     *       SCTP_EOR not yet supported on reference platforms
     *       TTL support limited...
     */
    @Override
    public int send(ByteBuffer buffer, MessageInfo messageInfo)
            throws IOException {
        if (buffer == null)
            throw new IllegalArgumentException("buffer cannot be null");

        if (messageInfo == null)
            throw new IllegalArgumentException("messageInfo cannot be null");

        checkAssociation(messageInfo.association());
        checkStreamNumber(messageInfo.streamNumber());

        synchronized (sendLock) {
            ensureSendOpen();

            int n = 0;
            try {
                begin();

                synchronized (stateLock) {
                    if(!isOpen())
                        return 0;
                    senderThread = NativeThread.current();
                }

                do {
                    n = send(fdVal, buffer, messageInfo);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());

                return IOStatus.normalize(n);
            } finally {
                senderCleanup();
                end((n > 0) || (n == IOStatus.UNAVAILABLE));
                assert IOStatus.check(n);
            }
        }
    }

    private int send(int fd, ByteBuffer src, MessageInfo messageInfo)
            throws IOException {
        int streamNumber = messageInfo.streamNumber();
        SocketAddress target = messageInfo.address();
        boolean unordered = messageInfo.isUnordered();
        int ppid = messageInfo.payloadProtocolID();

        if (src instanceof DirectBuffer)
            return sendFromNativeBuffer(fd, src, target, streamNumber,
                    unordered, ppid);

        /* Substitute a native buffer */
        int pos = src.position();
        int lim = src.limit();
        assert (pos <= lim && streamNumber >= 0);

        int rem = (pos <= lim ? lim - pos : 0);
        ByteBuffer bb = Util.getTemporaryDirectBuffer(rem);
        try {
            bb.put(src);
            bb.flip();
            /* Do not update src until we see how many bytes were written */
            src.position(pos);

            int n = sendFromNativeBuffer(fd, bb, target, streamNumber,
                    unordered, ppid);
            if (n > 0) {
                /* now update src */
                src.position(pos + n);
            }
            return n;
        } finally {
            Util.releaseTemporaryDirectBuffer(bb);
        }
    }

    private int sendFromNativeBuffer(int fd,
                                     ByteBuffer bb,
                                     SocketAddress target,
                                     int streamNumber,
                                     boolean unordered,
                                     int ppid)
            throws IOException {
        int pos = bb.position();
        int lim = bb.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);

        int written = send0(fd, ((DirectBuffer)bb).address() + pos,
                            rem, target, -1 /*121*/, streamNumber, unordered, ppid);
        if (written > 0)
            bb.position(pos + written);
        return written;
    }

    @Override
    public SctpChannel shutdown() throws IOException {
        synchronized(stateLock) {
            if (isShutdown)
                return this;

            ensureSendOpen();
            SctpNet.shutdown(fdVal, -1);
            if (senderThread != 0)
                NativeThread.signal(senderThread);
            isShutdown = true;
        }
        return this;
    }

    @Override
    public Set<SocketAddress> getAllLocalAddresses()
            throws IOException {
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (!isBound())
                return Collections.EMPTY_SET;

            return SctpNet.getLocalAddresses(fdVal);
        }
    }

    @Override
    public Set<SocketAddress> getRemoteAddresses()
            throws IOException {
        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (!isConnected() || isShutdown)
                return Collections.EMPTY_SET;

            try {
                return SctpNet.getRemoteAddresses(fdVal, 0/*unused*/);
            } catch (SocketException unused) {
                /* an open connected channel should always have remote addresses */
                return remoteAddresses;
            }
        }
    }

    /* Native */
    private static native void initIDs();

    static native int receive0(int fd, SctpResultContainer resultContainer,
            long address, int length, boolean peek) throws IOException;

    static native int send0(int fd, long address, int length,
            SocketAddress target, int assocId, int streamNumber,
            boolean unordered, int ppid) throws IOException;

    private static native int checkConnect(FileDescriptor fd, boolean block,
            boolean ready) throws IOException;

    static {
        Util.load();   /* loads nio & net native libraries */
        java.security.AccessController.doPrivileged(
                new sun.security.action.LoadLibraryAction("sctp"));
        initIDs();
    }
}
