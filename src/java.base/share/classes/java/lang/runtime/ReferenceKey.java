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

package java.lang.runtime;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * View/wrapper of keys used by the backing {@link ReferencedKeyMap}.
 * There are two style of keys; one for entries in the backing map and
 * one for queries to the backing map. This second style avoids the
 * overhead of a {@link Reference} object.
 *
 * @param <T> key type
 *
 * @since 21
 *
 * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
 *          Do not rely on its availability.
 */
interface ReferenceKey<T> {
    /**
     * {@return the value of the unwrapped key}
     */
    T get();

    /**
     * Cleanup unused key.
     */
    void unused();

    /**
     * {@link WeakReference} wrapper key for entries in the backing map.
     *
     * @param <T> key type
     *
     * @since 21
     */
    class WeakKey<T> extends WeakReference<T> implements ReferenceKey<T> {
        /**
         * Saved hashcode of the key. Used when {@link WeakReference} is
         * null.
         */
        private final int hashcode;

        /**
         * Private constructor.
         *
         * @param key   unwrapped key value
         * @param queue reference queue
         */
        WeakKey(T key, ReferenceQueue<T> queue) {
            super(key, queue);
            this.hashcode = Objects.hashCode(key);
        }

        /**
         * Cleanup unused key. No need to enqueue since the key did not make it
         * into the map.
         */
        @Override
        public void unused() {
            clear();
        }

        @Override
        public boolean equals(Object obj) {
            // Necessary when removing a null reference
            if (obj == this) {
                return true;
            }
            // Necessary when comparing an unwrapped key
            if (obj instanceof ReferenceKey<?> key) {
                obj = key.get();
            }
            return Objects.equals(get(), obj);
        }

        @Override
        public int hashCode() {
            // Use saved hashcode
            return hashcode;
        }

        @Override
        public String toString() {
            return this.getClass().getCanonicalName() + "#" + System.identityHashCode(this);
        }
    }

    /**
     * {@link SoftReference} wrapper key for entries in the backing map.
     *
     * @param <T> key type
     *
     * @since 21
     */
    class SoftKey<T> extends SoftReference<T> implements ReferenceKey<T> {
        /**
         * Saved hashcode of the key. Used when {@link SoftReference} is
         * null.
         */
        private final int hashcode;

        /**
         * Private constructor.
         *
         * @param key   unwrapped key value
         * @param queue reference queue
         */
        SoftKey(T key, ReferenceQueue<T> queue) {
            super(key, queue);
            this.hashcode = Objects.hashCode(key);
        }

        /**
         * Cleanup unused key. No need to enqueue since the key did not make it
         * into the map.
         */
        @Override
        public void unused() {
            clear();
        }

        @Override
        public boolean equals(Object obj) {
            // Necessary when removing a null reference
            if (obj == this) {
                return true;
            }
            // Necessary when comparing an unwrapped key
            if (obj instanceof ReferenceKey<?> key) {
                obj = key.get();
            }
            return Objects.equals(get(), obj);
        }

        @Override
        public int hashCode() {
            // Use saved hashcode
            return hashcode;
        }

        @Override
        public String toString() {
            return this.getClass().getCanonicalName() + "#" + System.identityHashCode(this);
        }
    }

    /**
     * Wrapper for querying the backing map. Avoids the overhead of an
     * {@link Reference} object.
     *
     * @param <T> key type
     *
     * @since 21
     */
    class StrongKey<T> implements ReferenceKey<T> {
        T key;

        /**
         * Private constructor.
         *
         * @param key unwrapped key value
         */
        StrongKey(T key) {
            this.key = key;
        }

        /**
         * {@return the unwrapped key}
         */
        @Override
        public T get() {
            return key;
        }

        @Override
        public void unused() {
            key = null;
        }

        @Override
        public boolean equals(Object obj) {
            // Necessary when comparing an unwrapped key
            if (obj instanceof ReferenceKey<?> key) {
                obj = key.get();
            }
            return Objects.equals(get(), obj);
        }

        @Override
        public int hashCode() {
            // Use unwrapped key hash code
            return get().hashCode();
        }

        @Override
        public String toString() {
            return this.getClass().getCanonicalName() + "#" + System.identityHashCode(this);
        }
    }

}
