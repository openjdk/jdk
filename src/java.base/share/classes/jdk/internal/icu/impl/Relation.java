// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 **********************************************************************
 * Copyright (c) 2002-2015, International Business Machines
 * Corporation and others.  All Rights Reserved.
 **********************************************************************
 * Author: Mark Davis
 **********************************************************************
 */
package jdk.internal.icu.impl;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import jdk.internal.icu.util.Freezable;

/**
 * A Relation is a set of mappings from keys to values.
 * Unlike Map, there is not guaranteed to be a single value per key.
 * The Map-like APIs return collections for values.
 * @author medavis

 */
public class Relation<K, V> implements Freezable<Relation<K,V>> { // TODO: add , Map<K, Collection<V>>, but requires API changes
    private Map<K, Set<V>> data;

    Constructor<? extends Set<V>> setCreator;
    Object[] setComparatorParam;

    public static <K, V> Relation<K, V> of(Map<K, Set<V>> map, Class<?> setCreator) {
        return new Relation<>(map, setCreator);
    }

    public static <K,V> Relation<K, V> of(Map<K, Set<V>> map, Class<?> setCreator, Comparator<V> setComparator) {
        return new Relation<>(map, setCreator, setComparator);
    }

    public Relation(Map<K, Set<V>> map, Class<?> setCreator) {
        this(map, setCreator, null);
    }

    @SuppressWarnings("unchecked")
    public Relation(Map<K, Set<V>> map, Class<?> setCreator, Comparator<V> setComparator) {
        try {
            setComparatorParam = setComparator == null ? null : new Object[]{setComparator};
            if (setComparator == null) {
                this.setCreator = ((Class<? extends Set<V>>)setCreator).getConstructor();
                this.setCreator.newInstance(setComparatorParam); // check to make sure compiles
            } else {
                this.setCreator = ((Class<? extends Set<V>>)setCreator).getConstructor(Comparator.class);
                this.setCreator.newInstance(setComparatorParam); // check to make sure compiles
            }
            data = map == null ? new HashMap<K, Set<V>>() : map;
        } catch (Exception e) {
            throw (RuntimeException) new IllegalArgumentException("Can't create new set").initCause(e);
        }
    }

    public void clear() {
        data.clear();
    }

    public boolean containsKey(Object key) {
        return data.containsKey(key);
    }

    public boolean containsValue(Object value) {
        for (Set<V> values : data.values()) {
            if (values.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public final Set<Entry<K, V>> entrySet() {
        return keyValueSet();
    }

    public Set<Entry<K, Set<V>>> keyValuesSet() {
        return data.entrySet();
    }

    public Set<Entry<K, V>> keyValueSet() {
        Set<Entry<K, V>> result = new LinkedHashSet<>();
        for (K key : data.keySet()) {
            for (V value : data.get(key)) {
                result.add(new SimpleEntry<>(key, value));
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (o.getClass() != this.getClass())
            return false;
        return data.equals(((Relation<?, ?>) o).data);
    }

    //  public V get(Object key) {
    //      Set<V> set = data.get(key);
    //      if (set == null || set.size() == 0)
    //        return null;
    //      return set.iterator().next();
    //  }

    public Set<V> getAll(Object key) {
        return data.get(key);
    }

    public Set<V> get(Object key) {
        return data.get(key);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public Set<K> keySet() {
        return data.keySet();
    }

    public V put(K key, V value) {
        Set<V> set = data.get(key);
        if (set == null) {
            data.put(key, set = newSet());
        }
        set.add(value);
        return value;
    }

    public V putAll(K key, Collection<? extends V> values) {
        Set<V> set = data.get(key);
        if (set == null) {
            data.put(key, set = newSet());
        }
        set.addAll(values);
        return values.size() == 0 ? null : values.iterator().next();
    }

    public V putAll(Collection<K> keys, V value) {
        V result = null;
        for (K key : keys) {
            result = put(key, value);
        }
        return result;
    }

    private Set<V> newSet() {
        try {
            return setCreator.newInstance(setComparatorParam);
        } catch (Exception e) {
            throw (RuntimeException) new IllegalArgumentException("Can't create new set").initCause(e);
        }
    }

    public void putAll(Map<? extends K, ? extends V> t) {
        for (Map.Entry<? extends K, ? extends V> entry : t.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public void putAll(Relation<? extends K, ? extends V> t) {
        for (K key : t.keySet()) {
            for (V value : t.getAll(key)) {
                put(key, value);
            }
        }
    }

    public Set<V> removeAll(K key) {
        try {
            return data.remove(key);
        } catch (NullPointerException e) {
            return null; // data doesn't allow null, eg ConcurrentHashMap
        }
    }

    public boolean remove(K key, V value) {
        try {
            Set<V> set = data.get(key);
            if (set == null) {
                return false;
            }
            boolean result = set.remove(value);
            if (set.size() == 0) {
                data.remove(key);
            }
            return result;
        } catch (NullPointerException e) {
            return false; // data doesn't allow null, eg ConcurrentHashMap
        }
    }

    public int size() {
        return data.size();
    }

    public Set<V> values() {
        return values(new LinkedHashSet<V>());
    }

    public <C extends Collection<V>> C values(C result) {
        for (Entry<K, Set<V>> keyValue : data.entrySet()) {
            result.addAll(keyValue.getValue());
        }
        return result;
    }

    @Override
    public String toString() {
        return data.toString();
    }

    static class SimpleEntry<K, V> implements Entry<K, V> {
        K key;

        V value;

        public SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public SimpleEntry(Entry<K, V> e) {
            this.key = e.getKey();
            this.value = e.getValue();
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }
    }

    public Relation<K,V> addAllInverted(Relation<V,K> source) {
        for (V value : source.data.keySet()) {
            for (K key : source.data.get(value)) {
                put(key, value);
            }
        }
        return this;
    }

    public Relation<K,V> addAllInverted(Map<V,K> source) {
        for (Map.Entry<V,K> entry : source.entrySet()) {
            put(entry.getValue(), entry.getKey());
        }
        return this;
    }

    volatile boolean frozen = false;

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public Relation<K, V> freeze() {
        if (!frozen) {
            // does not handle one level down, so we do that on a case-by-case basis
            for (K key : data.keySet()) {
                data.put(key, Collections.unmodifiableSet(data.get(key)));
            }
            // now do top level
            data = Collections.unmodifiableMap(data);
            frozen = true;
        }
        return this;
    }

    @Override
    public Relation<K, V> cloneAsThawed() {
        // TODO do later
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(Relation<K, V> toBeRemoved) {
        boolean result = false;
        for (K key : toBeRemoved.keySet()) {
            try {
                Set<V> values = toBeRemoved.getAll(key);
                if (values != null) {
                    result |= removeAll(key, values);
                }
            } catch (NullPointerException e) {
                // data doesn't allow null, eg ConcurrentHashMap
            }
        }
        return result;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")    // Not supported by Eclipse, but we need this for javac
    public final Set<V> removeAll(K... keys) {
        return removeAll(Arrays.asList(keys));
    }

    public boolean removeAll(K key, Iterable<V> toBeRemoved) {
        boolean result = false;
        for (V value : toBeRemoved) {
            result |= remove(key, value);
        }
        return result;
    }

    public Set<V> removeAll(Collection<K> toBeRemoved) {
        Set<V> result = new LinkedHashSet<>();
        for (K key : toBeRemoved) {
            try {
                final Set<V> removals = data.remove(key);
                if (removals != null) {
                    result.addAll(removals);
                }
            } catch (NullPointerException e) {
                // data doesn't allow null, eg ConcurrentHashMap
            }
        }
        return result;
    }
}
