/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.Arrays;

import org.graalvm.compiler.core.common.type.TypeReference;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Utility for deriving hint types for a type check instruction (e.g. checkcast or instanceof) based
 * on the target type of the check and any profiling information available for the instruction.
 */
public class TypeCheckHints {

    /**
     * A receiver type profiled in a type check instruction.
     */
    public static class Hint {

        /**
         * A type seen while profiling a type check instruction.
         */
        public final ResolvedJavaType type;

        /**
         * Specifies if {@link #type} is a sub-type of the checked type.
         */
        public final boolean positive;

        Hint(ResolvedJavaType type, boolean positive) {
            this.type = type;
            this.positive = positive;
        }
    }

    private static final Hint[] NO_HINTS = {};

    /**
     * If non-null, then this is the only type that could pass the type check because the target of
     * the type check is a final class or has been speculated to be a final class and this value is
     * the only concrete subclass of the target type.
     */
    public final ResolvedJavaType exact;

    /**
     * The most likely types that the type check instruction will see.
     */
    public final Hint[] hints;

    /**
     * The profile from which this information was derived.
     */
    public final JavaTypeProfile profile;

    /**
     * The total probability that the type check will hit one of the types in {@link #hints}.
     */
    public final double hintHitProbability;

    /**
     * Derives hint information for use when generating the code for a type check instruction.
     *
     * @param targetType the target type of the type check
     * @param profile the profiling information available for the instruction (if any)
     * @param assumptions the object in which speculations are recorded. This is null if
     *            speculations are not supported.
     * @param minHintHitProbability if the probability that the type check will hit one of the
     *            profiled types (up to {@code maxHints}) is below this value, then {@link #hints}
     *            will be null
     * @param maxHints the maximum length of {@link #hints}
     */
    public TypeCheckHints(TypeReference targetType, JavaTypeProfile profile, Assumptions assumptions, double minHintHitProbability, int maxHints) {
        this.profile = profile;
        if (targetType != null && targetType.isExact()) {
            exact = targetType.getType();
        } else {
            exact = null;
        }
        Double[] hitProbability = {null};
        this.hints = makeHints(targetType, profile, minHintHitProbability, maxHints, hitProbability);
        this.hintHitProbability = hitProbability[0];
    }

    private static Hint[] makeHints(TypeReference targetType, JavaTypeProfile profile, double minHintHitProbability, int maxHints, Double[] hitProbability) {
        double hitProb = 0.0d;
        Hint[] hintsBuf = NO_HINTS;
        if (profile != null) {
            double notRecordedTypes = profile.getNotRecordedProbability();
            ProfiledType[] ptypes = profile.getTypes();
            if (notRecordedTypes < (1D - minHintHitProbability) && ptypes != null && ptypes.length > 0) {
                hintsBuf = new Hint[ptypes.length];
                int hintCount = 0;
                for (ProfiledType ptype : ptypes) {
                    if (targetType != null) {
                        ResolvedJavaType hintType = ptype.getType();
                        hintsBuf[hintCount++] = new Hint(hintType, targetType.getType().isAssignableFrom(hintType));
                        hitProb += ptype.getProbability();
                    }
                    if (hintCount == maxHints) {
                        break;
                    }
                }
                if (hitProb >= minHintHitProbability) {
                    if (hintsBuf.length != hintCount || hintCount > maxHints) {
                        hintsBuf = Arrays.copyOf(hintsBuf, Math.min(maxHints, hintCount));
                    }
                } else {
                    hintsBuf = NO_HINTS;
                    hitProb = 0.0d;
                }
            }
        }
        hitProbability[0] = hitProb;
        return hintsBuf;
    }
}
