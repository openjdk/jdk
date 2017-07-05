/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta;

/**
 * Abstract base class for values.
 */
public abstract class Value {

    public static final Value[] NO_VALUES = new Value[0];

    public static final AllocatableValue ILLEGAL = new IllegalValue();

    private static final class IllegalValue extends AllocatableValue {
        private IllegalValue() {
            super(LIRKind.Illegal);
        }

        @Override
        public String toString() {
            return "-";
        }

        @Override
        public boolean equals(Object other) {
            // Due to de-serialization this object may exist multiple times. So we compare classes
            // instead of the individual objects. (This anonymous class has always the same meaning)
            return other instanceof IllegalValue;
        }
    }

    private final LIRKind lirKind;

    /**
     * Initializes a new value of the specified kind.
     *
     * @param lirKind the kind
     */
    protected Value(LIRKind lirKind) {
        this.lirKind = lirKind;
    }

    /**
     * Returns a String representation of the kind, which should be the end of all
     * {@link #toString()} implementation of subclasses.
     */
    protected final String getKindSuffix() {
        return "|" + getPlatformKind().getTypeChar();
    }

    public final LIRKind getLIRKind() {
        return lirKind;
    }

    /**
     * Returns the platform specific kind used to store this value.
     */
    public final PlatformKind getPlatformKind() {
        return lirKind.getPlatformKind();
    }

    @Override
    public int hashCode() {
        return 41 + lirKind.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Value) {
            Value that = (Value) obj;
            return lirKind.equals(that.lirKind);
        }
        return false;
    }

    /**
     * Checks if this value is identical to {@code other}.
     *
     * Warning: Use with caution! Usually equivalence {@link #equals(Object)} is sufficient and
     * should be used.
     */
    public final boolean identityEquals(Value other) {
        return this == other;
    }
}
