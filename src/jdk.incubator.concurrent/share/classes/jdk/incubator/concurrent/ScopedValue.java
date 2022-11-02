/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Red Hat Inc.
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

package jdk.incubator.concurrent;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.lang.ref.Reference;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaUtilConcurrentTLRAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.ScopedValueContainer;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.ReservedStackAccess;
import jdk.internal.vm.annotation.Stable;
import sun.security.action.GetPropertyAction;

/**
 * Represents a value that is local to a <em>scope</em>. It is a per-thread value
 * that allows context to be set in a caller and read by callees. The <em>scope</em> is
 * the set of methods that the caller directly invokes, and any methods invoked
 * transitively. Scoped values also provide a way to share immutable data across
 * threads.
 *
 * <p> A scoped value is bound, meaning it gets a value, when invoking an
 * operation with the {@link Carrier#run(Runnable) Carrier.run} or {@link
 * Carrier#call(Callable) Carrier.call} methods. {@link Carrier Carrier} instances are
 * created by the static method {@link #where(ScopedValue, Object)}. The operations
 * executed by the {@code run} and {@code call} methods use the {@link #get()} method to
 * read the value of a bound `ScopedValue`. A scoped value reverts to being
 * unbound (or its previous value) when the operation completes.
 *
 * <p> An {@code ScopedValue} object will typically be declared in a {@code private
 * static final} field so that it can only be accessed by code in that class (or other
 * classes within its nest).
 *
 * <p> {@link ScopedValue} bindings are immutable: there is no "{@code set}" method.
 * There may be cases when an operation might need to use the same scoped value
 * to communicate a different value to the methods that it calls. The requirement is not
 * to change the original binding but to establish a new binding for nested calls. If an
 * scoped value already has a value, then {@code run} or {@code call} methods may be
 * invoked to run another operation with a newly-bound value. Code executed by the
 * operation will read the new value of the scoped value. The scoped value reverts to its
 * previous value when the operation completes.
 *
 * <h2> Sharing scoped values across threads </h2>
 *
 * Scoped-value bindings can be shared across threads when used in conjunction with
 * {@link StructuredTaskScope}. Creating a {@code StructuredTaskScope} captures the
 * current thread's scoped-value bindings for inheritance by threads {@link
 * StructuredTaskScope#fork(Callable) forked} in the task scope. This means that a thread
 * may bind a scoped value and share its value in a structured concurrency
 * context. Threads forked in the task scope that read the scoped value will read
 * the value bound by the thread that created the task scope.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be thrown.
 *
 * @apiNote
 * The following example uses an scoped value to make credentials available to callees.
 *
 * {@snippet lang=java :
 *   // @link substring="newInstance" target="ScopedValue#newInstance" :
 *   private static final ScopedValue<Credentials> CREDENTIALS = ScopedValue.newInstance();
 *
 *   Credentials creds = ...
 *   ScopedValue.where(CREDENTIALS, creds).run(() -> {
 *       ...
 *       Connection connection = connectDatabase();
 *       ...
 *   });
 *
 *   ...
 *
 *   Connection connectDatabase() {
 *       // @link substring="get" target="ScopedValue#get" :
 *       Credentials credentials = CREDENTIALS.get();
 *       ...
 *   }
 * }
 *
 * @implNote
 * scoped values are designed to be used in fairly small
 * numbers. {@link #get} initially performs a search through enclosing
 * scopes to find a scoped value's innermost binding. It
 * then caches the result of the search in a small thread-local
 * cache. Subsequent invocations of {@link #get} for that scoped value
 * will almost always be very fast. However, if a program has many
 * scoped values that it uses cyclically, the cache hit rate
 * will be low and performance will be poor. This design allows
 * scoped-value inheritance by {@link StructuredTaskScope} threads to
 * be very fast: in essence, no more than copying a pointer, and
 * leaving an scoped-value binding also requires little more than
 * updating a pointer.
 *
 * <p>Because the scoped-value per-thread cache is small, you should
 * try to minimize the number of bound scoped values in
 * use. For example, if you need to pass a number of values in this
 * way, it makes sense to create a record class to hold those values,
 * and then bind a single `ScopedValue` to an instance of that
 * record.
 *
 * <p>For this incubator release, we have provided some system properties
 * to tune the performance of scoped values.
 *
 * <p>The system property {@code jdk.incubator.concurrent.ScopedValue.cacheSize}
 * controls the size of the (per-thread) scoped-value cache. This cache is crucial
 * for the performance of scoped values. If it is too small,
 * the runtime library will repeatedly need to scan for each
 * {@link #get}. If it is too large, memory will be unnecessarily
 * consumed. The default scoped-value cache size is 16 entries. It may
 * be varied from 2 to 16 entries in size. {@code ScopedValue.cacheSize}
 * must be an integer power of 2.
 *
 * <p>For example, you could use {@code -Djdk.incubator.concurrent.ScopedValue.cacheSize=8}.
 *
 * <p>The other system property is {@code jdk.preserveScopedValueCache}.
 * This property determines whether the per-thread scoped-value
 * cache is preserved when a virtual thread is blocked. By default
 * this property is set to {@code true}, meaning that every virtual
 * thread preserves its scoped-value cache when blocked. Like {@code
 * ScopedValue.cacheSize}, this is a space versus speed trade-off: if
 * you have a great many virtual threads that are blocked most of the
 * time, setting this property to {@code false} might result in a
 * useful memory saving, but each virtual thread's scoped-value cache
 * would have to be regenerated after a blocking operation.
 *
 * @param <T> the scoped value's type
 * @since 20
 */
