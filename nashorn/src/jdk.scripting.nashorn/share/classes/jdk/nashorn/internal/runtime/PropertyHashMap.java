/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable hash map implementation for properties.  Properties are keyed on strings.
 * Copying and cloning is avoided by relying on immutability.
 * <p>
 * When adding an element to a hash table, only the head of a bin list is updated, thus
 * an add only requires the cloning of the bins array and adding an element to the head
 * of the bin list.  Similarly for removal, only a portion of a bin list is updated.
 * <p>
 * A separate chronological list is kept for quick generation of keys and values, and,
 * for rehashing.
 * <p>
 * Details:
 * <p>
 * The main goal is to be able to retrieve properties from a map quickly, keying on
 * the property name (String.)  A secondary, but important goal, is to keep maps
 * immutable, so that a map can be shared by multiple objects in a context.
 * Sharing maps allows objects to be categorized as having similar properties, a
 * fact that call site guards rely on.  In this discussion, immutability allows us
 * to significantly reduce the amount of duplication we have in our maps.
 * <p>
 * The simplest of immutable maps is a basic singly linked list.  New properties
 * are simply added to the head of the list.  Ancestor maps are not affected by the
 * addition, since they continue to refer to their own head.  Searching is done by
 * walking linearly though the elements until a match is found, O(N).
 * <p>
 * A hash map can be thought of as an optimization of a linked list map, where the
 * linked list is broken into fragments based on hashCode(key) .  An array is use
 * to quickly reference these fragments, indexing on hashCode(key) mod tableSize
 * (tableSize is typically a power of 2 so that the mod is a fast masking
 * operation.)  If the size of the table is sufficient large, then search time
 * approaches O(1).  In fact, most bins in a hash table are typically empty or
 * contain a one element list.
 * <p>
 * For immutable hash maps, we can think of the hash map as an array of the shorter
 * linked list maps.  If we add an element to the head of one of those lists,  it
 * doesn't affect any ancestor maps.  Thus adding an element to an immutable hash
 * map only requires cloning the array and inserting an element at the head of one
 * of the bins.
 * <p>
 * Using Java HashMaps we don't have enough control over the entries to allow us to
 * implement this technique, so we are forced to clone the entire hash map.
 * <p>
 * Removing elements is done similarly.  We clone the array and then only modify
 * the bin containing the removed element.  More often than not, the list contains
 * only one element (or is very short), so this is not very costly.  When the list
 * has several items, we need to clone the list portion prior to the removed item.
 * <p>
 * Another requirement of property maps is that we need to be able to gather all
 * properties in chronological (add) order.  We have been using LinkedHashMap to
 * provide this.  For the implementation of immutable hash map, we use a singly
 * linked list that is linked in reverse chronological order.  This means we simply
 * add new entries to the head of the list.  If we need to work with the list in
 * forward order, it's simply a matter of allocating an array (size is known) and
 * back filling in reverse order.  Removal of elements from the chronological list
 * is trickier.  LinkedHashMap uses a doubly linked list to give constant time
 * removal. Immutable hash maps can't do that and maintain immutability.  So we
 * manage the chronological list the same way we manage the bins, cloning up to the
 * point of removal.  Don't panic.  This cost is more than offset by the cost of
 * cloning an entire LinkedHashMap.  Plus removal is far more rare than addition.
 * <p>
 * One more optimization.  Maps with a small number of entries don't use the hash
 * map at all, the chronological list is used instead.
 * <p>
 * So the benefits from immutable arrays are; fewer objects and less copying.  For
 * immutable hash map, when no removal is involved, the number of elements per
 * property is two (bin + chronological elements).  For LinkedHashMap it is one
 * (larger element) times the number of maps that refer to the property.  For
 * immutable hash map, addition is constant time.  For LinkedHashMap it's O(N+C)
 * since we have to clone the older map.
 */
public final class PropertyHashMap implements Map <Object, Property> {
    /** Number of initial bins. Power of 2. */
    private static final int INITIAL_BINS = 32;

    /** Threshold before using bins. */
    private static final int LIST_THRESHOLD = 8;

    /** Initial map. */
    public static final PropertyHashMap EMPTY_HASHMAP = new PropertyHashMap();

    /** Number of properties in the map. */
    private final int size;

    /** Threshold before growing the bins. */
    private final int threshold;

    /** Reverse list of all properties. */
    private final Element list;

    /** Hash map bins. */
    private final Element[] bins;

    /** All properties as an array (lazy). */
    private Property[] properties;

