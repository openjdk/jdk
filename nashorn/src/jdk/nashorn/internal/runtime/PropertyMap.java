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

import static jdk.nashorn.internal.runtime.PropertyHashMap.EMPTY_HASHMAP;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.getArrayIndex;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.isValidArrayIndex;

import java.lang.invoke.SwitchPoint;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;

/**
 * Map of object properties. The PropertyMap is the "template" for JavaScript object
 * layouts. It contains a map with prototype names as keys and {@link Property} instances
 * as values. A PropertyMap is typically passed to the {@link ScriptObject} constructor
 * to form the seed map for the ScriptObject.
 * <p>
 * All property maps are immutable. If a property is added, modified or removed, the mutator
 * will return a new map.
 */
public final class PropertyMap implements Iterable<Object> {
    /** Used for non extensible PropertyMaps, negative logic as the normal case is extensible. See {@link ScriptObject#preventExtensions()} */
    public static final int NOT_EXTENSIBLE        = 0b0000_0001;
    /** Does this map contain valid array keys? */
    public static final int CONTAINS_ARRAY_KEYS   = 0b0000_0010;

    /** Map status flags. */
    private int flags;

    /** Map of properties. */
    private final PropertyHashMap properties;

    /** Number of fields in use. */
    private int fieldCount;

    /** Number of fields available. */
    private int fieldMaximum;

    /** Length of spill in use. */
    private int spillLength;

    /** {@link SwitchPoint}s for gets on inherited properties. */
    private HashMap<String, SwitchPoint> protoGetSwitches;

    /** History of maps, used to limit map duplication. */
    private WeakHashMap<Property, SoftReference<PropertyMap>> history;

    /** History of prototypes, used to limit map duplication. */
    private WeakHashMap<PropertyMap, SoftReference<PropertyMap>> protoHistory;

    /** property listeners */
    private PropertyListeners listeners;

    /**
     * Constructor.
     *
     * @param properties   A {@link PropertyHashMap} with initial contents.
     * @param fieldCount   Number of fields in use.
     * @param fieldMaximum Number of fields available.
     * @param spillLength  Number of spill slots used.
     * @param containsArrayKeys True if properties contain numeric keys
     */
    private PropertyMap(final PropertyHashMap properties, final int fieldCount, final int fieldMaximum, final int spillLength, final boolean containsArrayKeys) {
        this.properties   = properties;
        this.fieldCount   = fieldCount;
        this.fieldMaximum = fieldMaximum;
        this.spillLength  = spillLength;
        if (containsArrayKeys) {
            setContainsArrayKeys();
        }

        if (Context.DEBUG) {
            count++;
        }
    }

    /**
     * Cloning constructor.
     *
     * @param propertyMap Existing property map.
     * @param properties  A {@link PropertyHashMap} with a new set of properties.
     */
    private PropertyMap(final PropertyMap propertyMap, final PropertyHashMap properties) {
        this.properties   = properties;
        this.flags        = propertyMap.flags;
        this.spillLength  = propertyMap.spillLength;
        this.fieldCount   = propertyMap.fieldCount;
        this.fieldMaximum = propertyMap.fieldMaximum;
        // We inherit the parent property listeners instance. It will be cloned when a new listener is added.
        this.listeners    = propertyMap.listeners;

        if (Context.DEBUG) {
            count++;
            clonedCount++;
        }
    }

    /**
     * Cloning constructor.
     *
     * @param propertyMap Existing property map.
      */
    private PropertyMap(final PropertyMap propertyMap) {
        this(propertyMap, propertyMap.properties);
    }

    /**
     * Duplicates this PropertyMap instance. This is used to duplicate 'shared'
     * maps {@link PropertyMap} used as process wide singletons. Shared maps are
     * duplicated for every global scope object. That way listeners, proto and property
     * histories are scoped within a global scope.
     *
     * @return Duplicated {@link PropertyMap}.
     */
    public PropertyMap duplicate() {
        if (Context.DEBUG) {
            duplicatedCount++;
        }
        return new PropertyMap(this.properties, 0, 0, 0, containsArrayKeys());
    }

