/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.virtual.phases.ea;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;

/**
 * An {@link EffectList} can be used to maintain a list of {@link Effect}s and backtrack to a
 * previous state by truncating the list.
 */
public class EffectList implements Iterable<EffectList.Effect> {

    public interface Effect {
        default boolean isVisible() {
            return true;
        }

        default boolean isCfgKill() {
            return false;
        }

        void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes);
    }

    public interface SimpleEffect extends Effect {
        @Override
        default void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
            apply(graph);
        }

        void apply(StructuredGraph graph);
    }

    private static final Effect[] EMPTY_ARRAY = new Effect[0];
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final DebugContext debug;
    private Effect[] effects = EMPTY_ARRAY;
    private String[] names = EMPTY_STRING_ARRAY;
    private int size;

    public EffectList(DebugContext debug) {
        this.debug = debug;
    }

    private void enlarge(int elements) {
        int length = effects.length;
        if (size + elements > length) {
            while (size + elements > length) {
                length = Math.max(length * 2, 4);
            }
            effects = Arrays.copyOf(effects, length);
            if (debug.isLogEnabled()) {
                names = Arrays.copyOf(names, length);
            }
        }
    }

    public void add(String name, SimpleEffect effect) {
        add(name, (Effect) effect);
    }

    public void add(String name, Effect effect) {
        assert effect != null;
        enlarge(1);
        if (debug.isLogEnabled()) {
            names[size] = name;
        }
        effects[size++] = effect;
    }

    public void addAll(EffectList list) {
        enlarge(list.size);
        System.arraycopy(list.effects, 0, effects, size, list.size);
        if (debug.isLogEnabled()) {
            System.arraycopy(list.names, 0, names, size, list.size);
        }
        size += list.size;
    }

    public void insertAll(EffectList list, int position) {
        assert position >= 0 && position <= size;
        enlarge(list.size);
        System.arraycopy(effects, position, effects, position + list.size, size - position);
        System.arraycopy(list.effects, 0, effects, position, list.size);
        if (debug.isLogEnabled()) {
            System.arraycopy(names, position, names, position + list.size, size - position);
            System.arraycopy(list.names, 0, names, position, list.size);
        }
        size += list.size;
    }

    public int checkpoint() {
        return size;
    }

    public int size() {
        return size;
    }

    public void backtrack(int checkpoint) {
        assert checkpoint <= size;
        size = checkpoint;
    }

    @Override
    public Iterator<Effect> iterator() {
        return new Iterator<Effect>() {

            int index;
            final int listSize = EffectList.this.size;

            @Override
            public boolean hasNext() {
                return index < listSize;
            }

            @Override
            public Effect next() {
                return effects[index++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public Effect get(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return effects[index];
    }

    public void clear() {
        size = 0;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes, boolean cfgKills) {
        boolean message = false;
        for (int i = 0; i < size(); i++) {
            Effect effect = effects[i];
            if (effect.isCfgKill() == cfgKills) {
                if (!message) {
                    message = true;
                    debug.log(cfgKills ? " ==== cfg kill effects" : " ==== effects");
                }
                try {
                    effect.apply(graph, obsoleteNodes);
                } catch (Throwable t) {
                    StringBuilder str = new StringBuilder();
                    toString(str, i);
                    throw new GraalError(t).addContext("effect", str);
                }
                if (effect.isVisible() && debug.isLogEnabled()) {
                    StringBuilder str = new StringBuilder();
                    toString(str, i);
                    debug.log("    %s", str);
                }
            }
        }
    }

    private void toString(StringBuilder str, int i) {
        Effect effect = effects[i];
        str.append(getName(i)).append(" [");
        boolean first = true;
        for (Field field : effect.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object object = field.get(effect);
                if (object == this) {
                    // Inner classes could capture the EffectList itself.
                    continue;
                }
                str.append(first ? "" : ", ").append(field.getName()).append("=").append(format(object));
                first = false;
            } catch (SecurityException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        str.append(']');
    }

    private static String format(Object object) {
        if (object != null && Object[].class.isAssignableFrom(object.getClass())) {
            return Arrays.toString((Object[]) object);
        }
        return "" + object;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            Effect effect = get(i);
            if (effect.isVisible()) {
                toString(str, i);
                str.append('\n');
            }
        }
        return str.toString();
    }

    private String getName(int i) {
        if (debug.isLogEnabled()) {
            return names[i];
        } else {
            return "";
        }
    }
}
