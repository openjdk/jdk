/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.generators;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Mixed results between different generators with configurable weights.
 */
class MixedGenerator<G extends Generator<T>, T> extends BoundGenerator<T> {
    private final TreeMap<Integer, G> generators = new TreeMap<>();
    private final int totalWeight;

    /**
     * Creates a new {@link MixedGenerator}, which samples from a list of generators at random,
     * according to specified weights.
     */
    MixedGenerator(Generators g, List<G> generators, List<Integer> weights) {
        super(g);
        if (weights.size() != generators.size()) {
            throw new IllegalArgumentException("weights and generators must have the same size");
        }
        int acc = 0;
        for (int i = 0; i < generators.size(); i++) {
            int weight = weights.get(i);
            if (weight <= 0) {
                throw new IllegalArgumentException("weights must be positive");
            }
            acc += weight;
            this.generators.put(acc, generators.get(i));
        }
        this.totalWeight = acc;
    }

    /**
     * Creates a new mixed generator by mapping each generator of the old generator to a new value or removing it.
     * @param other The generator to copy from.
     * @param generatorMapper A function that is called for each subgenerator in the old generator. Either return a
     *                        generator that takes the role of the old generator (might be the same) or null to remove
     *                        the generator completely. In this case, the weights of the other generators stay the same.
     */
    MixedGenerator(MixedGenerator<G, T> other, Function<G, G> generatorMapper) {
        super(other.g);
        // We could map and create new lists and delegate to the other constructor but that would allocate
        // two additional lists, so in the interest of memory efficiency we construct the new TreeMap ourselves.
        int acc = 0;
        int prevKey = 0;
        // entrySet: "The set's iterator returns the entries in ascending key order." (documentation)
        // This means we iterate over the generators exactly in the order they were inserted as we insert with ascending
        // keys (due to summing positive numbers).
        for (var entry : other.generators.entrySet()) {
            var gen = generatorMapper.apply(entry.getValue());
            if (gen != null) {
                // entry.getKey() is the sum of all generator weights up to this one.
                // We compute this generator's weight by taking the difference to the previous key
                int weight = entry.getKey() - prevKey;
                acc += weight;
                this.generators.put(acc, gen);
            }
            prevKey = entry.getKey();
        }
        if (this.generators.isEmpty()) {
            throw new EmptyGeneratorException();
        }
        this.totalWeight = acc;
    }

    @Override
    public T next() {
        int r = g.random.nextInt(0, totalWeight);
        return generators.higherEntry(r).getValue().next();
    }
}
