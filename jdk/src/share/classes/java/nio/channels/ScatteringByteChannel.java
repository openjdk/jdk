/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.nio.ByteBuffer;


/**
 * A channel that can read bytes into a sequence of buffers.
 *
 * <p> A <i>scattering</i> read operation reads, in a single invocation, a
 * sequence of bytes into one or more of a given sequence of buffers.
 * Scattering reads are often useful when implementing network protocols or
 * file formats that, for example, group data into segments consisting of one
 * or more fixed-length headers followed by a variable-length body.  Similar
 * <i>gathering</i> write operations are defined in the {@link
 * GatheringByteChannel} interface.  </p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public interface ScatteringByteChannel
    extends ReadableByteChannel
{

    /**
     * Reads a sequence of bytes from this channel into a subsequence of the
     * given buffers.
     *
     * <p> An invocation of this method attempts to read up to <i>r</i> bytes
     * from this channel, where <i>r</i> is the total number of bytes remaining
     * the specified subsequence of the given buffer array, that is,
     *
     * <blockquote><pre>
     * dsts[offset].remaining()
     *     + dsts[offset+1].remaining()
     *     + ... + dsts[offset+length-1].remaining()</pre></blockquote>
     *
     * at the moment that this method is invoked.
     *
     * <p> Suppose that a byte sequence of length <i>n</i> is read, where
     * <tt>0</tt>&nbsp;<tt>&lt;=</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>.
     * Up to the first <tt>dsts[offset].remaining()</tt> bytes of this sequence
     * are transferred into buffer <tt>dsts[offset]</tt>, up to the next
     * <tt>dsts[offset+1].remaining()</tt> bytes are transferred into buffer
     * <tt>dsts[offset+1]</tt>, and so forth, until the entire byte sequence
     * is transferred into the given buffers.  As many bytes as possible are
     * transferred into each buffer, hence the final position of each updated
     * buffer, except the last updated buffer, is guaranteed to be equal to
     * that buffer's limit.
     *
     * <p> This method may be invoked at any time.  If another thread has
     * already initiated a read operation upon this channel, however, then an
     * invocation of this method will block until the first operation is
     * complete. </p>
     *
     * @param  dsts
     *         The buffers into which bytes are to be transferred
     *
     * @param  offset
     *         The offset within the buffer array of the first buffer into
     *         which bytes are to be transferred; must be non-negative and no
     *         larger than <tt>dsts.length</tt>
     *
     * @param  length
     *         The maximum number of buffers to be accessed; must be
     *         non-negative and no larger than
     *         <tt>dsts.length</tt>&nbsp;-&nbsp;<tt>offset</tt>
     *
     * @return The number of bytes read, possibly zero,
     *         or <tt>-1</tt> if the channel has reached end-of-stream
     *
     * @throws  IndexOutOfBoundsException
     *          If the preconditions on the <tt>offset</tt> and <tt>length</tt>
     *          parameters do not hold
     *
     * @throws  NonReadableChannelException
     *          If this channel was not opened for reading
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  AsynchronousCloseException
     *          If another thread closes this channel
     *          while the read operation is in progress
     *
     * @throws  ClosedByInterruptException
     *          If another thread interrupts the current thread
     *          while the read operation is in progress, thereby
     *          closing the channel and setting the current thread's
     *          interrupt status
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException;

    /**
     * Reads a sequence of bytes from this channel into the given buffers.
     *
     * <p> An invocation of this method of the form <tt>c.read(dsts)</tt>
     * behaves in exactly the same manner as the invocation
     *
     * <blockquote><pre>
     * c.read(dsts, 0, dsts.length);</pre></blockquote>
     *
     * @param  dsts
     *         The buffers into which bytes are to be transferred
     *
     * @return The number of bytes read, possibly zero,
     *         or <tt>-1</tt> if the channel has reached end-of-stream
     *
     * @throws  NonReadableChannelException
     *          If this channel was not opened for reading
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  AsynchronousCloseException
     *          If another thread closes this channel
     *          while the read operation is in progress
     *
     * @throws  ClosedByInterruptException
     *          If another thread interrupts the current thread
     *          while the read operation is in progress, thereby
     *          closing the channel and setting the current thread's
     *          interrupt status
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public long read(ByteBuffer[] dsts) throws IOException;

}