public final class ScopedValue<T> {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private final @Stable int hash;

    @Override
    public int hashCode() { return hash; }

    /**
     * An immutable map from {@code ScopedValue} to values.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument to a constructor
     * or method in this class will cause a {@link NullPointerException} to be thrown.
     */
    static sealed class Snapshot permits EmptySnapshot {
        final Snapshot prev;
        final Carrier bindings;
        final int bitmask;

        private static final Object NIL = new Object();

        Snapshot(Carrier bindings, Snapshot prev) {
            this.prev = prev;
            this.bindings = bindings;
            this.bitmask = bindings.bitmask | prev.bitmask;
        }

        protected Snapshot() {
            this.prev = null;
            this.bindings = null;
            this.bitmask = 0;
        }

        Object find(ScopedValue<?> key) {
            int bits = key.bitmask();
            for (Snapshot snapshot = this;
                 containsAll(snapshot.bitmask, bits);
                 snapshot = snapshot.prev) {
                for (Carrier carrier = snapshot.bindings;
                     carrier != null && containsAll(carrier.bitmask, bits);
                     carrier = carrier.prev) {
                    if (carrier.getKey() == key) {
                        Object value = carrier.get();
                        return value;
                    }
                }
            }
            return NIL;
        }
    }

    static final class EmptySnapshot extends Snapshot {

        private EmptySnapshot() {
            super();
        }

        private static final Snapshot SINGLETON = new EmptySnapshot();

        static final Snapshot getInstance() {
            return SINGLETON;
        }
    }

    /**
     * An immutable map of scoped values to values.
     * It define the {@link #run(Runnable) run} and {@link #call(Callable) call} methods
     * to invoke an operation with the scoped value mappings bound to the thread
     * that invokes {@code run} or {@code call}.
     *
     * @since 20
     */
    public static final class Carrier {
        // Bit masks: a 1 in postion n indicates that this set of bound values
        // hits that slot in the cache.
        final int bitmask;
        final ScopedValue<?> key;
        final Object value;
        final Carrier prev;

        Carrier(ScopedValue<?> key, Object value, Carrier prev) {
            this.key = key;
            this.value = value;
            this.prev = prev;
            int bits = key.bitmask();
            if (prev != null) {
                bits |= prev.bitmask;
            }
            this.bitmask = bits;
        }

