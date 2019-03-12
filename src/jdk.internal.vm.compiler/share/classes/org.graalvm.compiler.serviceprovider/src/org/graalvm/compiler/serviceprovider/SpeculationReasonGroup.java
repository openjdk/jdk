/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.serviceprovider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Facility for creating speculation reasons partitioned in groups.
 */
public final class SpeculationReasonGroup {

    private final int id;
    private final String name;
    private final Class<?>[] signature;

    private static final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Creates speculation group whose context will always match {@code signature}.
     */
    public SpeculationReasonGroup(String name, Class<?>... signature) {
        this.id = nextId.get();
        this.name = name;
        this.signature = signature;
        for (Class<?> c : signature) {
            if (!isOfSupportedType(c)) {
                throw new IllegalArgumentException("Unsupported speculation context type: " + c.getName());
            }
        }
    }

    @Override
    public String toString() {
        return String.format("%s{id:%d, sig=%s}", name, id, Arrays.toString(signature));
    }

    /**
     * Creates a speculation reason described by this group.
     *
     * @param context the components of the reason instance being created
     */
    public SpeculationReason createSpeculationReason(Object... context) {
        assert checkSignature(context);
        return GraalServices.createSpeculationReason(id, name, context);
    }

    private static final Set<Class<?>> SUPPORTED_EXACT_TYPES = new HashSet<>(Arrays.asList(
                    String.class,
                    int.class,
                    long.class,
                    float.class,
                    double.class,
                    BytecodePosition.class));

    private static boolean isOfSupportedType(Class<?> c) {
        if (SUPPORTED_EXACT_TYPES.contains(c)) {
            return true;
        }
        if (Enum.class.isAssignableFrom(c)) {
            // Trust the ordinal of an Enum to be unique
            return true;
        }
        if (SpeculationContextObject.class.isAssignableFrom(c)) {
            return true;
        }
        if (ResolvedJavaMethod.class.isAssignableFrom(c) || ResolvedJavaType.class.isAssignableFrom(c)) {
            // Only the JVMCI implementation specific concrete subclasses
            // of these types will be accepted but we cannot test for that
            // here since we are in JVMCI implementation agnostic code.
            return true;
        }
        return false;
    }

    static Class<?> toBox(Class<?> c) {
        if (c == int.class) {
            return Integer.class;
        }
        if (c == long.class) {
            return Long.class;
        }
        if (c == float.class) {
            return Float.class;
        }
        if (c == double.class) {
            return Double.class;
        }
        return c;
    }

    private boolean checkSignature(Object[] context) {
        assert signature.length == context.length : name + ": Incorrect number of context arguments. Expected " + signature.length + ", got " + context.length;
        for (int i = 0; i < context.length; i++) {
            Object o = context[i];
            Class<?> c = signature[i];
            if (o != null) {
                if (c == ResolvedJavaMethod.class || c == ResolvedJavaType.class || SpeculationContextObject.class.isAssignableFrom(c)) {
                    c.cast(o);
                } else {
                    Class<?> oClass = o.getClass();
                    assert toBox(c) == oClass : name + ": Context argument " + i + " is not a " + c.getName() + " but a " + oClass.getName();
                }
            } else {
                if (c.isPrimitive() || Enum.class.isAssignableFrom(c)) {
                    throw new AssertionError(name + ": Cannot pass null for argument " + i);
                }
            }
        }
        return true;
    }

    /**
     * Denotes part of a {@linkplain SpeculationReasonGroup#createSpeculationReason(Object...)
     * reason} that can have its attributes {@linkplain #accept(Visitor) visited}.
     */
    public interface SpeculationContextObject {
        void accept(Visitor v);

        public interface Visitor {
            void visitBoolean(boolean v);

            void visitByte(byte v);

            void visitChar(char v);

            void visitShort(short v);

            void visitInt(int v);

            void visitLong(long v);

            void visitFloat(float v);

            void visitDouble(double v);

            void visitObject(Object v);
        }
    }
}
