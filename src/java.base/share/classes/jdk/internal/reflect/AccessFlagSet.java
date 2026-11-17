/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jdk.internal.vm.annotation.Stable;

import static java.lang.reflect.AccessFlag.*;

/// An access flag set is an optimized immutable set backed by an integer mask
/// and a "definition" that interprets each bit in that mask.
/// This set has a well-defined iteration order from the least significant bit
/// to the most significant bit, is null-hostile, and throws UOE for any
/// modification operation, like the other unmodifiable collections.
///
/// The "definition" is an array of 16 entries, the maximum number of different
/// access flags a classfile `u2 access_flags` field can represent.
/// Given an access flag bit, we can look up the array entry corresponding to
/// its bit position to find the corresponding AccessFlag.
///
/// The definition can vary by location and class file format of the
/// access_flags field.
/// If a bit position does not have an access flag defined, its corresponding
/// array entry is `null`.
public final class AccessFlagSet extends AbstractSet<AccessFlag> {

    public static final @Stable AccessFlag[]
            CLASS_FLAGS = createDefinition(PUBLIC, FINAL, SUPER, INTERFACE, ABSTRACT, SYNTHETIC, ANNOTATION, ENUM, MODULE),
            FIELD_FLAGS = createDefinition(PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, VOLATILE, TRANSIENT, SYNTHETIC, ENUM),
            METHOD_FLAGS = createDefinition(PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, SYNCHRONIZED, BRIDGE, VARARGS, NATIVE, ABSTRACT, STRICT, SYNTHETIC),
            INNER_CLASS_FLAGS = createDefinition(PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, INTERFACE, ABSTRACT, SYNTHETIC, ANNOTATION, ENUM),
            METHOD_PARAMETER_FLAGS = createDefinition(FINAL, SYNTHETIC, MANDATED),
            MODULE_FLAGS = createDefinition(OPEN, SYNTHETIC, MANDATED),
            MODULE_REQUIRES_FLAGS = createDefinition(TRANSITIVE, STATIC_PHASE, SYNTHETIC, MANDATED),
            MODULE_EXPORTS_FLAGS = createDefinition(SYNTHETIC, MANDATED),
            MODULE_OPENS_FLAGS = createDefinition(SYNTHETIC, MANDATED);

    /// Finds a definition that works for access flags for a classfile location for the latest class file format.
    /// @see #findDefinition(Location, ClassFileFormatVersion)
    public static AccessFlag[] findDefinition(Location location) {
        return findDefinition(location, ClassFileFormatVersion.latest());
    }

    /// Finds a definition that works for access flags for a classfile location for the given class file format.
    /// The definition may define extraneous flags not present in the given class file format, so do not derive
    /// the mask of defined flags from this definition.
    public static AccessFlag[] findDefinition(Location location, ClassFileFormatVersion cffv) {
        Objects.requireNonNull(cffv);
        // implicit null check location
        return switch (location) {
            case CLASS -> CLASS_FLAGS;
            case FIELD -> FIELD_FLAGS;
            case METHOD -> METHOD_FLAGS;
            case INNER_CLASS -> INNER_CLASS_FLAGS;
            case METHOD_PARAMETER -> METHOD_PARAMETER_FLAGS;
            case MODULE -> MODULE_FLAGS;
            case MODULE_REQUIRES -> MODULE_REQUIRES_FLAGS;
            case MODULE_EXPORTS -> MODULE_EXPORTS_FLAGS;
            case MODULE_OPENS -> MODULE_OPENS_FLAGS;
        };
    }

    /// Converts an array of defined access flags to a proper "definition".
    /// Users no longer have to explicitly assign access flags to their correct bit positions.
    static AccessFlag[] createDefinition(AccessFlag... known) {
        var ret = new AccessFlag[Character.SIZE];
        for (var flag : known) {
            var mask = flag.mask();
            int pos = Integer.numberOfTrailingZeros(mask);
            assert ret[pos] == null : ret[pos] + " duplicates " + flag;
            ret[pos] = flag;
        }
        return ret;
    }

    /// Creates an access flag set with a mask where every set bit corresponds
    /// to an access flag in the definition.
    /// The mask must be validated: if any bit in the mask does not have a
    /// corresponding access flag, this set will be corrupted.
    public static Set<AccessFlag> ofValidated(AccessFlag[] definition, int mask) {
        return new AccessFlagSet(definition, mask);
    }

    // Minimal mask validation for a definition.
    // In practice, more bits are usually rejected due to context like class file versions.
    private static int undefinedMask(AccessFlag[] definition, int mask) {
        assert definition.length == Character.SIZE;
        int definedMask = 0;
        for (int i = 0; i < Character.SIZE; i++) {
            if (definition[i] != null) {
                definedMask |= 1 << i;
            }
        }
        return mask & ~definedMask;
    }

    private final @Stable AccessFlag[] definition;
    private final int mask;

    // all mutating methods throw UnsupportedOperationException
    @Override public boolean add(AccessFlag e) { throw uoe(); }
    @Override public boolean addAll(Collection<? extends AccessFlag> c) { throw uoe(); }
    @Override public void    clear() { throw uoe(); }
    @Override public boolean remove(Object o) { throw uoe(); }
    @Override public boolean removeAll(Collection<?> c) { throw uoe(); }
    @Override public boolean removeIf(Predicate<? super AccessFlag> filter) { throw uoe(); }
    @Override public boolean retainAll(Collection<?> c) { throw uoe(); }
    private static UnsupportedOperationException uoe() { return new UnsupportedOperationException(); }

    private AccessFlagSet(AccessFlag[] definition, int mask) {
        assert undefinedMask(definition, mask) == 0 : mask;
        this.definition = definition;
        this.mask = mask;
    }

    @Override
    public Iterator<AccessFlag> iterator() {
        return new AccessFlagIterator(definition, mask);
    }

    @Override
    public void forEach(Consumer<? super AccessFlag> action) {
        Objects.requireNonNull(action); // in case of empty
        for (int i = 0; i < Character.SIZE; i++) {
            if ((mask & (1 << i)) != 0) {
                action.accept(definition[i]);
            }
        }
    }

    private static final class AccessFlagIterator implements Iterator<AccessFlag> {
        private final @Stable AccessFlag[] definition;
        private int remainingMask;

        private AccessFlagIterator(AccessFlag[] definition, int remainingMask) {
            this.definition = definition;
            this.remainingMask = remainingMask;
        }

        @Override
        public boolean hasNext() {
            return remainingMask != 0;
        }

        @Override
        public AccessFlag next() {
            int flagBit = Integer.lowestOneBit(remainingMask);
            if (flagBit == 0) {
                throw new NoSuchElementException();
            }
            remainingMask &= ~flagBit;
            return definition[Integer.numberOfTrailingZeros(flagBit)];
        }
    }

    @Override
    public int size() {
        return Integer.bitCount(mask);
    }

    @Override
    public boolean contains(Object o) {
        if (Objects.requireNonNull(o) instanceof AccessFlag flag) {
            int bit = flag.mask();
            return (bit & mask) != 0 && definition[Integer.numberOfTrailingZeros(bit)] == flag;
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return mask == 0;
    }
}