        /**
         * Add a binding to this map, returning a new Carrier instance.
         */
        private static final <T> Carrier where(ScopedValue<T> key, T value,
                                               Carrier prev) {
            return new Carrier(key, value, prev);
        }

        /**
         * Returns a new {@link Carrier Carrier}, which consists of the contents of this
         * carrier plus a new mapping from {@code key} to {@code value}. If this carrier
         * already has a mapping for the scoped value {@code key} then the new
         * value added by this method overrides the previous mapping. That is to say, if
         * there is a list of {@code where(...)} clauses, the rightmost clause wins.
         * @param key   the ScopedValue to bind a value to
         * @param value the new value, can be {@code null}
         * @param <T>   the type of the ScopedValue
         * @return a new carrier, consisting of {@code this} plus a new binding
         * ({@code this} is unchanged)
         */
        public <T> Carrier where(ScopedValue<T> key, T value) {
            return where(key, value, this);
        }

        /*
         * Return a new set consisting of a single binding.
         */
        static <T> Carrier of(ScopedValue<T> key, T value) {
            return where(key, value, null);
        }

        final Object get() {
            return value;
        }

        final ScopedValue<?> getKey() {
            return key;
        }

        /**
         * Returns the value bound to a {@link ScopedValue} in this map of scoped values.
         * @param key the {@link ScopedValue} instance
         * @param <T> the type of the ScopedValue
         * @return the value
         * @throws NoSuchElementException if key is not bound to any value
         */
        @SuppressWarnings("unchecked")
        public <T> T get(ScopedValue<T> key) {
            var bits = key.bitmask();
            for (Carrier carrier = this;
                 carrier != null && containsAll(carrier.bitmask, bits);
                 carrier = carrier.prev) {
                if (carrier.getKey() == key) {
                    Object value = carrier.get();
                    return (T)value;
                }
            }
            throw new NoSuchElementException();
        }

        /**
         * Runs a value-returning operation with this map of scoped-value
         * bindings. Code invoked by {@code op} can use the {@link ScopedValue#get() get}
         * method to get the value of the scoped value. The scoped values
         * revert to their previously-bound values or become {@linkplain #isBound() unbound} when
         * the operation completes.
         *
         * <p> Scoped values are intended to be used in a <em>structured
         * manner</em>. If {@code op} creates any {@link StructuredTaskScope}s but does
         * not close them, then exiting {@code op} causes the underlying construct of each
         * {@link StructuredTaskScope} to be closed (in the reverse order that they were
         * created in), and {@link StructureViolationException} to be thrown.
         *
         * @param op    the operation to run
         * @param <R>   the type of the result of the function
         * @return the result
         * @throws Exception if {@code op} completes with an exception
         */
        public <R> R call(Callable<R> op) throws Exception {
            Objects.requireNonNull(op);
            Cache.invalidate(bitmask);
            var prevSnapshot = scopedValueBindings();
            var newSnapshot = new Snapshot(this, prevSnapshot);
            R result;
            try {
                JLA.setScopedValueBindings(newSnapshot);
                JLA.ensureMaterializedForStackWalk(newSnapshot);
                result = ScopedValueContainer.call(op);
            } finally {
                Reference.reachabilityFence(newSnapshot);
                JLA.setScopedValueBindings(prevSnapshot);
                Cache.invalidate(bitmask);
            }
            return result;
        }

        /**
         * Runs an operation with this map of scoped-value bindings. Code executed
         * by the operation can use the {@link ScopedValue#get() get()} method to get the
         * value of the scoped value. The scoped values revert to their previous
         * values or become {@linkplain #isBound() unbound} when the operation completes.
         *
         * <p> Scoped values are intended to be used in a <em>structured
         * manner</em>. If {@code op} creates any {@link StructuredTaskScope}s but does
         * not close them, then exiting {@code op} causes the underlying construct of each
         * {@link StructuredTaskScope} to be closed (in the reverse order that they were
         * created in), and {@link StructureViolationException} to be thrown.
         *
         * @param op    the operation to run
         */
        public void run(Runnable op) {
            Objects.requireNonNull(op);
            Cache.invalidate(bitmask);
            Snapshot newSnapshot = null;
            JLA.ensureMaterializedForStackWalk(newSnapshot);
            var prevSnapshot = scopedValueBindings();
            newSnapshot = new Snapshot(this, prevSnapshot);
            try {
                JLA.setScopedValueBindings(newSnapshot);
                JLA.ensureMaterializedForStackWalk(newSnapshot);
                ScopedValueContainer.run(op);
            } finally {
                Reference.reachabilityFence(newSnapshot);
                JLA.setScopedValueBindings(prevSnapshot);
                Cache.invalidate(bitmask);
            }
        }
    }

