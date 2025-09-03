/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.constant;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.invoke.MethodHandles;

import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;

import static java.util.Objects.requireNonNull;

/**
 * A <a href="package-summary.html#nominal">nominal descriptor</a> for the class
 * constant corresponding to a primitive type (e.g., {@code int.class}).
 */
@AOTSafeClassInitializer // identity-sensitive static final fields
public final class PrimitiveClassDescImpl
        extends DynamicConstantDesc<Class<?>> implements ClassDesc {

    /** {@link ClassDesc} representing the primitive type {@code int} */
    public static final PrimitiveClassDescImpl CD_int = new PrimitiveClassDescImpl("I");

    /** {@link ClassDesc} representing the primitive type {@code long} */
    public static final PrimitiveClassDescImpl CD_long = new PrimitiveClassDescImpl("J");

    /** {@link ClassDesc} representing the primitive type {@code float} */
    public static final PrimitiveClassDescImpl CD_float = new PrimitiveClassDescImpl("F");

    /** {@link ClassDesc} representing the primitive type {@code double} */
    public static final PrimitiveClassDescImpl CD_double = new PrimitiveClassDescImpl("D");

    /** {@link ClassDesc} representing the primitive type {@code short} */
    public static final PrimitiveClassDescImpl CD_short = new PrimitiveClassDescImpl("S");

    /** {@link ClassDesc} representing the primitive type {@code byte} */
    public static final PrimitiveClassDescImpl CD_byte = new PrimitiveClassDescImpl("B");

    /** {@link ClassDesc} representing the primitive type {@code char} */
    public static final PrimitiveClassDescImpl CD_char = new PrimitiveClassDescImpl("C");

    /** {@link ClassDesc} representing the primitive type {@code boolean} */
    public static final PrimitiveClassDescImpl CD_boolean = new PrimitiveClassDescImpl("Z");

    /** {@link ClassDesc} representing the primitive type {@code void} */
    public static final PrimitiveClassDescImpl CD_void = new PrimitiveClassDescImpl("V");

    private final String descriptor;
    private @Stable Wrapper lazyWrapper; // initialized only after this

    /**
     * Creates a {@linkplain ClassDesc} given a descriptor string for a primitive
     * type.
     *
     * @param descriptor the descriptor string, which must be a one-character
     * string corresponding to one of the nine base types
     * @throws IllegalArgumentException if the descriptor string does not
     * describe a valid primitive type
     * @jvms 4.3 Descriptors
     */
    private PrimitiveClassDescImpl(String descriptor) {
        super(ConstantDescs.BSM_PRIMITIVE_CLASS, requireNonNull(descriptor), ConstantDescs.CD_Class);
        this.descriptor = descriptor;
    }

    public Wrapper wrapper() {
        var wrapper = this.lazyWrapper;
        if (wrapper != null)
            return wrapper;
        return this.lazyWrapper = Wrapper.forBasicType(descriptorString().charAt(0));
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public ClassDesc arrayType(int rank) {
        ConstantUtils.validateArrayRank(rank);
        if (this == CD_void)
            throw new IllegalArgumentException("not a valid reference type descriptor: " + "[".repeat(rank) + "V");
        return ArrayClassDescImpl.ofValidated(this, rank);
    }

    @Override
    public ClassDesc arrayType() {
        if (this == CD_void)
            throw new IllegalArgumentException("not a valid reference type descriptor: [V");
        return ArrayClassDescImpl.ofValidated(this, 1);
    }

    @Override
    public String displayName() {
        return wrapper().primitiveSimpleName();
    }

    @Override
    public String descriptorString() {
        return descriptor;
    }

    @Override
    public Class<?> resolveConstantDesc(MethodHandles.Lookup lookup) {
        return wrapper().primitiveType();
    }

    @Override
    public String toString() {
        return String.format("PrimitiveClassDesc[%s]", displayName());
    }
}
