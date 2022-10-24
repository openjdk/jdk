/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;

public class DocFinder {

    /*
     * A specialized function that accepts a method in the hierarchy and
     * returns a value that controls the search or throws a possibly
     * checked exception, which terminates the search and transparently
     * bubbles up the stack.
     *
     * FIXME: If a method does not meet the criterion, returns an empty optional.
     *
     * In a rare case, if a criterion performs inner searches and the search
     * client needs to disambiguate between the outer search exceptions and
     * any inner search exceptions, the criterion needs to provide extra code
     * (to wrap and/or unwrap exceptions). For example:
     *
     *     try {
     *         r = finder.trySearch(method, m -> {
     *             ...
     *             try {
     *                 r1 = finder.trySearch(...);
     *             } catch (NoOverriddenMethodsFound e) {
     *                 throw new MyInnerSearchException(e);
     *             }
     *             ...
     *         });
     *     } catch (NoOverriddenMethodsFound e) {
     *         ...
     *     } catch (MyInnerSearchException e) {
     *         ...
     *     }
     *
     * Since such a use case should be rare, this API does not account for it.
     * This allows to reduce bloat and streamline a typical use case.
     *
     * Here are some examples of the API bloat avoided:
     *
     *   - unconditional unwrapping of a dedicated exception thrown by
     *     a search method
     *   - either unconditional handling of a dedicated exception thrown by
     *     a search method, or having multiple (overloaded?) search methods
     *     and types of criterion: a criterion that can throw and a criterion
     *     that cannot throw an exception
     */
    @FunctionalInterface
    public interface Criterion<T, X extends Throwable> {
        Result<T> apply(ExecutableElement method) throws X;
    }

    private final Function<ExecutableElement, ExecutableElement> overriddenMethodLookup;
    private final BiFunction<ExecutableElement, ExecutableElement, Iterable<ExecutableElement>> implementedMethodsLookup;

    DocFinder(Function<ExecutableElement, ExecutableElement> overriddenMethodLookup,
              BiFunction<ExecutableElement, ExecutableElement, Iterable<ExecutableElement>> implementedMethodsLookup) {
        this.overriddenMethodLookup = overriddenMethodLookup;
        this.implementedMethodsLookup = implementedMethodsLookup;
    }

    public static final class NoOverriddenMethodsFound extends Exception {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        // only DocFinder should instantiate this exception
        private NoOverriddenMethodsFound() { }
    }

    public <T, X extends Throwable> Result<T> search(ExecutableElement method,
                                                     Criterion<T, X> criterion)
            throws X
    {
        return search(method, true, criterion);
    }

    public <T, X extends Throwable> Result<T> search(ExecutableElement method,
                                                     boolean includeMethod,
                                                     Criterion<T, X> criterion)
            throws X
    {
        try {
            return search0(method, includeMethod, false, criterion);
        } catch (NoOverriddenMethodsFound e) {
            // should not happen because the exception flag is unset
            throw new AssertionError(e);
        }
    }

    public <T, X extends Throwable> Result<T> trySearch(ExecutableElement method,
                                                        Criterion<T, X> criterion)
            throws NoOverriddenMethodsFound, X
    {
        return search0(method, false, true, criterion);
    }

    /*
     * Overridden methods hierarchy search.
     *
     * Depending on how it is instructed, search starts either from the given
     * method or the first method it overrides. The search then applies the
     * given criterion to that method and methods up the hierarchy, in order,
     * until either of the following happens:
     *
     *  - the criterion returns a non-empty optional
     *  - the criterion throws an exception
     *  - the hierarchy is exhausted
     *  - the given method overrides no methods and
     *    the search is instructed to detect that
     */
    private <T, X extends Throwable> Result<T> search0(ExecutableElement method,
                                                       boolean includeMethodInSearch,
                                                       boolean throwExceptionIfDoesNotOverride,
                                                       Criterion<T, X> criterion)
            throws NoOverriddenMethodsFound, X
    {
        // if the "overrides" check is requested, perform it first so that we
        // could throw the exception regardless of the search outcome on
        // this method
        Iterator<ExecutableElement> methods = methodsOverriddenBy(method);
        if (throwExceptionIfDoesNotOverride && !methods.hasNext() ) {
            throw new NoOverriddenMethodsFound();
        }
        Result<T> r = includeMethodInSearch ? criterion.apply(method) : Result.CONTINUE();
        if (!(r instanceof Result.Continue<T>)) {
            return r;
        }
        while (methods.hasNext()) {
            ExecutableElement m = methods.next();
            r = search0(m, true, false /* don't check for overrides */, criterion);
            if (r instanceof Result.Conclude<T>) {
                return r;
            }
        }
        return r;
    }

    // we see overridden and implemented methods as overridden (the way JLS does)
    private Iterator<ExecutableElement> methodsOverriddenBy(ExecutableElement method) {
        // TODO: add laziness if required
        var list = new ArrayList<ExecutableElement>();
        ExecutableElement overridden = overriddenMethodLookup.apply(method);
        if (overridden != null) {
            list.add(overridden);
        }
        implementedMethodsLookup.apply(method, method).forEach(list::add);
        return list.iterator();
    }

    // SKIP and CONTINUE could alternatively be modelled as constants of an
    // enum that implements Control<T> in a sealed fashion. However, there
    // are a few issues with that:
    //
    //   1. Since enums cannot be generic, such an enum would need to implement
    //      Control<Object> and each use of its constants directly would
    //      require ugly casts. Yes, helper static methods could
    //      remediate that, but then those methods would
    //      coexist with enum constants. That won't be
    //      clean. (See Collections.EMPTY_LIST and
    //      Collections.emptyList().)
    //   2. Having pattern match on Control<T> subtypes in switch would
    //      then require a subsequent switch on the enum.
    //      (Result is not an enum, so it has to be
    //      ruled out first. Then a switch could
    //      determine a particular constant.)
    //
    // `Conclude` could be modelled as a record implementing Control<T>, but
    // it would be a bit limiting and asymmetric with respect to other
    // controls.
    //
    // For those and other reasons a simpler approach was chosen.

    private static final Result<?> SKIP = new Result.Skip<>();
    private static final Result<?> CONTINUE = new Result.Continue<>();

    // only DocFinder should instantiate subtypes of Result<T>
    public sealed interface Result<T> {

        final class Skip<T> implements Result<T> {
            private Skip() { }
        }

        final class Continue<T> implements Result<T> {
            private Continue() { }
        }

        final class Conclude<T> implements Result<T> {

            private final T value;

            private Conclude(T value) {
                this.value = Objects.requireNonNull(value);
            }

            public T value() {
                return value;
            }

            @Override
            public Optional<T> toOptional() {
                return Optional.of(value);
            }
        }

        @SuppressWarnings("unchecked")
        static <T> Result<T> SKIP() {
            return (Result<T>) SKIP;
        }

        @SuppressWarnings("unchecked")
        static <T> Result<T> CONTINUE() {
            return (Result<T>) CONTINUE;
        }

        static <T> Result<T> CONCLUDE(T value) {
            return new Conclude<>(value);
        }

        // A pair of convenience methods for the most typical scenario

        default Optional<T> toOptional() {
            return Optional.empty();
        }

        static <T> Result<T> fromOptional(Optional<T> optional) {
            return optional.map(Result::CONCLUDE).orElseGet(Result::CONTINUE);
        }
    }
}