    /**
     * Public property map allocator.
     *
     * <p>It is the caller's responsibility to make sure that {@code properties} does not contain
     * properties with keys that are valid array indices.</p>
     *
     * @param properties   Collection of initial properties.
     * @param fieldCount   Number of fields in use.
     * @param fieldMaximum Number of fields available.
     * @param spillLength  Number of used spill slots.
     * @return New {@link PropertyMap}.
     */
    public static PropertyMap newMap(final Collection<Property> properties, final int fieldCount, final int fieldMaximum,  final int spillLength) {
        PropertyHashMap newProperties = EMPTY_HASHMAP.immutableAdd(properties);
        return new PropertyMap(newProperties, fieldCount, fieldMaximum, spillLength, false);
    }

    /**
     * Public property map allocator. Used by nasgen generated code.
     *
     * <p>It is the caller's responsibility to make sure that {@code properties} does not contain
     * properties with keys that are valid array indices.</p>
     *
     * @param properties Collection of initial properties.
     * @return New {@link PropertyMap}.
     */
    public static PropertyMap newMap(final Collection<Property> properties) {
        return (properties == null || properties.isEmpty())? newMap() : newMap(properties, 0, 0, 0);
    }

    /**
     * Return a sharable empty map.
     *
     * @return New empty {@link PropertyMap}.
     */
    public static PropertyMap newMap() {
        return new PropertyMap(EMPTY_HASHMAP, 0, 0, 0, false);
    }

    /**
     * Return number of properties in the map.
     *
     * @return Number of properties.
     */
    public int size() {
        return properties.size();
    }

    /**
     * Get the listeners of this map, or null if none exists
     *
     * @return the listeners
     */
    public PropertyListeners getListeners() {
        return listeners;
    }

    /**
     * Add {@code listenerMap} as a listener to this property map for the given {@code key}.
     *
     * @param key the property name
     * @param listenerMap the listener map
     */
    public void addListener(final String key, final PropertyMap listenerMap) {
        if (listenerMap != this) {
            // We need to clone listener instance when adding a new listener since we share
            // the listeners instance with our parent maps that don't need to see the new listener.
            listeners = PropertyListeners.addListener(listeners, key, listenerMap);
        }
    }

    /**
     * A new property is being added.
     *
     * @param property The new Property added.
     */
    public void propertyAdded(final Property property) {
        invalidateProtoGetSwitchPoint(property);
        if (listeners != null) {
            listeners.propertyAdded(property);
        }
    }

    /**
     * An existing property is being deleted.
     *
     * @param property The property being deleted.
     */
    public void propertyDeleted(final Property property) {
        invalidateProtoGetSwitchPoint(property);
        if (listeners != null) {
            listeners.propertyDeleted(property);
        }
    }

    /**
     * An existing property is being redefined.
     *
     * @param oldProperty The old property
     * @param newProperty The new property
     */
    public void propertyModified(final Property oldProperty, final Property newProperty) {
        invalidateProtoGetSwitchPoint(oldProperty);
        if (listeners != null) {
            listeners.propertyModified(oldProperty, newProperty);
        }
    }

    /**
     * The prototype of an object associated with this {@link PropertyMap} is changed.
     */
    public void protoChanged() {
        invalidateAllProtoGetSwitchPoints();
        if (listeners != null) {
            listeners.protoChanged();
        }
    }

    /**
     * Return a SwitchPoint used to track changes of a property in a prototype.
     *
     * @param key Property key.
     * @return A shared {@link SwitchPoint} for the property.
     */
    public synchronized SwitchPoint getSwitchPoint(final String key) {
        if (protoGetSwitches == null) {
            protoGetSwitches = new HashMap<>();
        }

        SwitchPoint switchPoint = protoGetSwitches.get(key);
        if (switchPoint == null) {
            switchPoint = new SwitchPoint();
            protoGetSwitches.put(key, switchPoint);
        }

        return switchPoint;
    }

    /**
     * Indicate that a prototype property has changed.
     *
     * @param property {@link Property} to invalidate.
     */
    synchronized void invalidateProtoGetSwitchPoint(final Property property) {
        if (protoGetSwitches != null) {

            final String key = property.getKey();
            final SwitchPoint sp = protoGetSwitches.get(key);
            if (sp != null) {
                protoGetSwitches.remove(key);
                if (Context.DEBUG) {
                    protoInvalidations++;
                }
                SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
            }
        }
    }

