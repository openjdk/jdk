/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import static jdk.internal.javac.PreviewFeature.Feature.SCOPE_LOCALS;

/**
 * Represents a scoped variable.
 *
 * <p> A scope-local variable differs from a normal variable in that it is dynamically
 * scoped and intended for cases where context needs to be passed from a caller
 * to a transitive callee without using an explicit parameter. A scope-local variable
 * does not have a default/initial value: it is bound, meaning it gets a value,
 * when executing an operation specified to {@link #where(ScopeLocal, Object)}.
 * Code executed by the operation
 * uses the {@link #get()} method to get the value of the variable. The variable reverts
 * to being unbound (or its previous value) when the operation completes.
 *
 * <p> Access to the value of a scoped variable is controlled by the accessibility
 * of the {@code ScopeLocal} object. A {@code ScopeLocal} object  will typically be declared
 * in a private static field so that it can only be accessed by code in that class
 * (or other classes within its nest).
 *
 * <p> ScopeLocal variables support nested bindings. If a scoped variable has a value
 * then the {@code runWithBinding} or {@code callWithBinding} can be invoked to run
 * another operation with a new value. Code executed by this methods "sees" the new
 * value of the variable. The variable reverts to its previous value when the
 * operation completes.
 *
 * <p> An <em>inheritable scoped variable</em> is created with the {@link
 * #inheritableForType(Class)} method and provides inheritance of values from
 * parent thread to child thread that is arranged when the child thread is
 * created. Unlike {@link InheritableThreadLocal}, inheritable scoped variable
 * are not copied into the child thread, instead the child thread will access
 * the same variable as the parent thread. The value of inheritable scoped
 * variables should be immutable to avoid needing synchronization to coordinate
 * access.
 *
 * <p> As an advanced feature, the {@link #snapshot()} method is defined to obtain
 * a {@link Snapshot} of the inheritable scoped variables that are currently bound.
 * This can be used to support cases where inheritance needs to be done at times
 * other than thread creation.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be thrown.
 *
 * @apiNote
 * The following example uses a scoped variable to make credentials available to callees.
 *
 * <pre>{@code
 *   private static final ScopeLocal<Credentials> CREDENTIALS = ScopeLocal.forType(Credentials.class);
 *
 *   Credentials creds = ...
 *   ScopeLocal.where(CREDENTIALS, creds).run(creds, () -> {
 *       :
 *       Connection connection = connectDatabase();
 *       :
 *   });
 *
 *   Connection connectDatabase() {
 *       Credentials credentials = CREDENTIALS.get();
 *       :
 *   }
 * }</pre>
 *
 * @param <T> the variable type
 * @since 99
 */
@jdk.internal.javac.PreviewFeature(feature=SCOPE_LOCALS)
public final class ScopeLocal<T> {
    private final @Stable Class<? super T> type;
    private final @Stable int hash;

    // Is this scope-local value inheritable? We could handle this by
    // making ScopeLocal an abstract base class and scopeLocalBindings() a
    // virtual method, but that seems a little excessive.
    private final @Stable boolean isInheritable;

    public final int hashCode() { return hash; }

    /**
     * An immutable map from {@code ScopeLocal} to values.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument to a constructor
     * or method in this class will cause a {@link NullPointerException} to be thrown.
     *
     * @since 99
     * @see ScopeLocal#snapshot()
     */

    public static class Snapshot {
        final Snapshot prev;
        final SingleBinding bindings;
        final short primaryBits;

        private static final Object NIL = new Object();

        Snapshot(SingleBinding bindings, Snapshot prev, short primaryBits) {
            this.prev = prev;
            this.bindings = bindings;
            this.primaryBits = primaryBits;
        }

        Object find(ScopeLocal<?> key) {
            for (Snapshot b = this; b != null; b = b.prev) {
                if (((1 << Cache.primaryIndex(key)) & b.primaryBits) != 0) {
                    for (SingleBinding binding = b.bindings;
                         binding != null;
                         binding = binding.prev) {
                        if (binding.getKey() == key) {
                            Object value = binding.get();
                            return value;
                        }
                    }
                }
            }
            return NIL;
        }
    }

    /**
     * An immutable map from a set of ScopeLocals to their bound values.
     * When map() or call() is invoked, the ScopeLocals bound in this set
     * are bound, such that calling the get() method returns the associated
     * value.
     */
    public static final class Carrier {
        // Bit masks: a 1 in postion n indicates that this set of bound values
        // hits that slot in the cache
        final short primaryBits, secondaryBits;
        final SingleBinding inheritables, nonInheritables;

