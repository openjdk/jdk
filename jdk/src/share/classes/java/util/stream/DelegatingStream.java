/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util.stream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * A {@code Stream} implementation that delegates operations to another {@code
 * Stream}.
 *
 * @param <T> type of stream elements for this stream and underlying delegate
 * stream
 *
 * @since 1.8
 */
public class DelegatingStream<T> implements Stream<T> {
    final private Stream<T> delegate;

    /**
     * Construct a {@code Stream} that delegates operations to another {@code
     * Stream}.
     *
     * @param delegate the underlying {@link Stream} to which we delegate all
     *                 {@code Stream} methods
     * @throws NullPointerException if the delegate is null
     */
    public DelegatingStream(Stream<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    // -- BaseStream methods --

    @Override
    public Spliterator<T> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public boolean isParallel() {
        return delegate.isParallel();
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    // -- Stream methods --

    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
        return delegate.filter(predicate);
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return delegate.map(mapper);
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return delegate.mapToInt(mapper);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return delegate.mapToLong(mapper);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return delegate.mapToDouble(mapper);
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return delegate.flatMap(mapper);
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return delegate.flatMapToInt(mapper);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return delegate.flatMapToLong(mapper);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return delegate.flatMapToDouble(mapper);
    }

    @Override
    public Stream<T> distinct() {
        return delegate.distinct();
    }

    @Override
    public Stream<T> sorted() {
        return delegate.sorted();
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
        return delegate.sorted(comparator);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        delegate.forEach(action);
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        delegate.forEachOrdered(action);
    }

    @Override
    public Stream<T> peek(Consumer<? super T> consumer) {
        return delegate.peek(consumer);
    }

    @Override
    public Stream<T> limit(long maxSize) {
        return delegate.limit(maxSize);
    }

    @Override
    public Stream<T> substream(long startingOffset) {
        return delegate.substream(startingOffset);
    }

    @Override
    public Stream<T> substream(long startingOffset, long endingOffset) {
        return delegate.substream(startingOffset, endingOffset);
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return delegate.toArray(generator);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return delegate.reduce(identity, accumulator);
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return delegate.reduce(accumulator);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator,
                        BinaryOperator<U> combiner) {
        return delegate.reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(Supplier<R> resultFactory,
                         BiConsumer<R, ? super T> accumulator,
                         BiConsumer<R, R> combiner) {
        return delegate.collect(resultFactory, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return delegate.collect(collector);
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return delegate.max(comparator);
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return delegate.min(comparator);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return delegate.anyMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return delegate.allMatch(predicate);
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return delegate.noneMatch(predicate);
    }

    @Override
    public Optional<T> findFirst() {
        return delegate.findFirst();
    }

    @Override
    public Optional<T> findAny() {
        return delegate.findAny();
    }

    @Override
    public Stream<T> unordered() {
        return delegate.unordered();
    }

    @Override
    public Stream<T> sequential() {
        return delegate.sequential();
    }

    @Override
    public Stream<T> parallel() {
        return delegate.parallel();
    }
}
