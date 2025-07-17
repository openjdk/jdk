/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import java.util.Map;
import java.util.Objects;

/**
 * An immutable container for a key and a value, both of which are nullable.
 *
 * <p>This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; programmers should treat instances that are
 * {@linkplain #equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may
 * occur. For example, in a future release, synchronization may fail.
 *
 * @apiNote
 * This class is not exported. Instances are created by various Map implementations
 * when they need a Map.Entry that isn't connected to the Map.
 *
 * <p>This class differs from AbstractMap.SimpleImmutableEntry in that it is not
 * serializable and that it is final. This class differs from java.util.KeyValueHolder
 * in that the key and value are nullable.
 *
 * <p>In principle this class could be a variation on KeyValueHolder. However,
 * making that class selectively support nullable keys and values is quite intricate.
 * Various specifications (such as Map.ofEntries and Map.entry) specify non-nullability
 * of the key and the value. Map.Entry.copyOf also requires non-null keys and values;
 * but it simply passes through KeyValueHolder instances, assuming their keys and values
 * are non-nullable. If a KVH with nullable keys and values were introduced, some way
 * to distinguish it would be necessary. This could be done by introducing a subclass
 * (requiring KVH to be made non-final) or by introducing some kind of "mode" field
 * (potentially increasing the size of every KVH instance, though another field could
 * probably fit into the object's padding in most JVMs.) More critically, a mode field
 * would have to be checked in all the right places to get the right behavior.
 *
 * <p>A longer range possibility is to selectively relax the restrictions against nulls in
 * Map.entry and Map.Entry.copyOf. This would also require some intricate specification
 * changes and corresponding implementation changes (e.g., the implementations backing
 * Map.of might still need to reject nulls, and so would Map.ofEntries) but allowing
 * a Map.Entry itself to contain nulls seems beneficial in general. If this is done,
 * merging KeyValueHolder and NullableKeyValueHolder should be reconsidered.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
@jdk.internal.ValueBased
public final class NullableKeyValueHolder<K,V> implements Map.Entry<K,V> {
    final K key;
    final V value;

    /**
     * Constructs a NullableKeyValueHolder.
     *
     * @param k the key, may be null
     * @param v the value, may be null
     */
    public NullableKeyValueHolder(K k, V v) {
        key = k;
        value = v;
    }

    /**
     * Constructs a NullableKeyValueHolder from a Map.Entry. No need for an
     * idempotent copy at this time.
     *
     * @param entry the entry, must not be null
     */
    public NullableKeyValueHolder(Map.Entry<K,V> entry) {
        Objects.requireNonNull(entry);
        key = entry.getKey();
        value = entry.getValue();
    }

    /**
     * Gets the key from this holder.
     *
     * @return the key, may be null
     */
    @Override
    public K getKey() {
        return key;
    }

    /**
     * Gets the value from this holder.
     *
     * @return the value, may be null
     */
    @Override
    public V getValue() {
        return value;
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @param value ignored
     * @return never returns normally
     */
    @Override
    public V setValue(V value) {
        throw new UnsupportedOperationException("not supported");
    }

    /**
     * Compares the specified object with this entry for equality.
     * Returns {@code true} if the given object is also a map entry and
     * the two entries' keys and values are equal.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof Map.Entry<?, ?> e
                && Objects.equals(key, e.getKey())
                && Objects.equals(value, e.getValue());
    }

    private int hash(Object obj) {
        return (obj == null) ? 0 : obj.hashCode();
    }

    /**
     * Returns the hash code value for this map entry. The hash code
     * is {@code key.hashCode() ^ value.hashCode()}.
     */
    @Override
    public int hashCode() {
        return hash(key) ^ hash(value);
    }

    /**
     * Returns a String representation of this map entry.  This
     * implementation returns the string representation of this
     * entry's key followed by the equals character ("{@code =}")
     * followed by the string representation of this entry's value.
     *
     * @return a String representation of this map entry
     */
    @Override
    public String toString() {
        return key + "=" + value;
    }
}
