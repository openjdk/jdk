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

import java.util.List;

/**
 * {@link StructuralName}s represent things like method and class names, and can be added to the local
 * scope with {@link Template#addStructuralName}, and accessed with {@link Template#structuralNames}, from where
 * count, list or even sample random {@link StructuralName}s. Every {@link StructuralName} has a {@link StructuralName.Type},
 * so that sampling can be restricted to these types.
 *
 * <p>
 * For field and variable names and alike, there are the analogous {@link DataName}s.
 *
 * @param name The {@link String} name used in code.
 * @param type The type of the {@link StructuralName}.
 * @param weight The weight of the {@link StructuralName}, it corresponds to the probability of choosing this
 *               {@link StructuralName} when sampling later on.
 */
public record StructuralName(String name, StructuralName.Type type, int weight) implements Name {

    /**
     * Creates a new {@link StructuralName}.
     */
    public StructuralName {
    }

    /**
     * The interface for the type of a {@link StructuralName}.
     */
    public interface Type extends Name.Type {
        /**
         * The name of the type, that can be used in code.
         *
         * @return The {@link String} representation of the type, that can be used in code.
         */
        String name();

        /**
         * Defines the subtype relationship with other types, which is used to filter {@link StructuralName}s
         * in {@link FilteredSet#exactOf}, {@link FilteredSet#subtypeOf}, and {@link FilteredSet#supertypeOf}.
         *
         * @param other The other type, where we check if it is the supertype of {@code 'this'}.
         * @return If {@code 'this'} is a subtype of {@code 'other'}.
         */
        boolean isSubtypeOf(StructuralName.Type other);
    }

    /**
     * The {@link FilteredSet} represents a filtered set of {@link StructuralName}s in the current scope.
     * It can be obtained with {@link Template#structuralNames}. It can be used to count the
     * available {@link StructuralName}s, or sample a random {@link StructuralName} according to the
     * weights of the {@link StructuralName}s in the filtered set.
     * Note: The {@link FilteredSet} is only a filtered view on the set of {@link StructuralName}s,
     * and may return different results in different contexts.
     */
    public static final class FilteredSet {
        private final StructuralName.Type subtype;
        private final StructuralName.Type supertype;

        FilteredSet(StructuralName.Type subtype, StructuralName.Type supertype) {
            this.subtype = subtype;
            this.supertype = supertype;
        }

        FilteredSet() {
            this(null, null);
        }

        NameSet.Predicate predicate() {
            if (subtype == null && supertype == null) {
                throw new UnsupportedOperationException("Must first call 'subtypeOf', 'supertypeOf', or 'exactOf'.");
            }
            return (Name name) -> {
                if (!(name instanceof StructuralName structuralName)) { return false; }
                if (subtype != null && !structuralName.type().isSubtypeOf(subtype)) { return false; }
                if (supertype != null && !supertype.isSubtypeOf(structuralName.type())) { return false; }
                return true;
            };
        }

        /**
         * Create a {@link FilteredSet}, where all {@link StructuralName}s must be subtypes of {@code type}.
         *
         * @param type The type of which all {@link StructuralName}s must be subtypes of.
         * @return The updated filtered set.
         * @throws UnsupportedOperationException If this {@link FilteredSet} was already filtered with
         *                                       {@link #subtypeOf} or {@link #exactOf}.
         */
        public FilteredSet subtypeOf(StructuralName.Type type) {
            if (subtype != null) {
                throw new UnsupportedOperationException("Cannot constrain to subtype " + type + ", is already constrained: " + subtype);
            }
            return new FilteredSet(type, supertype);
        }

        /**
         * Create a {@link FilteredSet}, where all {@link StructuralName}s must be supertypes of {@code type}.
         *
         * @param type The type of which all {@link StructuralName}s must be supertype of.
         * @return The updated filtered set.
         * @throws UnsupportedOperationException If this {@link FilteredSet} was already filtered with
         *                                       {@link #supertypeOf} or {@link #exactOf}.
         */
        public FilteredSet supertypeOf(StructuralName.Type type) {
            if (supertype != null) {
                throw new UnsupportedOperationException("Cannot constrain to supertype " + type + ", is already constrained: " + supertype);
            }
            return new FilteredSet(subtype, type);
        }

        /**
         * Create a {@link FilteredSet}, where all {@link StructuralName}s must be of exact {@code type},
         * hence it must be both subtype and supertype thereof.
         *
         * @param type The type of which all {@link StructuralName}s must be.
         * @return The updated filtered set.
         * @throws UnsupportedOperationException If this {@link FilteredSet} was already filtered with
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public FilteredSet exactOf(StructuralName.Type type) {
            return subtypeOf(type).supertypeOf(type);
        }

        /**
         * Samples a random {@link StructuralName} from the filtered set, according to the weights
         * of the contained {@link StructuralName}s.
         *
         * @return The sampled {@link StructuralName}.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         * @throws RendererException If the set was empty.
         */
        public StructuralName sample() {
            StructuralName n = (StructuralName)Renderer.getCurrent().sampleName(predicate());
            if (n == null) {
                String msg1 = (subtype == null) ? "" : " subtypeOf(" + subtype + ")";
                String msg2 = (supertype == null) ? "" : " supertypeOf(" + supertype + ")";
                throw new RendererException("No variable:" + msg1 + msg2 + ".");
            }
            return n;
        }

        /**
         * Counts the number of {@link StructuralName}s in the filtered set.
         *
         * @return The number of {@link StructuralName}s in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public int count() {
            return Renderer.getCurrent().countNames(predicate());
        }

        /**
         * Checks if there are any {@link StructuralName}s in the filtered set.
         *
         * @return Returns {@code true} iff there is at least one {@link StructuralName} in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public boolean hasAny() {
            return Renderer.getCurrent().hasAnyNames(predicate());
        }

        /**
         * Collects all {@link StructuralName}s in the filtered set.
         *
         * @return A {@link List} of all {@link StructuralName}s in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public List<StructuralName> toList() {
            List<Name> list = Renderer.getCurrent().listNames(predicate());
            return list.stream().map(n -> (StructuralName)n).toList();
        }
    }
}