    /**
     * Creates a binding for a scoped value.
     * The {@link Carrier Carrier} may be used later to invoke a {@link Callable} or
     * {@link Runnable} instance. More bindings may be added to the {@link Carrier Carrier}
     * by further calls to this method.
     *
     * @param key the ScopedValue to bind
     * @param value the value to bind it to, can be {@code null}
     * @param <T> the type of the ScopedValue
     * @return A Carrier instance that contains one binding, that of key and value
     */
    public static <T> Carrier where(ScopedValue<T> key, T value) {
        return Carrier.of(key, value);
    }

    /**
     * Creates a binding for a scoped value and runs a
     * value-returning operation with that {@link ScopedValue} bound to the value.
     * @param key the ScopedValue to bind
     * @param value the value to bind it to, can be {@code null}
     * @param <T> the type of the ScopedValue
     * @param <U> the type of the Result
     * @param op the operation to call
     * @return the result
     * @throws Exception if the operation completes with an exception
     */
    public static <T, U> U where(ScopedValue<T> key, T value, Callable<U> op) throws Exception {
        return where(key, value).call(op);
    }

    /**
     * Creates a binding for a scoped value variable and runs an
     * operation with that  {@link ScopedValue} bound to the value.
     * @param key the {@link ScopedValue} to bind
     * @param value the value to bind it to, can be {@code null}
     * @param <T> the type of the {@link ScopedValue}
     * @param op the operation to run
     */
    public static <T> void where(ScopedValue<T> key, T value, Runnable op) {
        where(key, value).run(op);
    }

    private ScopedValue() {
        this.hash = generateKey();
    }

    /**
     * Creates a scoped value to refer to a value of type T.
     *
     * @param <T> the type of the scoped value.
     * @return a scoped value
     */
    public static <T> ScopedValue<T> newInstance() {
        return new ScopedValue<T>();
    }

    /**
     * Returns the current thread's bound value for this scoped-value variable.
     * @return the value of the scoped value
     * @throws NoSuchElementException if the scoped value is not bound
     */
    @ForceInline
    @SuppressWarnings("unchecked")
    public T get() {
        Object[] objects;
        if ((objects = scopedValueCache()) != null) {
            // This code should perhaps be in class Cache. We do it
            // here because the generated code is small and fast and
            // we really want it to be inlined in the caller.
            int n = (hash & Cache.SLOT_MASK) * 2;
            if (objects[n] == this) {
                return (T)objects[n + 1];
            }
            n = ((hash >>> Cache.INDEX_BITS) & Cache.SLOT_MASK) * 2;
            if (objects[n] == this) {
                return (T)objects[n + 1];
            }
        }
        return slowGet();
    }

    @SuppressWarnings("unchecked")
    private T slowGet() {
        var value = findBinding();
        if (value == Snapshot.NIL) {
            throw new NoSuchElementException();
        }
        Cache.put(this, value);
        return (T)value;
    }

    /**
     * {@return {@code true} if the scoped value is bound to a value}
     */
    public boolean isBound() {
        // ??? Do we want to search cache for this? In most cases we don't expect
        // this {@link ScopedValue} to be bound, so it's not worth it. But I may
        // be wrong about that.
/*
        if (Cache.find(this) != Snapshot.NIL) {
            return true;
        }
 */
        return findBinding() != Snapshot.NIL;
    }