    /**
     * Empty map constructor.
     */
    private PropertyHashMap() {
        this.size      = 0;
        this.threshold = 0;
        this.bins      = null;
        this.list      = null;
    }

    /**
     * Clone Constructor
     *
     * @param map Original {@link PropertyHashMap}.
     */
    private PropertyHashMap(final PropertyHashMap map) {
        this.size      = map.size;
        this.threshold = map.threshold;
        this.bins      = map.bins;
        this.list      = map.list;
    }

    /**
     * Constructor used internally to extend a map
     *
     * @param size Size of the new {@link PropertyHashMap}.
     * @param bins The hash bins.
     * @param list The {@link Property} list.
     */
    private PropertyHashMap(final int size, final Element[] bins, final Element list) {
        this.size      = size;
        this.threshold = bins != null ? threeQuarters(bins.length) : 0;
        this.bins      = bins;
        this.list      = list;
    }

    /**
     * Clone a property map, replacing a property with a new one in the same place,
     * which is important for property iterations if a property changes types
     * @param property    old property
     * @param newProperty new property
     * @return new property map
     */
    public PropertyHashMap immutableReplace(final Property property, final Property newProperty) {
        assert property.getKey().equals(newProperty.getKey()) : "replacing properties with different keys: '" + property.getKey() + "' != '" + newProperty.getKey() + "'";
        assert findElement(property.getKey()) != null         : "replacing property that doesn't exist in map: '" + property.getKey() + "'";
        return cloneMap().replaceNoClone(property.getKey(), newProperty);
    }

    /**
     * Clone a {@link PropertyHashMap} and add a {@link Property}.
     *
     * @param property {@link Property} to add.
     *
     * @return New {@link PropertyHashMap}.
     */
    public PropertyHashMap immutableAdd(final Property property) {
        final int newSize = size + 1;
        PropertyHashMap newMap = cloneMap(newSize);
        newMap = newMap.addNoClone(property);
        return newMap;
    }

    /**
     * Clone a {@link PropertyHashMap} and add an array of properties.
     *
     * @param newProperties Properties to add.
     *
     * @return New {@link PropertyHashMap}.
     */
    public PropertyHashMap immutableAdd(final Property... newProperties) {
        final int newSize = size + newProperties.length;
        PropertyHashMap newMap = cloneMap(newSize);
        for (final Property property : newProperties) {
            newMap = newMap.addNoClone(property);
        }
        return newMap;
    }

    /**
     * Clone a {@link PropertyHashMap} and add a collection of properties.
     *
     * @param newProperties Properties to add.
     *
     * @return New {@link PropertyHashMap}.
     */
    public PropertyHashMap immutableAdd(final Collection<Property> newProperties) {
        if (newProperties != null) {
            final int newSize = size + newProperties.size();
            PropertyHashMap newMap = cloneMap(newSize);
            for (final Property property : newProperties) {
                newMap = newMap.addNoClone(property);
            }
            return newMap;
        }
        return this;
    }

    /**
     * Clone a {@link PropertyHashMap} and remove a {@link Property}.
     *
     * @param property {@link Property} to remove.
     *
     * @return New {@link PropertyHashMap}.
     */
    public PropertyHashMap immutableRemove(final Property property) {
        return immutableRemove(property.getKey());
    }

    /**
     * Clone a {@link PropertyHashMap} and remove a {@link Property} based on its key.
     *
     * @param key Key of {@link Property} to remove.
     *
     * @return New {@link PropertyHashMap}.
     */
    public PropertyHashMap immutableRemove(final Object key) {
        if (bins != null) {
            final int binIndex = binIndex(bins, key);
            final Element bin = bins[binIndex];
            if (findElement(bin, key) != null) {
                final int newSize = size - 1;
                Element[] newBins = null;
                if (newSize >= LIST_THRESHOLD) {
                    newBins = bins.clone();
                    newBins[binIndex] = removeFromList(bin, key);
                }
                final Element newList = removeFromList(list, key);
                return new PropertyHashMap(newSize, newBins, newList);
            }
        } else if (findElement(list, key) != null) {
            final int newSize = size - 1;
            return newSize != 0 ? new PropertyHashMap(newSize, null, removeFromList(list, key)) : EMPTY_HASHMAP;
        }
        return this;
    }

    /**
     * Find a {@link Property} in the {@link PropertyHashMap}.
     *
     * @param key Key of {@link Property} to find.
     *
     * @return {@link Property} matching key or {@code null} if not found.
     */
    public Property find(final Object key) {
        final Element element = findElement(key);
        return element != null ? element.getProperty() : null;
    }

