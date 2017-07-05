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
import java.io.IOException;

/**
 * A Future for a pending I/O operation. A PendingFuture allows for the
 * attachment of an additional arbitrary context object and a timer task.
 */

final class PendingFuture<V,A>
    extends AbstractFuture<V,A>
{
    private static final CancellationException CANCELLED =
        new CancellationException();

    private final CompletionHandler<V,? super A> handler;

    // true if result (or exception) is available
    private volatile boolean haveResult;
    private volatile V result;
    private volatile Throwable exc;

    // latch for waiting (created lazily if needed)
    private CountDownLatch latch;

    // optional timer task that is cancelled when result becomes available
    private Future<?> timeoutTask;

    // optional context object
    private volatile Object context;


    PendingFuture(AsynchronousChannel channel,
                  CompletionHandler<V,? super A> handler,
                  A attachment,
                  Object context)
    {
        super(channel, attachment);
        this.handler = handler;
        this.context = context;
    }

    PendingFuture(AsynchronousChannel channel,
                  CompletionHandler<V,? super A> handler,
                  A attachment)
    {
        super(channel, attachment);
        this.handler = handler;
    }

    CompletionHandler<V,? super A> handler() {
        return handler;
    }

    void setContext(Object context) {
        this.context = context;
    }

    Object getContext() {
        return context;
    }

    void setTimeoutTask(Future<?> task) {
        synchronized (this) {
            if (haveResult) {
                task.cancel(false);
            } else {
                this.timeoutTask = task;
            }
        }
    }

    // creates latch if required; return true if caller needs to wait
    private boolean prepareForWait() {
        synchronized (this) {
            if (haveResult) {
                return false;
            } else {
                if (latch == null)
                    latch = new CountDownLatch(1);
                return true;
            }
        }
    }

    /**
     * Sets the result, or a no-op if the result or exception is already set.
     */
    boolean setResult(V res) {
        synchronized (this) {
            if (haveResult)
                return false;
            result = res;
            haveResult = true;
            if (timeoutTask != null)
                timeoutTask.cancel(false);
            if (latch != null)
                latch.countDown();
            return true;
        }
    }

    /**
     * Sets the result, or a no-op if the result or exception is already set.
     */
    boolean setFailure(Throwable x) {
        if (!(x instanceof IOException) && !(x instanceof SecurityException))
            x = new IOException(x);
        synchronized (this) {
            if (haveResult)
                return false;
            exc = x;
            haveResult = true;
            if (timeoutTask != null)
                timeoutTask.cancel(false);
            if (latch != null)
                latch.countDown();
            return true;
        }
    }

    @Override
    public V get() throws ExecutionException, InterruptedException {
        if (!haveResult) {
            boolean needToWait = prepareForWait();
            if (needToWait)
                latch.await();
        }
        if (exc != null) {
            if (exc == CANCELLED)
                throw new CancellationException();
            throw new ExecutionException(exc);
        }
        return result;
    }

    @Override
    public V get(long timeout, TimeUnit unit)
        throws ExecutionException, InterruptedException, TimeoutException
    {
        if (!haveResult) {
            boolean needToWait = prepareForWait();
            if (needToWait)
                if (!latch.await(timeout, unit)) throw new TimeoutException();
        }
        if (exc != null) {
            if (exc == CANCELLED)
                throw new CancellationException();
            throw new ExecutionException(exc);
        }
        return result;
    }

    @Override
    Throwable exception() {
        return (exc != CANCELLED) ? exc : null;
    }

    @Override
    V value() {
        return result;
    }

    @Override
    public boolean isCancelled() {
        return (exc == CANCELLED);
    }

    @Override
    public boolean isDone() {
        return haveResult;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (haveResult)
                return false;    // already completed

            // A shutdown of the channel group will close all channels and
            // shutdown the executor. To ensure that the completion handler
            // is executed we queue the task while holding the lock.
            if (handler != null) {
                prepareForWait();
                Runnable cancelTask = new Runnable() {
                    public void run() {
                        while (!haveResult) {
                            try {
                                latch.await();
                            } catch (InterruptedException ignore) { }
                        }
                        handler.cancelled(attachment());
                    }
                };
                AsynchronousChannel ch = channel();
                if (ch instanceof Groupable) {
                    ((Groupable)ch).group().executeOnPooledThread(cancelTask);
                } else {
                    if (ch instanceof AsynchronousFileChannelImpl) {
                        ((AsynchronousFileChannelImpl)ch).executor().execute(cancelTask);
                    } else {
                        throw new AssertionError("Should not get here");
                    }
                }
            }

            // notify channel
            if (channel() instanceof Cancellable)
                ((Cancellable)channel()).onCancel(this);

            // set result and cancel timer
            exc = CANCELLED;
            haveResult = true;
            if (timeoutTask != null)
                timeoutTask.cancel(false);
        }

        // close channel if forceful cancel
        if (mayInterruptIfRunning) {
            try {
                channel().close();
            } catch (IOException ignore) { }
        }

        // release waiters (this also releases the invoker)
        if (latch != null)
            latch.countDown();
        return true;
    }
}
