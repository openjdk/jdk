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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.SwitchPoint;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import jdk.nashorn.internal.scripts.JO;

/**
 * Map of object properties. The PropertyMap is the "template" for JavaScript object
 * layouts. It contains a map with prototype names as keys and {@link Property} instances
 * as values. A PropertyMap is typically passed to the {@link ScriptObject} constructor
 * to form the seed map for the ScriptObject.
 * <p>
 * All property maps are immutable. If a property is added, modified or removed, the mutator
 * will return a new map.
 */
public final class PropertyMap implements Iterable<Object>, Serializable {
    /** Used for non extensible PropertyMaps, negative logic as the normal case is extensible. See {@link ScriptObject#preventExtensions()} */
    public static final int NOT_EXTENSIBLE        = 0b0000_0001;
    /** Does this map contain valid array keys? */
    public static final int CONTAINS_ARRAY_KEYS   = 0b0000_0010;

    /** Map status flags. */
    private int flags;

    /** Map of properties. */
    private transient PropertyHashMap properties;

    /** Number of fields in use. */
    private int fieldCount;

    /** Number of fields available. */
    private final int fieldMaximum;

    /** Length of spill in use. */
    private int spillLength;

    /** Structure class name */
    private String className;

    /** {@link SwitchPoint}s for gets on inherited properties. */
    private transient HashMap<String, SwitchPoint> protoGetSwitches;

    /** History of maps, used to limit map duplication. */
    private transient WeakHashMap<Property, SoftReference<PropertyMap>> history;

    /** History of prototypes, used to limit map duplication. */
    private transient WeakHashMap<ScriptObject, SoftReference<PropertyMap>> protoHistory;

    /** property listeners */
    private transient PropertyListeners listeners;

    private transient BitSet freeSlots;

    private static final long serialVersionUID = -7041836752008732533L;

    /**
     * Constructor.
     *
     * @param properties   A {@link PropertyHashMap} with initial contents.
     * @param fieldCount   Number of fields in use.
     * @param fieldMaximum Number of fields available.
     * @param spillLength  Number of spill slots used.
     * @param containsArrayKeys True if properties contain numeric keys
     */
    private PropertyMap(final PropertyHashMap properties, final String className, final int fieldCount,
                        final int fieldMaximum, final int spillLength, final boolean containsArrayKeys) {
        this.properties   = properties;
        this.className    = className;
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
        this.freeSlots    = propertyMap.freeSlots;

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

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(properties.getProperties());
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        final Property[] props = (Property[]) in.readObject();
        this.properties = EMPTY_HASHMAP.immutableAdd(props);

        assert className != null;
        final Class<?> structure = Context.forStructureClass(className);
        for (final Property prop : props) {
            prop.initMethodHandles(structure);
        }
    }

    /**
     * Public property map allocator.
     *
     * <p>It is the caller's responsibility to make sure that {@code properties} does not contain
     * properties with keys that are valid array indices.</p>
     *
     * @param properties   Collection of initial properties.
     * @param className    class name
     * @param fieldCount   Number of fields in use.
     * @param fieldMaximum Number of fields available.
     * @param spillLength  Number of used spill slots.
     * @return New {@link PropertyMap}.
     */
    public static PropertyMap newMap(final Collection<Property> properties, final String className, final int fieldCount, final int fieldMaximum,  final int spillLength) {
        final PropertyHashMap newProperties = EMPTY_HASHMAP.immutableAdd(properties);
        return new PropertyMap(newProperties, className, fieldCount, fieldMaximum, spillLength, false);
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
        return properties == null || properties.isEmpty()? newMap() : newMap(properties, JO.class.getName(), 0, 0, 0);
    }

