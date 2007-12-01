/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.channels;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.spi.*;


/**
 * A selectable channel for stream-oriented listening sockets.
 *
 * <p> Server-socket channels are not a complete abstraction of listening
 * network sockets.  Binding and the manipulation of socket options must be
 * done through an associated {@link java.net.ServerSocket} object obtained by
 * invoking the {@link #socket() socket} method.  It is not possible to create
 * a channel for an arbitrary, pre-existing server socket, nor is it possible
 * to specify the {@link java.net.SocketImpl} object to be used by a server
 * socket associated with a server-socket channel.
 *
 * <p> A server-socket channel is created by invoking the {@link #open() open}
 * method of this class.  A newly-created server-socket channel is open but not
 * yet bound.  An attempt to invoke the {@link #accept() accept} method of an
 * unbound server-socket channel will cause a {@link NotYetBoundException} to
 * be thrown.  A server-socket channel can be bound by invoking one of the
 * {@link java.net.ServerSocket#bind(java.net.SocketAddress,int) bind} methods
 * of an associated server socket.
 *
 * <p> Server-socket channels are safe for use by multiple concurrent threads.
 * </p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class ServerSocketChannel
    extends AbstractSelectableChannel
{

    /**
     * Initializes a new instance of this class.
     */
    protected ServerSocketChannel(SelectorProvider provider) {
        super(provider);
    }

    /**
     * Opens a server-socket channel.
     *
     * <p> The new channel is created by invoking the {@link
     * java.nio.channels.spi.SelectorProvider#openServerSocketChannel
     * openServerSocketChannel} method of the system-wide default {@link
     * java.nio.channels.spi.SelectorProvider} object.
     *
     * <p> The new channel's socket is initially unbound; it must be bound to a
     * specific address via one of its socket's {@link
     * java.net.ServerSocket#bind(SocketAddress) bind} methods before
     * connections can be accepted.  </p>
     *
     * @return  A new socket channel
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static ServerSocketChannel open() throws IOException {
        return SelectorProvider.provider().openServerSocketChannel();
    }

    /**
     * Returns an operation set identifying this channel's supported
     * operations.
     *
     * <p> Server-socket channels only support the accepting of new
     * connections, so this method returns {@link SelectionKey#OP_ACCEPT}.
     * </p>
     *
     * @return  The valid-operation set
     */
    public final int validOps() {
        return SelectionKey.OP_ACCEPT;
    }


    // -- ServerSocket-specific operations --

    /**
     * Retrieves a server socket associated with this channel.
     *
     * <p> The returned object will not declare any public methods that are not
     * declared in the {@link java.net.ServerSocket} class.  </p>
     *
     * @return  A server socket associated with this channel
     */
    public abstract ServerSocket socket();

    /**
     * Accepts a connection made to this channel's socket.
     *
     * <p> If this channel is in non-blocking mode then this method will
     * immediately return <tt>null</tt> if there are no pending connections.
     * Otherwise it will block indefinitely until a new connection is available
     * or an I/O error occurs.
     *
     * <p> The socket channel returned by this method, if any, will be in
     * blocking mode regardless of the blocking mode of this channel.
     *
     * <p> This method performs exactly the same security checks as the {@link
     * java.net.ServerSocket#accept accept} method of the {@link
     * java.net.ServerSocket} class.  That is, if a security manager has been
     * installed then for each new connection this method verifies that the
     * address and port number of the connection's remote endpoint are
     * permitted by the security manager's {@link
     * java.lang.SecurityManager#checkAccept checkAccept} method.  </p>
     *
     * @return  The socket channel for the new connection,
     *          or <tt>null</tt> if this channel is in non-blocking mode
     *          and no connection is available to be accepted
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  AsynchronousCloseException
     *          If another thread closes this channel
     *          while the accept operation is in progress
     *
     * @throws  ClosedByInterruptException
     *          If another thread interrupts the current thread
     *          while the accept operation is in progress, thereby
     *          closing the channel and setting the current thread's
     *          interrupt status
     *
     * @throws  NotYetBoundException
     *          If this channel's socket has not yet been bound
     *
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit access to the remote endpoint
     *          of the new connection
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract SocketChannel accept() throws IOException;

}