       Carrier(SingleBinding inheritables, SingleBinding nonInheritables, short primaryBits, short secondaryBits) {
            this.inheritables = inheritables;
            this.nonInheritables = nonInheritables;
            this.primaryBits = primaryBits;
            this.secondaryBits = secondaryBits;
        }

        /**
         * Add a binding to this map, returning a new Carrier instance.
         */
        private static final <T> Carrier where(ScopeLocal<T> key, T value,
                                           SingleBinding inheritables, SingleBinding nonInheritables,
                                           short primaryBits, short secondaryBits) {
            if (key.isInheritable) {
                inheritables = new SingleBinding(key, value, inheritables);
            } else {
                nonInheritables = new SingleBinding(key, value, nonInheritables);
            }
            primaryBits |= (short)(1 << Cache.primaryIndex(key));
            secondaryBits |= (short)(1 << Cache.secondaryIndex(key));
            return new Carrier(inheritables, nonInheritables, primaryBits, secondaryBits);
        }

        /**
         * Return a new map, which consists of the contents of this map plus a
         * new binding of key and value.
         * @param key   The ScopeLocal to bind a value to
         * @param value The new value
         * @param <T>   The type of the ScopeLocal
         * @return TBD
         */
        public final <T> Carrier where(ScopeLocal<T> key, T value) {
            return where(key, value, inheritables, nonInheritables, primaryBits, secondaryBits);
        }

        /*
         * Return a new set consisting of a single binding.
         */
        static final <T> Carrier of(ScopeLocal<T> key, T value) {
            return where(key, value, null, null, (short)0, (short)0);
        }

        /**
         * Search for the value of a binding in this set
         * @param key the ScopeLocal to find
         * @param <T> the type of the ScopeLocal
         * @return the value
         */
        @SuppressWarnings("unchecked")
        public final <T> T get(ScopeLocal<T> key) {
            for (SingleBinding b = key.isInheritable ? inheritables : nonInheritables;
                 b != null; b = b.prev) {
                if (b.getKey() == key) {
                    Object value = b.get();
                    return (T)value;
                }
            }
            throw new NoSuchElementException();
        }

        /**
         * Runs a value-returning operation with this some ScopeLocals bound to values.
         * Code executed by the operation can use the {@link #get()} method to
         * get the value of the variables. The variables revert to their previous values or
         * becomes {@linkplain #isBound() unbound} when the operation completes.
         *
         * @param op    the operation to run
         * @param <R>   the type of the result of the function
         * @return the result
         * @throws Exception if the operation completes with an exception
         */
        public final <R> R call(Callable<R> op) throws Exception {
            Objects.requireNonNull(op);
            Cache.invalidate(primaryBits | secondaryBits);
            var inheritables = addScopeLocalBindings(this.inheritables, primaryBits,true);
            var nonInheritables = addScopeLocalBindings(this.nonInheritables, primaryBits,false);
            try {
                return op.call();
            } finally {
                Thread currentThread = Thread.currentThread();
                currentThread.noninheritableScopeLocalBindings = nonInheritables;
                currentThread.inheritableScopeLocalBindings = inheritables;
                Cache.invalidate(primaryBits | secondaryBits);
            }
        }

        /**
         * Runs a value-returning operation with this some ScopeLocals bound to values.
         * If the operation terminates with an exception {@code e}, apply {@code handler}
         * to {@code e} and return the result.
         *
         * @param op the operation to run
         * @param handler a function to be applied if the operation completes with an exception
         * @param <R> the type of the result of the function
         * @return the result
         */
        public final <R> R callOrElse(Callable<R> op,
                                      Function<? super Exception, ? extends R> handler) {
            try {
                return call(op);
            } catch (Exception e) {
                return handler.apply(e);
            }
        }

        /**
         * Runs an operation with some ScopeLocals bound to our values.
         * Code executed by the operation can use the {@link #get()} method to
         * get the value of the variables. The variables revert to their previous values or
         * becomes {@linkplain #isBound() unbound} when the operation completes.
         *
         * @param op    the operation to run
         */
        public final void run(Runnable op) {
            Objects.requireNonNull(op);
            Cache.invalidate(primaryBits | secondaryBits);
            var inheritables = addScopeLocalBindings(this.inheritables, primaryBits,true);
            var nonInheritables = addScopeLocalBindings(this.nonInheritables, primaryBits,false);
            try {
                op.run();
            } finally {
                Thread currentThread = Thread.currentThread();
                currentThread.noninheritableScopeLocalBindings = nonInheritables;
                currentThread.inheritableScopeLocalBindings = inheritables;
                Cache.invalidate(primaryBits | secondaryBits);
            }
        }

