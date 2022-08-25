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

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

public class DocFinder {

    /*
     * A specialized function that accepts a method and either returns an
     * optional result or throws a possibly checked exception, which
     * terminates the search and transparently bubbles up the stack.
     *
     * If a method does not meet the criterion, returns an empty optional.
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
        Optional<T> apply(ExecutableElement method) throws X;
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

    public <T, X extends Throwable> Optional<T> search(ExecutableElement method,
                                                       Criterion<T, X> criterion)
            throws X
    {
        return search(method, true, criterion);
    }

    public <T, X extends Throwable> Optional<T> search(ExecutableElement method,
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

    public <T, X extends Throwable> Optional<T> trySearch(ExecutableElement method,
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
    private <T, X extends Throwable> Optional<T> search0(ExecutableElement method,
                                                         boolean includeMethodInSearch,
                                                         boolean throwExceptionIfDoesNotOverride,
                                                         Criterion<T, X> criterion)
            throws NoOverriddenMethodsFound, X
    {
        // if required, first check if the method overrides anything, so that
        // the result would not depend on whether the method itself is included
        // in the search
        Iterator<ExecutableElement> overriddenMethods = new OverriddenMethodsHierarchy(method);
        if (throwExceptionIfDoesNotOverride && !overriddenMethods.hasNext()) {
            throw new NoOverriddenMethodsFound();
        }
        if (includeMethodInSearch) {
            Optional<T> r = criterion.apply(method);
            if (r.isPresent())
                return r;
        }
        while (overriddenMethods.hasNext()) {
            ExecutableElement m = overriddenMethods.next();
            Optional<T> r = criterion.apply(m);
            if (r.isPresent())
                return r;
        }
        return Optional.empty();
    }

    /*
     * An iterator over methods overridden by some method.
     *
     * The iteration order is as defined in the Documentation Comment
     * Specification for the Standard Doclet.
     *
     * This iterator can be used to create a stream; for example:
     *
     *     var spliterator = Spliterators.spliteratorUnknownSize(iterator,
     *             Spliterator.ORDERED | Spliterator.NONNULL
     *                     | Spliterator.IMMUTABLE | Spliterator.DISTINCT);
     *     var stream = StreamSupport.stream(spliterator, false);
     */
    private class OverriddenMethodsHierarchy implements Iterator<ExecutableElement> {

        final Deque<LazilyAccessedImplementedMethods> path = new LinkedList<>();
        ExecutableElement next;

        public OverriddenMethodsHierarchy(ExecutableElement method) {
            assert method.getKind() == ElementKind.METHOD : method.getKind();
            next = method;
            updateNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public ExecutableElement next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            var r = next;
            updateNext();
            return r;
        }

        private void updateNext() {
            assert next != null;
            var superClassMethod = overriddenMethodLookup.apply(next);
            path.push(new LazilyAccessedImplementedMethods(next));
            if (superClassMethod != null) {
                next = superClassMethod;
                return;
            }
            while (!path.isEmpty()) {
                var superInterfaceMethods = path.peek();
                if (superInterfaceMethods.hasNext()) {
                    next = superInterfaceMethods.next();
                    return;
                } else {
                    path.pop();
                }
            }
            next = null; // end-of-hierarchy
        }

        class LazilyAccessedImplementedMethods implements Iterator<ExecutableElement> {

            final ExecutableElement method;
            Iterator<ExecutableElement> iterator;

            public LazilyAccessedImplementedMethods(ExecutableElement method) {
                this.method = method;
            }

            @Override
            public boolean hasNext() {
                return getIterator().hasNext();
            }

            @Override
            public ExecutableElement next() {
                return getIterator().next();
            }

            Iterator<ExecutableElement> getIterator() {
                if (iterator != null) {
                    return iterator;
                }
                return iterator = implementedMethodsLookup.apply(method, next).iterator();
            }
        }
    }
}
