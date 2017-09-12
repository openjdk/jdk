/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.http.internal.websocket;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.List.of;
import static java.util.Objects.requireNonNull;

/*
 * Auxiliary test infrastructure
 */
final class TestSupport {

    private TestSupport() { }

    static <A, B, R> Iterator<R> cartesianIterator(List<A> a,
                                                   List<B> b,
                                                   F2<A, B, R> f2) {
        @SuppressWarnings("unchecked")
        F<R> t = p -> f2.apply((A) p[0], (B) p[1]);
        return cartesianIterator(of(a, b), t);
    }

    static <A, B, C, R> Iterator<R> cartesianIterator(List<A> a,
                                                      List<B> b,
                                                      List<C> c,
                                                      F3<A, B, C, R> f3) {
        @SuppressWarnings("unchecked")
        F<R> t = p -> f3.apply((A) p[0], (B) p[1], (C) p[2]);
        return cartesianIterator(of(a, b, c), t);
    }

    static <A, B, C, D, R> Iterator<R> cartesianIterator(List<A> a,
                                                         List<B> b,
                                                         List<C> c,
                                                         List<D> d,
                                                         F4<A, B, C, D, R> f4) {
        @SuppressWarnings("unchecked")
        F<R> t = p -> f4.apply((A) p[0], (B) p[1], (C) p[2], (D) p[3]);
        return cartesianIterator(of(a, b, c, d), t);
    }

    static <A, B, C, D, E, R> Iterator<R> cartesianIterator(List<A> a,
                                                            List<B> b,
                                                            List<C> c,
                                                            List<D> d,
                                                            List<E> e,
                                                            F5<A, B, C, D, E, R> f5) {
        @SuppressWarnings("unchecked")
        F<R> t = p -> f5.apply((A) p[0], (B) p[1], (C) p[2], (D) p[3], (E) p[4]);
        return cartesianIterator(of(a, b, c, d, e), t);
    }

    static <R> Iterator<R> cartesianIterator(List<? extends List<?>> params,
                                             F<R> function) {
        if (params.isEmpty()) {
            return Collections.emptyIterator();
        }
        for (List<?> l : params) {
            if (l.isEmpty()) {
                return Collections.emptyIterator();
            }
        }
        // Assertion: if we are still here, there is at least a single element
        // in the product
        return new Iterator<>() {

            private final int arity = params.size();
            private final int[] coordinates = new int[arity];
            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public R next() {
                if (!hasNext) {
                    throw new NoSuchElementException();
                }
                Object[] array = new Object[arity];
                for (int i = 0; i < arity; i++) {
                    array[i] = params.get(i).get(coordinates[i]);
                }
                int p = arity - 1;
                while (p >= 0 && coordinates[p] == params.get(p).size() - 1) {
                    p--;
                }
                if (p < 0) {
                    hasNext = false;
                } else {
                    coordinates[p]++;
                    for (int i = p + 1; i < arity; i++) {
                        coordinates[i] = 0;
                    }
                }
                return function.apply(array);
            }
        };
    }

    @FunctionalInterface
    public interface F1<A, R> {
        R apply(A a);
    }

    @FunctionalInterface
    public interface F2<A, B, R> {
        R apply(A a, B b);
    }

    @FunctionalInterface
    public interface F3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    public interface F4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    public interface F5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    public interface F<R> {
        R apply(Object[] args);
    }

    static <T> Iterator<T> iteratorOf1(T element) {
        return List.of(element).iterator();
    }

    @SafeVarargs
    static <T> Iterator<T> iteratorOf(T... elements) {
        return List.of(elements).iterator();
    }

    static <T> Iterator<T> limit(int maxElements, Iterator<? extends T> elements) {
        return new Iterator<>() {

            int count = maxElements;

            @Override
            public boolean hasNext() {
                return count > 0 && elements.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                count--;
                return elements.next();
            }
        };
    }

//    static <T> Iterator<T> filter(Iterator<? extends T> source,
//                                  Predicate<? super T> predicate) {
//        return new Iterator<>() {
//
//            { findNext(); }
//
//            T next;
//            boolean hasNext;
//
//            @Override
//            public boolean hasNext() {
//                return hasNext;
//            }
//
//            @Override
//            public T next() {
//                if (!hasNext) {
//                    throw new NoSuchElementException();
//                }
//                T n = this.next;
//                findNext();
//                return n;
//            }
//
//            void findNext() {
//                while (source.hasNext()) {
//                    T n = source.next();
//                    if (predicate.test(n)) {
//                        hasNext = true;
//                        next = n;
//                        break;
//                    }
//                }
//            }
//        };
//    }

