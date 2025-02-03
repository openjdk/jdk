/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework;

import java.util.HashMap;
import java.util.Map;

class Frame {
    private final StringBuilder builder = new StringBuilder();
    private final Map<Hook, Integer> hookInsertionIndex = new HashMap<>();
    private final Map<String, String> variableNames = new HashMap<>();
    private final Map<String, String> context = new HashMap<>();

    void addString(String s) {
        builder.append(s);
    }

    void addHook(Hook hook) {
        hookInsertionIndex.put(hook, builder.length());
    }

    boolean hasHook(Hook hook) {
        return hookInsertionIndex.containsKey(hook);
    }

    void insertIntoHook(Hook hook, String s) {
        int index = hookInsertionIndex.get(hook);
        builder.insert(index, s);
        hookInsertionIndex.put(hook, index + s.length());
    }

    public void addContext(String key, String value) {
        context.put(key, value);
    }

    public String getContext(String key) {
        if (context.containsKey(key)) {
            return context.get(key);
        }
        throw new RendererException("Tried to interpolate field " + key + " which does not exist.");
    }

    String variableName(String name) {
        return variableNames.computeIfAbsent(name, s -> name + Renderer.variableId++);
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
