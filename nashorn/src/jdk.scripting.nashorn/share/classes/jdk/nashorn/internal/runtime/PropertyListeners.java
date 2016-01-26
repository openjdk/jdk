/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Helper class to manage property listeners and notification.
 */
public class PropertyListeners {

    private Map<Object, WeakPropertyMapSet> listeners;

    // These counters are updated in debug mode
    private static LongAdder listenersAdded;
    private static LongAdder listenersRemoved;

    static {
        if (Context.DEBUG) {
            listenersAdded = new LongAdder();
            listenersRemoved = new LongAdder();
        }
    }

    /**
     * Copy constructor
     * @param listener listener to copy
     */
    PropertyListeners(final PropertyListeners listener) {
        if (listener != null && listener.listeners != null) {
            this.listeners = new WeakHashMap<>();
            // We need to copy the nested weak sets in order to avoid concurrent modification issues, see JDK-8146274
            synchronized (listener) {
                for (final Map.Entry<Object, WeakPropertyMapSet> entry : listener.listeners.entrySet()) {
                    this.listeners.put(entry.getKey(), new WeakPropertyMapSet(entry.getValue()));
                }
            }
        }
    }

    /**
     * Return aggregate listeners added to all PropertyListenerManagers
     * @return the listenersAdded
     */
    public static long getListenersAdded() {
        return listenersAdded.longValue();
    }

    /**
     * Return aggregate listeners removed from all PropertyListenerManagers
     * @return the listenersRemoved
     */
    public static long getListenersRemoved() {
        return listenersRemoved.longValue();
    }

    /**
     * Return number of listeners added to a ScriptObject.
     * @param obj the object
     * @return the listener count
     */
    public static int getListenerCount(final ScriptObject obj) {
        return obj.getMap().getListenerCount();
    }

    /**
     * Return the number of listeners added to this PropertyListeners instance.
     * @return the listener count;
     */
    public int getListenerCount() {
        return listeners == null ? 0 : listeners.size();
    }

    // Property listener management methods

    /**
     * Add {@code propertyMap} as property listener to {@code listeners} using key {@code key} by
     * creating and returning a new {@code PropertyListeners} instance.
     *
     * @param listeners the original property listeners instance, may be null
     * @param key the property key
     * @param propertyMap the property map
     * @return the new property map
     */
    public static PropertyListeners addListener(final PropertyListeners listeners, final String key, final PropertyMap propertyMap) {
        final PropertyListeners newListeners;
        if (listeners == null || !listeners.containsListener(key, propertyMap)) {
            newListeners = new PropertyListeners(listeners);
            newListeners.addListener(key, propertyMap);
            return newListeners;
        }
        return listeners;
    }

    /**
     * Checks whether {@code propertyMap} is registered as listener with {@code key}.
     *
     * @param key the property key
     * @param propertyMap the property map
     * @return true if property map is registered with property key
     */
    synchronized boolean containsListener(final String key, final PropertyMap propertyMap) {
        if (listeners == null) {
            return false;
        }
        final WeakPropertyMapSet set = listeners.get(key);
        return set != null && set.contains(propertyMap);
    }

    /**
     * Add a property listener to this object.
     *
     * @param propertyMap The property listener that is added.
     */
    synchronized final void addListener(final String key, final PropertyMap propertyMap) {
        if (Context.DEBUG) {
            listenersAdded.increment();
        }
        if (listeners == null) {
            listeners = new WeakHashMap<>();
        }

        WeakPropertyMapSet set = listeners.get(key);
        if (set == null) {
            set = new WeakPropertyMapSet();
            listeners.put(key, set);
        }
        if (!set.contains(propertyMap)) {
            set.add(propertyMap);
        }
    }

    /**
     * A new property is being added.
     *
     * @param prop The new Property added.
     */
    public synchronized void propertyAdded(final Property prop) {
        if (listeners != null) {
            final WeakPropertyMapSet set = listeners.get(prop.getKey());
            if (set != null) {
                for (final PropertyMap propertyMap : set.elements()) {
                    propertyMap.propertyAdded(prop, false);
                }
                listeners.remove(prop.getKey());
                if (Context.DEBUG) {
                    listenersRemoved.increment();
                }
            }
        }
    }

    /**
     * An existing property is being deleted.
     *
     * @param prop The property being deleted.
     */
    public synchronized void propertyDeleted(final Property prop) {
        if (listeners != null) {
            final WeakPropertyMapSet set = listeners.get(prop.getKey());
            if (set != null) {
                for (final PropertyMap propertyMap : set.elements()) {
                    propertyMap.propertyDeleted(prop, false);
                }
                listeners.remove(prop.getKey());
                if (Context.DEBUG) {
                    listenersRemoved.increment();
                }
            }
        }
    }

    /**
     * An existing Property is being replaced with a new Property.
     *
     * @param oldProp The old property that is being replaced.
     * @param newProp The new property that replaces the old property.
     *
     */
    public synchronized void propertyModified(final Property oldProp, final Property newProp) {
        if (listeners != null) {
            final WeakPropertyMapSet set = listeners.get(oldProp.getKey());
            if (set != null) {
                for (final PropertyMap propertyMap : set.elements()) {
                    propertyMap.propertyModified(oldProp, newProp, false);
                }
                listeners.remove(oldProp.getKey());
                if (Context.DEBUG) {
                    listenersRemoved.increment();
                }
            }
        }
    }

    /**
     * Callback for when a proto is changed
     */
    public synchronized void protoChanged() {
        if (listeners != null) {
            for (final WeakPropertyMapSet set : listeners.values()) {
                for (final PropertyMap propertyMap : set.elements()) {
                    propertyMap.protoChanged(false);
                }
            }
            listeners.clear();
        }
    }

    private static class WeakPropertyMapSet {

        private final WeakHashMap<PropertyMap, Boolean> map;

        WeakPropertyMapSet() {
            this.map = new WeakHashMap<>();
        }

        WeakPropertyMapSet(final WeakPropertyMapSet set) {
            this.map = new WeakHashMap<>(set.map);
        }

        void add(final PropertyMap propertyMap) {
            map.put(propertyMap, Boolean.TRUE);
        }

        boolean contains(final PropertyMap propertyMap) {
            return map.containsKey(propertyMap);
        }

        Set<PropertyMap> elements() {
            return map.keySet();
        }

    }
}