    static ByteBuffer fullCopy(ByteBuffer src) {
        ByteBuffer copy = ByteBuffer.allocate(src.capacity());
        int p = src.position();
        int l = src.limit();
        src.clear();
        copy.put(src).position(p).limit(l);
        src.position(p).limit(l);
        return copy;
    }

    static void forEachBufferPartition(ByteBuffer src,
                                       Consumer<? super Iterable<? extends ByteBuffer>> action) {
        forEachPartition(src.remaining(),
                (lengths) -> {
                    int end = src.position();
                    List<ByteBuffer> buffers = new LinkedList<>();
                    for (int len : lengths) {
                        ByteBuffer d = src.duplicate();
                        d.position(end);
                        d.limit(end + len);
                        end += len;
                        buffers.add(d);
                    }
                    action.accept(buffers);
                });
    }

    private static void forEachPartition(int n,
                                         Consumer<? super Iterable<Integer>> action) {
        forEachPartition(n, new Stack<>(), action);
    }

    private static void forEachPartition(int n,
                                         Stack<Integer> path,
                                         Consumer<? super Iterable<Integer>> action) {
        if (n == 0) {
            action.accept(path);
        } else {
            for (int i = 1; i <= n; i++) {
                path.push(i);
                forEachPartition(n - i, path, action);
                path.pop();
            }
        }
    }

