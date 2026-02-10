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
import java.util.function.Function;

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

        // Wrap the FilteredSet as a Predicate.
        private record StructuralNamePredicate(FilteredSet fs) implements NameSet.Predicate {
            public boolean check(Name type) {
                return fs.check(type);
            }
            public String toString() {
                return fs.toString();
            }
        }

        NameSet.Predicate predicate() {
            if (subtype == null && supertype == null) {
                throw new UnsupportedOperationException("Must first call 'subtypeOf', 'supertypeOf', or 'exactOf'.");
            }
            return new StructuralNamePredicate(this);
        }

        boolean check(Name name) {
            if (!(name instanceof StructuralName structuralName)) { return false; }
            if (subtype != null && !structuralName.type().isSubtypeOf(subtype)) { return false; }
            if (supertype != null && !supertype.isSubtypeOf(structuralName.type())) { return false; }
            return true;
        }

        public String toString() {
            String msg1 = (subtype == null) ? "" : " subtypeOf(" + subtype + ")";
            String msg2 = (supertype == null) ? "" : " supertypeOf(" + supertype + ")";
            return "StructuralName.FilteredSet(" + msg1 + msg2 + ")";
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
         * of the contained {@link StructuralName}s, making the sampled {@link StructuralName}
         * available to an inner scope.
         *
         * @param function The {@link Function} that creates the inner {@link ScopeToken} given
         *                 the sampled {@link StructuralName}.
         * @return a token that represents the sampling and inner scope.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token sample(Function<StructuralName, ScopeToken> function) {
            return new NameSampleToken<>(predicate(), null, null, function);
        }

        /**
         * Samples a random {@link StructuralName} from the filtered set, according to the weights
         * of the contained {@link StructuralName}s, and makes a hashtag replacement for both
         * the name and type of the {@link StructuralName}, in the current scope.
         *
         * @param name the key of the hashtag replacement for the {@link StructuralName} name.
         * @param type the key of the hashtag replacement for the {@link StructuralName} type.
         * @return a token that represents the sampling and hashtag replacement definition.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token sampleAndLetAs(String name, String type) {
            return new NameSampleToken<StructuralName>(predicate(), name, type, n -> Template.transparentScope());
        }

        /**
         * Samples a random {@link StructuralName} from the filtered set, according to the weights
         * of the contained {@link StructuralName}s, and makes a hashtag replacement for the
         * name of the {@link StructuralName}, in the current scope.
         *
         * @param name the key of the hashtag replacement for the {@link StructuralName} name.
         * @return a token that represents the sampling and hashtag replacement definition.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token sampleAndLetAs(String name) {
            return new NameSampleToken<StructuralName>(predicate(), name, null, n -> Template.transparentScope());
        }

        /**
         * Counts the number of {@link StructuralName}s in the filtered set, making the count
         * available to an inner scope.
         *
         * @param function The {@link Function} that creates the inner {@link ScopeToken} given
         *                 the count.
         * @return a token that represents the counting and inner scope.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token count(Function<Integer, ScopeToken> function) {
            return new NameCountToken(predicate(), function);
        }

        /**
         * Checks if there are any {@link StructuralName}s in the filtered set, making the resulting boolean
         * available to an inner scope.
         *
         * @param function The {@link Function} that creates the inner {@link ScopeToken} given
         *                 the boolean indicating iff there are any {@link StructuralName}s in the filtered set.
         * @return a token that represents the checking and inner scope.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token hasAny(Function<Boolean, ScopeToken> function) {
            return new NameHasAnyToken(predicate(), function);
        }
        /**
         * Collects all {@link StructuralName}s in the filtered set, making the collected list
         * available to an inner scope.
         *
         * @param function The {@link Function} that creates the inner {@link ScopeToken} given
         *                 the list of {@link StructuralName}.
         * @return A {@link List} of all {@link StructuralName}s in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token toList(Function<List<StructuralName>, ScopeToken> function) {
            return new NamesToListToken<>(predicate(), function);
        }

        /**
         * Calls the provided {@code function} for each {@link StructuralName}s in the filtered set,
         * making each of these {@link StructuralName}s available to a separate inner scope.
         *
         * @param function The {@link Function} that is called to create the inner {@link ScopeToken}s
         *                 for each of the {@link StructuralName}s in the filtereds set.
         * @return The token representing the for-each execution and the respective inner scopes.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token forEach(Function<StructuralName, ScopeToken> function) {
            return new NameForEachToken<>(predicate(), null, null, function);
        }

        /**
         * Calls the provided {@code function} for each {@link StructuralName}s in the filtered set,
         * making each of these {@link StructuralName}s available to a separate inner scope, and additionally
         * setting hashtag replacements for the {@code name} and {@code type} of the respective
         * {@link StructuralName}s.
         *
         * <p>
         * Note, to avoid duplication of the {@code name} and {@code type}
         * hashtag replacements, the scope created by the provided {@code function} should be
         * non-transparent to hashtag replacements, for example {@link Template#scope} or
         * {@link Template#hashtagScope}.
         *
         * @param name the key of the hashtag replacement for each individual {@link StructuralName} name.
         * @param type the key of the hashtag replacement for each individual {@link StructuralName} type.
         * @param function The {@link Function} that is called to create the inner {@link ScopeToken}s
         *                 for each of the {@link StructuralName}s in the filtereds set.
         * @return The token representing the for-each execution and the respective inner scopes.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token forEach(String name, String type, Function<StructuralName, ScopeToken> function) {
            return new NameForEachToken<>(predicate(), name, type, function);
        }
    }
}