    /**
     * Indicate that proto itself has changed in hierarchy somewhere.
     */
    synchronized void invalidateAllProtoGetSwitchPoints() {
        if (protoGetSwitches != null && !protoGetSwitches.isEmpty()) {
            if (Context.DEBUG) {
                protoInvalidations += protoGetSwitches.size();
            }
            SwitchPoint.invalidateAll(protoGetSwitches.values().toArray(new SwitchPoint[protoGetSwitches.values().size()]));
            protoGetSwitches.clear();
        }
    }

    /**
     * Add a property to the map, re-binding its getters and setters,
     * if available, to a given receiver. This is typically the global scope. See
     * {@link ScriptObject#addBoundProperties(ScriptObject)}
     *
     * @param property {@link Property} being added.
     * @param bindTo   Object to bind to.
     *
     * @return New {@link PropertyMap} with {@link Property} added.
     */
    PropertyMap addPropertyBind(final AccessorProperty property, final Object bindTo) {
        // No need to store bound property in the history as bound properties can't be reused.
        return addPropertyNoHistory(new AccessorProperty(property, bindTo));
    }

    /**
     * Add a property to the map without adding it to the history. This should be used for properties that
     * can't be shared such as bound properties, or properties that are expected to be added only once.
     *
     * @param property {@link Property} being added.
     * @return New {@link PropertyMap} with {@link Property} added.
     */
    public PropertyMap addPropertyNoHistory(final Property property) {
        if (listeners != null) {
            listeners.propertyAdded(property);
        }
        final PropertyHashMap newProperties = properties.immutableAdd(property);
        final PropertyMap newMap = new PropertyMap(this, newProperties);

        if(!property.isSpill()) {
            newMap.fieldCount = Math.max(newMap.fieldCount, property.getSlot() + 1);
        }
        if (isValidArrayIndex(getArrayIndex(property.getKey()))) {
            newMap.setContainsArrayKeys();
        }

        newMap.spillLength += property.getSpillCount();
        return newMap;
    }

    /**
     * Add a property to the map.  Cloning or using an existing map if available.
     *
     * @param property {@link Property} being added.
     *
     * @return New {@link PropertyMap} with {@link Property} added.
     */
    public PropertyMap addProperty(final Property property) {
        if (listeners != null) {
            listeners.propertyAdded(property);
        }
        PropertyMap newMap = checkHistory(property);

        if (newMap == null) {
            final PropertyHashMap newProperties = properties.immutableAdd(property);
            newMap = new PropertyMap(this, newProperties);
            addToHistory(property, newMap);

            if(!property.isSpill()) {
                newMap.fieldCount = Math.max(newMap.fieldCount, property.getSlot() + 1);
            }
            if (isValidArrayIndex(getArrayIndex(property.getKey()))) {
                newMap.setContainsArrayKeys();
            }

            newMap.spillLength += property.getSpillCount();
        }

        return newMap;
    }

    /**
     * Remove a property from a map. Cloning or using an existing map if available.
     *
     * @param property {@link Property} being removed.
     *
     * @return New {@link PropertyMap} with {@link Property} removed or {@code null} if not found.
     */
    public PropertyMap deleteProperty(final Property property) {
        if (listeners != null) {
            listeners.propertyDeleted(property);
        }
        PropertyMap newMap = checkHistory(property);
        final String key = property.getKey();

        if (newMap == null && properties.containsKey(key)) {
            final PropertyHashMap newProperties = properties.immutableRemove(key);
            newMap = new PropertyMap(this, newProperties);
            addToHistory(property, newMap);
        }

        return newMap;
    }

