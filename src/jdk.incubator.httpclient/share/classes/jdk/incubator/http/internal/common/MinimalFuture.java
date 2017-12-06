/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import java.util.concurrent.atomic.AtomicLong;

/*
 * A CompletableFuture which does not allow any obtrusion logic.
 * All methods of CompletionStage return instances of this class.
 */
public final class MinimalFuture<T> extends CompletableFuture<T> {

    @FunctionalInterface
    public interface ExceptionalSupplier<U> {
        U get() throws Throwable;
    }

    final static AtomicLong TOKENS = new AtomicLong();
    final long id;

    public static <U> MinimalFuture<U> completedFuture(U value) {
        MinimalFuture<U> f = new MinimalFuture<>();
        f.complete(value);
        return f;
    }

    public static <U> CompletableFuture<U> failedFuture(Throwable ex) {
        requireNonNull(ex);
        MinimalFuture<U> f = new MinimalFuture<>();
        f.completeExceptionally(ex);
        return f;
    }

    public static <U> CompletableFuture<U> supply(ExceptionalSupplier<U> supplier) {
        CompletableFuture<U> cf = new MinimalFuture<>();
        try {
            U value = supplier.get();
            cf.complete(value);
        } catch (Throwable t) {
            cf.completeExceptionally(t);
        }
        return cf;
    }

    public static <U> CompletableFuture<U> supply(ExceptionalSupplier<U> supplier, Executor executor) {
        CompletableFuture<U> cf = new MinimalFuture<>();
        cf.completeAsync( () -> {
            try {
                return supplier.get();
            } catch (Throwable ex) {
                throw new CompletionException(ex);
            }
        }, executor);
        return cf;
    }

    public MinimalFuture() {
        super();
        this.id = TOKENS.incrementAndGet();
    }

    /**
     * Creates a defensive copy of the given {@code CompletionStage}.
     *
     * <p> Might be useful both for producers and consumers of {@code
     * CompletionStage}s.
     *
     * <p> Producers are protected from possible uncontrolled modifications
     * (cancellation, completion, obtrusion, etc.) as well as from executing
     * unknown potentially lengthy or faulty dependants in the given {@code
     * CompletionStage}'s default execution facility or synchronously.
     *
     * <p> Consumers are protected from some of the aspects of misbehaving
     * implementations (e.g. accepting results, applying functions, running
     * tasks, etc. more than once or escape of a reference to their private
     * executor, etc.) by providing a reliable proxy they use instead.
     *
     * @param src
     *         the {@code CompletionStage} to make a copy from
     * @param executor
     *         the executor used to propagate the completion
     * @param <T>
     *         the type of the {@code CompletionStage}'s result
     *
     * @return a copy of the given stage
     */
    public static <T> MinimalFuture<T> copy(CompletionStage<T> src,
                                            Executor executor) {
        MinimalFuture<T> copy = new MinimalFuture<>();
        BiConsumer<T, Throwable> relay =
                (result, error) -> {
                    if (error != null) {
                        copy.completeExceptionally(error);
                    } else {
                        copy.complete(result);
                    }
                };

        if (src.getClass() == CompletableFuture.class) {
            // No subclasses! Strictly genuine CompletableFuture.
            src.whenCompleteAsync(relay, executor);
            return copy;
        } else {
            // Don't give our executor away to an unknown CS!
            src.whenComplete(relay);
            return (MinimalFuture<T>)
                    copy.thenApplyAsync(Function.identity(), executor);
        }
    }

    public static <U> MinimalFuture<U> newMinimalFuture() {
        return new MinimalFuture<>();
    }

    @Override
    public void obtrudeValue(T value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void obtrudeException(Throwable ex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return super.toString() + " (id=" + id +")";
    }

    public static <U> MinimalFuture<U> of(CompletionStage<U> stage) {
        MinimalFuture<U> cf = newMinimalFuture();
        stage.whenComplete((r,t) -> complete(cf, r, t));
        return cf;
    }

    private static <U> void complete(CompletableFuture<U> cf, U result, Throwable t) {
        if (t == null) {
            cf.complete(result);
        } else {
            cf.completeExceptionally(t);
        }
    }
}
