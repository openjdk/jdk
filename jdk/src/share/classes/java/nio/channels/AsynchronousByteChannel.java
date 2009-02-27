/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * An asynchronous channel that can read and write bytes.
 *
 * <p> Some channels may not allow more than one read or write to be outstanding
 * at any given time. If a thread invokes a read method before a previous read
 * operation has completed then a {@link ReadPendingException} will be thrown.
 * Similarly, if a write method is invoked before a previous write has completed
 * then {@link WritePendingException} is thrown. Whether or not other kinds of
 * I/O operations may proceed concurrently with a read operation depends upon
 * the type of the channel.
 *
 * <p> Note that {@link java.nio.ByteBuffer ByteBuffers} are not safe for use by
 * multiple concurrent threads. When a read or write operation is initiated then
 * care must be taken to ensure that the buffer is not accessed until the
 * operation completes.
 *
 * @see Channels#newInputStream(AsynchronousByteChannel)
 * @see Channels#newOutputStream(AsynchronousByteChannel)
 *
 * @since 1.7
 */

public interface AsynchronousByteChannel
    extends AsynchronousChannel
{
    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * <p> This method initiates an operation to read a sequence of bytes from
     * this channel into the given buffer. The method returns a {@link Future}
     * representing the pending result of the operation. The result of the
     * operation, obtained by invoking the {@code Future} 's {@link
     * Future#get() get} method, is the number of bytes read or {@code -1} if
     * all bytes have been read and the channel has reached end-of-stream.
     *
     * <p> This method initiates a read operation to read up to <i>r</i> bytes
     * from the channel, where <i>r</i> is the number of bytes remaining in the
     * buffer, that is, {@code dst.remaining()} at the time that the read is
     * attempted. Where <i>r</i> is 0, the read operation completes immediately
     * with a result of {@code 0} without initiating an I/O operation.
     *
     * <p> Suppose that a byte sequence of length <i>n</i> is read, where
     * <tt>0</tt>&nbsp;<tt>&lt;</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>.
     * This byte sequence will be transferred into the buffer so that the first
     * byte in the sequence is at index <i>p</i> and the last byte is at index
     * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<tt>1</tt>,
     * where <i>p</i> is the buffer's position at the moment the read is
     * performed. Upon completion the buffer's position will be equal to
     * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>; its limit will not have changed.
     *
     * <p> Buffers are not safe for use by multiple concurrent threads so care
     * should be taken to not to access the buffer until the operaton has completed.
     *
     * <p> This method may be invoked at any time. Some channel types may not
     * allow more than one read to be outstanding at any given time. If a thread
     * initiates a read operation before a previous read operation has
     * completed then a {@link ReadPendingException} will be thrown.
     *
     * <p> The <tt>handler</tt> parameter is used to specify a {@link
     * CompletionHandler}. When the read operation completes the handler's
     * {@link CompletionHandler#completed completed} method is executed.
     *
     *
     * @param   dst
     *          The buffer into which bytes are to be transferred
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The completion handler object; can be {@code null}
     *
     * @return  A Future representing the result of the operation
     *
     * @throws  IllegalArgumentException
     *          If the buffer is read-only
     * @throws  ReadPendingException
     *          If the channel does not allow more than one read to be outstanding
     *          and a previous read has not completed
     */
    <A> Future<Integer> read(ByteBuffer dst,
                             A attachment,
                             CompletionHandler<Integer,? super A> handler);

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * <p> An invocation of this method of the form <tt>c.read(dst)</tt>
     * behaves in exactly the same manner as the invocation
     * <blockquote><pre>
     * c.read(dst, null, null);</pre></blockquote>
     *
     * @param   dst
     *          The buffer into which bytes are to be transferred
     *
     * @return  A Future representing the result of the operation
     *
     * @throws  IllegalArgumentException
     *          If the buffer is read-only
     * @throws  ReadPendingException
     *          If the channel does not allow more than one read to be outstanding
     *          and a previous read has not completed
     */
    Future<Integer> read(ByteBuffer dst);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * <p> This method initiates an operation to write a sequence of bytes to
     * this channel from the given buffer. This method returns a {@link
     * Future} representing the pending result of the operation. The result
     * of the operation, obtained by invoking the <tt>Future</tt>'s {@link
     * Future#get() get} method, is the number of bytes written, possibly zero.
     *
     * <p> This method initiates a write operation to write up to <i>r</i> bytes
     * to the channel, where <i>r</i> is the number of bytes remaining in the
     * buffer, that is, {@code src.remaining()}  at the moment the write is
     * attempted. Where <i>r</i> is 0, the write operation completes immediately
     * with a result of {@code 0} without initiating an I/O operation.
     *
     * <p> Suppose that a byte sequence of length <i>n</i> is written, where
     * <tt>0</tt>&nbsp;<tt>&lt;</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>.
     * This byte sequence will be transferred from the buffer starting at index
     * <i>p</i>, where <i>p</i> is the buffer's position at the moment the
     * write is performed; the index of the last byte written will be
     * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<tt>1</tt>.
     * Upon completion the buffer's position will be equal to
     * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>; its limit will not have changed.
     *
     * <p> Buffers are not safe for use by multiple concurrent threads so care
     * should be taken to not to access the buffer until the operaton has completed.
     *
     * <p> This method may be invoked at any time. Some channel types may not
     * allow more than one write to be outstanding at any given time. If a thread
     * initiates a write operation before a previous write operation has
     * completed then a {@link WritePendingException} will be thrown.
     *
     * <p> The <tt>handler</tt> parameter is used to specify a {@link
     * CompletionHandler}. When the write operation completes the handler's
     * {@link CompletionHandler#completed completed} method is executed.
     *
     * @param   src
     *          The buffer from which bytes are to be retrieved
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The completion handler object; can be {@code null}
     *
     * @return  A Future representing the result of the operation
     *
     * @throws  WritePendingException
     *          If the channel does not allow more than one write to be outstanding
     *          and a previous write has not completed
     */
    <A> Future<Integer> write(ByteBuffer src,
                              A attachment,
                              CompletionHandler<Integer,? super A> handler);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * <p> An invocation of this method of the form <tt>c.write(src)</tt>
     * behaves in exactly the same manner as the invocation
     * <blockquote><pre>
     * c.write(src, null, null);</pre></blockquote>
     *
     * @param   src
     *          The buffer from which bytes are to be retrieved
     *
     * @return A Future representing the result of the operation
     *
     * @throws  WritePendingException
     *          If the channel does not allow more than one write to be outstanding
     *          and a previous write has not completed
     */
    Future<Integer> write(ByteBuffer src);
}
