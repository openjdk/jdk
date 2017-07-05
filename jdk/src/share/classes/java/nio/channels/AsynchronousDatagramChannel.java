/*
 * Copyright (c) 2007, 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.channels;

import java.nio.channels.spi.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.io.IOException;
import java.net.SocketOption;
import java.net.SocketAddress;
import java.net.ProtocolFamily;
import java.nio.ByteBuffer;

/**
 * An asynchronous channel for datagram-oriented sockets.
 *
 * <p> An asynchronous datagram channel is created by invoking one of the {@link
 * #open open} methods defined by this class. It is not possible to create a channel
 * for an arbitrary, pre-existing datagram socket. A newly-created asynchronous
 * datagram channel is open but not connected. It need not be connected in order
 * for the {@link #send send} and {@link #receive receive} methods to be used.
 * A datagram channel may be connected, by invoking its {@link #connect connect}
 * method, in order to avoid the overhead of the security checks that are otherwise
 * performed as part of every send and receive operation when a security manager
 * is set. The channel must be connected in order to use the {@link #read read}
 * and {@link #write write} methods, since those methods do not accept or return
 * socket addresses. Once connected, an asynchronous datagram channel remains
 * connected until it is disconnected or closed.
 *
 * <p> Socket options are configured using the {@link #setOption(SocketOption,Object)
 * setOption} method. An asynchronous datagram channel to an Internet Protocol
 * (IP) socket supports the following options:
 * <blockquote>
 * <table border>
 *   <tr>
 *     <th>Option Name</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#SO_SNDBUF SO_SNDBUF} </td>
 *     <td> The size of the socket send buffer </td>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#SO_RCVBUF SO_RCVBUF} </td>
 *     <td> The size of the socket receive buffer </td>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#SO_REUSEADDR SO_REUSEADDR} </td>
 *     <td> Re-use address </td>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#SO_BROADCAST SO_BROADCAST} </td>
 *     <td> Allow transmission of broadcast datagrams </td>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#IP_TOS IP_TOS} </td>
 *     <td> The Type of Service (ToS) octet in the Internet Protocol (IP) header </td>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#IP_MULTICAST_IF IP_MULTICAST_IF} </td>
 *     <td> The network interface for Internet Protocol (IP) multicast datagrams </td>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#IP_MULTICAST_TTL
 *       IP_MULTICAST_TTL} </td>
 *     <td> The <em>time-to-live</em> for Internet Protocol (IP) multicast
 *       datagrams </td>
 *   </tr>
 *   <tr>
 *     <td> {@link java.net.StandardSocketOption#IP_MULTICAST_LOOP
 *       IP_MULTICAST_LOOP} </td>
 *     <td> Loopback for Internet Protocol (IP) multicast datagrams </td>
 *   </tr>
 * </table>
 * </blockquote>
 * Additional (implementation specific) options may also be supported.
 *
 * <p> Asynchronous datagram channels allow more than one read/receive and
 * write/send to be oustanding at any given time.
 *
 * <p> <b>Usage Example:</b>
 * <pre>
 *  final AsynchronousDatagramChannel dc = AsynchronousDatagramChannel.open()
 *      .bind(new InetSocketAddress(4000));
 *
 *  // print the source address of all packets that we receive
 *  dc.receive(buffer, buffer, new CompletionHandler&lt;SocketAddress,ByteBuffer&gt;() {
 *      public void completed(SocketAddress sa, ByteBuffer buffer) {
 *          System.out.println(sa);
 *          buffer.clear();
 *          dc.receive(buffer, buffer, this);
 *      }
 *      public void failed(Throwable exc, ByteBuffer buffer) {
 *          ...
 *      }
 *  });
 * </pre>
 *
 * @since 1.7
 */

