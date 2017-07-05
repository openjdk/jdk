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

import java.lang.invoke.SwitchPoint;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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
public final class PropertyMap implements Iterable<Object>, PropertyListener {
    /** Used for non extensible PropertyMaps, negative logic as the normal case is extensible. See {@link ScriptObject#preventExtensions()} */
    public static final int NOT_EXTENSIBLE        = 0b0000_0001;
    /** This mask is used to preserve certain flags when cloning the PropertyMap. Others should not be copied */
    private static final int CLONEABLE_FLAGS_MASK = 0b0000_1111;
    /** Has a listener been added to this property map. This flag is not copied when cloning a map. See {@link PropertyListener} */
    public static final int IS_LISTENER_ADDED     = 0b0001_0000;
    /** Is this process wide "shared" map?. This flag is not copied when cloning a map */
    public static final int IS_SHARED             = 0b0010_0000;

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
    private Map<String, SwitchPoint> protoGetSwitches;

    /** History of maps, used to limit map duplication. */
    private HashMap<Property, PropertyMap> history;

    /** History of prototypes, used to limit map duplication. */
    private WeakHashMap<ScriptObject, WeakReference<PropertyMap>> protoHistory;

    /** Cache for hashCode */
    private int hashCode;

    /**
     * Constructor.
     *
     * @param properties   A {@link PropertyHashMap} with initial contents.
     * @param fieldCount   Number of fields in use.
     * @param fieldMaximum Number of fields available.
     * @param spillLength  Number of spill slots used.
     */
    private PropertyMap(final PropertyHashMap properties, final int fieldCount, final int fieldMaximum, final int spillLength) {
        this.properties   = properties;
        this.fieldCount   = fieldCount;
        this.fieldMaximum = fieldMaximum;
        this.spillLength  = spillLength;

        if (Context.DEBUG) {
            count++;
        }
    }

    /**
     * Constructor.
     *
     * @param properties A {@link PropertyHashMap} with initial contents.
     */
    private PropertyMap(final PropertyHashMap properties) {
        this(properties, 0, 0, 0);
    }

    /**
     * Cloning constructor.
     *
     * @param propertyMap Existing property map.
     * @param properties  A {@link PropertyHashMap} with a new set of properties.
     */
    private PropertyMap(final PropertyMap propertyMap, final PropertyHashMap properties) {
        this.properties   = properties;
        this.flags        = propertyMap.getClonedFlags();
        this.spillLength  = propertyMap.spillLength;
        this.fieldCount   = propertyMap.fieldCount;
        this.fieldMaximum = propertyMap.fieldMaximum;

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
        return new PropertyMap(this.properties);
    }

    /**
     * Public property map allocator.
     *
     * @param properties   Collection of initial properties.
     * @param fieldCount   Number of fields in use.
     * @param fieldMaximum Number of fields available.
     * @param spillLength  Number of used spill slots.
     * @return New {@link PropertyMap}.
     */
    public static PropertyMap newMap(final Collection<Property> properties, final int fieldCount, final int fieldMaximum,  final int spillLength) {
        PropertyHashMap newProperties = EMPTY_HASHMAP.immutableAdd(properties);
        return new PropertyMap(newProperties, fieldCount, fieldMaximum, spillLength);
    }

    /**
     * Public property map allocator. Used by nasgen generated code.
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
        return new PropertyMap(EMPTY_HASHMAP);
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
     * Return a SwitchPoint used to track changes of a property in a prototype.
     *
     * @param proto  Object prototype.
     * @param key    {@link Property} key.
     *
     * @return A shared {@link SwitchPoint} for the property.
     */
    public SwitchPoint getProtoGetSwitchPoint(final ScriptObject proto, final String key) {
        assert !isShared() : "proto SwitchPoint from a shared PropertyMap";

        if (proto == null) {
            return null;
        }

        if (protoGetSwitches == null) {
            protoGetSwitches = new HashMap<>();
            if (! isListenerAdded()) {
                proto.addPropertyListener(this);
                setIsListenerAdded();
            }
        }

        if (protoGetSwitches.containsKey(key)) {
            return protoGetSwitches.get(key);
        }

        final SwitchPoint switchPoint = new SwitchPoint();
        protoGetSwitches.put(key, switchPoint);

        return switchPoint;
    }