    /**
     * Return an array of properties in chronological order of adding.
     *
     * @return Array of all properties.
     */
    Property[] getProperties() {
        if (properties == null) {
            final Property[] array = new Property[size];
            int i = size;
            for (Element element = list; element != null; element = element.getLink()) {
                array[--i] = element.getProperty();
            }
            properties = array;
        }
        return properties;
    }

    /**
     * Returns the bin index from the key.
     *
     * @param bins     The bins array.
     * @param key      {@link Property} key.
     *
     * @return The bin index.
     */
    private static int binIndex(final Element[] bins, final Object key) {
        return  key.hashCode() & bins.length - 1;
    }

    /**
     * Calculate the number of bins needed to contain n properties.
     *
     * @param n Number of elements.
     *
     * @return Number of bins required.
     */
    private static int binsNeeded(final int n) {
        // 50% padding
        return 1 << 32 - Integer.numberOfLeadingZeros(n + (n >>> 1) | INITIAL_BINS - 1);
    }

    /**
     * Used to calculate the current capacity of the bins.
     *
     * @param n Number of bin slots.
     *
     * @return 75% of n.
     */
    private static int threeQuarters(final int n) {
        return (n >>> 1) + (n >>> 2);
    }

    /**
     * Regenerate the bin table after changing the number of bins.
     *
     * @param list    // List of all properties.
     * @param binSize // New size of bins.
     *
     * @return Populated bins.
     */
    private static Element[] rehash(final Element list, final int binSize) {
        final Element[] newBins = new Element[binSize];
        for (Element element = list; element != null; element = element.getLink()) {
            final Property property = element.getProperty();
            final Object   key      = property.getKey();
            final int      binIndex = binIndex(newBins, key);

            newBins[binIndex] = new Element(newBins[binIndex], property);
        }
        return newBins;
    }

    /**
     * Locate an element based on key.
     *
     * @param key {@link Element} key.
     *
     * @return {@link Element} matching key or {@code null} if not found.
     */
    private Element findElement(final Object key) {
        if (bins != null) {
            final int binIndex = binIndex(bins, key);
            return findElement(bins[binIndex], key);
        }
        return findElement(list, key);
    }

    /**
     * Locate an {@link Element} based on key from a specific list.
     *
     * @param elementList Head of {@link Element} list
     * @param key         {@link Element} key.
     * @return {@link Element} matching key or {@code null} if not found.
     */
    private static Element findElement(final Element elementList, final Object key) {
        final int hashCode = key.hashCode();
        for (Element element = elementList; element != null; element = element.getLink()) {
            if (element.match(key, hashCode)) {
                return element;
            }
        }
        return null;
    }


    private PropertyHashMap cloneMap() {
        return new PropertyHashMap(size, bins == null ? null : bins.clone(), list);
    }

    /**
     * Clone {@link PropertyHashMap} to accommodate new size.
     *
     * @param newSize New size of {@link PropertyHashMap}.
     *
     * @return Cloned {@link PropertyHashMap} with new size.
     */
    private PropertyHashMap cloneMap(final int newSize) {
        Element[] newBins;
        if (bins == null && newSize <= LIST_THRESHOLD) {
            newBins = null;
        } else if (newSize > threshold) {
            newBins = rehash(list, binsNeeded(newSize));
        } else {
            newBins = bins.clone();
        }
        return new PropertyHashMap(newSize, newBins, list);
    }



    /**
     * Add a {@link Property} to a temporary {@link PropertyHashMap}, that has
     * been already cloned.  Removes duplicates if necessary.
     *
     * @param property {@link Property} to add.
     *
     * @return New {@link PropertyHashMap}.
     */
    private PropertyHashMap addNoClone(final Property property) {
        int newSize = size;
        final Object key = property.getKey();
        Element newList = list;
        if (bins != null) {
            final int binIndex = binIndex(bins, key);
            Element bin = bins[binIndex];
            if (findElement(bin, key) != null) {
                newSize--;
                bin = removeFromList(bin, key);
                newList = removeFromList(list, key);
            }
            bins[binIndex] = new Element(bin, property);
        } else {
            if (findElement(list, key) != null) {
                newSize--;
                newList = removeFromList(list, key);
            }
        }
        newList = new Element(newList, property);
        return new PropertyHashMap(newSize, bins, newList);
    }

    private PropertyHashMap replaceNoClone(final Object key, final Property property) {
        if (bins != null) {
            final int binIndex = binIndex(bins, key);
            Element bin = bins[binIndex];
            bin = replaceInList(bin, key, property);
            bins[binIndex] = bin;
        }
        Element newList = list;
        newList = replaceInList(newList, key, property);
        return new PropertyHashMap(size, bins, newList);
    }

