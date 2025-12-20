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

        // Wrap the FilteredSet as a Predicate.
        private record DataNamePredicate(FilteredSet fs) implements NameSet.Predicate {
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
            return new DataNamePredicate(this);
        }

        boolean check(Name name) {
            if (!(name instanceof DataName dataName)) { return false; }
            if (mutability == Mutability.MUTABLE && !dataName.mutable()) { return false; }
            if (mutability == Mutability.IMMUTABLE && dataName.mutable()) { return false; }
            if (subtype != null && !dataName.type().isSubtypeOf(subtype)) { return false; }
            if (supertype != null && !supertype.isSubtypeOf(dataName.type())) { return false; }
            return true;
        }

        public String toString() {
            String msg1 = (subtype == null) ? "" : ", subtypeOf(" + subtype + ")";
            String msg2 = (supertype == null) ? "" : ", supertypeOf(" + supertype + ")";
            return "DataName.FilterdSet(" + mutability + msg1 + msg2 + ")";
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
         * of the contained {@link DataName}s, making the sampled {@link DataName}
         * available to an inner scope.
         *
         * @param function The {@link Function} that creates the inner {@link ScopeToken} given
         *                 the sampled {@link DataName}.
         * @return a token that represents the sampling and inner scope.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token sample(Function<DataName, ScopeToken> function) {
            return new NameSampleToken<>(predicate(), null, null, function);
        }

        /**
         * Samples a random {@link DataName} from the filtered set, according to the weights
         * of the contained {@link DataName}s, and makes a hashtag replacement for both
         * the name and type of the {@link DataName}, in the current scope.
         *
         * <p>
         * Note, that the following two do the equivalent:
         *
         * <p>
         * {@snippet lang=java :
         * var template = Template.make(() -> scope(
         *     dataNames(MUTABLE).subtypeOf(type).sampleAndLetAs("name", "type"),
         *     """
         *     #name #type
         *     """
         * ));
         * }
         *
         * <p>
         * {@snippet lang=java :
         * var template = Template.make(() -> scope(
         *     dataNames(MUTABLE).subtypeOf(type).sample((DataName dn) -> transparentScope(
         *         // The "let" hashtag definitions escape the "transparentScope".
         *         let("name", dn.name()),
         *         let("type", dn.type())
         *     )),
         *     """
         *     #name #type
         *     """
         * ));
         * }
         *
         * @param name the key of the hashtag replacement for the {@link DataName} name.
         * @param type the key of the hashtag replacement for the {@link DataName} type.
         * @return a token that represents the sampling and hashtag replacement definition.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token sampleAndLetAs(String name, String type) {
            return new NameSampleToken<DataName>(predicate(), name, type, n -> Template.transparentScope());
        }

        /**
         * Samples a random {@link DataName} from the filtered set, according to the weights
         * of the contained {@link DataName}s, and makes a hashtag replacement for the
         * name of the {@link DataName}, in the current scope.
         *
         * <p>
         * Note, that the following two do the equivalent:
         *
         * <p>
         * {@snippet lang=java :
         * var template = Template.make(() -> scope(
         *     dataNames(MUTABLE).subtypeOf(type).sampleAndLetAs("name"),
         *     """
         *     #name
         *     """
         * ));
         * }
         *
         * <p>
         * {@snippet lang=java :
         * var template = Template.make(() -> scope(
         *     dataNames(MUTABLE).subtypeOf(type).sample((DataName dn) -> transparentScope(
         *         // The "let" hashtag definition escape the "transparentScope".
         *         let("name", dn.name())
         *     )),
         *     """
         *     #name
         *     """
         * ));
         * }
         *
         * @param name the key of the hashtag replacement for the {@link DataName} name.
         * @return a token that represents the sampling and hashtag replacement definition.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token sampleAndLetAs(String name) {
            return new NameSampleToken<DataName>(predicate(), name, null, n -> Template.transparentScope());
        }

        /**
         * Counts the number of {@link DataName}s in the filtered set, making the count
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
         * Checks if there are any {@link DataName}s in the filtered set, making the resulting boolean
         * available to an inner scope.
         *
         * @param function The {@link Function} that creates the inner {@link ScopeToken} given
         *                 the boolean indicating iff there are any {@link DataName}s in the filtered set.
         * @return a token that represents the checking and inner scope.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token hasAny(Function<Boolean, ScopeToken> function) {
            return new NameHasAnyToken(predicate(), function);
        }

        /**
         * Collects all {@link DataName}s in the filtered set, making the collected list
         * available to an inner scope.
         *
         * @param function The {@link Function} that creates the inner {@link ScopeToken} given
         *                 the list of {@link DataName}.
         * @return A {@link List} of all {@link DataName}s in the filtered set.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token toList(Function<List<DataName>, ScopeToken> function) {
            return new NamesToListToken<>(predicate(), function);
        }

        /**
         * Calls the provided {@code function} for each {@link DataName}s in the filtered set,
         * making each of these {@link DataName}s available to a separate inner scope.
         *
         * @param function The {@link Function} that is called to create the inner {@link ScopeToken}s
         *                 for each of the {@link DataName}s in the filtered set.
         * @return The token representing the for-each execution and the respective inner scopes.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token forEach(Function<DataName, ScopeToken> function) {
            return new NameForEachToken<>(predicate(), null, null, function);
        }

        /**
         * Calls the provided {@code function} for each {@link DataName}s in the filtered set,
         * making each of these {@link DataName}s available to a separate inner scope, and additionally
         * setting hashtag replacements for the {@code name} and {@code type} of the respective
         * {@link DataName}s.
         *
         * <p>
         * Note, to avoid duplication of the {@code name} and {@code type}
         * hashtag replacements, the scope created by the provided {@code function} should be
         * non-transparent to hashtag replacements, for example {@link Template#scope} or
         * {@link Template#hashtagScope}.
         *
         * @param name the key of the hashtag replacement for each individual {@link DataName} name.
         * @param type the key of the hashtag replacement for each individual {@link DataName} type.
         * @param function The {@link Function} that is called to create the inner {@link ScopeToken}s
         *                 for each of the {@link DataName}s in the filtereds set.
         * @return The token representing the for-each execution and the respective inner scopes.
         * @throws UnsupportedOperationException If the type was not constrained with either of
         *                                       {@link #subtypeOf}, {@link #supertypeOf} or {@link #exactOf}.
         */
        public Token forEach(String name, String type, Function<DataName, ScopeToken> function) {
            return new NameForEachToken<>(predicate(), name, type, function);
        }
    }
}
