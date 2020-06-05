/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.api.directives.GraalDirectives.LIKELY_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.UNLIKELY_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.SLOWPATH_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude

/**
 * Substitutions for {@code java.lang.StringLatin1} methods.
 *
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringLatin1", optional = true)
public class AMD64StringLatin1Substitutions {

    @Fold
    static int byteArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Byte);
    }

    @Fold
    static int byteArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Byte);
    }

    @Fold
    static int charArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Char);
    }

    @Fold
    static int charArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Char);
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final MetaAccessProvider INJECTED = null;

    /**
     * @param value is byte[]
     * @param other is byte[]
     */
    @MethodSubstitution
    public static int compareTo(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Byte, JavaKind.Byte);
    }

    /**
     * @param value is byte[]
     * @param other is char[]
     */
    @MethodSubstitution
    public static int compareToUTF16(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Byte, JavaKind.Char);
    }

    private static Word pointer(byte[] target) {
        return Word.objectToTrackedPointer(target).add(byteArrayBaseOffset(INJECTED));
    }

    private static Word byteOffsetPointer(byte[] source, int offset) {
        return pointer(source).add(offset * byteArrayIndexScale(INJECTED));
    }

    @MethodSubstitution
    public static int indexOf(byte[] value, int ch, int origFromIndex) {
        int fromIndex = origFromIndex;
        if (injectBranchProbability(UNLIKELY_PROBABILITY, ch >>> 8 != 0)) {
            // search value must be a byte value
            return -1;
        }
        int length = value.length;
        if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex < 0)) {
            fromIndex = 0;
        } else if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex >= length)) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }
        return AMD64ArrayIndexOf.indexOf1Byte(value, length, fromIndex, (byte) ch);
    }

    @MethodSubstitution
    public static int indexOf(byte[] source, int sourceCount, byte[] target, int targetCount, int origFromIndex) {
        int fromIndex = origFromIndex;
        if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex >= sourceCount)) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex < 0)) {
            fromIndex = 0;
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 0)) {
            // The empty string is in every string.
            return fromIndex;
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, sourceCount - fromIndex < targetCount)) {
            // The empty string contains nothing except the empty string.
            return -1;
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 1)) {
            return AMD64ArrayIndexOf.indexOf1Byte(source, sourceCount, fromIndex, target[0]);
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            int offset = fromIndex;
            while (injectBranchProbability(LIKELY_PROBABILITY, offset < haystackLength)) {
                int indexOfResult = AMD64ArrayIndexOf.indexOfTwoConsecutiveBytes(source, haystackLength, offset, target[0], target[1]);
                if (injectBranchProbability(UNLIKELY_PROBABILITY, indexOfResult < 0)) {
                    return -1;
                }
                offset = indexOfResult;
                if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 2)) {
                    return offset;
                } else {
                    Pointer cmpSourcePointer = byteOffsetPointer(source, offset);
                    Pointer targetPointer = pointer(target);
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, ArrayRegionEqualsNode.regionEquals(cmpSourcePointer, targetPointer, targetCount, JavaKind.Byte))) {
                        return offset;
                    }
                }
                offset++;
            }
            return -1;
        }
    }

    /**
     * Intrinsic for {@code java.lang.StringLatin1.inflate([BI[CII)V}.
     *
     * <pre>
     * &#64;HotSpotIntrinsicCandidate
     * public static void inflate(byte[] src, int src_indx, char[] dst, int dst_indx, int len)
     * </pre>
     */
    @MethodSubstitution
    public static void inflate(byte[] src, int srcIndex, char[] dest, int destIndex, int len) {
        if (injectBranchProbability(SLOWPATH_PROBABILITY, len < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex + len > src.length) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex + len > dest.length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }

        // Offset calc. outside of the actual intrinsic.
        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * byteArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(charArrayBaseOffset(INJECTED)).add(destIndex * charArrayIndexScale(INJECTED));
        AMD64StringLatin1InflateNode.inflate(srcPointer, destPointer, len, JavaKind.Char);
    }

    /**
     * Intrinsic for {@code }java.lang.StringLatin1.inflate([BI[BII)V}.
     *
     * <pre>
     * &#64;HotSpotIntrinsicCandidate
     * public static void inflate(byte[] src, int src_indx, byte[] dst, int dst_indx, int len)
     * </pre>
     *
     * In this variant {@code dest} refers to a byte array containing 2 byte per char so
     * {@code destIndex} and {@code len} are in terms of char elements and have to be scaled by 2
     * when referring to {@code dest}
     */
    @MethodSubstitution
    public static void inflate(byte[] src, int srcIndex, byte[] dest, int destIndex, int len) {
        if (injectBranchProbability(SLOWPATH_PROBABILITY, len < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex + len > src.length) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex * 2 + len * 2 > dest.length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }

        // Offset calc. outside of the actual intrinsic.
        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * byteArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * 2 * byteArrayIndexScale(INJECTED));
        AMD64StringLatin1InflateNode.inflate(srcPointer, destPointer, len, JavaKind.Byte);
    }

}
