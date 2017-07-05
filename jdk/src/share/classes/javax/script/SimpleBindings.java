/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package javax.script;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;

/**
 * A simple implementation of Bindings backed by
 * a <code>HashMap</code> or some other specified <code>Map</code>.
 *
 * @author Mike Grogan
 * @since 1.6
 */
public class SimpleBindings implements Bindings {

    /**
     * The <code>Map</code> field stores the attributes.
     */
    private Map<String,Object> map;

    /**
     * Constructor uses an existing <code>Map</code> to store the values.
     * @param m The <code>Map</code> backing this <code>SimpleBindings</code>.
     * @throws NullPointerException if m is null
     */
    public SimpleBindings(Map<String,Object> m) {
        if (m == null) {
            throw new NullPointerException();
        }
        this.map = m;
    }

    /**
     * Default constructor uses a <code>HashMap</code>.
     */
    public SimpleBindings() {
        this(new HashMap<String,Object>());
    }

    /**
     * Sets the specified key/value in the underlying <code>map</code> field.
     *
     * @param name Name of value
     * @param value Value to set.
     *
     * @return Previous value for the specified key.  Returns null if key was previously
     * unset.
     *
     * @throws NullPointerException if the name is null.
     * @throws IllegalArgumentException if the name is empty.
     */
    public Object put(String name, Object value) {
        checkKey(name);
        return map.put(name,value);
    }

    /**
     * <code>putAll</code> is implemented using <code>Map.putAll</code>.
     *
     * @param toMerge The <code>Map</code> of values to add.
     *
     * @throws NullPointerException
     *         if toMerge map is null or if some key in the map is null.
     * @throws IllegalArgumentException
     *         if some key in the map is an empty String.
     */
    public void putAll(Map<? extends String, ? extends Object> toMerge) {
        if (toMerge == null) {
            throw new NullPointerException("toMerge map is null");
        }
        for (Map.Entry<? extends String, ? extends Object> entry : toMerge.entrySet()) {
            String key = entry.getKey();
            checkKey(key);
            put(key, entry.getValue());
        }
    }

    /** {@inheritDoc} */
    public void clear() {
        map.clear();
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the specified
     * key.  More formally, returns <tt>true</tt> if and only if
     * this map contains a mapping for a key <tt>k</tt> such that
     * <tt>(key==null ? k==null : key.equals(k))</tt>.  (There can be
     * at most one such mapping.)
     *
     * @param key key whose presence in this map is to be tested.
     * @return <tt>true</tt> if this map contains a mapping for the specified
     *         key.
     *
     * @throws NullPointerException if key is null
     * @throws ClassCastException if key is not String
     * @throws IllegalArgumentException if key is empty String
     */
    public boolean containsKey(Object key) {
        checkKey(key);
        return map.containsKey(key);
    }

    /** {@inheritDoc} */
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    /** {@inheritDoc} */
    public Set<Map.Entry<String, Object>> entrySet() {
        return map.entrySet();
    }

    /**
     * Returns the value to which this map maps the specified key.  Returns
     * <tt>null</tt> if the map contains no mapping for this key.  A return
     * value of <tt>null</tt> does not <i>necessarily</i> indicate that the
     * map contains no mapping for the key; it's also possible that the map
     * explicitly maps the key to <tt>null</tt>.  The <tt>containsKey</tt>
     * operation may be used to distinguish these two cases.
     *
     * <p>More formally, if this map contains a mapping from a key
     * <tt>k</tt> to a value <tt>v</tt> such that <tt>(key==null ? k==null :
     * key.equals(k))</tt>, then this method returns <tt>v</tt>; otherwise
     * it returns <tt>null</tt>.  (There can be at most one such mapping.)
     *
     * @param key key whose associated value is to be returned.
     * @return the value to which this map maps the specified key, or
     *         <tt>null</tt> if the map contains no mapping for this key.
     *
     * @throws NullPointerException if key is null
     * @throws ClassCastException if key is not String
     * @throws IllegalArgumentException if key is empty String
     */
    public Object get(Object key) {
        checkKey(key);
        return map.get(key);
    }

    /** {@inheritDoc} */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /** {@inheritDoc} */
    public Set<String> keySet() {
        return map.keySet();
    }

    /**
     * Removes the mapping for this key from this map if it is present
     * (optional operation).   More formally, if this map contains a mapping
     * from key <tt>k</tt> to value <tt>v</tt> such that
     * <code>(key==null ?  k==null : key.equals(k))</code>, that mapping
     * is removed.  (The map can contain at most one such mapping.)
     *
     * <p>Returns the value to which the map previously associated the key, or
     * <tt>null</tt> if the map contained no mapping for this key.  (A
     * <tt>null</tt> return can also indicate that the map previously
     * associated <tt>null</tt> with the specified key if the implementation
     * supports <tt>null</tt> values.)  The map will not contain a mapping for
     * the specified  key once the call returns.
     *
     * @param key key whose mapping is to be removed from the map.
     * @return previous value associated with specified key, or <tt>null</tt>
     *         if there was no mapping for key.
     *
     * @throws NullPointerException if key is null
     * @throws ClassCastException if key is not String
     * @throws IllegalArgumentException if key is empty String
     */
    public Object remove(Object key) {
        checkKey(key);
        return map.remove(key);
    }

    /** {@inheritDoc} */
    public int size() {
        return map.size();
    }

    /** {@inheritDoc} */
    public Collection<Object> values() {
        return map.values();
    }

    private void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException("key can not be null");
        }
        if (!(key instanceof String)) {
            throw new ClassCastException("key should be a String");
        }
        if (key.equals("")) {
            throw new IllegalArgumentException("key can not be empty");
        }
    }
}