        /*
         * Add a list of bindings to the current Thread's set of bound values.
         */
        private final static Snapshot addScopeLocalBindings(SingleBinding bindings, short primaryBits, boolean isInheritable) {
            Snapshot prev = getScopeLocalBindings(isInheritable);
            if (bindings != null) {
                var b = new Snapshot(bindings, prev, primaryBits);
                ScopeLocal.setScopeLocalBindings(b, isInheritable);
            }
            return prev;
        }
    }

    /**
     * Creates a binding for a ScopeLocal instance.
     * That {@link Carrier} may be used later to invoke a {@link Callable} or
     * {@link Runnable} instance. More bindings may be added to the {@link Carrier}
     * by the {@link Carrier#where(ScopeLocal, Object)} method.
     *
     * @param key the ScopeLocal to bind
     * @param value The value to bind it to
     * @param <T> the type of the ScopeLocal
     * @return A Carrier instance that contains one binding, that of key and value
     */
    public static <T> Carrier where(ScopeLocal<T> key, T value) {
        // This could be made more efficient by not creating the Carrier instance.
        return Carrier.of(key, value);
    }

    /**
     * Creates a binding for a ScopeLocal instance and runs a value-returning
     * operation with that bound ScopeLocal.
     * @param key the ScopeLocal to bind
     * @param value The value to bind it to
     * @param <T> the type of the ScopeLocal
     * @param <U> the type of the Result
     * @param op the operation to call
     * @return the result
     * @throws Exception if the operation completes with an exception
     */
    public static <T, U> U where(ScopeLocal<T> key, T value, Callable<U> op) throws Exception {
        // This could be made more efficient by not creating the Carrier instance.
        return where(key, value).call(op);
    }

    /**
     * Creates a binding for a ScopeLocal instance and runs an
     * operation with that bound ScopeLocal.
     * @param key the ScopeLocal to bind
     * @param value The value to bind it to
     * @param <T> the type of the ScopeLocal
     * @param op the operation to run
     */
    public static <T> void where(ScopeLocal<T> key, T value, Runnable op) {
        where(key, value).run(op);
    }

    /**
     * Runs a value-returning operation with a snapshot of inheritable
     * scoped variables.
     *
     * @param op the operation to run
     * @param s the Snapshot. May be null.
     * @param <R> the type of the result of the function
     * @return the result
     * @throws Exception if the operation completes with an exception
     */
    public static <R> R callWithSnapshot(Callable<R> op, Snapshot s) throws Exception {
        var prev = Thread.currentThread().inheritableScopeLocalBindings;
        if (prev == s) {
            return op.call();
        }
        var cache = Thread.scopeLocalCache();
        Cache.invalidate();
        try {
            Thread.currentThread().inheritableScopeLocalBindings = s;
            return op.call();
        } finally {
            Thread.currentThread().inheritableScopeLocalBindings = prev;
            Thread.setScopeLocalCache(cache);
        }
    }

    /**
     * Runs an operation with this snapshot of inheritable scoped variables.
     *
     * @param op the operation to run
     * @param s the Snapshot. May be null.
     */
    public static void runWithSnapshot(Runnable op, Snapshot s) {
        var prev = Thread.currentThread().inheritableScopeLocalBindings;
        if (prev == s) {
            op.run();
            return;
        }
        var cache = Thread.scopeLocalCache();
        Cache.invalidate();
        try {
            Thread.currentThread().inheritableScopeLocalBindings = s;
            op.run();
        } finally {
            Thread.currentThread().inheritableScopeLocalBindings = prev;
            Thread.setScopeLocalCache(cache);
        }
    }

    private ScopeLocal(Class<? super T> type, boolean isInheritable) {
        this.type = Objects.requireNonNull(type);
        this.isInheritable = isInheritable;
        this.hash = generateKey();
    }

    /**
     * Creates a scoped variable to hold a value with the given type.
     *
     * @param <T> the type of the scoped variable's value.
     * @param <U> a supertype of {@code T}. It should either be {@code T} itself or, if T is a parameterized type, its generic type.
     * @param type The {@code Class} instance {@code T.class}
     * @return a scope variable
     */
    public static <U,T extends U> ScopeLocal<T> forType(Class<U> type) {
        return new ScopeLocal<T>(type, false);
    }