public abstract class AsynchronousDatagramChannel
    implements AsynchronousByteChannel, MulticastChannel
{
    private final AsynchronousChannelProvider provider;

    /**
     * Initializes a new instance of this class.
     */
    protected AsynchronousDatagramChannel(AsynchronousChannelProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel.
     */
    public final AsynchronousChannelProvider provider() {
        return provider;
    }

    /**
     * Opens an asynchronous datagram channel.
     *
     * <p> The new channel is created by invoking the {@link
     * java.nio.channels.spi.AsynchronousChannelProvider#openAsynchronousDatagramChannel
     * openAsynchronousDatagramChannel} method on the {@link
     * java.nio.channels.spi.AsynchronousChannelProvider} object that created
     * the given group (or the default provider where {@code group} is {@code
     * null}).
     *
     * <p> The {@code family} parameter is used to specify the {@link ProtocolFamily}.
     * If the datagram channel is to be used for Internet Protocol {@link
     * MulticastChannel multicasting} then this parameter should correspond to
     * the address type of the multicast groups that this channel will join.
     *
     * @param   family
     *          The protocol family, or {@code null} to use the default protocol
     *          family
     * @param   group
     *          The group to which the newly constructed channel should be bound,
     *          or {@code null} for the default group
     *
     * @return  A new asynchronous datagram channel
     *
     * @throws  UnsupportedOperationException
     *          If the specified protocol family is not supported. For example,
     *          suppose the parameter is specified as {@link
     *          java.net.StandardProtocolFamily#INET6 INET6} but IPv6 is not
     *          enabled on the platform.
     * @throws  ShutdownChannelGroupException
     *          The specified group is shutdown
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static AsynchronousDatagramChannel open(ProtocolFamily family,
                                                   AsynchronousChannelGroup group)
        throws IOException
    {
        AsynchronousChannelProvider provider = (group == null) ?
            AsynchronousChannelProvider.provider() : group.provider();
        return provider.openAsynchronousDatagramChannel(family, group);
    }

    /**
     * Opens an asynchronous datagram channel.
     *
     * <p> This method returns an asynchronous datagram channel that is
     * bound to the <em>default group</em>. This method is equivalent to evaluating
     * the expression:
     * <blockquote><pre>
     * open((ProtocolFamily)null,&nbsp;(AsynchronousChannelGroup)null);
     * </pre></blockquote>
     *
     * @return  A new asynchronous datagram channel
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static AsynchronousDatagramChannel open()
        throws IOException
    {
        return open(null, null);
    }

    // -- Socket-specific operations --

    /**
     * @throws  AlreadyBoundException               {@inheritDoc}
     * @throws  UnsupportedAddressTypeException     {@inheritDoc}
     * @throws  ClosedChannelException              {@inheritDoc}
     * @throws  IOException                         {@inheritDoc}
     * @throws  SecurityException
     *          If a security manager has been installed and its {@link
     *          SecurityManager#checkListen checkListen} method denies the
     *          operation
     */
    @Override
    public abstract AsynchronousDatagramChannel bind(SocketAddress local)
        throws IOException;

    /**
     * @throws  IllegalArgumentException                {@inheritDoc}
     * @throws  ClosedChannelException                  {@inheritDoc}
     * @throws  IOException                             {@inheritDoc}
     */
    @Override
    public abstract <T> AsynchronousDatagramChannel setOption(SocketOption<T> name, T value)
        throws IOException;

    /**
     * Returns the remote address to which this channel is connected.
     *
     * <p> Where the channel is connected to an Internet Protocol socket address
     * then the return value from this method is of type {@link
     * java.net.InetSocketAddress}.
     *
     * @return  The remote address; {@code null} if the channel's socket is not
     *          connected
     *
     * @throws  ClosedChannelException
     *          If the channel is closed
     * @throws  IOException
     *          If an I/O error occurs
     */
    public abstract SocketAddress getRemoteAddress() throws IOException;

    /**
     * Connects this channel's socket.
     *
     * <p> The channel's socket is configured so that it only receives
     * datagrams from, and sends datagrams to, the given remote <i>peer</i>
     * address.  Once connected, datagrams may not be received from or sent to
     * any other address.  A datagram socket remains connected until it is
     * explicitly disconnected or until it is closed.
     *
     * <p> This method performs exactly the same security checks as the {@link
     * java.net.DatagramSocket#connect connect} method of the {@link
     * java.net.DatagramSocket} class.  That is, if a security manager has been
     * installed then this method verifies that its {@link
     * java.lang.SecurityManager#checkAccept checkAccept} and {@link
     * java.lang.SecurityManager#checkConnect checkConnect} methods permit
     * datagrams to be received from and sent to, respectively, the given
     * remote address.
     *
     * <p> This method may be invoked at any time. Whether it has any effect
     * on outstanding read or write operations is implementation specific and
     * therefore not specified.
     *
     * @param  remote
     *         The remote address to which this channel is to be connected
     *
     * @return  This datagram channel
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit access to the given remote address
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract AsynchronousDatagramChannel connect(SocketAddress remote)
        throws IOException;

    /**
     * Disconnects this channel's socket.
     *
     * <p> The channel's socket is configured so that it can receive datagrams
     * from, and sends datagrams to, any remote address so long as the security
     * manager, if installed, permits it.
     *
     * <p> This method may be invoked at any time. Whether it has any effect
     * on outstanding read or write operations is implementation specific and
     * therefore not specified.
     *
     * @return  This datagram channel
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract AsynchronousDatagramChannel disconnect() throws IOException;

    /**
     * Receives a datagram via this channel.
     *
     * <p> This method initiates the receiving of a datagram into the given
     * buffer. The {@code handler} parameter is a completion handler that is
     * invoked when the receive operation completes (or fails). The result
     * passed to the completion handler is the datagram's source address.
     *
     * <p> The datagram is transferred into the given byte buffer starting at
     * its current position, as if by a regular {@link AsynchronousByteChannel#read
     * read} operation. If there are fewer bytes remaining in the buffer
     * than are required to hold the datagram then the remainder of the datagram
     * is silently discarded.
     *
     * <p> If a timeout is specified and the timeout elapses before the operation
     * completes then the operation completes with the exception {@link
     * InterruptedByTimeoutException}. When a timeout elapses then the state of
     * the {@link ByteBuffer} is not defined. The buffers should be discarded or
     * at least care must be taken to ensure that the buffer is not accessed
     * while the channel remains open.
     *
     * <p> When a security manager has been installed and the channel is not
     * connected, then it verifies that the source's address and port number are
     * permitted by the security manager's {@link SecurityManager#checkAccept
     * checkAccept} method. The permission check is performed with privileges that
     * are restricted by the calling context of this method. If the permission
     * check fails then the operation completes with a {@link SecurityException}.
     * The overhead of this security check can be avoided by first connecting the
     * socket via the {@link #connect connect} method.
     *
     * @param   dst
     *          The buffer into which the datagram is to be transferred
     * @param   timeout
     *          The timeout, or {@code 0L} for no timeout
     * @param   unit
     *          The time unit of the {@code timeout} argument
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  IllegalArgumentException
     *          If the timeout is negative or the buffer is read-only
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public abstract <A> void receive(ByteBuffer dst,
                                     long timeout,
                                     TimeUnit unit,
                                     A attachment,
                                     CompletionHandler<SocketAddress,? super A> handler);

    /**
     * Receives a datagram via this channel.
     *
     * <p> This method initiates the receiving of a datagram into the given
     * buffer. The {@code handler} parameter is a completion handler that is
     * invoked when the receive operation completes (or fails). The result
     * passed to the completion handler is the datagram's source address.
     *
     * <p> This method is equivalent to invoking {@link
     * #receive(ByteBuffer,long,TimeUnit,Object,CompletionHandler)} with a
     * timeout of {@code 0L}.
     *
     * @param   dst
     *          The buffer into which the datagram is to be transferred
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  IllegalArgumentException
     *          If the buffer is read-only
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public final <A> void receive(ByteBuffer dst,
                                  A attachment,
                                  CompletionHandler<SocketAddress,? super A> handler)
    {
        receive(dst, 0L, TimeUnit.MILLISECONDS, attachment, handler);
    }

    /**
     * Receives a datagram via this channel.
     *
     * <p> This method initiates the receiving of a datagram into the given
     * buffer. The method behaves in exactly the same manner as the {@link
     * #receive(ByteBuffer,Object,CompletionHandler)
     * receive(ByteBuffer,Object,CompletionHandler)} method except that instead
     * of specifying a completion handler, this method returns a {@code Future}
     * representing the pending result. The {@code Future}'s {@link Future#get()
     * get} method returns the datagram's source address.
     *
     * @param   dst
     *          The buffer into which the datagram is to be transferred
     *
     * @return  a {@code Future} object representing the pending result
     *
     * @throws  IllegalArgumentException
     *          If the buffer is read-only
     */
    public abstract Future<SocketAddress> receive(ByteBuffer dst);

    /**
     * Sends a datagram via this channel.
     *
     * <p> This method initiates sending of a datagram from the given buffer to
     * the given address. The {@code handler} parameter is a completion handler
     * that is invoked when the send completes (or fails). The result passed to
     * the completion handler is the number of bytes sent.
     *
     * <p> Otherwise this method works in the same manner as the {@link
     * AsynchronousByteChannel#write(ByteBuffer,Object,CompletionHandler)}
     * method.
     *
     * @param   src
     *          The buffer containing the datagram to be sent
     * @param   target
     *          The address to which the datagram is to be sent
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  UnresolvedAddressException
     *          If the given remote address is not fully resolved
     * @throws  UnsupportedAddressTypeException
     *          If the type of the given remote address is not supported
     * @throws  IllegalArgumentException
     *          If the channel's socket is connected and is connected to an
     *          address that is not equal to {@code target}
     * @throws  SecurityException
     *          If a security manager has been installed and it does not permit
     *          datagrams to be sent to the given address
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public abstract <A> void send(ByteBuffer src,
                                  SocketAddress target,
                                  A attachment,
                                  CompletionHandler<Integer,? super A> handler);

    /**
     * Sends a datagram via this channel.
     *
     * <p> This method initiates sending of a datagram from the given buffer to
     * the given address. The method behaves in exactly the same manner as the
     * {@link #send(ByteBuffer,SocketAddress,Object,CompletionHandler)
     * send(ByteBuffer,SocketAddress,Object,CompletionHandler)} method except
     * that instead of specifying a completion handler, this method returns a
     * {@code Future} representing the pending result. The {@code Future}'s
     * {@link Future#get() get} method returns the number of bytes sent.
     *
     * @param   src
     *          The buffer containing the datagram to be sent
     * @param   target
     *          The address to which the datagram is to be sent
     *
     * @return  a {@code Future} object representing the pending result
     *
     * @throws  UnresolvedAddressException
     *          If the given remote address is not fully resolved
     * @throws  UnsupportedAddressTypeException
     *          If the type of the given remote address is not supported
     * @throws  IllegalArgumentException
     *          If the channel's socket is connected and is connected to an
     *          address that is not equal to {@code target}
     * @throws  SecurityException
     *          If a security manager has been installed and it does not permit
     *          datagrams to be sent to the given address
     */
    public abstract Future<Integer> send(ByteBuffer src, SocketAddress target);

    /**
     * Receives a datagram via this channel.
     *
     * <p> This method initiates the receiving of a datagram into the given
     * buffer. The {@code handler} parameter is a completion handler that is
     * invoked when the receive operation completes (or fails). The result
     * passed to the completion handler is number of bytes read.
     *
     * <p> This method may only be invoked if this channel is connected, and it
     * only accepts datagrams from the peer that the channel is connected too.
     * The datagram is transferred into the given byte buffer starting at
     * its current position and exactly as specified in the {@link
     * AsynchronousByteChannel} interface. If there are fewer bytes
     * remaining in the buffer than are required to hold the datagram then the
     * remainder of the datagram is silently discarded.
     *
     * <p> If a timeout is specified and the timeout elapses before the operation
     * completes then the operation completes with the exception {@link
     * InterruptedByTimeoutException}. When a timeout elapses then the state of
     * the {@link ByteBuffer} is not defined. The buffers should be discarded or
     * at least care must be taken to ensure that the buffer is not accessed
     * while the channel remains open.
     *
     * @param   dst
     *          The buffer into which the datagram is to be transferred
     * @param   timeout
     *          The timeout, or {@code 0L} for no timeout
     * @param   unit
     *          The time unit of the {@code timeout} argument
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  IllegalArgumentException
     *          If the timeout is negative or buffer is read-only
     * @throws  NotYetConnectedException
     *          If this channel is not connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public abstract <A> void read(ByteBuffer dst,
                                  long timeout,
                                  TimeUnit unit,
                                  A attachment,
                                  CompletionHandler<Integer,? super A> handler);

    /**
     * @throws  NotYetConnectedException
     *          If this channel is not connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    @Override
    public final <A> void read(ByteBuffer dst,
                               A attachment,
                               CompletionHandler<Integer,? super A> handler)
    {
        read(dst, 0L, TimeUnit.MILLISECONDS, attachment, handler);
    }

    /**
     * @throws  NotYetConnectedException
     *          If this channel is not connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    @Override
    public abstract Future<Integer> read(ByteBuffer dst);

    /**
     * @throws  NotYetConnectedException
     *          If this channel is not connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    @Override
    public abstract <A> void  write(ByteBuffer src,
                                    A attachment,
                                    CompletionHandler<Integer,? super A> handler);


    /**
     * @throws  NotYetConnectedException
     *          If this channel is not connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    @Override
    public abstract Future<Integer> write(ByteBuffer src);
}
