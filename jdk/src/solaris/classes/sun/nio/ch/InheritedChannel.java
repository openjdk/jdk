/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.lang.reflect.Constructor;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.spi.SelectorProvider;

class InheritedChannel {

    // the "types" of socket returned by soType0
    private static final int UNKNOWN            = -1;
    private static final int SOCK_STREAM        = 1;
    private static final int SOCK_DGRAM         = 2;

    // oflag values when opening a file
    private static final int O_RDONLY           = 0;
    private static final int O_WRONLY           = 1;
    private static final int O_RDWR             = 2;

    /*
     * In order to "detach" the standard streams we dup them to /dev/null.
     * In order to reduce the possibility of an error at close time we
     * open /dev/null early - that way we know we won't run out of file
     * descriptors at close time. This makes the close operation a
     * simple dup2 operation for each of the standard streams.
     */
    private static int devnull = -1;

    private static void detachIOStreams() {
        try {
            dup2(devnull, 0);
            dup2(devnull, 1);
            dup2(devnull, 2);
        } catch (IOException ioe) {
            // this shouldn't happen
            throw new InternalError();
        }
    }

    /*
     * Override the implCloseSelectableChannel for each channel type - this
     * allows us to "detach" the standard streams after closing and ensures
     * that the underlying socket really closes.
     */
    public static class InheritedSocketChannelImpl extends SocketChannelImpl {

        InheritedSocketChannelImpl(SelectorProvider sp,
                                   FileDescriptor fd,
                                   InetSocketAddress remote)
            throws IOException
        {
            super(sp, fd, remote);
        }

        protected void implCloseSelectableChannel() throws IOException {
            super.implCloseSelectableChannel();
            detachIOStreams();
        }
    }

    public static class InheritedServerSocketChannelImpl extends
        ServerSocketChannelImpl {

        InheritedServerSocketChannelImpl(SelectorProvider sp,
                                         FileDescriptor fd)
            throws IOException
        {
            super(sp, fd);
        }

        protected void implCloseSelectableChannel() throws IOException {
            super.implCloseSelectableChannel();
            detachIOStreams();
        }

    }

    public static class InheritedDatagramChannelImpl extends
        DatagramChannelImpl {

        InheritedDatagramChannelImpl(SelectorProvider sp,
                                     FileDescriptor fd)
            throws IOException
        {
            super(sp, fd);
        }

        protected void implCloseSelectableChannel() throws IOException {
            super.implCloseSelectableChannel();
            detachIOStreams();
        }
    }

    /*
     * If there's a SecurityManager then check for the appropriate
     * RuntimePermission.
     */
    private static void checkAccess(Channel c) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(
                new RuntimePermission("inheritedChannel")
            );
        }
    }


    /*
     * If standard inherited channel is connected to a socket then return a Channel
     * of the appropriate type based standard input.
     */
    private static Channel createChannel() throws IOException {

        // dup the file descriptor - we do this so that for two reasons :-
        // 1. Avoids any timing issues with FileDescriptor.in being closed
        //    or redirected while we create the channel.
        // 2. Allows streams based on file descriptor 0 to co-exist with
        //    the channel (closing one doesn't impact the other)

        int fdVal = dup(0);

        // Examine the file descriptor - if it's not a socket then we don't
        // create a channel so we release the file descriptor.

        int st;
        st = soType0(fdVal);
        if (st != SOCK_STREAM && st != SOCK_DGRAM) {
            close0(fdVal);
            return null;
        }


        // Next we create a FileDescriptor for the dup'ed file descriptor
        // Have to use reflection and also make assumption on how FD
        // is implemented.

        Class paramTypes[] = { int.class };
        Constructor ctr = Reflect.lookupConstructor("java.io.FileDescriptor",
                                                    paramTypes);
        Object args[] = { new Integer(fdVal) };
        FileDescriptor fd = (FileDescriptor)Reflect.invoke(ctr, args);


        // Now create the channel. If the socket is a streams socket then
        // we see if tthere is a peer (ie: connected). If so, then we
        // create a SocketChannel, otherwise a ServerSocketChannel.
        // If the socket is a datagram socket then create a DatagramChannel

        SelectorProvider provider = SelectorProvider.provider();
        assert provider instanceof sun.nio.ch.SelectorProviderImpl;

        Channel c;
        if (st == SOCK_STREAM) {
            InetAddress ia = peerAddress0(fdVal);
            if (ia == null) {
               c = new InheritedServerSocketChannelImpl(provider, fd);
            } else {
               int port = peerPort0(fdVal);
               assert port > 0;
               InetSocketAddress isa = new InetSocketAddress(ia, port);
               c = new InheritedSocketChannelImpl(provider, fd, isa);
            }
        } else {
            c = new InheritedDatagramChannelImpl(provider, fd);
        }
        return c;
    }

    private static boolean haveChannel = false;
    private static Channel channel = null;

    /*
     * Returns a Channel representing the inherited channel if the
     * inherited channel is a stream connected to a network socket.
     */
    public static synchronized Channel getChannel() throws IOException {
        if (devnull < 0) {
            devnull = open0("/dev/null", O_RDWR);
        }

        // If we don't have the channel try to create it
        if (!haveChannel) {
            channel = createChannel();
            haveChannel = true;
        }

        // if there is a channel then do the security check before
        // returning it.
        if (channel != null) {
            checkAccess(channel);
        }
        return channel;
    }


    // -- Native methods --

    private static native int dup(int fd) throws IOException;
    private static native void dup2(int fd, int fd2) throws IOException;
    private static native int open0(String path, int oflag) throws IOException;
    private static native void close0(int fd) throws IOException;
    private static native int soType0(int fd);
    private static native InetAddress peerAddress0(int fd);
    private static native int peerPort0(int fd);

    static {
        Util.load();
    }
}
