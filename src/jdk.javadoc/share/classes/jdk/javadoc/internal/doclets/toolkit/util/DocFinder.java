/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;

public class DocFinder {

    /*
     * A specialized, possibly stateful, function that accepts a method in the
     * hierarchy and returns a value that controls the search or throws an
     * exception, which terminates the search and transparently bubbles
     * up the stack.
     */
    @FunctionalInterface
    public interface Criterion<T, X extends Throwable> {
        Result<T> apply(ExecutableElement method) throws X;
    }

    private final Function<ExecutableElement, Iterable<? extends ExecutableElement>> overriddenMethodLookup;

    DocFinder(Function<ExecutableElement, Iterable<? extends ExecutableElement>> overriddenMethodLookup) {
        this.overriddenMethodLookup = overriddenMethodLookup;
    }

    @SuppressWarnings("serial")
    public static final class NoOverriddenMethodFound extends Exception {

        // only DocFinder should instantiate this exception
        private NoOverriddenMethodFound() { }
    }

    public <T, X extends Throwable> Result<T> search(ExecutableElement method,
                                                     Criterion<T, X> criterion)
            throws X
    {
        try {
            return search0(method, true, false, criterion);
        } catch (NoOverriddenMethodFound e) {
            // should not happen because the exception flag is unset
            throw new AssertionError(e);
        }
    }

    public <T, X extends Throwable> Result<T> find(ExecutableElement method,
                                                   Criterion<T, X> criterion)
            throws NoOverriddenMethodFound, X
    {
        return search0(method, false, true, criterion);
    }

    /*
     * Searches through the overridden methods hierarchy of the provided method.
     *
     * Depending on how it is instructed, the search begins from either the given
     * method or the first method that the given method overrides. The search
     * then applies the given criterion to methods it encounters, in the
     * hierarchy order, until either of the following happens:
     *
     *  - the criterion concludes the search
     *  - the criterion throws an exception
     *  - the hierarchy is exhausted
     *
     * If the search succeeds, the returned result is of type Conclude.
     * Otherwise, the returned result is generally that of the most
     * recent call to Criterion::apply.
     *
     * If the given method overrides no methods (i.e. hierarchy consists of the
     * given method only) and the search is instructed to detect that, the
     * search terminates with an exception.
     */
    private <T, X extends Throwable> Result<T> search0(ExecutableElement method,
                                                       boolean includeMethodInSearch,
                                                       boolean throwExceptionIfDoesNotOverride,
                                                       Criterion<T, X> criterion)
            throws NoOverriddenMethodFound, X
    {
        // if the "overrides" check is requested and does not pass, throw the exception
        // first so that it trumps the result that the search would otherwise had
        Iterator<? extends ExecutableElement> methods = overriddenMethodLookup.apply(method).iterator();
        if (throwExceptionIfDoesNotOverride && !methods.hasNext() ) {
            throw new NoOverriddenMethodFound();
        }
        Result<T> r = includeMethodInSearch ? criterion.apply(method) : Result.CONTINUE();
        if (!(r instanceof Result.Continue<T>)) {
            return r;
        }
        while (methods.hasNext()) {
            ExecutableElement m = methods.next();
            r = criterion.apply(m);
            if (r instanceof Result.Conclude<T>) {
                return r;
            }
        }
        return r;
    }

    private static final Result<?> SKIP = new Skipped<>();
    private static final Result<?> CONTINUE = new Continued<>();

    /*
     * Use static factory methods to get the desired result to return from
     * Criterion. Use instanceof to check for a result type returned from
     * a search. If a use case permits and you prefer Optional API, use
     * the fromOptional/toOptional convenience methods to get and
     * check for the result respectively.
     */
    public sealed interface Result<T> {

        sealed interface Skip<T> extends Result<T> permits Skipped { }

        sealed interface Continue<T> extends Result<T> permits Continued { }

        sealed interface Conclude<T> extends Result<T> permits Concluded {

            T value();
        }

        /*
         * Skips the search on the part of the hierarchy above the method for
         * which this result is returned and continues the search from that
         * method sibling, if any.
         */
        @SuppressWarnings("unchecked")
        static <T> Result<T> SKIP() {
            return (Result<T>) SKIP;
        }

        /*
         * Continues the search.
         */
        @SuppressWarnings("unchecked")
        static <T> Result<T> CONTINUE() {
            return (Result<T>) CONTINUE;
        }

        /*
         * Concludes the search with the given result.
         */
        static <T> Result<T> CONCLUDE(T value) {
            return new Concluded<>(value);
        }

        /*
         * Translates this Result into Optional.
         *
         * Convenience method. Call on the result of a search if you are only
         * interested in whether the search succeeded or failed and you
         * prefer the Optional API.
         */
        default Optional<T> toOptional() {
            return Optional.empty();
        }

        /*
         * Translates the given Optional into a binary decision whether to
         * conclude the search or to continue it.
         *
         * Convenience method. Use in Criterion that can easily provide
         * suitable Optional. Don't use if Criterion needs to skip.
         */
        static <T> Result<T> fromOptional(Optional<T> optional) {
            return optional.map(Result::CONCLUDE).orElseGet(Result::CONTINUE);
        }
    }

    // Note: we hide records behind interfaces, as implementation detail.
    // We don't directly implement Result with these records because it
    // would require more exposure and commitment than is desired. For
    // example, there would need to be public constructors, which
    // would circumvent static factory methods.

    private record Skipped<T>() implements DocFinder.Result.Skip<T> { }

    private record Continued<T>() implements DocFinder.Result.Continue<T> { }

    private record Concluded<T>(T value) implements DocFinder.Result.Conclude<T> {

        Concluded {
            Objects.requireNonNull(value);
        }

        @Override
        public Optional<T> toOptional() {
            return Optional.of(value);
        }
    }
}