    static void forEachPermutation(int n, Consumer<? super int[]> c) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        permutations(0, a, c);
    }

    private static void permutations(int i, int[] a, Consumer<? super int[]> c) {
        if (i == a.length) {
            c.accept(Arrays.copyOf(a, a.length));
            return;
        }
        for (int j = i; j < a.length; j++) {
            swap(a, i, j);
            permutations(i + 1, a, c);
            swap(a, i, j);
        }
    }

    private static void swap(int[] a, int i, int j) {
        int x = a[i];
        a[i] = a[j];
        a[j] = x;
    }

    static <T> Iterator<T> concat(Iterator<? extends Iterator<? extends T>> iterators) {
        requireNonNull(iterators);
        return new Iterator<>() {

            private Iterator<? extends T> current = Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                while (!current.hasNext()) {
                    if (!iterators.hasNext()) {
                        return false;
                    } else {
                        current = iterators.next();
                    }
                }
                return true;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
    }

    interface Mock {

        /*
         * Completes exceptionally if there are any expectations that haven't
         * been met within the given time period, otherwise completes normally
         */
        CompletableFuture<Void> expectations(long timeout, TimeUnit unit);
    }

    static final class InvocationChecker {

        private final Object lock = new Object();
        private final Iterator<InvocationExpectation> expectations;
        private final CompletableFuture<Void> expectationsViolation
                = new CompletableFuture<>();

        InvocationChecker(Iterable<InvocationExpectation> expectations) {
            this.expectations = requireNonNull(expectations).iterator();
        }

        /*
         * Completes exceptionally if there are any expectations that haven't
         * been met within the given time period, otherwise completes normally
         */
        CompletableFuture<Void> expectations(long timeout, TimeUnit unit) {
            return expectationsViolation
                    .orTimeout(timeout, unit)
                    .handle((v, t) -> {
                        if (t == null) {
                            throw new InternalError(
                                    "Unexpected normal completion: " + v);
                        } else if (t instanceof TimeoutException) {
                            synchronized (lock) {
                                if (!expectations.hasNext()) {
                                    return null;
                                } else {
                                    throw new AssertionFailedException(
                                            "More invocations were expected");
                                }
                            }
                        } else if (t instanceof AssertionFailedException) {
                            throw (AssertionFailedException) t;
                        } else {
                            throw new RuntimeException(t);
                        }
                    });
        }

        void checkInvocation(String name, Object... args) {
            synchronized (lock) {
                if (!expectations.hasNext()) {
                    expectationsViolation.completeExceptionally(
                            new AssertionFailedException(
                                    "Less invocations were expected: " + name));
                    return;
                }
                InvocationExpectation next = expectations.next();
                if (!next.name.equals(name)) {
                    expectationsViolation.completeExceptionally(
                            new AssertionFailedException(
                                    "A different invocation was expected: " + name)
                    );
                    return;
                }
                if (!next.predicate.apply(args)) {
                    expectationsViolation.completeExceptionally(
                            new AssertionFailedException(
                                    "Invocation doesn't match the predicate: "
                                            + name + ", " + Arrays.toString(args))
                    );
                }
            }
        }
    }

    static final class InvocationExpectation {

        final String name;
        final F<Boolean> predicate;

        InvocationExpectation(String name, F<Boolean> predicate) {
            this.name = requireNonNull(name);
            this.predicate = requireNonNull(predicate);
        }
    }

    static void checkExpectations(Mock... mocks) {
        checkExpectations(0, TimeUnit.SECONDS, mocks);
    }

    static void checkExpectations(long timeout, TimeUnit unit, Mock... mocks) {
        CompletableFuture<?>[] completableFutures = Stream.of(mocks)
                .map(m -> m.expectations(timeout, unit))
                .collect(Collectors.toList()).toArray(new CompletableFuture<?>[0]);
        CompletableFuture<Void> cf = CompletableFuture.allOf(completableFutures);
        try {
            cf.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AssertionFailedException) {
                throw (AssertionFailedException) cause;
            } else {
                throw e;
            }
        }
    }

    public static <T extends Throwable> T assertThrows(Class<? extends T> clazz,
                                                       ThrowingProcedure code) {
        @SuppressWarnings("unchecked")
        T t = (T) assertThrows(clazz::isInstance, code);
        return t;
    }

    /*
     * The rationale behind asking for a regex is to not pollute variable names
     * space in the scope of assertion: if it's something as simple as checking
     * a message, we can do it inside
     */
    @SuppressWarnings("unchecked")
    static <T extends Throwable> T assertThrows(Class<? extends T> clazz,
                                                String messageRegex,
                                                ThrowingProcedure code) {
        requireNonNull(messageRegex, "messagePattern");
        Predicate<Throwable> p = e -> clazz.isInstance(e)
                && Pattern.matches(messageRegex, e.getMessage());
        return (T) assertThrows(p, code);
    }

    static Throwable assertThrows(Predicate<? super Throwable> predicate,
                                  ThrowingProcedure code) {
        requireNonNull(predicate, "predicate");
        requireNonNull(code, "code");
        Throwable caught = null;
        try {
            code.run();
        } catch (Throwable t) {
            caught = t;
        }
        if (predicate.test(caught)) {
            return caught;
        }
        if (caught == null) {
            throw new AssertionFailedException("No exception was thrown");
        }
        throw new AssertionFailedException("Caught exception didn't match the predicate", caught);
    }

    /*
     * Blocking assertion, waits for completion
     */
    static Throwable assertCompletesExceptionally(Class<? extends Throwable> clazz,
                                                  CompletionStage<?> stage) {
        CompletableFuture<?> cf =
                CompletableFuture.completedFuture(null).thenCompose(x -> stage);
        return assertThrows(t -> clazz.isInstance(t.getCause()), cf::get);
    }

    interface ThrowingProcedure {
        void run() throws Throwable;
    }

    static final class Expectation {

        private final List<Predicate<? super Throwable>> list = new LinkedList<>();

        static Expectation ifExpect(boolean condition,
                                    Predicate<? super Throwable> predicate) {
            return addPredicate(new Expectation(), condition, predicate);
        }

        Expectation orExpect(boolean condition,
                             Predicate<? super Throwable> predicate) {
            return addPredicate(this, condition, predicate);
        }

        static Expectation addPredicate(Expectation e, boolean condition,
                                        Predicate<? super Throwable> predicate) {
            if (condition) {
                e.list.add(requireNonNull(predicate));
            }
            return e;
        }

        public Throwable assertThrows(ThrowingProcedure code) {
            Predicate<Throwable> p;
            if (list.isEmpty()) {
                p = Objects::isNull;
            } else {
                p = e -> list.stream().anyMatch(x -> x.test(e));
            }
            return TestSupport.assertThrows(p, code);
        }
    }

    static final class AssertionFailedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        AssertionFailedException(String message) {
            super(message);
        }

        AssertionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
