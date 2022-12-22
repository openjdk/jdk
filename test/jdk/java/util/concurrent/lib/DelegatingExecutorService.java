/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * Wraps an ExecutorService to allow ExecutorService and Future's default
 * methods to be tested.
 */
class DelegatingExecutorService implements ExecutorService {
    private final ExecutorService delegate;

    DelegatingExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    private <V> Future<V> wrap(Future<V> future) {
        return new Future<V>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return future.cancel(mayInterruptIfRunning);
            }
            @Override
            public boolean isCancelled() {
                return future.isCancelled();
            }
            @Override
            public boolean isDone() {
                return future.isDone();
            }
            @Override
            public V get() throws InterruptedException, ExecutionException {
                return future.get();
            }
            @Override
            public V get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                return future.get(timeout, unit);
            }
        };
    };

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }
    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }
    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return wrap(delegate.submit(task));
    }
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return wrap(delegate.submit(task, result));
    }
    @Override
    public Future<?> submit(Runnable task) {
        return wrap(delegate.submit(task));
    }
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(tasks).stream().map(f -> wrap(f)).toList();
    }
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit).stream().map(f -> wrap(f)).toList();
    }
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }
    @Override
    public void execute(Runnable task) {
        delegate.execute(task);
    }
}