    /**
     * Return the value of the scoped value or NIL if not bound.
     */
    private Object findBinding() {
        Object value = scopedValueBindings().find(this);
        return value;
    }

    /**
     * Returns the value of the scoped value if bound, otherwise returns {@code other}.
     * @param other the value to return if not bound, can be {@code null}
     * @return the value of the scoped value if bound, otherwise {@code other}
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
     * Returns the value of the scoped value if bound, otherwise throws the exception
     * produced by the exception supplying function.
     * @param <X> Type of the exception to be thrown
     * @param exceptionSupplier the supplying function that produces the exception to throw
     * @return the value of the scoped value if bound
     * @throws X produced by the exception suppyling function if the scoped value is unbound
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

    private static Object[] scopedValueCache() {
        return JLA.scopedValueCache();
    }

    private static void setScopedValueCache(Object[] cache) {
        JLA.setScopedValueCache(cache);
    }

    private static Snapshot scopedValueBindings() {
        // Bindings can be in one of four states:
        //
        // 1: class Thread: this is a new Thread instance, and no
        // scoped values have ever been bound in this Thread.
        // 2: EmptySnapshot.SINGLETON: This is effectively an empty binding.
        // 3: A Snapshot instance: this contains one or more scoped value
        // bindings.
        // 4: null: there may be some bindings in this Thread, but we don't know
        // where they are. We must invoke JLA.findScopedValueBindings() to walk
        // the stack to find them.

        Object bindings = JLA.scopedValueBindings();
        if (bindings == Thread.class) {
            // This must be a new thread
           return EmptySnapshot.getInstance();
        }
        if (bindings == null) {
            // Search the stack
            bindings = JLA.findScopedValueBindings();
            if (bindings == null) {
                // Nothing on the stack.
                return EmptySnapshot.getInstance();
            }
        }
        assert (bindings != null);
        return (Snapshot) bindings;
    }

    private static int nextKey = 0xf0f0_f0f0;

    // A Marsaglia xor-shift generator used to generate hashes. This one has full period, so
    // it generates 2**32 - 1 hashes before it repeats. We're going to use the lowest n bits
    // and the next n bits as cache indexes, so we make sure that those indexes map
    // to different slots in the cache.
    private static synchronized int generateKey() {
        int x = nextKey;
        do {
            x ^= x >>> 12;
            x ^= x << 9;
            x ^= x >>> 23;
        } while (Cache.primarySlot(x) == Cache.secondarySlot(x));
        return (nextKey = x);
    }

    /**
     * Return a bit mask that may be used to determine if this ScopedValue is
     * bound in the current context. Each Carrier holds a bit mask which is
     * the OR of all the bit masks of the bound ScopedValues.
     * @return the bitmask
     */
    int bitmask() {
        return (1 << Cache.primaryIndex(this)) | (1 << (Cache.secondaryIndex(this) + Cache.TABLE_SIZE));
    }

    // Return true iff bitmask, considered as a set of bits, contains all
    // of the bits in targetBits.
    static boolean containsAll(int bitmask, int targetBits) {
        return (bitmask & targetBits) == targetBits;
    }

    // A small fixed-size key-value cache. When an scoped value's get() method
    // is invoked, we record the result of the lookup in this per-thread cache
    // for fast access in future.
    private static class Cache {
        static final int INDEX_BITS = 4;  // Must be a power of 2
        static final int TABLE_SIZE = 1 << INDEX_BITS;
        static final int TABLE_MASK = TABLE_SIZE - 1;
        static final int PRIMARY_MASK = (1 << TABLE_SIZE) - 1;

        // The number of elements in the cache array, and a bit mask used to
        // select elements from it.
        private static final int CACHE_TABLE_SIZE, SLOT_MASK;
        // The largest cache we allow. Must be a power of 2 and greater than
        // or equal to 2.
        private static final int MAX_CACHE_SIZE = 16;

