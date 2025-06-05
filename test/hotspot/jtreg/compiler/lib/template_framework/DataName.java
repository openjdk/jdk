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
 * {@link DataName}s represent things like fields and local variables, and can be added to the local
 * scope with {@link Template#addDataName}, and accessed with {@link Template#dataNames}, to
 * count, list or even sample random {@link DataName}s. Every {@link DataName} has a {@link DataName.Type},
 * so that sampling can be restricted to these types.
 *
 * <p>
 * For method and class names and alike, there are the analogous {@link StructuralName}s.
 *
 * @param name The {@link String} name used in code.
 * @param type The type of the {@link DataName}.
 * @param mutable Defines if the {@link DataName} is considered mutable or immutable.
 * @param weight The weight of the {@link DataName}, it corresponds to the probability of choosing this
 *               {@link DataName} when sampling later on.
 */
public record DataName(String name, DataName.Type type, boolean mutable, int weight) implements Name {

    /**
     * {@link Mutability} defines the possible states of {@link DataName}s, or the
     * desired state when filtering.
     */
    public enum Mutability {
        /**
         * Used for mutable fields and variables, i.e. writing is allowed.
         */
        MUTABLE,
        /**
         * Used for immutable fields and variables, i.e. writing is not allowed,
         * for example because the field or variable is final.
         */
        IMMUTABLE,
        /**
         * When filtering, we sometimes want to indicate that we accept
         * mutable and immutable fields and variables, for example when
         * we are only reading, the mutability state does not matter.
         */
        MUTABLE_OR_IMMUTABLE
    }

    /**
     * Creates a new {@link DataName}.
     */
    public DataName {
    }

    /**
     * The interface for the type of a {@link DataName}.
     */
    public interface Type extends Name.Type {
        /**
         * The name of the type, that can be used in code.
         *
         * @return The {@link String} representation of the type, that can be used in code.
         */
        String name();

        /**
         * Defines the subtype relationship with other types, which is used to filter {@link DataName}s
         * in {@link FilteredSet#exactOf}, {@link FilteredSet#subtypeOf}, and {@link FilteredSet#supertypeOf}.
         *
         * @param other The other type, where we check if it is the supertype of {@code 'this'}.
         * @return If {@code 'this'} is a subtype of {@code 'other'}.
         */
        boolean isSubtypeOf(DataName.Type other);
    }

    /**
     * The {@link FilteredSet} represents a filtered set of {@link DataName}s in the current scope.
     * It can be obtained with {@link Template#dataNames}. It can be used to count the
     * available {@link DataName}s, or sample a random {@link DataName} according to the
     * weights of the {@link DataName}s in the filtered set.
     * Note: The {@link FilteredSet} is only a filtered view on the set of {@link DataName}s,
     * and may return different results in different contexts.
     */
    public static final class FilteredSet {
        private final Mutability mutability;
        private final DataName.Type subtype;
        private final DataName.Type supertype;

        FilteredSet(Mutability mutability, DataName.Type subtype, DataName.Type supertype) {
            this.mutability = mutability;
            this.subtype = subtype;
            this.supertype = supertype;
        }

        FilteredSet(Mutability mutability) {
            this(mutability, null, null);
        }

        NameSet.Predicate predicate() {
            if (subtype == null && supertype == null) {
                throw new UnsupportedOperationException("Must first call 'subtypeOf', 'supertypeOf', or 'exactOf'.");
            }
            return (Name name) -> {
                if (!(name instanceof DataName dataName)) { return false; }
                if (mutability == Mutability.MUTABLE && !dataName.mutable()) { return false; }
                if (mutability == Mutability.IMMUTABLE && dataName.mutable()) { return false; }
                if (subtype != null && !dataName.type().isSubtypeOf(subtype)) { return false; }
                if (supertype != null && !supertype.isSubtypeOf(dataName.type())) { return false; }
                return true;
            };
        }

        /**
         * Create a {@link FilteredSet}, where all {@link DataName}s must be subtypes of {@code type}.
         *
         * @param type The type of which all {@link DataName}s must be subtypes of.
         * @return The updated filtered set.
         * @throws UnsupportedOperationException If this {@link FilteredSet} was already filtered with
         *                                       {@link #subtypeOf} or {@link #exactOf}.
         */
        public FilteredSet subtypeOf(DataName.Type type) {
            if (subtype != null) {
                throw new UnsupportedOperationException("Cannot constrain to subtype " + type + ", is already constrained: " + subtype);
            }
            return new FilteredSet(mutability, type, supertype);
        }

        /**
         * Create a {@link FilteredSet}, where all {@link DataName}s must be supertypes of {@code type}.
         *
         * @param type The type of which all {@link DataName}s must be supertype of.
         * @return The updated filtered set.
         * @throws UnsupportedOperationException If this {@link FilteredSet} was already filtered with
         *                                       {@link #supertypeOf} or {@link #exactOf}.
         */
        public FilteredSet supertypeOf(DataName.Type type) {
            if (supertype != null) {
                throw new UnsupportedOperationException("Cannot constrain to supertype " + type + ", is already constrained: " + supertype);
            }
            return new FilteredSet(mutability, subtype, type);
        }

        /**
         * Create a {@link FilteredSet}, where all {@link DataName}s must be of exact {@code type},
         * hence it must be both subtype and supertype thereof.
         *
         * @param type The type of which all {@link DataName}s must be.
         * @return The updated filtered set.
         * @throws UnsupportedOperationException If this {@link FilteredSet} was already filtered with
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public FilteredSet exactOf(DataName.Type type) {
            return subtypeOf(type).supertypeOf(type);
        }

        /**
         * Samples a random {@link DataName} from the filtered set, according to the weights
         * of the contained {@link DataName}s.
         *
         * @return The sampled {@link DataName}.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         * @throws RendererException If the set was empty.
         */
        public DataName sample() {
            DataName n = (DataName)Renderer.getCurrent().sampleName(predicate());
            if (n == null) {
                String msg1 = (subtype == null) ? "" : ", subtypeOf(" + subtype + ")";
                String msg2 = (supertype == null) ? "" : ", supertypeOf(" + supertype + ")";
                throw new RendererException("No variable: " + mutability + msg1 + msg2 + ".");
            }
            return n;
        }

        /**
         * Counts the number of {@link DataName}s in the filtered set.
         *
         * @return The number of {@link DataName}s in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public int count() {
            return Renderer.getCurrent().countNames(predicate());
        }

        /**
         * Checks if there are any {@link DataName}s in the filtered set.
         *
         * @return Returns {@code true} iff there is at least one {@link DataName} in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public boolean hasAny() {
            return Renderer.getCurrent().hasAnyNames(predicate());
        }

        /**
         * Collects all {@link DataName}s in the filtered set.
         *
         * @return A {@link List} of all {@link DataName}s in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public List<DataName> toList() {
            List<Name> list = Renderer.getCurrent().listNames(predicate());
            return list.stream().map(n -> (DataName)n).toList();
        }
    }
}
