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

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

public class DocFinder {

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
    }

    public <T> Optional<T> search(
            ExecutableElement method,
            Function<? super ExecutableElement, Optional<T>> criteria)
    {
        return search(method, true, criteria);
    }

    public <T> Optional<T> search(
            ExecutableElement method,
            boolean includeMethod,
            Function<? super ExecutableElement, Optional<T>> criteria)
    {
        try {
            return search0(method, includeMethod, false, criteria);
        } catch (NoOverriddenMethodsFound e) {
            // should never happen because throwExceptionIfNoOverriddenMethods == false
            throw new AssertionError(e);
        }
    }

    public <T> Optional<T> trySearch(
            ExecutableElement method,
            Function<? super ExecutableElement, Optional<T>> criteria)
            throws NoOverriddenMethodsFound
    {
        return search0(method, false, true, criteria);
    }

    private <T> Optional<T> search0(
            ExecutableElement method,
            boolean includeMethod,
            boolean throwExceptionIfNoOverriddenMethods,
            Function<? super ExecutableElement, Optional<T>> criteria)
            throws NoOverriddenMethodsFound
    {
        Iterator<ExecutableElement> iterator = new HierarchyTraversingIterator(method, includeMethod);
        if (throwExceptionIfNoOverriddenMethods && !iterator.hasNext()) {
            throw new NoOverriddenMethodsFound();
        }
        var spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED
                | Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.DISTINCT);
        return StreamSupport.stream(spliterator, false)
                .flatMap(m -> criteria.apply(m).stream()).findFirst();
    }

    private class HierarchyTraversingIterator implements Iterator<ExecutableElement> {

        final Deque<LazilyAccessedImplementedMethods> path = new LinkedList<>();
        ExecutableElement next;

        /*
         * Constructs an iterator over methods overridden by the given method.
         *
         * The iteration order is as defined in the Documentation Comment
         * Specification for the Standard Doclet. Whether the iteration
         * sequence includes the given method itself is controlled by
         * the includeMethod flag.
         */
        public HierarchyTraversingIterator(ExecutableElement method,
                                           boolean includeMethod) {
            assert method.getKind() == ElementKind.METHOD : method.getKind();
            next = method;
            if (!includeMethod) {
                updateNext();
            }
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