    /**
     * Indicate that a prototype property hash changed.
     *
     * @param property {@link Property} to invalidate.
     */
    private void invalidateProtoGetSwitchPoint(final Property property) {
        assert !isShared() : "proto invalidation on a shared PropertyMap";

        if (protoGetSwitches != null) {
            final String key = property.getKey();
            final SwitchPoint sp = protoGetSwitches.get(key);
            if (sp != null) {
                protoGetSwitches.put(key, new SwitchPoint());
                if (Context.DEBUG) {
                    protoInvalidations++;
                }
                SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
            }
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
        return addProperty(new AccessorProperty(property, bindTo));
    }

    /**
     * Add a property to the map.  Cloning or using an existing map if available.
     *
     * @param property {@link Property} being added.
     *
     * @return New {@link PropertyMap} with {@link Property} added.
     */
    public PropertyMap addProperty(final Property property) {
        PropertyMap newMap = checkHistory(property);

        if (newMap == null) {
            final PropertyHashMap newProperties = properties.immutableAdd(property);
            newMap = new PropertyMap(this, newProperties);
            addToHistory(property, newMap);

            if(!property.isSpill()) {
                newMap.fieldCount = Math.max(newMap.fieldCount, property.getSlot() + 1);
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

        newMap.flags = getClonedFlags();

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
     * Make this property map 'shared' one. Shared property map instances are
     * process wide singleton objects. A shaped map should never be added as a listener
     * to a proto object. Nor it should have history or proto history. A shared map
     * is just a template that is meant to be duplicated before use. All nasgen initialized
     * property maps are shared.
     *
     * @return this map after making it as shared
     */
    public PropertyMap setIsShared() {
        assert !isListenerAdded() : "making PropertyMap shared after listener added";
        assert protoHistory == null : "making PropertyMap shared after associating a proto with it";
        if (Context.DEBUG) {
            sharedCount++;
        }

        flags |= IS_SHARED;
        // clear any history on this PropertyMap, won't be used.
        history = null;
        return this;
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
     * @param newProto New prototype object.
     *
     * @return Existing {@link PropertyMap} or {@code null} if not found.
     */
    private PropertyMap checkProtoHistory(final ScriptObject newProto) {
        final PropertyMap cachedMap;
        if (protoHistory != null) {
            final WeakReference<PropertyMap> weakMap = protoHistory.get(newProto);
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
     * @param newProto Prototype to add (key.)
     * @param newMap   {@link PropertyMap} associated with prototype.
     */
    private void addToProtoHistory(final ScriptObject newProto, final PropertyMap newMap) {
        assert !isShared() : "proto history modified on a shared PropertyMap";

        if (protoHistory == null) {
            protoHistory = new WeakHashMap<>();
        }

        protoHistory.put(newProto, new WeakReference<>(newMap));
    }

    /**
     * Track the modification of the map.
     *
     * @param property Mapping property.
     * @param newMap   Modified {@link PropertyMap}.
     */
    private void addToHistory(final Property property, final PropertyMap newMap) {
        assert !isShared() : "history modified on a shared PropertyMap";

        if (!properties.isEmpty()) {
            if (history == null) {
                history = new LinkedHashMap<>();
            }

            history.put(property, newMap);
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
            PropertyMap historicMap = history.get(property);

            if (historicMap != null) {
                if (Context.DEBUG) {
                    historyHit++;
                }

                return historicMap;
            }
        }

        return null;
    }

    /**
     * Calculate the hash code for the map.
     *
     * @return Computed hash code.
     */
    private int computeHashCode() {
        int hash = 0;

        for (final Property property : getProperties()) {
            hash = hash << 7 ^ hash >> 7;
            hash ^= property.hashCode();
        }

        return hash;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0 && !properties.isEmpty()) {
            hashCode = computeHashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof PropertyMap)) {
            return false;
        }

        final PropertyMap otherMap = (PropertyMap)other;

        if (properties.size() != otherMap.properties.size()) {
            return false;
        }

        final Iterator<Property> iter      = properties.values().iterator();
        final Iterator<Property> otherIter = otherMap.properties.values().iterator();

        while (iter.hasNext() && otherIter.hasNext()) {
            if (!iter.next().equals(otherIter.next())) {
                return false;
            }
        }

        return true;
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
     * Check whether a {@link PropertyListener} has been added to this map.
     *
     * @return {@code true} if {@link PropertyListener} exists
     */
    public boolean isListenerAdded() {
        return (flags & IS_LISTENER_ADDED) != 0;
    }

    /**
     * Check if this map shared or not.
     *
     * @return true if this map is shared.
     */
    public boolean isShared() {
        return (flags & IS_SHARED) != 0;
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
     * Change the prototype of objects associated with this {@link PropertyMap}.
     *
     * @param oldProto Current prototype object.
     * @param newProto New prototype object to replace oldProto.
     *
     * @return New {@link PropertyMap} with prototype changed.
     */
    PropertyMap changeProto(final ScriptObject oldProto, final ScriptObject newProto) {
        assert !isShared() : "proto associated with a shared PropertyMap";

        if (oldProto == newProto) {
            return this;
        }

        final PropertyMap nextMap = checkProtoHistory(newProto);
        if (nextMap != null) {
            return nextMap;
        }

        if (Context.DEBUG) {
            incrementSetProtoNewMapCount();
        }

        final PropertyMap newMap = new PropertyMap(this);
        addToProtoHistory(newProto, newMap);

        return newMap;
    }

    /**
     * Indicate that the map has listeners.
     */
    private void setIsListenerAdded() {
        flags |= IS_LISTENER_ADDED;
    }

    /**
     * Return only the flags that should be copied during cloning.
     *
     * @return Subset of flags that should be copied.
     */
    private int getClonedFlags() {
        return flags & CLONEABLE_FLAGS_MASK;
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
     * PropertyListener implementation.
     */

    @Override
    public void propertyAdded(final ScriptObject object, final Property prop) {
        invalidateProtoGetSwitchPoint(prop);
    }

    @Override
    public void propertyDeleted(final ScriptObject object, final Property prop) {
        invalidateProtoGetSwitchPoint(prop);
    }

    @Override
    public void propertyModified(final ScriptObject object, final Property oldProp, final Property newProp) {
        invalidateProtoGetSwitchPoint(oldProp);
    }

    /*
     * Debugging and statistics.
     */

    // counters updated only in debug mode
    private static int count;
    private static int clonedCount;
    private static int sharedCount;
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
     * @return The number of maps that are shared.
     */
    public static int getSharedCount() {
        return sharedCount;
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

    /**
     * Increment the prototype set count.
     */
    private static void incrementSetProtoNewMapCount() {
        setProtoNewMapCount++;
    }
}