    /**
     * Replace an existing property with a new one.
     *
     * @param oldProperty Property to replace.
     * @param newProperty New {@link Property}.
     *
     * @return New {@link PropertyMap} with {@link Property} replaced.
     */
    PropertyMap replaceProperty(final Property oldProperty, final Property newProperty) {
        if (listeners != null) {
            listeners.propertyModified(oldProperty, newProperty);
        }
        // Add replaces existing property.
        final PropertyHashMap newProperties = properties.immutableAdd(newProperty);
        final PropertyMap newMap = new PropertyMap(this, newProperties);

        /*
         * See ScriptObject.modifyProperty and ScriptObject.setUserAccessors methods.
         *
         * This replaceProperty method is called only for the following three cases:
         *
         *   1. To change flags OR TYPE of an old (cloned) property. We use the same spill slots.
         *   2. To change one UserAccessor property with another - user getter or setter changed via
         *      Object.defineProperty function. Again, same spill slots are re-used.
         *   3. Via ScriptObject.setUserAccessors method to set user getter and setter functions
         *      replacing the dummy AccessorProperty with null method handles (added during map init).
         *
         * In case (1) and case(2), the property type of old and new property is same. For case (3),
         * the old property is an AccessorProperty and the new one is a UserAccessorProperty property.
         */

        final boolean sameType = (oldProperty.getClass() == newProperty.getClass());
        assert sameType ||
                (oldProperty instanceof AccessorProperty &&
                newProperty instanceof UserAccessorProperty) : "arbitrary replaceProperty attempted";

        newMap.flags = flags;

        /*
         * spillLength remains same in case (1) and (2) because of slot reuse. Only for case (3), we need
         * to add spill count of the newly added UserAccessorProperty property.
         */
        newMap.spillLength = spillLength + (sameType? 0 : newProperty.getSpillCount());
        return newMap;
    }

    /**
     * Make a new UserAccessorProperty property. getter and setter functions are stored in
     * this ScriptObject and slot values are used in property object. Note that slots
     * are assigned speculatively and should be added to map before adding other
     * properties.
     *
     * @param key the property name
     * @param propertyFlags attribute flags of the property
     * @return the newly created UserAccessorProperty
     */
    public UserAccessorProperty newUserAccessors(final String key, final int propertyFlags) {
        int oldSpillLength = spillLength;

        final int getterSlot = oldSpillLength++;
        final int setterSlot = oldSpillLength++;

        return new UserAccessorProperty(key, propertyFlags, getterSlot, setterSlot);
    }

    /**
     * Find a property in the map.
     *
     * @param key Key to search for.
     *
     * @return {@link Property} matching key.
     */
    public Property findProperty(final String key) {
        return properties.find(key);
    }

    /**
     * Adds all map properties from another map.
     *
     * @param other The source of properties.
     *
     * @return New {@link PropertyMap} with added properties.
     */
    public PropertyMap addAll(final PropertyMap other) {
        assert this != other : "adding property map to itself";
        final Property[] otherProperties = other.properties.getProperties();
        final PropertyHashMap newProperties = properties.immutableAdd(otherProperties);

        final PropertyMap newMap = new PropertyMap(this, newProperties);
        for (final Property property : otherProperties) {
            if (isValidArrayIndex(getArrayIndex(property.getKey()))) {
                newMap.setContainsArrayKeys();
            }
            newMap.spillLength += property.getSpillCount();
        }

        return newMap;
    }

    /**
     * Return an array of all properties.
     *
     * @return Properties as an array.
     */
    public Property[] getProperties() {
        return properties.getProperties();
    }

    /**
     * Prevents the map from having additional properties.
     *
     * @return New map with {@link #NOT_EXTENSIBLE} flag set.
     */
    PropertyMap preventExtensions() {
        final PropertyMap newMap = new PropertyMap(this);
        newMap.flags |= NOT_EXTENSIBLE;
        return newMap;
    }

    /**
     * Prevents properties in map from being modified.
     *
     * @return New map with {@link #NOT_EXTENSIBLE} flag set and properties with
     * {@link Property#NOT_CONFIGURABLE} set.
     */
    PropertyMap seal() {
        PropertyHashMap newProperties = EMPTY_HASHMAP;

        for (final Property oldProperty :  properties.getProperties()) {
            newProperties = newProperties.immutableAdd(oldProperty.addFlags(Property.NOT_CONFIGURABLE));
        }

        final PropertyMap newMap = new PropertyMap(this, newProperties);
        newMap.flags |= NOT_EXTENSIBLE;

        return newMap;
    }