    /**
     * Return a sharable empty map.
     *
     * @return New empty {@link PropertyMap}.
     */
    public static PropertyMap newMap() {
        return new PropertyMap(EMPTY_HASHMAP, JO.class.getName(), 0, 0, 0, false);
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

    // Get a logical slot index for a property, with spill slot 0 starting at fieldMaximum.
    private int logicalSlotIndex(final Property property) {
        final int slot = property.getSlot();
        if (slot < 0) {
            return -1;
        }
        return property.isSpill() ? slot + fieldMaximum : slot;
    }

    // Update boundaries and flags after a property has been added
    private void updateFlagsAndBoundaries(final Property newProperty) {
        if(newProperty.isSpill()) {
            spillLength = Math.max(spillLength, newProperty.getSlot() + 1);
        } else {
            fieldCount = Math.max(fieldCount, newProperty.getSlot() + 1);
        }
        if (isValidArrayIndex(getArrayIndex(newProperty.getKey()))) {
            setContainsArrayKeys();
        }
    }

    // Update the free slots bitmap for a property that has been deleted and/or added.
    private void updateFreeSlots(final Property oldProperty, final Property newProperty) {
        // Free slots bitset is possibly shared with parent map, so we must clone it before making modifications.
        boolean freeSlotsCloned = false;
        if (oldProperty != null) {
            final int slotIndex = logicalSlotIndex(oldProperty);
            if (slotIndex >= 0) {
                final BitSet newFreeSlots = freeSlots == null ? new BitSet() : (BitSet)freeSlots.clone();
                assert !newFreeSlots.get(slotIndex);
                newFreeSlots.set(slotIndex);
                freeSlots = newFreeSlots;
                freeSlotsCloned = true;
            }
        }
        if (freeSlots != null && newProperty != null) {
            final int slotIndex = logicalSlotIndex(newProperty);
            if (slotIndex > -1 && freeSlots.get(slotIndex)) {
                final BitSet newFreeSlots = freeSlotsCloned ? freeSlots : ((BitSet)freeSlots.clone());
                newFreeSlots.clear(slotIndex);
                freeSlots = newFreeSlots.isEmpty() ? null : newFreeSlots;
            }
        }
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
        newMap.updateFlagsAndBoundaries(property);
        newMap.updateFreeSlots(null, property);

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
            newMap.updateFlagsAndBoundaries(property);
            newMap.updateFreeSlots(null, property);
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
            final boolean isSpill = property.isSpill();
            final int slot = property.getSlot();
            // If deleted property was last field or spill slot we can make it reusable by reducing field/slot count.
            // Otherwise mark it as free in free slots bitset.
            if (isSpill && slot >= 0 && slot == spillLength - 1) {
                newMap = new PropertyMap(newProperties, className, fieldCount, fieldMaximum, spillLength - 1, containsArrayKeys());
                newMap.freeSlots = freeSlots;
            } else if (!isSpill && slot >= 0 && slot == fieldCount - 1) {
                newMap = new PropertyMap(newProperties, className, fieldCount - 1, fieldMaximum, spillLength, containsArrayKeys());
                newMap.freeSlots = freeSlots;
            } else {
                newMap = new PropertyMap(this, newProperties);
                newMap.updateFreeSlots(property, null);
            }
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
        final PropertyHashMap newProperties = properties.immutableReplace(oldProperty, newProperty);
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

        final boolean sameType = oldProperty.getClass() == newProperty.getClass();
        assert sameType ||
                oldProperty instanceof AccessorProperty &&
                newProperty instanceof UserAccessorProperty :
            "arbitrary replaceProperty attempted " + sameType + " oldProperty=" + oldProperty.getClass() + " newProperty=" + newProperty.getClass() + " [" + oldProperty.getLocalType() + " => " + newProperty.getLocalType() + "]";

        newMap.flags = flags;

        /*
         * spillLength remains same in case (1) and (2) because of slot reuse. Only for case (3), we need
         * to add spill count of the newly added UserAccessorProperty property.
         */
        if (!sameType) {
            newMap.spillLength = Math.max(spillLength, newProperty.getSlot() + 1);
            newMap.updateFreeSlots(oldProperty, newProperty);
        }
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
        return new UserAccessorProperty(key, propertyFlags, getFreeSpillSlot());
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
            // This method is only safe to use with non-slotted, native getter/setter properties
            assert property.getSlot() == -1;
            assert !(isValidArrayIndex(getArrayIndex(property.getKey())));
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

        for (final Property oldProperty : properties.getProperties()) {
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
     * @param proto New prototype object.
     *
     * @return Existing {@link PropertyMap} or {@code null} if not found.
     */
    private PropertyMap checkProtoHistory(final ScriptObject proto) {
        final PropertyMap cachedMap;
        if (protoHistory != null) {
            final SoftReference<PropertyMap> weakMap = protoHistory.get(proto);
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
        if (protoHistory == null) {
            protoHistory = new WeakHashMap<>();
        }

        protoHistory.put(newProto, new SoftReference<>(newMap));
    }

    /**
     * Track the modification of the map.
     *
     * @param property Mapping property.
     * @param newMap   Modified {@link PropertyMap}.
     */
    private void addToHistory(final Property property, final PropertyMap newMap) {
        if (history == null) {
            history = new WeakHashMap<>();
        }

        history.put(property, new SoftReference<>(newMap));
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
            final SoftReference<PropertyMap> ref = history.get(property);
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

    /**
     * Returns true if the two maps have identical properties in the same order, but allows the properties to differ in
     * their types. This method is mostly useful for tests.
     * @param otherMap the other map
     * @return true if this map has identical properties in the same order as the other map, allowing the properties to
     * differ in type.
     */
    public boolean equalsWithoutType(final PropertyMap otherMap) {
        if (properties.size() != otherMap.properties.size()) {
            return false;
        }

        final Iterator<Property> iter      = properties.values().iterator();
        final Iterator<Property> otherIter = otherMap.properties.values().iterator();

        while (iter.hasNext() && otherIter.hasNext()) {
            if (!iter.next().equalsWithoutType(otherIter.next())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(Debug.id(this));
        sb.append(" = {\n");

        for (final Property property : getProperties()) {
            sb.append('\t');
            sb.append(property);
            sb.append('\n');
        }

        sb.append('}');

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
     * Return a free field slot for this map, or {@code -1} if none is available.
     *
     * @return free field slot or -1
     */
    int getFreeFieldSlot() {
        if (freeSlots != null) {
            final int freeSlot = freeSlots.nextSetBit(0);
            if (freeSlot > -1 && freeSlot < fieldMaximum) {
                return freeSlot;
            }
        }
        if (fieldCount < fieldMaximum) {
            return fieldCount;
        }
        return -1;
    }

    /**
     * Get a free spill slot for this map.
     *
     * @return free spill slot
     */
    int getFreeSpillSlot() {
        if (freeSlots != null) {
            final int freeSlot = freeSlots.nextSetBit(fieldMaximum);
            if (freeSlot > -1) {
                return freeSlot - fieldMaximum;
            }
        }
        return spillLength;
    }

    /**
     * Return a property map with the same layout that is associated with the new prototype object.
     *
     * @param newProto New prototype object to replace oldProto.
     * @return New {@link PropertyMap} with prototype changed.
     */
    public PropertyMap changeProto(final ScriptObject newProto) {

        final PropertyMap nextMap = checkProtoHistory(newProto);
        if (nextMap != null) {
            return nextMap;
        }

        if (Context.DEBUG) {
            setProtoNewMapCount++;
        }

        final PropertyMap newMap = new PropertyMap(this);
        addToProtoHistory(newProto, newMap);

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
            throw new UnsupportedOperationException("remove");
        }
    }

    /*
     * Debugging and statistics.
     */

    /**
     * Debug helper function that returns the diff of two property maps, only
     * displaying the information that is different and in which map it exists
     * compared to the other map. Can be used to e.g. debug map guards and
     * investigate why they fail, causing relink
     *
     * @param map0 the first property map
     * @param map1 the second property map
     *
     * @return property map diff as string
     */
    public static String diff(final PropertyMap map0, final PropertyMap map1) {
        final StringBuilder sb = new StringBuilder();

        if (map0 != map1) {
           sb.append(">>> START: Map diff");
           boolean found = false;

           for (final Property p : map0.getProperties()) {
               final Property p2 = map1.findProperty(p.getKey());
               if (p2 == null) {
                   sb.append("FIRST ONLY : [" + p + "]");
                   found = true;
               } else if (p2 != p) {
                   sb.append("DIFFERENT  : [" + p + "] != [" + p2 + "]");
                   found = true;
               }
           }

           for (final Property p2 : map1.getProperties()) {
               final Property p1 = map0.findProperty(p2.getKey());
               if (p1 == null) {
                   sb.append("SECOND ONLY: [" + p2 + "]");
                   found = true;
               }
           }

           //assert found;

           if (!found) {
                sb.append(map0).
                    append("!=").
                    append(map1);
           }

           sb.append("<<< END: Map diff\n");
        }

        return sb.toString();
    }

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
}
