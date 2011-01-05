/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.ch;

import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.nio.channels.SelectionKey;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.spi.SelectorProvider;
import com.sun.nio.sctp.IllegalUnbindException;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import com.sun.nio.sctp.SctpSocketOption;
import com.sun.nio.sctp.SctpStandardSocketOption;

/**
 * An implementation of SctpServerChannel
 */
public class SctpServerChannelImpl extends SctpServerChannel
    implements SelChImpl
{
    private final FileDescriptor fd;

    private final int fdVal;

    /* IDs of native thread doing accept, for signalling */
    private volatile long thread = 0;

    /* Lock held by thread currently blocked in this channel */
    private final Object lock = new Object();

    /* Lock held by any thread that modifies the state fields declared below
     * DO NOT invoke a blocking I/O operation while holding this lock! */
    private final Object stateLock = new Object();

    private enum ChannelState {
        UNINITIALIZED,
        INUSE,
        KILLPENDING,
        KILLED,
    }
    /* -- The following fields are protected by stateLock -- */
    private ChannelState state = ChannelState.UNINITIALIZED;

    /* Binding: Once bound the port will remain constant. */
    int port = -1;
    private HashSet<InetSocketAddress> localAddresses = new HashSet<InetSocketAddress>();
    /* Has the channel been bound to the wildcard address */
    private boolean wildcard; /* false */

    /* -- End of fields protected by stateLock -- */

    /**
     * Initializes a new instance of this class.
     */
    public SctpServerChannelImpl(SelectorProvider provider)
            throws IOException {
        //TODO: update provider remove public modifier
        super(provider);
        this.fd = SctpNet.socket(true);
        this.fdVal = IOUtil.fdVal(fd);
        this.state = ChannelState.INUSE;
    }

    @Override
    public SctpServerChannel bind(SocketAddress local, int backlog)
            throws IOException {
        synchronized (lock) {
            synchronized (stateLock) {
                if (!isOpen())
                    throw new ClosedChannelException();
                if (isBound())
                    SctpNet.throwAlreadyBoundException();

                InetSocketAddress isa = (local == null) ?
                    new InetSocketAddress(0) : Net.checkAddress(local);
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkListen(isa.getPort());
                Net.bind(fd, isa.getAddress(), isa.getPort());

                InetSocketAddress boundIsa = Net.localAddress(fd);
                port = boundIsa.getPort();
                localAddresses.add(isa);
                    if (isa.getAddress().isAnyLocalAddress())
                        wildcard = true;

                SctpNet.listen(fdVal, backlog < 1 ? 50 : backlog);
            }
        }
        return this;
    }

    @Override
    public SctpServerChannel bindAddress(InetAddress address)
            throws IOException {
        return bindUnbindAddress(address, true);
    }

    @Override
    public SctpServerChannel unbindAddress(InetAddress address)
            throws IOException {
        return bindUnbindAddress(address, false);
    }

    private SctpServerChannel bindUnbindAddress(InetAddress address, boolean add)
            throws IOException {
        if (address == null)
            throw new IllegalArgumentException();

        synchronized (lock) {
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
        return this;
    }

    private boolean isBound() {
        synchronized (stateLock) {
            return port == -1 ? false : true;
        }
    }

    private void acceptCleanup() throws IOException {
        synchronized (stateLock) {
            thread = 0;
            if (state == ChannelState.KILLPENDING)
                kill();
        }
    }

    @Override
    public SctpChannel accept() throws IOException {
        synchronized (lock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (!isBound())
                throw new NotYetBoundException();
            SctpChannel sc = null;

            int n = 0;
            FileDescriptor newfd = new FileDescriptor();
            InetSocketAddress[] isaa = new InetSocketAddress[1];

            try {
                begin();
                if (!isOpen())
                    return null;
                thread = NativeThread.current();
                for (;;) {
                    n = accept0(fd, newfd, isaa);
                    if ((n == IOStatus.INTERRUPTED) && isOpen())
                        continue;
                    break;
                }
            } finally {
                acceptCleanup();
                end(n > 0);
                assert IOStatus.check(n);
            }

            if (n < 1)
                return null;

            IOUtil.configureBlocking(newfd, true);
            InetSocketAddress isa = isaa[0];
            sc = new SctpChannelImpl(provider(), newfd);

            SecurityManager sm = System.getSecurityManager();
            if (sm != null)
                sm.checkAccept(isa.getAddress().getHostAddress(),
                               isa.getPort());

            return sc;
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        IOUtil.configureBlocking(fd, block);
    }

    @Override
    public void implCloseSelectableChannel() throws IOException {
        synchronized (stateLock) {
            SctpNet.preClose(fdVal);
            if (thread != 0)
                NativeThread.signal(thread);
            if (!isRegistered())
                kill();
        }
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

            // Postpone the kill if there is a thread in accept
            if (thread == 0) {
                SctpNet.close(fdVal);
                state = ChannelState.KILLED;
            } else {
                state = ChannelState.KILLPENDING;
            }
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
    private boolean translateReadyOps(int ops, int initialOps,
                                     SelectionKeyImpl sk) {
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
            return (newOps & ~oldOps) != 0;
        }

        if (((ops & PollArrayWrapper.POLLIN) != 0) &&
            ((intOps & SelectionKey.OP_ACCEPT) != 0))
                newOps |= SelectionKey.OP_ACCEPT;

        sk.nioReadyOps(newOps);
        return (newOps & ~oldOps) != 0;
    }

    @Override
    public boolean translateAndUpdateReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, sk.nioReadyOps(), sk);
    }

    @Override
    public boolean translateAndSetReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, 0, sk);
    }

    @Override
    public void translateAndSetInterestOps(int ops, SelectionKeyImpl sk) {
        int newOps = 0;

        /* Translate ops */
        if ((ops & SelectionKey.OP_ACCEPT) != 0)
            newOps |= PollArrayWrapper.POLLIN;
        /* Place ops into pollfd array */
        sk.selector.putEventOps(sk, newOps);

    }

    @Override
    public <T> SctpServerChannel setOption(SctpSocketOption<T> name, T value)
            throws IOException {
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();

            SctpNet.setSocketOption(fdVal, name, value, 0 /*oneToOne*/);
            return this;
        }
    }

    @Override
    public <T> T getOption(SctpSocketOption<T> name) throws IOException {
        if (name == null)
            throw new NullPointerException();
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            if (!isOpen())
                throw new ClosedChannelException();

            return (T) SctpNet.getSocketOption(fdVal, name, 0 /*oneToOne*/);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SctpSocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SctpSocketOption<?>> defaultOptions() {
            HashSet<SctpSocketOption<?>> set = new HashSet<SctpSocketOption<?>>(1);
            set.add(SctpStandardSocketOption.SCTP_INIT_MAXSTREAMS);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SctpSocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
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

    /* Native */
    private static native void initIDs();

    private static native int accept0(FileDescriptor ssfd,
        FileDescriptor newfd, InetSocketAddress[] isaa) throws IOException;

    static {
        Util.load();   // loads nio & net native libraries
        java.security.AccessController.doPrivileged(
                new sun.security.action.LoadLibraryAction("sctp"));
        initIDs();
    }
}
