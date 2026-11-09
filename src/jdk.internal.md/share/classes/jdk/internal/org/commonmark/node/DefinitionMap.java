/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.node;

import jdk.internal.org.commonmark.internal.util.Escaping;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A map that can be used to store and look up reference definitions by a label. The labels are case-insensitive and
 * normalized, the same way as for {@link LinkReferenceDefinition} nodes.
 *
 * @param <D> the type of value
 */
public class DefinitionMap<D> {

    private final Class<D> type;
    // LinkedHashMap for determinism and to preserve document order
    private final Map<String, D> definitions = new LinkedHashMap<>();

    public DefinitionMap(Class<D> type) {
        this.type = type;
    }

    public Class<D> getType() {
        return type;
    }

    public void addAll(DefinitionMap<D> that) {
        for (var entry : that.definitions.entrySet()) {
            // Note that keys are already normalized, so we can add them directly
            definitions.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Store a new definition unless one is already in the map. If there is no definition for that label yet, return null.
     * Otherwise, return the existing definition.
     * <p>
     * The label is normalized by the definition map before storing.
     */
    public D putIfAbsent(String label, D definition) {
        String normalizedLabel = Escaping.normalizeLabelContent(label);

        // spec: When there are multiple matching link reference definitions, the first is used
        return definitions.putIfAbsent(normalizedLabel, definition);
    }

    /**
     * Look up a definition by label. The label is normalized by the definition map before lookup.
     *
     * @return the value or null
     */
    public D get(String label) {
        String normalizedLabel = Escaping.normalizeLabelContent(label);
        return definitions.get(normalizedLabel);
    }

    public Set<String> keySet() {
        return definitions.keySet();
    }

    public Collection<D> values() {
        return definitions.values();
    }
}