    /**
     * Creates an inheritable scoped variable to hold a value with the given type.
     *
     * @param <T> the type of the scoped variable's value.
     * @param <U> a supertype of {@code T}. It should either be {@code T} itself or, if T is a parameterized type, its generic type.
     * @param type The {@code Class} instance {@code T.class}
     * @return a scope variable
     */
    public static <U,T extends U> ScopeLocal<T> inheritableForType(Class<U> type) {
        return new ScopeLocal<T>(type, true);
    }

    /**
     * Returns the value of the variable.
     * @return the value of the variable
     * @throws NoSuchElementException if the variable is not bound (exception is TBD)
     */
    @ForceInline
    @SuppressWarnings("unchecked")
    public T get() {
        Object[] objects;
        if ((objects = Thread.scopeLocalCache()) != null) {
            // This code should perhaps be in class Cache. We do it
            // here because the generated code is small and fast and
            // we really want it to be inlined in the caller.
            int n = (hash & Cache.TABLE_MASK) * 2;
            if (objects[n] == this) {
                return (T)objects[n + 1];
            }
            n = ((hash >>> Cache.INDEX_BITS) & Cache.TABLE_MASK) * 2;
            if (objects[n] == this) {
                return (T)objects[n + 1];
            }
        }
        return slowGet();
    }

    @SuppressWarnings("unchecked")
    private T slowGet() {
        var bindings = scopeLocalBindings();
        if (bindings == null)
            throw new NoSuchElementException();
        var value =  bindings.find(this);
        if (value == Snapshot.NIL) {
            throw new NoSuchElementException();
        }
        Cache.put(this, value);
        return (T)value;
    }

    /**
     * Returns {@code true} if the variable is bound to a value.
     *
     * @return {@code true} if the variable is bound to a value, otherwise {@code false}
     */
    @SuppressWarnings("unchecked")
    public boolean isBound() {
        var bindings = scopeLocalBindings();
        if (bindings == null) {
            return false;
        }
        return (bindings.find(this) != Snapshot.NIL);
    }

    /**
     * Return the value of the variable or NIL if not bound.
     */
    private Object findBinding() {
        var bindings = scopeLocalBindings();
        if (bindings != null) {
            return bindings.find(this);
        } else {
            return Snapshot.NIL;
        }
    }

    /**
     * Return the value of the variable if bound, otherwise returns {@code other}.
     * @param other the value to return if not bound, can be {@code null}
     * @return the value of the variable if bound, otherwise {@code other}
     */
    public T orElse(T other) {
        Object obj = findBinding();
        if (obj != Snapshot.NIL) {
            @SuppressWarnings("unchecked")
            T value = (T) obj;
            return value;
        } else {
            return other;
        }
    }

    /**
     * Return the value of the variable if bound, otherwise throws an exception
     * produced by the exception supplying function.
     * @param <X> Type of the exception to be thrown
     * @param exceptionSupplier the supplying function that produces an
     *        exception to be thrown
     * @return the value of the variable if bound
     * @throws X if the variable is unbound
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        Objects.requireNonNull(exceptionSupplier);
        Object obj = findBinding();
        if (obj != Snapshot.NIL) {
            @SuppressWarnings("unchecked")
            T value = (T) obj;
            return value;
        } else {
            throw exceptionSupplier.get();
        }
    }

    private static Snapshot getScopeLocalBindings(boolean isInheritable) {
        Thread currentThread = Thread.currentThread();
        return isInheritable
                ? currentThread.inheritableScopeLocalBindings
                : currentThread.noninheritableScopeLocalBindings;
    }

    private static void setScopeLocalBindings(Snapshot bindings, boolean isInheritable) {
        Thread currentThread = Thread.currentThread();
        if (isInheritable) {
            currentThread.inheritableScopeLocalBindings = bindings;
        } else {
            currentThread.noninheritableScopeLocalBindings = bindings;
        }
    }

    private Snapshot scopeLocalBindings() {
        return getScopeLocalBindings(isInheritable);
    }

    private static int nextKey = 0xf0f0_f0f0;

    // A Marsaglia xor-shift generator used to generate hashes. This one has full period, so
    // it generates 2**32 - 1 hashes before it repeats. We're going to use the lowest n bits
    // and the next n bits as cache indexes, so we make sure that those indexes are
    // different.
    private static synchronized int generateKey() {
        int x = nextKey;
        do {
            x ^= x >>> 12;
            x ^= x << 9;
            x ^= x >>> 23;
        } while ((x & Cache.TABLE_MASK)
                == ((x >>> Cache.INDEX_BITS) & Cache.TABLE_MASK));
        return (nextKey = x);
    }

    /**
     * Returns a "snapshot" of the inheritable scoped variables that are currently
     * bound.
     *
     * <p>This snapshot may be capured at any time. It is inteneded to be used
     * in circumstances where values may be shared by sub-tasks.
     *
     * @return a "snapshot" of the currently-bound inheritable scoped variables. May be null.
     */
    public static Snapshot snapshot() {
        return Thread.currentThread().inheritableScopeLocalBindings;
    }