    /**
     * Removes an {@link Element} from a specific list, avoiding duplication.
     *
     * @param list List to remove from.
     * @param key  Key of {@link Element} to remove.
     *
     * @return New list with {@link Element} removed.
     */
    private static Element removeFromList(final Element list, final Object key) {
        if (list == null) {
            return null;
        }
        final int hashCode = key.hashCode();
        if (list.match(key, hashCode)) {
            return list.getLink();
        }
        final Element head = new Element(null, list.getProperty());
        Element previous = head;
        for (Element element = list.getLink(); element != null; element = element.getLink()) {
            if (element.match(key, hashCode)) {
                previous.setLink(element.getLink());
                return head;
            }
            final Element next = new Element(null, element.getProperty());
            previous.setLink(next);
            previous = next;
        }
        return list;
    }

    // for element x. if x get link matches,
    private static Element replaceInList(final Element list, final Object key, final Property property) {
        assert list != null;
        final int hashCode = key.hashCode();

        if (list.match(key, hashCode)) {
            return new Element(list.getLink(), property);
        }

        final Element head = new Element(null, list.getProperty());
        Element previous = head;
        for (Element element = list.getLink(); element != null; element = element.getLink()) {
            if (element.match(key, hashCode)) {
                previous.setLink(new Element(element.getLink(), property));
                return head;
            }
            final Element next = new Element(null, element.getProperty());
            previous.setLink(next);
            previous = next;
        }
        return list;
    }


    /*
     * Map implementation
     */

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(final Object key) {
        assert key instanceof String || key instanceof Symbol;
        return findElement(key) != null;
    }

    @Override
    public boolean containsValue(final Object value) {
        if (value instanceof Property) {
            final Property property = (Property) value;
            final Element element = findElement(property.getKey());
            return element != null && element.getProperty().equals(value);
        }
        return false;
    }

    @Override
    public Property get(final Object key) {
        assert key instanceof String || key instanceof Symbol;
        final Element element = findElement(key);
        return element != null ? element.getProperty() : null;
    }

    @Override
    public Property put(final Object key, final Property value) {
        throw new UnsupportedOperationException("Immutable map.");
    }

    @Override
    public Property remove(final Object key) {
        throw new UnsupportedOperationException("Immutable map.");
    }

    @Override
    public void putAll(final Map<? extends Object, ? extends Property> m) {
        throw new UnsupportedOperationException("Immutable map.");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Immutable map.");
    }

    @Override
    public Set<Object> keySet() {
        final HashSet<Object> set = new HashSet<>();
        for (Element element = list; element != null; element = element.getLink()) {
            set.add(element.getKey());
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public Collection<Property> values() {
        return Collections.unmodifiableList(Arrays.asList(getProperties()));
    }

    @Override
    public Set<Entry<Object, Property>> entrySet() {
        final HashSet<Entry<Object, Property>> set = new HashSet<>();
        for (Element element = list; element != null; element = element.getLink()) {
            set.add(element);
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * List map element.
     */
    static final class Element implements Entry<Object, Property> {
        /** Link for list construction. */
        private Element link;

        /** Element property. */
        private final Property property;

        /** Element key. Kept separate for performance.) */
        private final Object key;

        /** Element key hash code. */
        private final int hashCode;

        /*
         * Constructors
         */

        Element(final Element link, final Property property) {
            this.link     = link;
            this.property = property;
            this.key      = property.getKey();
            this.hashCode = this.key.hashCode();
        }

        boolean match(final Object otherKey, final int otherHashCode) {
            return this.hashCode == otherHashCode && this.key.equals(otherKey);
        }

        /*
         * Entry implmentation.
         */

        @Override
        public boolean equals(final Object other) {
            assert property != null && other != null;
            return other instanceof Element && property.equals(((Element)other).property);
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Property getValue() {
            return property;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public Property setValue(final Property value) {
            throw new UnsupportedOperationException("Immutable map.");
        }

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer();

            sb.append('[');

            Element elem = this;
            do {
                sb.append(elem.getValue());
                elem = elem.link;
                if (elem != null) {
                    sb.append(" -> ");
                }
            } while (elem != null);

            sb.append(']');

            return sb.toString();
        }

        /*
         * Accessors
         */

        Element getLink() {
            return link;
        }

        void setLink(final Element link) {
            this.link = link;
        }

        Property getProperty() {
            return property;
        }
    }

}
