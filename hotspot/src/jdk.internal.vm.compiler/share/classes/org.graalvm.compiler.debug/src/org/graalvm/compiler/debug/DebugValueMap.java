/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in a tree of values.
 */
public class DebugValueMap {

    /**
     * The top level maps for all threads.
     */
    private static final List<DebugValueMap> topLevelMaps = new ArrayList<>();

    private long[] values;
    private List<DebugValueMap> children;
    private String name;

    public DebugValueMap(String name) {
        this.name = name;
    }

    public void setCurrentValue(int index, long l) {
        ensureSize(index);
        values[index] = l;
    }

    public long getCurrentValue(int index) {
        ensureSize(index);
        return values[index];
    }

    public void clearChildren() {
        if (children != null) {
            children.clear();
        }
    }

    public void reset() {
        if (values != null) {
            Arrays.fill(values, 0L);
        }
        if (children != null) {
            for (DebugValueMap child : children) {
                child.reset();
            }
        }
    }

    private void ensureSize(int index) {
        if (values == null) {
            values = new long[index + 1];
        }
        if (values.length <= index) {
            values = Arrays.copyOf(values, index + 1);
        }
    }

    private int capacity() {
        return (values == null) ? 0 : values.length;
    }

    public void addChild(DebugValueMap map) {
        if (children == null) {
            children = new ArrayList<>(4);
        }
        children.add(map);
    }

    public List<DebugValueMap> getChildren() {
        if (children == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(children);
        }
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return "DebugValueMap<" + getName() + ">";
    }

    public static synchronized void registerTopLevel(DebugValueMap map) {
        topLevelMaps.add(map);
    }

    public static synchronized List<DebugValueMap> getTopLevelMaps() {
        return topLevelMaps;
    }

    /**
     * The top level map for the current thread.
     */
    private static final ThreadLocal<DebugValueMap> topLevelMap = new ThreadLocal<>();

    static DebugValueMap getTopLevelMap() {
        DebugValueMap map = topLevelMap.get();
        if (map == null) {
            map = new DebugValueMap(Thread.currentThread().getName());
            topLevelMap.set(map);
            registerTopLevel(map);
        }
        return map;
    }

    public void normalize() {
        if (hasChildren()) {
            Map<String, DebugValueMap> occurred = new HashMap<>();
            for (DebugValueMap map : children) {
                String mapName = map.getName();
                if (!occurred.containsKey(mapName)) {
                    occurred.put(mapName, map);
                    map.normalize();
                } else {
                    occurred.get(mapName).mergeWith(map);
                    occurred.get(mapName).normalize();
                }
            }

            if (occurred.values().size() < children.size()) {
                // At least one duplicate was found.
                children.clear();
                for (DebugValueMap map : occurred.values()) {
                    addChild(map);
                    map.normalize();
                }
            }
        }
    }

    private void mergeWith(DebugValueMap map) {
        if (map.hasChildren()) {
            if (hasChildren()) {
                children.addAll(map.children);
            } else {
                children = map.children;
            }
            map.children = null;
        }

        int size = Math.max(this.capacity(), map.capacity());
        ensureSize(size);
        for (int i = 0; i < size; ++i) {
            long curValue = getCurrentValue(i);
            long otherValue = map.getCurrentValue(i);
            setCurrentValue(i, curValue + otherValue);
        }
    }

    public void group() {
        if (this.hasChildren()) {
            List<DebugValueMap> oldChildren = new ArrayList<>(this.children);
            this.children.clear();
            for (DebugValueMap map : oldChildren) {
                mergeWith(map);
            }
        }
    }
}