        static {
            final String propertyName = "jdk.incubator.concurrent.ScopedValue.cacheSize";
            var sizeString = GetPropertyAction.privilegedGetProperty(propertyName, "16");
            var cacheSize = Integer.valueOf(sizeString);
            if (cacheSize < 2 || cacheSize > MAX_CACHE_SIZE) {
                cacheSize = MAX_CACHE_SIZE;
                System.err.println(propertyName + " is out of range: is " + sizeString);
            }
            if ((cacheSize & (cacheSize - 1)) != 0) {  // a power of 2
                cacheSize = MAX_CACHE_SIZE;
                System.err.println(propertyName + " must be an integer power of 2: is " + sizeString);
            }
            CACHE_TABLE_SIZE = cacheSize;
            SLOT_MASK = cacheSize - 1;
        }

        static final int primaryIndex(ScopedValue<?> key) {
            return key.hash & TABLE_MASK;
        }

        static final int secondaryIndex(ScopedValue<?> key) {
            return (key.hash >> INDEX_BITS) & TABLE_MASK;
        }

        private static final int primarySlot(ScopedValue<?> key) {
            return key.hashCode() & SLOT_MASK;
        }

        private static final int secondarySlot(ScopedValue<?> key) {
            return (key.hash >> INDEX_BITS) & SLOT_MASK;
        }

        static final int primarySlot(int hash) {
            return hash & SLOT_MASK;
        }

        static final int secondarySlot(int hash) {
            return (hash >> INDEX_BITS) & SLOT_MASK;
        }

        static void put(ScopedValue<?> key, Object value) {
            Object[] theCache = scopedValueCache();
            if (theCache == null) {
                theCache = new Object[CACHE_TABLE_SIZE * 2];
                setScopedValueCache(theCache);
            }
            // Update the cache to replace one entry with the value we just looked up.
            // Each value can be in one of two possible places in the cache.
            // Pick a victim at (pseudo-)random.
            int k1 = primarySlot(key);
            int k2 = secondarySlot(key);
            var usePrimaryIndex = chooseVictim();
            int victim = usePrimaryIndex ? k1 : k2;
            int other = usePrimaryIndex ? k2 : k1;
            setKeyAndObjectAt(victim, key, value);
            if (getKey(theCache, other) == key) {
                setKeyAndObjectAt(other, key, value);
            }
        }

        private static void setKeyAndObjectAt(int n, Object key, Object value) {
            var cache = scopedValueCache();
            cache[n * 2] = key;
            cache[n * 2 + 1] = value;
        }

        private static void setKeyAndObjectAt(Object[] cache, int n, Object key, Object value) {
            cache[n * 2] = key;
            cache[n * 2 + 1] = value;
        }

        private static Object getKey(Object[] objs, int n) {
            return objs[n * 2];
        }

        private static void setKey(Object[] objs, int n, Object key) {
            objs[n * 2] = key;
        }

        private static final JavaUtilConcurrentTLRAccess THREAD_LOCAL_RANDOM_ACCESS
                = SharedSecrets.getJavaUtilConcurrentTLRAccess();

        // Return either true or false, at pseudo-random, with a bias towards true.
        // This chooses either the primary or secondary cache slot, but the
        // primary slot is approximately twice as likely to be chosen as the
        // secondary one.
        private static boolean chooseVictim() {
            int r = THREAD_LOCAL_RANDOM_ACCESS.nextSecondaryThreadLocalRandomSeed();
            return (r & 15) >= 5;
        }

        @ReservedStackAccess @DontInline
        public static void invalidate() {
            setScopedValueCache(null);
        }

        // Null a set of cache entries, indicated by the 1-bits given
        @ReservedStackAccess @DontInline
        static void invalidate(int toClearBits) {
            toClearBits = (toClearBits >>> TABLE_SIZE) | (toClearBits & PRIMARY_MASK);
            Object[] objects;
            if ((objects = scopedValueCache()) != null) {
                for (int bits = toClearBits; bits != 0; ) {
                    int index = Integer.numberOfTrailingZeros(bits);
                    setKeyAndObjectAt(objects, index & SLOT_MASK, null, null);
                    bits &= ~1 << index;
                }
            }
        }
    }
}
