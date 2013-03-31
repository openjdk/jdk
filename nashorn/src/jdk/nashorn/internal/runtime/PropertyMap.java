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

import static jdk.nashorn.internal.runtime.PropertyHashMap.EMPTY_MAP;

import java.lang.invoke.MethodHandle;
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
    /** Is this a prototype PropertyMap? */
    public static final int IS_PROTOTYPE          = 0b0000_0001;
    /** Used for non extensible PropertyMaps, negative logic as the normal case is extensible. See {@link ScriptObject#preventExtensions()} */
    public static final int NOT_EXTENSIBLE        = 0b0000_0010;
    /** This mask is used to preserve certain flags when cloning the PropertyMap. Others should not be copied */
    private static final int CLONEABLE_FLAGS_MASK = 0b0000_1111;
    /** Has a listener been added to this property map. This flag is not copied when cloning a map. See {@link PropertyListener} */
    public static final int IS_LISTENER_ADDED     = 0b0001_0000;

    /** Map status flags. */
    private int flags;

    /** Class of object referenced.*/
    private final Class<?> structure;

    /** Context associated with this {@link PropertyMap}. */
    private final Context context;

    /** Map of properties. */
    private final PropertyHashMap properties;

    /** objects proto. */
    private ScriptObject proto;

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
     * @param structure  Class the map's {@link AccessorProperty}s apply to.
     * @param context    Context associated with this {@link PropertyMap}.
     * @param properties A {@link PropertyHashMap} with initial contents.
     */
    PropertyMap(final Class<?> structure, final Context context, final PropertyHashMap properties) {
        this.structure  = structure;
        this.context    = context;
        this.properties = properties;
        this.hashCode   = computeHashCode();

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
        this.structure   = propertyMap.structure;
        this.context     = propertyMap.context;
        this.properties  = properties;
        this.flags       = propertyMap.getClonedFlags();
        this.proto       = propertyMap.proto;
        this.spillLength = propertyMap.spillLength;
        this.hashCode    = computeHashCode();

        if (Context.DEBUG) {
            count++;
            clonedCount++;
        }
    }

    /**
     * Duplicates this PropertyMap instance. This is used by nasgen generated
     * prototype and constructor classes. {@link PropertyMap} used for singletons
     * like these (and global instance) are duplicated using this method and used.
     * The original filled map referenced by static fields of prototype and
     * constructor classes are not touched. This allows multiple independent global
     * instances to be used within a single context instance.
     *
     * @return Duplicated {@link PropertyMap}.
     */
    public PropertyMap duplicate() {
        return new PropertyMap(this.structure, this.context, this.properties);
    }

    /**
     * Public property map allocator.
     *
     * @param structure  Class the map's {@link AccessorProperty}s apply to.
     * @param properties Collection of initial properties.
     *
     * @return New {@link PropertyMap}.
     */
    public static PropertyMap newMap(final Class<?> structure, final Collection<Property> properties) {
        final Context context = Context.fromClass(structure);

        // Reduce the number of empty maps in the context.
        if (structure == jdk.nashorn.internal.scripts.JO.class) {
            return context.emptyMap;
        }

        PropertyHashMap newProperties = EMPTY_MAP.immutableAdd(properties);

        return new PropertyMap(structure, context, newProperties);
    }

    /**
     * Public property map factory allocator
     *
     * @param structure  Class the map's {@link AccessorProperty}s apply to.
     *
     * @return New {@link PropertyMap}.
     */
    public static PropertyMap newMap(final Class<?> structure) {
        return newMap(structure, null);
    }

    /**
     * Return a sharable empty map.
     *
     * @param  context the context
     * @return New empty {@link PropertyMap}.
     */
    public static PropertyMap newEmptyMap(final Context context) {
        return new PropertyMap(jdk.nashorn.internal.scripts.JO.class, context, EMPTY_MAP);
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
     * @param key {@link Property} key.
     *
     * @return A shared {@link SwitchPoint} for the property.
     */
    public SwitchPoint getProtoGetSwitchPoint(final String key) {
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
     * Add a property to the map.
     *
     * @param property {@link Property} being added.
     *
     * @return New {@link PropertyMap} with {@link Property} added.
     */
    public PropertyMap newProperty(final Property property) {
        return addProperty(property);
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
    PropertyMap newPropertyBind(final AccessorProperty property, final ScriptObject bindTo) {
        return newProperty(new AccessorProperty(property, bindTo));
    }

    /**
     * Add a new accessor property to the map.
     *
     * @param key           {@link Property} key.
     * @param propertyFlags {@link Property} flags.
     * @param slot          {@link Property} slot.
     * @param getter        {@link Property} get accessor method.
     * @param setter        {@link Property} set accessor method.
     *
     * @return  New {@link PropertyMap} with {@link AccessorProperty} added.
     */
    public PropertyMap newProperty(final String key, final int propertyFlags, final int slot, final MethodHandle getter, final MethodHandle setter) {
        return newProperty(new AccessorProperty(key, propertyFlags, slot, getter, setter));
    }

    /**
     * Add a property to the map.  Cloning or using an existing map if available.
     *
     * @param property {@link Property} being added.
     *
     * @return New {@link PropertyMap} with {@link Property} added.
     */
    PropertyMap addProperty(final Property property) {
        PropertyMap newMap = checkHistory(property);

        if (newMap == null) {
            final PropertyHashMap newProperties = properties.immutableAdd(property);
            newMap = new PropertyMap(this, newProperties);
            addToHistory(property, newMap);
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
        newMap.proto = proto;

        /*
         * spillLength remains same in case (1) and (2) because of slot reuse. Only for case (3), we need
         * to add spill count of the newly added UserAccessorProperty property.
         */
        newMap.spillLength = spillLength + (sameType? 0 : newProperty.getSpillCount());
        return newMap;
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
        final PropertyMap newMap = new PropertyMap(this, this.properties);
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
        PropertyHashMap newProperties = EMPTY_MAP;

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
        PropertyHashMap newProperties = EMPTY_MAP;

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
        if (!properties.isEmpty()) {
            if (protoHistory == null) {
                protoHistory = new WeakHashMap<>();
            }

            protoHistory.put(newProto, new WeakReference<>(newMap));
        }
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
        int hash = structure.hashCode();

        if (proto != null) {
            hash ^= proto.hashCode();
        }

        for (final Property property : getProperties()) {
            hash = hash << 7 ^ hash >> 7;
            hash ^= property.hashCode();
        }

        return hash;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof PropertyMap)) {
            return false;
        }

        final PropertyMap otherMap = (PropertyMap)other;

        if (structure != otherMap.structure ||
            proto != otherMap.proto ||
            properties.size() != otherMap.properties.size()) {
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
     * Return map's {@link Context}.
     *
     * @return The {@link Context} where the map originated.
     */
    Context getContext() {
        return context;
    }

    /**
     * Check if this map is a prototype
     *
     * @return {@code true} if is prototype
     */
    public boolean isPrototype() {
        return (flags & IS_PROTOTYPE) != 0;
    }

    /**
     * Flag this map as having a prototype.
     */
    private void setIsPrototype() {
        flags |= IS_PROTOTYPE;
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
     * Get length of spill area associated with this {@link PropertyMap}.
     *
     * @return Length of spill area.
     */
    int getSpillLength() {
        return spillLength;
    }

    /**
     * Return the prototype of objects associated with this {@link PropertyMap}.
     *
     * @return Prototype object.
     */
    ScriptObject getProto() {
        return proto;
    }

    /**
     * Set the prototype of objects associated with this {@link PropertyMap}.
     *
     * @param newProto Prototype object to use.
     *
     * @return New {@link PropertyMap} with prototype changed.
     */
    PropertyMap setProto(final ScriptObject newProto) {
        final ScriptObject oldProto = this.proto;

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
        final PropertyMap newMap = new PropertyMap(this, this.properties);
        addToProtoHistory(newProto, newMap);

        newMap.proto = newProto;

        if (oldProto != null && newMap.isListenerAdded()) {
            oldProto.removePropertyListener(newMap);
        }

        if (newProto != null) {
            newProto.getMap().setIsPrototype();
        }

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