    /**
     * Prevents properties in map from being modified or written to.
     *
     * @return New map with {@link #NOT_EXTENSIBLE} flag set and properties with
     * {@link Property#NOT_CONFIGURABLE} and {@link Property#NOT_WRITABLE} set.
     */
    PropertyMap freeze() {
        PropertyHashMap newProperties = EMPTY_HASHMAP;

        for (Property oldProperty : properties.getProperties()) {
            int propertyFlags = Property.NOT_CONFIGURABLE;

            if (!(oldProperty instanceof UserAccessorProperty)) {
                propertyFlags |= Property.NOT_WRITABLE;
            }

            newProperties = newProperties.immutableAdd(oldProperty.addFlags(propertyFlags));
        }

        final PropertyMap newMap = new PropertyMap(this, newProperties);
        newMap.flags |= NOT_EXTENSIBLE;

        return newMap;
    }

    /**
     * Check for any configurable properties.
     *
     * @return {@code true} if any configurable.
     */
    private boolean anyConfigurable() {
        for (final Property property : properties.getProperties()) {
            if (property.isConfigurable()) {
               return true;
            }
        }

        return false;
    }

    /**
     * Check if all properties are frozen.
     *
     * @return {@code true} if all are frozen.
     */
    private boolean allFrozen() {
        for (final Property property : properties.getProperties()) {
            // check if it is a data descriptor
            if (!(property instanceof UserAccessorProperty)) {
                if (property.isWritable()) {
                    return false;
                }
            }
            if (property.isConfigurable()) {
               return false;
            }
        }

        return true;
    }

    /**
     * Check prototype history for an existing property map with specified prototype.
     *
     * @param parentMap New prototype object.
     *
     * @return Existing {@link PropertyMap} or {@code null} if not found.
     */
    private PropertyMap checkProtoHistory(final PropertyMap parentMap) {
        final PropertyMap cachedMap;
        if (protoHistory != null) {
            final SoftReference<PropertyMap> weakMap = protoHistory.get(parentMap);
            cachedMap = (weakMap != null ? weakMap.get() : null);
        } else {
            cachedMap = null;
        }

        if (Context.DEBUG && cachedMap != null) {
            protoHistoryHit++;
        }

        return cachedMap;
    }

    /**
     * Add a map to the prototype history.
     *
     * @param parentMap Prototype to add (key.)
     * @param newMap   {@link PropertyMap} associated with prototype.
     */
    private void addToProtoHistory(final PropertyMap parentMap, final PropertyMap newMap) {
        if (protoHistory == null) {
            protoHistory = new WeakHashMap<>();
        }

        protoHistory.put(parentMap, new SoftReference<>(newMap));
    }

    /**
     * Track the modification of the map.
     *
     * @param property Mapping property.
     * @param newMap   Modified {@link PropertyMap}.
     */
    private void addToHistory(final Property property, final PropertyMap newMap) {
        if (!properties.isEmpty()) {
            if (history == null) {
                history = new WeakHashMap<>();
            }

            history.put(property, new SoftReference<>(newMap));
        }
    }

