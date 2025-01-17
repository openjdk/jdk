/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm.vector;

import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * The class {@code Float16Math} constains intrinsic entry points corresponding
 * to scalar numeric operations defined in Float16 class.
 * @since   25
 */
public final class Float16Math {
    private Float16Math() {
    }

    public interface Float16UnaryMathOp {
        Object apply(Object a);
    }

    public interface Float16TernaryMathOp {
        Object apply(Object a, Object b, Object c);
    }

    @IntrinsicCandidate
    public static Object sqrt(Class<?> box_class, Object oa, Float16UnaryMathOp defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(oa);
    }

    @IntrinsicCandidate
    public static Object fma(Class<?> box_class, Object oa, Object ob, Object oc, Float16TernaryMathOp defaultImpl) {
        assert isNonCapturingLambda(defaultImpl) : defaultImpl;
        return defaultImpl.apply(oa, ob, oc);
    }

    public static boolean isNonCapturingLambda(Object o) {
        return o.getClass().getDeclaredFields().length == 0;
    }
}
