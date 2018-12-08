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


package org.graalvm.compiler.core.common.type;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A stamp is the basis for a type system.
 */
public abstract class Stamp {

    protected Stamp() {
    }

    /**
     * Returns the type of the stamp, guaranteed to be non-null. In some cases, this requires the
     * lookup of class meta data, therefore the {@link MetaAccessProvider} is mandatory.
     */
    public abstract ResolvedJavaType javaType(MetaAccessProvider metaAccess);

    public boolean alwaysDistinct(Stamp other) {
        return join(other).isEmpty();
    }

    /**
     * Gets a Java {@link JavaKind} that can be used to store a value of this stamp on the Java
     * bytecode stack. Returns {@link JavaKind#Illegal} if a value of this stamp can not be stored
     * on the bytecode stack.
     */
    public abstract JavaKind getStackKind();

    /**
     * Gets a platform dependent {@link LIRKind} that can be used to store a value of this stamp.
     */
    public abstract LIRKind getLIRKind(LIRKindTool tool);

    /**
     * Returns the union of this stamp and the given stamp. Typically used to create stamps for phi
     * nodes.
     *
     * @param other The stamp that will enlarge this stamp.
     * @return The union of this stamp and the given stamp.
     */
    public abstract Stamp meet(Stamp other);

    /**
     * Returns the intersection of this stamp and the given stamp.
     *
     * @param other The stamp that will tighten this stamp.
     * @return The intersection of this stamp and the given stamp.
     */
    public abstract Stamp join(Stamp other);

    /**
     * Returns a stamp of the same kind, but allowing the full value range of the kind.
     *
     * {@link #unrestricted()} is the neutral element of the {@link #join(Stamp)} operation.
     */
    public abstract Stamp unrestricted();

    /**
     * Returns a stamp of the same kind, but with no allowed values.
     *
     * {@link #empty()} is the neutral element of the {@link #meet(Stamp)} operation.
     */
    public abstract Stamp empty();

    /**
     * If it is possible to represent single value stamps of this kind, this method returns the
     * stamp representing the single value c. stamp.constant(c).asConstant() should be equal to c.
     * <p>
     * If it is not possible to represent single value stamps, this method returns a stamp that
     * includes c, and is otherwise as narrow as possible.
     */
    public abstract Stamp constant(Constant c, MetaAccessProvider meta);

    /**
     * Test whether two stamps have the same base type.
     */
    public abstract boolean isCompatible(Stamp other);

    /**
     * Check that the constant {@code other} is compatible with this stamp.
     *
     * @param constant
     */
    public abstract boolean isCompatible(Constant constant);

    /**
     * Test whether this stamp has legal values.
     */
    public abstract boolean hasValues();

    /**
     * Tests whether this stamp represents an illegal value.
     */
    public final boolean isEmpty() {
        return !hasValues();
    }

    /**
     * Tests whether this stamp represents all values of this kind.
     */
    public boolean isUnrestricted() {
        return this.equals(this.unrestricted());
    }

    /**
     * If this stamp represents a single value, the methods returns this single value. It returns
     * null otherwise.
     *
     * @return the constant corresponding to the single value of this stamp and null if this stamp
     *         can represent less or more than one value.
     */
    public Constant asConstant() {
        return null;
    }

    /**
     * Read a value of this stamp from memory.
     *
     * @return the value read or null if the value can't be read for some reason.
     */
    public abstract Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement);

    /**
     * Tries to improve this stamp with the stamp given as parameter. If successful, returns the new
     * improved stamp. Otherwise, returns a stamp equal to this.
     *
     * @param other the stamp that should be used to improve this stamp
     * @return the newly improved stamp or a stamp equal to {@code this} if an improvement was not
     *         possible
     */
    public abstract Stamp improveWith(Stamp other);

    /**
     * Tries to improve this stamp with the stamp given as parameter. If successful, returns the new
     * improved stamp. Otherwise, returns null.
     *
     * @param other the stamp that should be used to improve this stamp
     * @return the newly improved stamp or {@code null} if an improvement was not possible
     */
    public final Stamp tryImproveWith(Stamp other) {
        Stamp improved = improveWith(other);
        if (improved.equals(this)) {
            return null;
        }
        return improved;
    }

    public boolean neverDistinct(Stamp other) {
        Constant constant = this.asConstant();
        if (constant != null) {
            Constant otherConstant = other.asConstant();
            return otherConstant != null && constant.equals(otherConstant);
        }
        return false;
    }
}