    /**
     * Check the history for a map that already has the given property added.
     *
     * @param property {@link Property} to add.
     *
     * @return Existing map or {@code null} if not found.
     */
    private PropertyMap checkHistory(final Property property) {

        if (history != null) {
            SoftReference<PropertyMap> ref = history.get(property);
            final PropertyMap historicMap = ref == null ? null : ref.get();

            if (historicMap != null) {
                if (Context.DEBUG) {
                    historyHit++;
                }

                return historicMap;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(" [");
        boolean isFirst = true;

        for (final Property property : properties.values()) {
            if (!isFirst) {
                sb.append(", ");
            }

            isFirst = false;

            sb.append(ScriptRuntime.safeToString(property.getKey()));
            final Class<?> ctype = property.getCurrentType();
            sb.append(" <").
                append(property.getClass().getSimpleName()).
                append(':').
                append(ctype == null ?
                    "undefined" :
                    ctype.getSimpleName()).
                append('>');
        }

        sb.append(']');

        return sb.toString();
    }

    @Override
    public Iterator<Object> iterator() {
        return new PropertyMapIterator(this);
    }

    /**
     * Check if this map contains properties with valid array keys
     *
     * @return {@code true} if this map contains properties with valid array keys
     */
    public final boolean containsArrayKeys() {
        return (flags & CONTAINS_ARRAY_KEYS) != 0;
    }

    /**
     * Flag this object as having array keys in defined properties
     */
    private void setContainsArrayKeys() {
        flags |= CONTAINS_ARRAY_KEYS;
    }

    /**
     * Test to see if {@link PropertyMap} is extensible.
     *
     * @return {@code true} if {@link PropertyMap} can be added to.
     */
    boolean isExtensible() {
        return (flags & NOT_EXTENSIBLE) == 0;
    }

    /**
     * Test to see if {@link PropertyMap} is not extensible or any properties
     * can not be modified.
     *
     * @return {@code true} if {@link PropertyMap} is sealed.
     */
    boolean isSealed() {
        return !isExtensible() && !anyConfigurable();
    }

    /**
     * Test to see if {@link PropertyMap} is not extensible or all properties
     * can not be modified.
     *
     * @return {@code true} if {@link PropertyMap} is frozen.
     */
    boolean isFrozen() {
        return !isExtensible() && allFrozen();
    }
    /**
     * Get the number of fields allocated for this {@link PropertyMap}.
     *
     * @return Number of fields allocated.
     */
    int getFieldCount() {
        return fieldCount;
    }
    /**
     * Get maximum number of fields available for this {@link PropertyMap}.
     *
     * @return Number of fields available.
     */
    int getFieldMaximum() {
        return fieldMaximum;
    }

    /**
     * Get length of spill area associated with this {@link PropertyMap}.
     *
     * @return Length of spill area.
     */
    int getSpillLength() {
        return spillLength;
    }

    /**
     * Return a property map with the same layout that is associated with the new prototype object.
     *
     * @param newProto New prototype object to replace oldProto.
     * @return New {@link PropertyMap} with prototype changed.
     */
    public PropertyMap changeProto(final ScriptObject newProto) {

        final PropertyMap parentMap = newProto == null ? null : newProto.getMap();
        final PropertyMap nextMap = checkProtoHistory(parentMap);
        if (nextMap != null) {
            return nextMap;
        }

        if (Context.DEBUG) {
            setProtoNewMapCount++;
        }

        final PropertyMap newMap = new PropertyMap(this);
        addToProtoHistory(parentMap, newMap);

        return newMap;
    }


    /**
     * {@link PropertyMap} iterator.
     */
    private static class PropertyMapIterator implements Iterator<Object> {
        /** Property iterator. */
        final Iterator<Property> iter;

        /** Current Property. */
        Property property;

        /**
         * Constructor.
         *
         * @param propertyMap {@link PropertyMap} to iterate over.
         */
        PropertyMapIterator(final PropertyMap propertyMap) {
            iter = Arrays.asList(propertyMap.properties.getProperties()).iterator();
            property = iter.hasNext() ? iter.next() : null;
            skipNotEnumerable();
        }

        /**
         * Ignore properties that are not enumerable.
         */
        private void skipNotEnumerable() {
            while (property != null && !property.isEnumerable()) {
                property = iter.hasNext() ? iter.next() : null;
            }
        }

        @Override
        public boolean hasNext() {
            return property != null;
        }

        @Override
        public Object next() {
            if (property == null) {
                throw new NoSuchElementException();
            }

            final Object key = property.getKey();
            property = iter.next();
            skipNotEnumerable();

            return key;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /*
     * Debugging and statistics.
     */

    // counters updated only in debug mode
    private static int count;
    private static int clonedCount;
    private static int duplicatedCount;
    private static int historyHit;
    private static int protoInvalidations;
    private static int protoHistoryHit;
    private static int setProtoNewMapCount;

    /**
     * @return Total number of maps.
     */
    public static int getCount() {
        return count;
    }

    /**
     * @return The number of maps that were cloned.
     */
    public static int getClonedCount() {
        return clonedCount;
    }

    /**
     * @return The number of maps that are duplicated.
     */
    public static int getDuplicatedCount() {
        return duplicatedCount;
    }

    /**
     * @return The number of times history was successfully used.
     */
    public static int getHistoryHit() {
        return historyHit;
    }

    /**
     * @return The number of times prototype changes caused invalidation.
     */
    public static int getProtoInvalidations() {
        return protoInvalidations;
    }

    /**
     * @return The number of times proto history was successfully used.
     */
    public static int getProtoHistoryHit() {
        return protoHistoryHit;
    }

    /**
     * @return The number of times prototypes were modified.
     */
    public static int getSetProtoNewMapCount() {
        return setProtoNewMapCount;
    }

}
