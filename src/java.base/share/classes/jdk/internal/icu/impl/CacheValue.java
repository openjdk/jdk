// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import jdk.internal.icu.util.ICUException;

/**
 * Value type for cache items:
 * Holds a value either via a direct reference or via a {@link Reference},
 * depending on the current "strength" when {@code getInstance()} was called.
 *
 * <p>The value is <i>conceptually<i> immutable.
 * If it is held via a direct reference, then it is actually immutable.
 *
 * <p>A {@code Reference} may be cleared (garbage-collected),
 * after which {@code get()} returns null.
 * It can then be reset via {@code resetIfAbsent()}.
 * The new value should be the same as, or equivalent to, the old value.
 *
 * <p>Null values are supported. They can be distinguished from cleared values
 * via {@code isNull()}.
 *
 * @param <V> Cache instance value type
 */
public abstract class CacheValue<V> {
    /**
     * "Strength" of holding a value in CacheValue instances.
     * The default strength is {@code SOFT}.
     */
    public enum Strength {
        /**
         * Subsequent {@code getInstance()}-created objects
         * will hold direct references to their values.
         */
        STRONG,
        /**
         * Subsequent {@code getInstance()}-created objects
         * will hold {@link SoftReference}s to their values.
         */
        SOFT
    };
    private static volatile Strength strength = Strength.SOFT;

    @SuppressWarnings("rawtypes")
    private static final CacheValue NULL_VALUE = new NullValue();

    /**
     * Changes the "strength" of value references for subsequent {@code getInstance()} calls.
     */
    public static void setStrength(Strength strength) { CacheValue.strength = strength; }

    /**
     * Returns true if the "strength" is set to {@code STRONG}.
     */
    public static boolean futureInstancesWillBeStrong() { return strength == Strength.STRONG; }

    /**
     * Returns a CacheValue instance that holds the value.
     * It holds it directly if the value is null or if the current "strength" is {@code STRONG}.
     * Otherwise, it holds it via a {@link Reference}.
     */
    @SuppressWarnings("unchecked")
    public static <V> CacheValue<V> getInstance(V value) {
        if (value == null) {
            return NULL_VALUE;
        }
        return strength == Strength.STRONG ? new StrongValue<V>(value) : new SoftValue<V>(value);
    }

    /**
     * Distinguishes a null value from a Reference value that has been cleared.
     *
     * @return true if this object represents a null value.
     */
    public boolean isNull() { return false; }
    /**
     * Returns the value (which can be null),
     * or null if it was held in a Reference and has been cleared.
     */
    public abstract V get();
    /**
     * If the value was held via a {@link Reference} which has been cleared,
     * then it is replaced with a new {@link Reference} to the new value,
     * and the new value is returned.
     * The old and new values should be the same or equivalent.
     *
     * <p>Otherwise the old value is returned.
     *
     * @param value Replacement value, for when the current {@link Reference} has been cleared.
     * @return The old or new value.
     */
    public abstract V resetIfCleared(V value);

    private static final class NullValue<V> extends CacheValue<V> {
        @Override
        public boolean isNull() { return true; }
        @Override
        public V get() { return null; }
        @Override
        public V resetIfCleared(V value) {
            if (value != null) {
                throw new ICUException("resetting a null value to a non-null value");
            }
            return null;
        }
    }

    private static final class StrongValue<V> extends CacheValue<V> {
        private V value;

        StrongValue(V value) { this.value = value; }
        @Override
        public V get() { return value; }
        @Override
        public V resetIfCleared(V value) {
            // value and this.value should be equivalent, but
            // we do not require equals() to be implemented appropriately.
            return this.value;
        }
    }

    private static final class SoftValue<V> extends CacheValue<V> {
        private volatile Reference<V> ref;  // volatile for unsynchronized get()

        SoftValue(V value) { ref = new SoftReference<V>(value); }
        @Override
        public V get() { return ref.get(); }
        @Override
        public synchronized V resetIfCleared(V value) {
            V oldValue = ref.get();
            if (oldValue == null) {
                ref = new SoftReference<V>(value);
                return value;
            } else {
                // value and oldValue should be equivalent, but
                // we do not require equals() to be implemented appropriately.
                return oldValue;
            }
        }
    }
}