    // An immutable object that represents the binding of a single value
    // to a single key.
    static final class SingleBinding {
        final ScopeLocal<?> key;
        final Object value;
        final SingleBinding prev;

        SingleBinding(ScopeLocal<?> key, Object value, SingleBinding prev) {
            this.value = key.type.cast(value);
            this.key = key;
            this.prev = prev;
        }

        final Object get() {
            return value;
        }

        final ScopeLocal<?> getKey() {
            return key;
        }
    }

    // A small fixed-size key-value cache. When a scope variable's get() method
    // is invoked, we record the result of the lookup in this per-thread cache
    // for fast access in future.
    private static class Cache {
        static final int INDEX_BITS = 4;  // Must be a power of 2
        static final int TABLE_SIZE = 1 << INDEX_BITS;
        static final int TABLE_MASK = TABLE_SIZE - 1;

        static final int primaryIndex(ScopeLocal<?> key) {
            return key.hash & TABLE_MASK;
        }

        static final int secondaryIndex(ScopeLocal<?> key) {
            return (key.hash >> INDEX_BITS) & TABLE_MASK;
        }

        static void put(ScopeLocal<?> key, Object value) {
            Object[] theCache = Thread.scopeLocalCache();
            if (theCache == null) {
                theCache = new Object[TABLE_SIZE * 2];
                Thread.setScopeLocalCache(theCache);
            }
            // Update the cache to replace one entry with the value we just looked up.
            // Each value can be in one of two possible places in the cache.
            // Pick a victim at (pseudo-)random.
            Thread thread = Thread.currentThread();
            int k1 = primaryIndex(key);
            int k2 = secondaryIndex(key);
            int tmp = chooseVictim(thread);
            int victim = tmp == 0 ? k1 : k2;
            int other = tmp == 0 ? k2 : k1;
            setKeyAndObjectAt(victim, key, value);
            if (getKey(theCache, other) == key) {
                setKey(theCache, other, null);
            }
        }

        private static final void update(Object key, Object value) {
            Object[] objects;
            if ((objects = Thread.scopeLocalCache()) != null) {
                int k1 = key.hashCode() & TABLE_MASK;
                if (getKey(objects, k1) == key) {
                    setKeyAndObjectAt(k1, key, value);
                }
                int k2 = (key.hashCode() >> INDEX_BITS) & TABLE_MASK;
                if (getKey(objects, k2) == key) {
                    setKeyAndObjectAt(k2, key, value);
                }
            }
        }

        private static final void remove(Object key) {
            Object[] objects;
            if ((objects = Thread.scopeLocalCache()) != null) {
                int k1 = key.hashCode() & TABLE_MASK;
                if (getKey(objects, k1) == key) {
                    setKeyAndObjectAt(k1, null, null);
                }
                int k2 = (key.hashCode() >> INDEX_BITS) & TABLE_MASK;
                if (getKey(objects, k2) == key) {
                    setKeyAndObjectAt(k2, null, null);
                }
            }
        }

        private static void setKeyAndObjectAt(int n, Object key, Object value) {
            Thread.scopeLocalCache()[n * 2] = key;
            Thread.scopeLocalCache()[n * 2 + 1] = value;
        }

        private static Object getKey(Object[] objs, int n) {
            return objs[n * 2];
        }

        private static void setKey(Object[] objs, int n, Object key) {
            objs[n * 2] = key;
        }

        // Return either 0 or 1, at pseudo-random. This chooses either the
        // primary or secondary cache slot.
        private static int chooseVictim(Thread thread) {
            int tmp = thread.victims;
            thread.victims = (tmp << 31) | (tmp >>> 1);
            return tmp & 1;
        }

        public static void invalidate() {
            Thread.setScopeLocalCache(null);
        }

        // Null a set of cache entries, indicated by the 1-bits given
        static void invalidate(int toClearBits) {
            assert(toClearBits == (short)toClearBits);
            Object[] objects;
            if ((objects = Thread.scopeLocalCache()) != null) {
                for (short bits = (short)toClearBits;
                     bits != 0; ) {
                    int index = Integer.numberOfTrailingZeros(bits);
                    setKey(objects, index, null);
                    bits &= ~1 << index;
                }
            }
        }
    }
}
