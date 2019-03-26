/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigBase.INJECTED_VMCONFIG;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyCallNode;

// JaCoCo Exclude

/**
 * Substitutions for {@code StringUTF16} methods for JDK9 and later.
 */
@ClassSubstitution(className = "java.lang.StringUTF16", optional = true)
public class StringUTF16Substitutions {

    private static final int MAX_LENGTH = Integer.MAX_VALUE >> 1;

    @MethodSubstitution
    public static byte[] toBytes(char[] value, int srcBegin, int length) {
        if (probability(SLOW_PATH_PROBABILITY, srcBegin < 0) ||
                        probability(SLOW_PATH_PROBABILITY, length < 0) ||
                        probability(SLOW_PATH_PROBABILITY, length > MAX_LENGTH) ||
                        probability(SLOW_PATH_PROBABILITY, srcBegin > value.length - length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
        byte[] val = (byte[]) NewArrayNode.newUninitializedArray(Byte.TYPE, length << 1);
        // the intrinsic does not perform bounds/type checks, so it can be used here.
        // Using KillsAny variant since we are reading and writing 2 different types.
        ArrayCopyCallNode.disjointArraycopyKillsAny(value, srcBegin, val, 0, length, JavaKind.Char, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return val;
    }

    @MethodSubstitution
    public static void getChars(byte[] value, int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        int length = srcEnd - srcBegin;
        if (probability(SLOW_PATH_PROBABILITY, srcBegin < 0) ||
                        probability(SLOW_PATH_PROBABILITY, length < 0) ||
                        probability(SLOW_PATH_PROBABILITY, srcBegin > (value.length >> 1) - length) ||
                        probability(SLOW_PATH_PROBABILITY, dstBegin > dst.length - length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
        // The intrinsic does not perform bounds/type checks, so it can be used here.
        // Using KillsAny variant since we are reading and writing 2 different types.
        ArrayCopyCallNode.disjointArraycopyKillsAny(value, srcBegin, dst, dstBegin, length, JavaKind.Char, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
    }
}
