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

import java.nio.channels.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.io.FileDescriptor;
import java.io.IOException;

/**
 * "Portable" implementation of AsynchronousFileChannel for use on operating
 * systems that don't support asynchronous file I/O.
 */

public class SimpleAsynchronousFileChannelImpl
    extends AsynchronousFileChannelImpl
{
    // lazy initialization of default thread pool for file I/O
    private static class DefaultExecutorHolder {
        static final ExecutorService defaultExecutor =
            ThreadPool.createDefault().executor();
    }

    // Used to make native read and write calls
    private static final FileDispatcher nd = new FileDispatcherImpl();

    // indicates if the associated thread pool is the default thread pool
    private final boolean isDefaultExecutor;

    // Thread-safe set of IDs of native threads, for signalling
    private final NativeThreadSet threads = new NativeThreadSet(2);


    SimpleAsynchronousFileChannelImpl(FileDescriptor fdObj,
                                      boolean reading,
                                      boolean writing,
                                      ExecutorService executor,
                                      boolean isDefaultexecutor)
    {
        super(fdObj, reading, writing, executor);
        this.isDefaultExecutor = isDefaultexecutor;
    }

    public static AsynchronousFileChannel open(FileDescriptor fdo,
                                               boolean reading,
                                               boolean writing,
                                               ThreadPool pool)
    {
        // Executor is either default or based on pool parameters
        ExecutorService executor;
        boolean isDefaultexecutor;
        if (pool == null) {
            executor = DefaultExecutorHolder.defaultExecutor;
            isDefaultexecutor = true;
        } else {
            executor = pool.executor();
            isDefaultexecutor = false;
        }
        return new SimpleAsynchronousFileChannelImpl(fdo,
            reading, writing, executor, isDefaultexecutor);
    }

    @Override
    public void close() throws IOException {
        // mark channel as closed
        synchronized (fdObj) {
            if (closed)
                return;     // already closed
            closed = true;
            // from this point on, if another thread invokes the begin() method
            // then it will throw ClosedChannelException
        }

        // Invalidate and release any locks that we still hold
        invalidateAllLocks();

        // signal any threads blocked on this channel
        nd.preClose(fdObj);
        threads.signalAndWait();

        // wait until all async I/O operations have completely gracefully
        closeLock.writeLock().lock();
        try {
            // do nothing
        } finally {
            closeLock.writeLock().unlock();
        }

        // close file
        nd.close(fdObj);

        // shutdown executor if specific to this channel
        if (!isDefaultExecutor) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    executor.shutdown();
                    return null;
                }
            });
        }
    }

    @Override
    public long size() throws IOException {
        int ti = threads.add();
        try {
            long n = 0L;
            try {
                begin();
                do {
                    n = nd.size(fdObj);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return n;
            } finally {
                end(n >= 0L);
            }
        } finally {
            threads.remove(ti);
        }
    }

    @Override
    public AsynchronousFileChannel truncate(long size) throws IOException {
        if (size < 0L)
            throw new IllegalArgumentException("Negative size");
        if (!writing)
            throw new NonWritableChannelException();
        int ti = threads.add();
        try {
            long n = 0L;
            try {
                begin();
                do {
                    n = nd.size(fdObj);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());

                // truncate file if 'size' less than current size
                if (size < n && isOpen()) {
                    do {
                        n = nd.truncate(fdObj, size);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                }
                return this;
            } finally {
                end(n > 0);
            }
        } finally {
            threads.remove(ti);
        }
    }

    @Override
    public void force(boolean metaData) throws IOException {
        int ti = threads.add();
        try {
            int n = 0;
            try {
                begin();
                do {
                    n = nd.force(fdObj, metaData);
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
            } finally {
                end(n >= 0);
            }
        } finally {
            threads.remove(ti);
        }
    }

    @Override
    public <A> Future<FileLock> lock(final long position,
                                     final long size,
                                     final boolean shared,
                                     A attachment,
                                     final CompletionHandler<FileLock,? super A> handler)
    {
        if (shared && !reading)
            throw new NonReadableChannelException();
        if (!shared && !writing)
            throw new NonWritableChannelException();

        // add to lock table
        final FileLockImpl fli = addToFileLockTable(position, size, shared);
        if (fli == null) {
            CompletedFuture<FileLock,A> result = CompletedFuture
                .withFailure(this, new ClosedChannelException(), attachment);
            Invoker.invokeIndirectly(handler, result, executor);
            return result;
        }

        final PendingFuture<FileLock,A> result =
            new PendingFuture<FileLock,A>(this, handler, attachment);
        Runnable task = new Runnable() {
            public void run() {
                int ti = threads.add();
                try {
                    int n;
                    try {
                        begin();
                        do {
                            n = nd.lock(fdObj, true, position, size, shared);
                        } while ((n == FileDispatcher.INTERRUPTED) && isOpen());
                        if (n == FileDispatcher.LOCKED && isOpen()) {
                            result.setResult(fli);
                        } else {
                            throw new AsynchronousCloseException();
                        }
                    } catch (IOException x) {
                        removeFromFileLockTable(fli);
                        if (!isOpen())
                            x = new AsynchronousCloseException();
                        result.setFailure(x);
                    } finally {
                        end();
                    }
                } finally {
                    threads.remove(ti);
                }
                Invoker.invokeUnchecked(handler, result);
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ree) {
            // rollback
            removeFromFileLockTable(fli);
            throw new ShutdownChannelGroupException();
        }
        return result;
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared)
        throws IOException
    {
        if (shared && !reading)
            throw new NonReadableChannelException();
        if (!shared && !writing)
            throw new NonWritableChannelException();

        // add to lock table
        FileLockImpl fli = addToFileLockTable(position, size, shared);
        if (fli == null)
            throw new ClosedChannelException();

        int ti = threads.add();
        boolean gotLock = false;
        try {
            begin();
            int n;
            do {
                n = nd.lock(fdObj, false, position, size, shared);
            } while ((n == FileDispatcher.INTERRUPTED) && isOpen());
            if (n == FileDispatcher.LOCKED && isOpen()) {
                gotLock = true;
                return fli;    // lock acquired
            }
            if (n == FileDispatcher.NO_LOCK)
                return null;    // locked by someone else
            if (n == FileDispatcher.INTERRUPTED)
                throw new AsynchronousCloseException();
            // should not get here
            throw new AssertionError();
        } finally {
            if (!gotLock)
                removeFromFileLockTable(fli);
            end();
            threads.remove(ti);
        }
    }

    @Override
    protected void implRelease(FileLockImpl fli) throws IOException {
        nd.release(fdObj, fli.position(), fli.size());
    }

    @Override
    public <A> Future<Integer> read(final ByteBuffer dst,
                                    final long position,
                                    A attachment,
                                    final CompletionHandler<Integer,? super A> handler)
    {
        if (position < 0)
            throw new IllegalArgumentException("Negative position");
        if (!reading)
            throw new NonReadableChannelException();
        if (dst.isReadOnly())
            throw new IllegalArgumentException("Read-only buffer");

        // complete immediately if channel closed or no space remaining
        if (!isOpen() || (dst.remaining() == 0)) {
            CompletedFuture<Integer,A> result;
            if (isOpen()) {
                result = CompletedFuture.withResult(this, 0, attachment);
            } else {
                result = CompletedFuture.withFailure(this,
                    new ClosedChannelException(), attachment);
            }
            Invoker.invokeIndirectly(handler, result, executor);
            return result;
        }

        final PendingFuture<Integer,A> result =
            new PendingFuture<Integer,A>(this, handler, attachment);
        Runnable task = new Runnable() {
            public void run() {
                int ti = threads.add();
                try {
                    begin();
                    int n;
                    do {
                        n = IOUtil.read(fdObj, dst, position, nd, null);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                    if (n < 0 && !isOpen())
                        throw new AsynchronousCloseException();
                    result.setResult(n);
                } catch (IOException x) {
                    if (!isOpen())
                        x = new AsynchronousCloseException();
                    result.setFailure(x);
                } finally {
                    end();
                    threads.remove(ti);
                }
                Invoker.invokeUnchecked(handler, result);
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
        return result;
    }

    @Override
    public <A> Future<Integer> write(final ByteBuffer src,
                                     final long position,
                                     A attachment,
                                     final CompletionHandler<Integer,? super A> handler)
    {
        if (position < 0)
            throw new IllegalArgumentException("Negative position");
        if (!writing)
            throw new NonWritableChannelException();

        // complete immediately if channel is closed or no bytes remaining
        if (!isOpen() || (src.remaining() == 0)) {
            CompletedFuture<Integer,A> result;
            if (isOpen()) {
                result = CompletedFuture.withResult(this, 0, attachment);
            } else {
                result = CompletedFuture.withFailure(this,
                    new ClosedChannelException(), attachment);
            }
            Invoker.invokeIndirectly(handler, result, executor);
            return result;
        }

        final PendingFuture<Integer,A> result =
            new PendingFuture<Integer,A>(this, handler, attachment);
        Runnable task = new Runnable() {
            public void run() {
                int ti = threads.add();
                try {
                    begin();
                    int n;
                    do {
                        n = IOUtil.write(fdObj, src, position, nd, null);
                    } while ((n == IOStatus.INTERRUPTED) && isOpen());
                    if (n < 0 && !isOpen())
                        throw new AsynchronousCloseException();
                    result.setResult(n);
                } catch (IOException x) {
                    if (!isOpen())
                        x = new AsynchronousCloseException();
                    result.setFailure(x);
                } finally {
                    end();
                    threads.remove(ti);
                }
                Invoker.invokeUnchecked(handler, result);
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
        return result;
    }
}
