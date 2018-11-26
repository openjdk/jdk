/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


















package jdk.internal.vm.compiler.word;

// JaCoCo Exclude

/**
 * Marker interface for location identities. A different location identity of two memory accesses
 * guarantees that the two accesses do not interfere.
 * <p>
 * Clients of {@link LocationIdentity} must use {@link #equals(Object)}, not {@code ==}, when
 * comparing two {@link LocationIdentity} values for equality. Likewise, they must not use
 * {@link java.util.IdentityHashMap}s with {@link LocationIdentity} values as keys.
 *
 * @since 1.0
 */
public abstract class LocationIdentity {

    private static final class AnyLocationIdentity extends LocationIdentity {
        @Override
        public boolean isImmutable() {
            return false;
        }

        @Override
        public String toString() {
            return "ANY_LOCATION";
        }
    }

    private static final class InitLocationIdentity extends LocationIdentity {
        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public String toString() {
            return "INIT_LOCATION";
        }
    }

    /**
     * Creates a new location identity. Subclasses are responsible to provide proper implementations
     * of {@link #equals} and {@link #hashCode}.
     *
     * @since 1.0
     */
    protected LocationIdentity() {
    }

    /**
     * Indicates that the given location is the union of all possible mutable locations. A write to
     * such a location kill all reads from mutable locations and a read from this location is killed
     * by any write (except for initialization writes).
     *
     * @since 1.0
     */
    public static final LocationIdentity ANY_LOCATION = new AnyLocationIdentity();

    /**
     * Location only allowed to be used for writes. Indicates that a completely new memory location
     * is written. Kills no read. The previous value at the given location must be either
     * uninitialized or null. Writes to this location do not need a GC pre-barrier.
     *
     * @since 1.0
     */
    public static final LocationIdentity INIT_LOCATION = new InitLocationIdentity();

    /**
     * Indicates that the given location is the union of all possible mutable locations. A write to
     * such a location kill all reads from mutable locations and a read from this location is killed
     * by any write (except for initialization writes).
     *
     * @since 1.0
     */
    public static LocationIdentity any() {
        return ANY_LOCATION;
    }

    /**
     * Location only allowed to be used for writes. Indicates that a completely new memory location
     * is written. Kills no read. The previous value at the given location must be either
     * uninitialized or null. Writes to this location do not need a GC pre-barrier.
     *
     * @since 1.0
     */
    public static LocationIdentity init() {
        return INIT_LOCATION;
    }

    /**
     * Denotes a location is unchanging in all cases. Not that this is different than the Java
     * notion of final which only requires definite assignment.
     *
     * @since 1.0
     */
    public abstract boolean isImmutable();

    /**
     * The inversion of {@link #isImmutable}.
     *
     * @since 1.0
     */
    public final boolean isMutable() {
        return !isImmutable();
    }

    /**
     * Returns true if this location identity is {@link #any}.
     *
     * @since 1.0
     */
    public final boolean isAny() {
        return this == ANY_LOCATION;
    }

    /**
     * Returns true if this location identity is {@link #init}.
     *
     * @since 1.0
     */
    public final boolean isInit() {
        return this == INIT_LOCATION;
    }

    /**
     * Returns true if this location identity is not {@link #any}.
     *
     * @since 1.0
     */
    public final boolean isSingle() {
        return this != ANY_LOCATION;
    }

    /**
     * Returns true if the memory slice denoted by this location identity may overlap with the
     * provided other location identity.
     *
     * @since 1.0
     */
    public final boolean overlaps(LocationIdentity other) {
        return isAny() || other.isAny() || this.equals(other);
    }
}
