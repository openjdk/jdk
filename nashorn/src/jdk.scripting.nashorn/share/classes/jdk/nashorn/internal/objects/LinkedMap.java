/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects;

import jdk.nashorn.internal.runtime.Undefined;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>A linked hash map used by the ES6 Map and Set objects. As required by the ECMA specification for these objects,
 * this class allows arbitrary modifications to the base collection while being iterated over. However, note that
 * such modifications are only safe from the same thread performing the iteration; the class is not thread-safe.</p>
 *
 * <p>Deletions and additions that occur during iteration are reflected in the elements visited by the iterator
 * (except for deletion of elements that have already been visited). In non-concurrent Java collections such as
 * {@code java.util.LinkedHashMap} this would result in a {@link java.util.ConcurrentModificationException}
 * being thrown.</p>
 *
 * <p>This class is implemented using a {@link java.util.HashMap} as backing storage with doubly-linked
 * list nodes as values.</p>
 *
 * @see <a href="http://www.ecma-international.org/ecma-262/6.0/#sec-map.prototype.foreach">Map.prototype.forEach</a>
 * @see <a href="http://www.ecma-international.org/ecma-262/6.0/#sec-set.prototype.foreach">Set.prototype.forEach</a>
 */
public class LinkedMap {

    // We use a plain hash map as our hash storage.
    private final Map<Object, Node> data = new HashMap<>();

    // The head and tail of our doubly-linked list. We use the same node to represent both the head and the
    // tail of the list, so the list is circular. This node is never unlinked and thus always remain alive.
    private final Node head = new Node();

    /**
     * A node of a linked list that is used as value in our map. The linked list uses insertion order
     * and allows fast iteration over its element even while the map is modified.
     */
    static class Node {
        private final Object key;
        private volatile Object value;

        private volatile boolean alive = true;
        private volatile Node prev;
        private volatile Node next;

        /**
         * Constructor for the list head. This creates an empty circular list.
         */
        private Node() {
            this(null, null);
            this.next = this;
            this.prev = this;
        }

        /**
         * Constructor for value nodes.
         *
         * @param key the key
         * @param value the value
         */
        private Node(final Object key, final Object value) {
            this.key = key;
            this.value = value;
        }

        /**
         * Get the node's key.
         * @return the hash key
         */
        public Object getKey() {
            return key;
        }

        /**
         * Get the node's value.
         * @return the value
         */
        public Object getValue() {
            return value;
        }

        /**
         * Set the node's value
         * @param value the new value
         */
        void setValue(final Object value) {
            this.value = value;
        }
    }

    /**
     * An iterator over the elements in the map.
     */
    class LinkedMapIterator {

        private Node cursor;

        private LinkedMapIterator() {
            this.cursor = head;
        }

        /**
         * Get the next node in this iteration. Changes in the underlying map are reflected in the iteration
         * as required by the ES6 specification. Note that this method could return a deleted node if deletion
         * occurred concurrently on another thread.
         *
         * @return the next node
         */
        public Node next() {

            if (cursor != null) {
                // If last node is not alive anymore (i.e. has been deleted) go back to the last live node
                // and continue from there. This may be the list head, which always remains alive.
                while (!cursor.alive) {
                    assert cursor != head;
                    cursor = cursor.prev;
                }

                cursor = cursor.next;

                if (cursor == head) {
                    cursor = null; // We've come full circle to the end
                }
            }

            return cursor;
        }
    }

    /**
     * Add a key-value pair to the map.
     * @param key the key
     * @param value the value
     */
    public void set(final Object key, final Object value) {
        Node node = data.get(key);
        if (node != null) {
            node.setValue(value);
        } else {
            node = new Node(key, value);
            data.put(key, node);
            link(node);
        }
    }

    /**
     * Get the value associated with {@code key}.
     * @param key the key
     * @return the associated value, or {@code null} if {@code key} is not contained in the map
     */
    public Object get(final Object key) {
        final Node node = data.get(key);
        return node == null ? Undefined.getUndefined() : node.getValue();
    }

    /**
     * Returns {@code true} if {@code key} is contained in the map.
     * @param key the key
     * @return {@code true} if {@code key} is contained
     */
    public boolean has(final Object key) {
        return data.containsKey(key);
    }

    /**
     * Delete the node associated with {@code key} from the map.
     * @param key the key
     * @return {@code true} if {@code key} was contained in the map
     */
    public boolean delete (final Object key) {
        final Node node = data.remove(key);
        if (node != null) {
            unlink(node);
            return true;
        }
        return false;
    }

    /**
     * Remove all key-value pairs from the map.
     */
    public void clear() {
        data.clear();
        for (Node node = head.next; node != head; node = node.next) {
            node.alive = false;
        }
        head.next = head;
        head.prev = head;
    }

    /**
     * Return the current number of key-value pairs in the map.
     * @return the map size
     */
    public int size() {
        return data.size();
    }

    /**
     * Get an iterator over the key-value pairs in the map.
     * @return an iterator
     */
    public LinkedMapIterator getIterator() {
        return new LinkedMapIterator();
    }

    private void link(final Node newNode) {
        // We always insert at the end (head == tail)
        newNode.next = head;
        newNode.prev = head.prev;
        newNode.prev.next = newNode;
        head.prev = newNode;
    }

    private void unlink(final Node oldNode) {
        // Note that we unlink references to the node being deleted, but keep the references from the deleted node.
        // This is necessary to allow iterators to go back to the last live node in case the current node has been
        // deleted. Also, the forward link of a deleted node may still be followed by an iterator and must not be null.
        oldNode.prev.next = oldNode.next;
        oldNode.next.prev = oldNode.prev;
        oldNode.alive = false;
    }

}
