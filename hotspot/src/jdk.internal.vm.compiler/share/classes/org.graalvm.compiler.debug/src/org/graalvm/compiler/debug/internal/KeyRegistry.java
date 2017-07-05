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
package org.graalvm.compiler.debug.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for allocating a globally unique integer id to each {@link DebugValue}.
 */
public class KeyRegistry {

    private static final Map<String, Integer> keyMap = new HashMap<>();
    private static final List<DebugValue> debugValues = new ArrayList<>();

    /**
     * Ensures a given debug value is registered.
     *
     * @return the globally unique id for {@code value}
     */
    public static synchronized int register(DebugValue value) {
        String name = value.getName();
        if (!keyMap.containsKey(name)) {
            keyMap.put(name, debugValues.size());
            debugValues.add(value);
        }
        return keyMap.get(name);
    }

    /**
     * Gets a immutable view of the registered debug values.
     *
     * @return a list where {@code get(i).getIndex() == i}
     */
    public static synchronized List<DebugValue> getDebugValues() {
        return Collections.unmodifiableList(debugValues);
    }
}
