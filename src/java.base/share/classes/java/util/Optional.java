/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A container object which may or may not contain a non-{@code null} value.
 * If a value is present, {@code isPresent()} returns {@code true}. If no
 * value is present, the object is considered <i>empty</i> and
 * {@code isPresent()} returns {@code false}.
 *
 * <p>Additional methods that depend on the presence or absence of a contained
 * value are provided, such as {@link #orElse(Object) orElse()}
 * (returns a default value if no value is present) and
 * {@link #ifPresent(Consumer) ifPresent()} (performs an
 * action if a value is present).
 *
 * <p>This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; programmers should treat instances that are
 * {@linkplain #equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may
 * occur. For example, in a future release, synchronization may fail.
 *
 * @apiNote
 * {@code Optional} is primarily intended for use as a method return type where
 * there is a clear need to represent "no result," and where using {@code null}
 * is likely to cause errors. A variable whose type is {@code Optional} should
 * never itself be {@code null}; it should always point to an {@code Optional}
 * instance.
 *
 * @param <T> the type of value
 * @since 1.8
 * @author KostiaY, VilaliiH, OleksiiS
 */
@jdk.internal.ValueBased
public sealed interface Optional<T> {

    /**
     * A record class representing a non-empty Optional
     * @param <T> the type of value
     */
    public record Some<T>(T value) implements Optional<T> {}

    /**
     * A record class representing an empty Optional
     * @param <T> the type of value
     */
    public record None<T>() implements Optional<T> {
        private static final None<?> INSTANCE = new None<>();
    }


    /**
     * Returns an empty {@code Optional} instance.  No value is present for this
     * {@code Optional}.
     *
     * @apiNote
     * Though it may be tempting to do so, avoid testing if an object is empty
     * by comparing with {@code ==} or {@code !=} against instances returned by
     * {@code Optional.empty()}.  There is no guarantee that it is a singleton.
     * Instead, use {@link #isEmpty()} or {@link #isPresent()}.
     *
     * @param <T> The type of the non-existent value
     * @return an empty {@code Optional}
     */
    public static<T> Optional<T> empty() {
        @SuppressWarnings("unchecked")
        Optional<T> t = (Optional<T>) None.INSTANCE;
        return t;
    }

    /**
     * Returns an {@code Optional} describing the given non-{@code null}
     * value.
     *
     * @param value the value to describe, which must be non-{@code null}
     * @param <T> the type of the value
     * @return an {@code Optional} with the value present
     * @throws NullPointerException if value is {@code null}
     */
    public static <T> Optional<T> of(T value) {
        return new Some<>(Objects.requireNonNull(value));
    }

    /**
     * Returns an {@code Optional} describing the given value, if
     * non-{@code null}, otherwise returns an empty {@code Optional}.
     *
     * @param value the possibly-{@code null} value to describe
     * @param <T> the type of the value
     * @return an {@code Optional} with a present value if the specified value
     *         is non-{@code null}, otherwise an empty {@code Optional}
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> ofNullable(T value) {
        return value == null ? (Optional<T>) None.INSTANCE
                             : new Some<>(value);
    }

    /**
     * If a value is present, returns the value, otherwise throws
     * {@code NoSuchElementException}.
     *
     * @apiNote
     * The preferred alternative to this method is {@link #orElseThrow()}.
     *
     * @return the non-{@code null} value described by this {@code Optional}
     * @throws NoSuchElementException if no value is present
     */
    default public T get() {
        return switch (this) {
            case Some<T>(var value) -> value;
            case None<T>() -> throw new NoSuchElementException("No value present");
        };
    }

    /**
     * If a value is present, returns {@code true}, otherwise {@code false}.
     *
     * @return {@code true} if a value is present, otherwise {@code false}
     */
    default public boolean isPresent() {
        return this instanceof Some<?>;
    }

    /**
     * If a value is  not present, returns {@code true}, otherwise
     * {@code false}.
     *
     * @return  {@code true} if a value is not present, otherwise {@code false}
     * @since   11
     */
    default public boolean isEmpty() {
        return this instanceof None<?>;
    }

    /**
     * If a value is present, performs the given action with the value,
     * otherwise does nothing.
     *
     * @param action the action to be performed, if a value is present
     * @throws NullPointerException if value is present and the given action is
     *         {@code null}
     */
    default public void ifPresent(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        if (this instanceof Some<T>(var value)) {
            action.accept(value);
        }
    }

    /**
     * If a value is present, performs the given action with the value,
     * otherwise performs the given empty-based action.
     *
     * @param action the action to be performed, if a value is present
     * @param emptyAction the empty-based action to be performed, if no value is
     *        present
     * @throws NullPointerException if a value is present and the given action
     *         is {@code null}, or no value is present and the given empty-based
     *         action is {@code null}.
     * @since 9
     */
    default public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(emptyAction);
        switch (this) {
            case Some<T>(var value) -> action.accept(value);
            case None<T>() -> emptyAction.run();
        }
    }

    /**
     * If a value is present, and the value matches the given predicate,
     * returns an {@code Optional} describing the value, otherwise returns an
     * empty {@code Optional}.
     *
     * @param predicate the predicate to apply to a value, if present
     * @return an {@code Optional} describing the value of this
     *         {@code Optional}, if a value is present and the value matches the
     *         given predicate, otherwise an empty {@code Optional}
     * @throws NullPointerException if the predicate is {@code null}
     */
    default public Optional<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return switch (this) {
            case Some<T>(var value) -> predicate.test(value) ? this : empty();
            case None<T>() -> empty();
        };
    }

    /**
     * If a value is present, returns an {@code Optional} describing (as if by
     * {@link #ofNullable}) the result of applying the given mapping function to
     * the value, otherwise returns an empty {@code Optional}.
     *
     * <p>If the mapping function returns a {@code null} result then this method
     * returns an empty {@code Optional}.
     *
     * @apiNote
     * This method supports post-processing on {@code Optional} values, without
     * the need to explicitly check for a return status.  For example, the
     * following code traverses a stream of URIs, selects one that has not
     * yet been processed, and creates a path from that URI, returning
     * an {@code Optional<Path>}:
     *
     * <pre>{@code
     *     Optional<Path> p =
     *         uris.stream().filter(uri -> !isProcessedYet(uri))
     *                       .findFirst()
     *                       .map(Paths::get);
     * }</pre>
     *
     * Here, {@code findFirst} returns an {@code Optional<URI>}, and then
     * {@code map} returns an {@code Optional<Path>} for the desired
     * URI if one exists.
     *
     * @param mapper the mapping function to apply to a value, if present
     * @param <U> The type of the value returned from the mapping function
     * @return an {@code Optional} describing the result of applying a mapping
     *         function to the value of this {@code Optional}, if a value is
     *         present, otherwise an empty {@code Optional}
     * @throws NullPointerException if the mapping function is {@code null}
     */
    default public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return switch (this) {
            case Some<T>(var value) -> ofNullable(mapper.apply(value));
            case None<T>() -> empty();
        };
    }

    /**
     * If a value is present, returns the result of applying the given
     * {@code Optional}-bearing mapping function to the value, otherwise returns
     * an empty {@code Optional}.
     *
     * <p>This method is similar to {@link #map(Function)}, but the mapping
     * function is one whose result is already an {@code Optional}, and if
     * invoked, {@code flatMap} does not wrap it within an additional
     * {@code Optional}.
     *
     * @param <U> The type of value of the {@code Optional} returned by the
     *            mapping function
     * @param mapper the mapping function to apply to a value, if present
     * @return the result of applying an {@code Optional}-bearing mapping
     *         function to the value of this {@code Optional}, if a value is
     *         present, otherwise an empty {@code Optional}
     * @throws NullPointerException if the mapping function is {@code null} or
     *         returns a {@code null} result
     */
    default public <U> Optional<U> flatMap(Function<? super T, ? extends Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        return switch (this) {
            case Some<T>(var value) -> {
                @SuppressWarnings("unchecked")
                Optional<U> r = (Optional<U>) mapper.apply(value);
                yield Objects.requireNonNull(r);
            }
            case None<T>() -> empty();
        };
    }

    /**
     * If a value is present, returns an {@code Optional} describing the value,
     * otherwise returns an {@code Optional} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code Optional}
     *        to be returned
     * @return returns an {@code Optional} describing the value of this
     *         {@code Optional}, if a value is present, otherwise an
     *         {@code Optional} produced by the supplying function.
     * @throws NullPointerException if the supplying function is {@code null} or
     *         produces a {@code null} result
     * @since 9
     */
    default public Optional<T> or(Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        return switch (this) {
            case Some<T>(var value) -> this;
            case None<T>() -> {
                @SuppressWarnings("unchecked")
                Optional<T> r = (Optional<T>) supplier.get();
                yield Objects.requireNonNull(r);
            }
        };
    }

    /**
     * If a value is present, returns a sequential {@link Stream} containing
     * only that value, otherwise returns an empty {@code Stream}.
     *
     * @apiNote
     * This method can be used to transform a {@code Stream} of optional
     * elements to a {@code Stream} of present value elements:
     * <pre>{@code
     *     Stream<Optional<T>> os = ..
     *     Stream<T> s = os.flatMap(Optional::stream)
     * }</pre>
     *
     * @return the optional value as a {@code Stream}
     * @since 9
     */
    default public Stream<T> stream() {
        return switch (this) {
            case Some<T>(var value) -> Stream.of(value);
            case None<T>() -> Stream.empty();
        };
    }

    /**
     * If a value is present, returns the value, otherwise returns
     * {@code other}.
     *
     * @param other the value to be returned, if no value is present.
     *        May be {@code null}.
     * @return the value, if present, otherwise {@code other}
     */
    default public T orElse(T other) {
        return switch (this) {
            case Some<T>(var value) -> value;
            case None<T>() -> other;
        };
    }

    /**
     * If a value is present, returns the value, otherwise returns the result
     * produced by the supplying function.
     *
     * @param supplier the supplying function that produces a value to be returned
     * @return the value, if present, otherwise the result produced by the
     *         supplying function
     * @throws NullPointerException if no value is present and the supplying
     *         function is {@code null}
     */
    default public T orElseGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return switch (this) {
            case Some<T>(var value) -> value;
            case None<T>() -> supplier.get();
        };
    }

    /**
     * If a value is present, returns the value, otherwise throws
     * {@code NoSuchElementException}.
     *
     * @return the non-{@code null} value described by this {@code Optional}
     * @throws NoSuchElementException if no value is present
     * @since 10
     */
    default public T orElseThrow() {
        return switch (this) {
            case Some<T>(var value) -> value;
            case None<T>() -> throw new NoSuchElementException("No value present");
        };
    }

    /**
     * If a value is present, returns the value, otherwise throws an exception
     * produced by the exception supplying function.
     *
     * @apiNote
     * A method reference to the exception constructor with an empty argument
     * list can be used as the supplier. For example,
     * {@code IllegalStateException::new}
     *
     * @param <X> Type of the exception to be thrown
     * @param exceptionSupplier the supplying function that produces an
     *        exception to be thrown
     * @return the value, if present
     * @throws X if no value is present
     * @throws NullPointerException if no value is present and the exception
     *         supplying function is {@code null} or produces a {@code null} result
     */
    default public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        Objects.requireNonNull(exceptionSupplier);
        return switch (this) {
            case Some<T>(var value) -> value;
            case None<T>() -> throw exceptionSupplier.get();
        };
    }
}
