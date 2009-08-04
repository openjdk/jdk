/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * Defines channels, which represent connections to entities that are capable of
 * performing I/O operations, such as files and sockets; defines selectors, for
 * multiplexed, non-blocking I/O operations.
 *
 * <a name="channels"></a>
 *
 * <blockquote><table cellspacing=1 cellpadding=0 summary="Lists channels and their descriptions">
 * <tr><th><p align="left">Channels</p></th><th><p align="left">Description</p></th></tr>
 * <tr><td valign=top><tt><i>{@link java.nio.channels.Channel}</i></tt></td>
 *     <td>A nexus for I/O operations</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.ReadableByteChannel}</i></tt></td>
 *     <td>Can read into a buffer</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.ScatteringByteChannel}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read into a sequence of&nbsp;buffers</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.WritableByteChannel}</i></tt></td>
 *     <td>Can write from a buffer</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.GatheringByteChannel}</i></tt></td>
 *     <td>Can write from a sequence of&nbsp;buffers</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.ByteChannel}</i></tt></td>
 *     <td>Can read/write to/from a&nbsp;buffer</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.SeekableByteChannel}</i></tt></td>
 *     <td>A {@code ByteChannel} connected to an entity that contains a variable-length sequence of bytes</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.AsynchronousChannel}</i></tt></td>
 *     <td>Supports asynchronous I/O operations.</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.AsynchronousByteChannel}</i></tt></td>
 *     <td>Can read and write bytes asynchronously</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.NetworkChannel}</i></tt></td>
 *     <td>A channel to a network socket</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.MulticastChannel}</i></tt></td>
 *     <td>Can join Internet Protocol (IP) multicast groups</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.Channels}</tt></td>
 *     <td>Utility methods for channel/stream interoperation</td></tr>
 * </table></blockquote>
 *
 * <p> A <i>channel</i> represents an open connection to an entity such as a
 * hardware device, a file, a network socket, or a program component that is
 * capable of performing one or more distinct I/O operations, for example reading
 * or writing.  As specified in the {@link java.nio.channels.Channel} interface,
 * channels are either open or closed, and they are both <i>asynchronously
 * closeable</i> and <i>interruptible</i>.
 *
 * <p> The {@link java.nio.channels.Channel} interface is extended by several
 * other interfaces.
 *
 * <p> The {@link java.nio.channels.ReadableByteChannel} interface specifies a
 * {@link java.nio.channels.ReadableByteChannel#read read} method that reads bytes
 * from the channel into a buffer; similarly, the {@link
 * java.nio.channels.WritableByteChannel} interface specifies a {@link
 * java.nio.channels.WritableByteChannel#write write} method that writes bytes
 * from a buffer to the channel. The {@link java.nio.channels.ByteChannel}
 * interface unifies these two interfaces for the common case of channels that can
 * both read and write bytes. The {@link java.nio.channels.SeekableByteChannel}
 * interface extends the {@code ByteChannel} interface with methods to {@link
 * java.nio.channels.SeekableByteChannel#position() query} and {@link
 * java.nio.channels.SeekableByteChannel#position(long) modify} the channel's
 * current position, and its {@link java.nio.channels.SeekableByteChannel#size
 * size}.
 *
 * <p> The {@link java.nio.channels.ScatteringByteChannel} and {@link
 * java.nio.channels.GatheringByteChannel} interfaces extend the {@link
 * java.nio.channels.ReadableByteChannel} and {@link
 * java.nio.channels.WritableByteChannel} interfaces, respectively, adding {@link
 * java.nio.channels.ScatteringByteChannel#read read} and {@link
 * java.nio.channels.GatheringByteChannel#write write} methods that take a
 * sequence of buffers rather than a single buffer.
 *
 * <p> The {@link java.nio.channels.NetworkChannel} interface specifies methods
 * to {@link java.nio.channels.NetworkChannel#bind bind} the channel's socket,
 * obtain the address to which the socket is bound, and methods to {@link
 * java.nio.channels.NetworkChannel#getOption get} and {@link
 * java.nio.channels.NetworkChannel#setOption set} socket options. The {@link
 * java.nio.channels.MulticastChannel} interface specifies methods to join
 * Internet Protocol (IP) multicast groups.
 *
 * <p> The {@link java.nio.channels.Channels} utility class defines static methods
 * that support the interoperation of the stream classes of the <tt>{@link
 * java.io}</tt> package with the channel classes of this package.  An appropriate
 * channel can be constructed from an {@link java.io.InputStream} or an {@link
 * java.io.OutputStream}, and conversely an {@link java.io.InputStream} or an
 * {@link java.io.OutputStream} can be constructed from a channel.  A {@link
 * java.io.Reader} can be constructed that uses a given charset to decode bytes
 * from a given readable byte channel, and conversely a {@link java.io.Writer} can
 * be constructed that uses a given charset to encode characters into bytes and
 * write them to a given writable byte channel.
 *
 * <blockquote><table cellspacing=1 cellpadding=0 summary="Lists file channels and their descriptions">
 * <tr><th><p align="left">File channels</p></th><th><p align="left">Description</p></th></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.FileChannel}</tt></td>
 *     <td>Reads, writes, maps, and manipulates files</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.FileLock}</tt></td>
 *     <td>A lock on a (region of a) file</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.MappedByteBuffer}&nbsp;&nbsp;</tt></td>
 *     <td>A direct byte buffer mapped to a region of a&nbsp;file</td></tr>
 * </table></blockquote>
 *
 * <p> The {@link java.nio.channels.FileChannel} class supports the usual
 * operations of reading bytes from, and writing bytes to, a channel connected to
 * a file, as well as those of querying and modifying the current file position
 * and truncating the file to a specific size.  It defines methods for acquiring
 * locks on the whole file or on a specific region of a file; these methods return
 * instances of the {@link java.nio.channels.FileLock} class.  Finally, it defines
 * methods for forcing updates to the file to be written to the storage device that
 * contains it, for efficiently transferring bytes between the file and other
 * channels, and for mapping a region of the file directly into memory.
 *
 * <p> A {@code FileChannel} is created by invoking one of its static {@link
 * java.nio.channels.FileChannel#open open} methods, or by invoking the {@code
 * getChannel} method of a {@link java.io.FileInputStream}, {@link
 * java.io.FileOutputStream}, or {@link java.io.RandomAccessFile} to return a
 * file channel connected to the same underlying file as the <tt>{@link java.io}</tt>
 * class.
 *
 * <a name="multiplex"></a>
 * <blockquote><table cellspacing=1 cellpadding=0 summary="Lists multiplexed, non-blocking channels and their descriptions">
 * <tr><th><p align="left">Multiplexed, non-blocking I/O</p></th><th><p align="left">Description</p></th></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.SelectableChannel}</tt></td>
 *     <td>A channel that can be multiplexed</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;{@link java.nio.channels.DatagramChannel}</tt></td>
 *     <td>A channel to a datagram-oriented socket</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;{@link java.nio.channels.Pipe.SinkChannel}</tt></td>
 *     <td>The write end of a pipe</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;{@link java.nio.channels.Pipe.SourceChannel}</tt></td>
 *     <td>The read end of a pipe</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;{@link java.nio.channels.ServerSocketChannel}&nbsp;&nbsp;</tt></td>
 *     <td>A channel to a stream-oriented listening socket</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;{@link java.nio.channels.SocketChannel}</tt></td>
 *     <td>A channel for a stream-oriented connecting socket</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.Selector}</tt></td>
 *     <td>A multiplexor of selectable channels</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.SelectionKey}</tt></td>
 *     <td>A token representing the registration <br> of a channel
 *     with&nbsp;a&nbsp;selector</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.Pipe}</tt></td>
 *     <td>Two channels that form a unidirectional&nbsp;pipe</td></tr>
 * </table></blockquote>
 *
 * <p> Multiplexed, non-blocking I/O, which is much more scalable than
 * thread-oriented, blocking I/O, is provided by <i>selectors</i>, <i>selectable
 * channels</i>, and <i>selection keys</i>.
 *
 * <p> A <a href="Selector.html"><i>selector</i></a> is a multiplexor of <a
 * href="SelectableChannel.html"><i>selectable channels</i></a>, which in turn are
 * a special type of channel that can be put into <a
 * href="SelectableChannel.html#bm"><i>non-blocking mode</i></a>.  To perform
 * multiplexed I/O operations, one or more selectable channels are first created,
 * put into non-blocking mode, and {@link
 * java.nio.channels.SelectableChannel#register <i>registered</i>}
 * with a selector.  Registering a channel specifies the set of I/O operations
 * that will be tested for readiness by the selector, and returns a <a
 * href="SelectionKey.html"><i>selection key</i></a> that represents the
 * registration.
 *
 * <p> Once some channels have been registered with a selector, a <a
 * href="Selector.html#selop"><i>selection operation</i></a> can be performed in
 * order to discover which channels, if any, have become ready to perform one or
 * more of the operations in which interest was previously declared.  If a channel
 * is ready then the key returned when it was registered will be added to the
 * selector's <i>selected-key set</i>.  The key set, and the keys within it, can
 * be examined in order to determine the operations for which each channel is
 * ready.  From each key one can retrieve the corresponding channel in order to
 * perform whatever I/O operations are required.
 *
 * <p> That a selection key indicates that its channel is ready for some operation
 * is a hint, but not a guarantee, that such an operation can be performed by a
 * thread without causing the thread to block.  It is imperative that code that
 * performs multiplexed I/O be written so as to ignore these hints when they prove
 * to be incorrect.
 *
 * <p> This package defines selectable-channel classes corresponding to the {@link
 * java.net.DatagramSocket}, {@link java.net.ServerSocket}, and {@link
 * java.net.Socket} classes defined in the <tt>{@link java.net}</tt> package.
 * Minor changes to these classes have been made in order to support sockets that
 * are associated with channels.  This package also defines a simple class that
 * implements unidirectional pipes.  In all cases, a new selectable channel is
 * created by invoking the static <tt>open</tt> method of the corresponding class.
 * If a channel needs an associated socket then a socket will be created as a side
 * effect of this operation.
 *
 * <p> The implementation of selectors, selectable channels, and selection keys
 * can be replaced by "plugging in" an alternative definition or instance of the
 * {@link java.nio.channels.spi.SelectorProvider} class defined in the <tt>{@link
 * java.nio.channels.spi}</tt> package.  It is not expected that many developers
 * will actually make use of this facility; it is provided primarily so that
 * sophisticated users can take advantage of operating-system-specific
 * I/O-multiplexing mechanisms when very high performance is required.
 *
 * <p> Much of the bookkeeping and synchronization required to implement the
 * multiplexed-I/O abstractions is performed by the {@link
 * java.nio.channels.spi.AbstractInterruptibleChannel}, {@link
 * java.nio.channels.spi.AbstractSelectableChannel}, {@link
 * java.nio.channels.spi.AbstractSelectionKey}, and {@link
 * java.nio.channels.spi.AbstractSelector} classes in the <tt>{@link
 * java.nio.channels.spi}</tt> package.  When defining a custom selector provider,
 * only the {@link java.nio.channels.spi.AbstractSelector} and {@link
 * java.nio.channels.spi.AbstractSelectionKey} classes should be subclassed
 * directly; custom channel classes should extend the appropriate {@link
 * java.nio.channels.SelectableChannel} subclasses defined in this package.
 *
 * <a name="async"></a>
 *
 * <blockquote><table cellspacing=1 cellpadding=0 summary="Lists asynchronous channels and their descriptions">
 * <tr><th><p align="left">Asynchronous I/O</p></th><th><p align="left">Description</p></th></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.AsynchronousFileChannel}</tt></td>
 *     <td>An asynchronous channel for reading, writing, and manipulating a file</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.AsynchronousSocketChannel}</tt></td>
 *     <td>An asynchronous channel to a stream-oriented connecting socket</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.AsynchronousServerSocketChannel}&nbsp;&nbsp;</tt></td>
 *     <td>An asynchronous channel to a stream-oriented listening socket</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.AsynchronousDatagramChannel}</tt></td>
 *     <td>An asynchronous channel to a datagram-oriented socket</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.CompletionHandler}</tt></td>
 *     <td>A handler for consuming the result of an asynchronous operation</td></tr>
 * <tr><td valign=top><tt>{@link java.nio.channels.AsynchronousChannelGroup}</tt></td>
 *     <td>A grouping of asynchronous channels for the purpose of resource sharing</td></tr>
 * </table></blockquote>
 *
 * <p> {@link java.nio.channels.AsynchronousChannel Asynchronous channels} are a
 * special type of channel capable of asynchronous I/O operations. Asynchronous
 * channels are non-blocking and define methods to initiate asynchronous
 * operations, returning a {@link java.util.concurrent.Future} representing the
 * pending result of each operation. The {@code Future} can be used to poll or
 * wait for the result of the operation. Asynchronous I/O operations can also
 * specify a {@link java.nio.channels.CompletionHandler} to invoke when the
 * operation completes. A completion handler is user provided code that is executed
 * to consume the result of I/O operation.
 *
 * <p> This package defines asynchronous-channel classes that are connected to
 * a stream-oriented connecting or listening socket, or a datagram-oriented socket.
 * It also defines the {@link java.nio.channels.AsynchronousFileChannel} class
 * for asynchronous reading, writing, and manipulating a file. As with the {@link
 * java.nio.channels.FileChannel} it supports operations to truncate the file
 * to a specific size, force updates to the file to be written to the storage
 * device, or acquire locks on the whole file or on a specific region of the file.
 * Unlike the {@code FileChannel} it does not define methods for mapping a
 * region of the file directly into memory. Where memory mapped I/O is required,
 * then a {@code FileChannel} can be used.
 *
 * <p> Asynchronous channels are bound to an asynchronous channel group for the
 * purpose of resource sharing. A group has an associated {@link
 * java.util.concurrent.ExecutorService} to which tasks are submitted to handle
 * I/O events and dispatch to completion handlers that consume the result of
 * asynchronous operations performed on channels in the group. The group can
 * optionally be specified when creating the channel or the channel can be bound
 * to a <em>default group</em>. Sophisticated users may wish to create their
 * own asynchronous channel groups or configure the {@code ExecutorService}
 * that will be used for the default group.
 *
 * <p> As with selectors, the implementatin of asynchronous channels can be
 * replaced by "plugging in" an alternative definition or instance of the {@link
 * java.nio.channels.spi.AsynchronousChannelProvider} class defined in the
 * <tt>{@link java.nio.channels.spi}</tt> package.  It is not expected that many
 * developers will actually make use of this facility; it is provided primarily
 * so that sophisticated users can take advantage of operating-system-specific
 * asynchronous I/O mechanisms when very high performance is required.
 *
 * <hr width="80%">
 * <p> Unless otherwise noted, passing a <tt>null</tt> argument to a constructor
 * or method in any class or interface in this package will cause a {@link
 * java.lang.NullPointerException NullPointerException} to be thrown.
 *
 * @since 1.4
 * @updated 1.7
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 */

package java.nio.channels;
