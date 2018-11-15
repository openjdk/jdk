/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude

/**
 * Substitutions for {@code java.lang.StringUTF16} methods.
 *
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringUTF16", optional = true)
public class AMD64StringUTF16Substitutions {

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
     * @param value is char[]
     * @param other is char[]
     */
    @MethodSubstitution
    public static int compareTo(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Char, JavaKind.Char);
    }

    /**
     * @param value is char[]
     * @param other is byte[]
     */
    @MethodSubstitution
    public static int compareToLatin1(byte[] value, byte[] other) {
        /*
         * Swapping array arguments because intrinsic expects order to be byte[]/char[] but kind
         * arguments stay in original order.
         */
        return ArrayCompareToNode.compareTo(other, value, other.length, value.length, JavaKind.Char, JavaKind.Byte);
    }

    @MethodSubstitution(optional = true)
    public static int indexOfCharUnsafe(byte[] value, int ch, int fromIndex, int max) {
        Pointer sourcePointer = Word.objectToTrackedPointer(value).add(byteArrayBaseOffset(INJECTED)).add(fromIndex * charArrayIndexScale(INJECTED));
        int result = AMD64ArrayIndexOf.indexOf1Char(sourcePointer, max - fromIndex, (char) ch);
        if (result != -1) {
            return result + fromIndex;
        }
        return result;
    }

    /**
     * Intrinsic for {@code java.lang.StringUTF16.compress([CI[BII)I}.
     *
     * <pre>
     * &#64;HotSpotIntrinsicCandidate
     * public static int compress(char[] src, int src_indx, byte[] dst, int dst_indx, int len)
     * </pre>
     */
    @MethodSubstitution
    public static int compress(char[] src, int srcIndex, byte[] dest, int destIndex, int len) {
        if (len < 0 || srcIndex < 0 || (srcIndex + len > src.length) || destIndex < 0 || (destIndex + len > dest.length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }

        Pointer srcPointer = Word.objectToTrackedPointer(src).add(charArrayBaseOffset(INJECTED)).add(srcIndex * charArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * byteArrayIndexScale(INJECTED));
        return AMD64StringUTF16CompressNode.compress(srcPointer, destPointer, len, JavaKind.Char);
    }

    /**
     * Intrinsic for {@code }java.lang.StringUTF16.compress([BI[BII)I}.
     *
     * <pre>
     * &#64;HotSpotIntrinsicCandidate
     * public static int compress(byte[] src, int src_indx, byte[] dst, int dst_indx, int len)
     * </pre>
     *
     * In this variant {@code dest} refers to a byte array containing 2 byte per char so
     * {@code srcIndex} and {@code len} are in terms of char elements and have to be scaled by 2
     * when referring to {@code src}.
     */
    @MethodSubstitution
    public static int compress(byte[] src, int srcIndex, byte[] dest, int destIndex, int len) {
        if (len < 0 || srcIndex < 0 || (srcIndex * 2 + len * 2 > src.length) || destIndex < 0 || (destIndex + len > dest.length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }

        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * 2 * byteArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * byteArrayIndexScale(INJECTED));
        return AMD64StringUTF16CompressNode.compress(srcPointer, destPointer, len, JavaKind.Byte);
    }

}
