/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.SwitchPoint;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Helper class for tracking and invalidation of switchpoints for inherited properties.
 */
public class PropertySwitchPoints {

    private final Map<Object, WeakSwitchPointSet> switchPointMap = new HashMap<>();

    private final static SwitchPoint[] EMPTY_SWITCHPOINT_ARRAY = new SwitchPoint[0];

    // These counters are updated in debug mode
    private static LongAdder switchPointsAdded;
    private static LongAdder switchPointsInvalidated;

    static {
        if (Context.DEBUG) {
            switchPointsAdded = new LongAdder();
            switchPointsInvalidated = new LongAdder();
        }
    }

    /**
     * Copy constructor
     *
     * @param switchPoints Proto switchpoints to copy
     */
    private PropertySwitchPoints(final PropertySwitchPoints switchPoints) {
        if (switchPoints != null) {
            // We need to copy the nested weak sets in order to avoid concurrent modification issues, see JDK-8146274
            synchronized (switchPoints) {
                for (final Map.Entry<Object, WeakSwitchPointSet> entry : switchPoints.switchPointMap.entrySet()) {
                    this.switchPointMap.put(entry.getKey(), new WeakSwitchPointSet(entry.getValue()));
                }
            }
        }
    }

    /**
     * Return aggregate switchpoints added to all ProtoSwitchPoints
     * @return the number of switchpoints added
     */
    public static long getSwitchPointsAdded() {
        return switchPointsAdded.longValue();
    }

    /**
     * Return aggregate switchPointMap invalidated in all ProtoSwitchPoints
     * @return the number of switchpoints invalidated
     */
    public static long getSwitchPointsInvalidated() {
        return switchPointsInvalidated.longValue();
    }

    /**
     * Return number of property switchPoints added to a ScriptObject.
     * @param obj the object
     * @return the switchpoint count
     */
    public static int getSwitchPointCount(final ScriptObject obj) {
        return obj.getMap().getSwitchPointCount();
    }

    /**
     * Return the number of switchpoints added to this ProtoSwitchPoints instance.
     * @return the switchpoint count;
     */
    int getSwitchPointCount() {
        return switchPointMap.size();
    }

    /**
     * Add {@code switchPoint} to the switchpoints for for property {@code key}, creating
     * and returning a new {@code ProtoSwitchPoints} instance if the switchpoint was not already contained
     *
     * @param oldSwitchPoints the original PropertySwitchPoints instance. May be null
     * @param key the property key
     * @param switchPoint the switchpoint to be added
     * @return the new PropertySwitchPoints instance, or this instance if switchpoint was already contained
     */
    static PropertySwitchPoints addSwitchPoint(final PropertySwitchPoints oldSwitchPoints, final String key, final SwitchPoint switchPoint) {
        if (oldSwitchPoints == null || !oldSwitchPoints.contains(key, switchPoint)) {
            final PropertySwitchPoints newSwitchPoints = new PropertySwitchPoints(oldSwitchPoints);
            newSwitchPoints.add(key, switchPoint);
            return newSwitchPoints;
        }
        return oldSwitchPoints;
    }

    /**
     * Checks whether {@code switchPoint} is contained in {@code key}'s set.
     *
     * @param key the property key
     * @param switchPoint the switchPoint
     * @return true if switchpoint is already contained for key
     */
    private synchronized boolean contains(final String key, final SwitchPoint switchPoint) {
        final WeakSwitchPointSet set = this.switchPointMap.get(key);
        return set != null && set.contains(switchPoint);
    }

    private synchronized void add(final String key, final SwitchPoint switchPoint) {
        if (Context.DEBUG) {
            switchPointsAdded.increment();
        }

        WeakSwitchPointSet set = this.switchPointMap.get(key);
        if (set == null) {
            set = new WeakSwitchPointSet();
            this.switchPointMap.put(key, set);
        }

        set.add(switchPoint);
    }

    Set<SwitchPoint> getSwitchPoints(final Object key) {
        WeakSwitchPointSet switchPointSet = switchPointMap.get(key);
        if (switchPointSet != null) {
            return switchPointSet.elements();
        }

        return Collections.emptySet();
    }

    /**
     * Invalidate all switchpoints for the given property. This is called when that
     * property is created, deleted, or modified in a script object.
     *
     * @param prop The property to invalidate.
     */
    synchronized void invalidateProperty(final Property prop) {
        final WeakSwitchPointSet set = switchPointMap.get(prop.getKey());
        if (set != null) {
            if (Context.DEBUG) {
                switchPointsInvalidated.add(set.size());
            }
            final SwitchPoint[] switchPoints = set.elements().toArray(EMPTY_SWITCHPOINT_ARRAY);
            SwitchPoint.invalidateAll(switchPoints);
            this.switchPointMap.remove(prop.getKey());
        }
    }


    /**
     * Invalidate all switchpoints except those defined in {@code map}. This is called
     * when the prototype of a script object is changed.
     *
     * @param map map of properties to exclude from invalidation
     */
    synchronized void invalidateInheritedProperties(final PropertyMap map) {
        for (final Map.Entry<Object, WeakSwitchPointSet> entry : switchPointMap.entrySet()) {
            if (map.findProperty(entry.getKey()) != null) {
                continue;
            }
            if (Context.DEBUG) {
                switchPointsInvalidated.add(entry.getValue().size());
            }
            final SwitchPoint[] switchPoints = entry.getValue().elements().toArray(EMPTY_SWITCHPOINT_ARRAY);
            SwitchPoint.invalidateAll(switchPoints);
        }
        switchPointMap.clear();
    }

    private static class WeakSwitchPointSet {

        private final WeakHashMap<SwitchPoint, Void> map;

        WeakSwitchPointSet() {
            map = new WeakHashMap<>();
        }

        WeakSwitchPointSet(final WeakSwitchPointSet set) {
            map = new WeakHashMap<>(set.map);
        }

        void add(final SwitchPoint switchPoint) {
            map.put(switchPoint, null);
        }

        boolean contains(final SwitchPoint switchPoint) {
            return map.containsKey(switchPoint);
        }

        Set<SwitchPoint> elements() {
            return map.keySet();
        }

        int size() {
            return map.size();
        }

    }
}
