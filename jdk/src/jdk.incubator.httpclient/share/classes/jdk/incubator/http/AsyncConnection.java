/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import jdk.incubator.http.internal.common.ByteBufferReference;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implemented by classes that offer an asynchronous interface.
 *
 * PlainHttpConnection, AsyncSSLConnection AsyncSSLDelegate.
 *
 * setAsyncCallbacks() is called to set the callback for reading
 * and error notification. Reads all happen on the selector thread, which
 * must not block.
 *
 * Writing uses the same write() methods as used in blocking mode.
 * Queues are employed on the writing side to buffer data while it is waiting
 * to be sent. This strategy relies on HTTP/2 protocol flow control to stop
 * outgoing queue from continually growing. Writes can be initiated by the
 * calling thread, but if socket becomes full then the queue is emptied by
 * the selector thread
 */
interface AsyncConnection {

    /**
     * Enables asynchronous sending and receiving mode. The given async
     * receiver will receive all incoming data. asyncInput() will be called
     * to trigger reads. asyncOutput() will be called to drive writes.
     *
     * The errorReceiver callback must be called when any fatal exception
     * occurs. Connection is assumed to be closed afterwards.
     */
    void setAsyncCallbacks(Consumer<ByteBufferReference> asyncReceiver,
                           Consumer<Throwable> errorReceiver,
                           Supplier<ByteBufferReference> readBufferSupplier);



    /**
     * Does whatever is required to start reading. Usually registers
     * an event with the selector thread.
     */
    void startReading();

    /**
     * In async mode, this method puts buffers at the end of the send queue.
     * When in async mode, calling this method should later be followed by
     * subsequent flushAsync invocation.
     * That allows multiple threads to put buffers into the queue while some other
     * thread is writing.
     */
    void writeAsync(ByteBufferReference[] buffers) throws IOException;

    /**
     * In async mode, this method may put buffers at the beginning of send queue,
     * breaking frames sequence and allowing to write these buffers before other
     * buffers in the queue.
     * When in async mode, calling this method should later be followed by
     * subsequent flushAsync invocation.
     * That allows multiple threads to put buffers into the queue while some other
     * thread is writing.
     */
    void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException;

    /**
     * This method should be called after any writeAsync/writeAsyncUnordered
     * invocation.
     * If there is a race to flushAsync from several threads one thread
     * (race winner) capture flush operation and write the whole queue content.
     * Other threads (race losers) exits from the method (not blocking)
     * and continue execution.
     */
    void flushAsync() throws IOException;

}
